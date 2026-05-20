package zsl.agent.funtions;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;
import zsl.agent.config.AiToolMethod;
import zsl.agent.entry.BackgroundTask;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 后台任务管理器（复刻Python版 BackgroundManager）
 * 异步执行shell命令，持久化任务状态，支持超时/通知/查询
 */
@Component
public class BackGroundTask {
    //  ===================== 常量配置（与Python原版一致） =====================
    private static final Path RUNTIME_DIR = Paths.get(".runtime");
    private static final Path WORKDIR = Paths.get(System.getProperty("user.dir"));
    private static final long TIMEOUT_SECONDS = 300;
    private static final int MAX_OUTPUT_LENGTH = 50000;
    private static final int PREVIEW_LIMIT = 500;

    //  ===================== 核心成员变量 =====================
    private final Map<String, BackgroundTask> tasks;
    private final Queue<Map<String, Object>> notificationQueue;
    private final ReentrantLock lock;
    private final ExecutorService executorService;

    // 初始化：创建目录、初始化线程/集合
    public BackGroundTask() {
        try {
            Files.createDirectories(RUNTIME_DIR);
        } catch (Exception e) {
            throw new RuntimeException("创建运行时目录失败", e);
        }
        this.tasks = new ConcurrentHashMap<>();
        this.notificationQueue = new ConcurrentLinkedQueue<>();
        this.lock = new ReentrantLock();
        // 守护线程池，主程序退出自动关闭
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }
    // ===================== 核心工具方法 =====================
    /** 任务JSON文件路径 */
    private Path recordPath(String taskId) {
        return RUNTIME_DIR.resolve(taskId + ".json");
    }

    /** 任务日志文件路径 */
    private Path outputPath(String taskId) {
        return RUNTIME_DIR.resolve(taskId + ".log");
    }
    /** 持久化任务到JSON文件 */
    private void persistTask(String taskId) {
        try {
            BackgroundTask task = tasks.get(taskId);
            String json = JSONUtil.toJsonStr(task);
//          覆盖写入
            Files.writeString(recordPath(taskId), json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /** 输出内容预览（压缩空格 + 截断） */
    private String preview(String output) {

        String compact = output.replaceAll("\\s+", " ").trim();
        return compact.length() > PREVIEW_LIMIT ? compact.substring(0, PREVIEW_LIMIT) : compact;
    }

    // ===================== 核心业务方法 =====================
    /**
     * 启动后台任务，立即返回taskId（复刻Python run方法）
     */
    @AiToolMethod(name = "bg_run", desc = "启动后台任务")
    public String run(JSONObject params) {
        String command = params.getStr("command");
        // 生成8位taskId
        String taskId = IdUtil.simpleUUID().substring(0, 8);
        Path outputFile = outputPath(taskId).toAbsolutePath(); // 强制绝对路径
        String relativeOutputPath = WORKDIR.toAbsolutePath().relativize(outputFile).toString();

        // 初始化任务
        BackgroundTask task = new BackgroundTask();
//         设置任务属性
        task.setId(taskId);
        task.setStatus("running");
        task.setResult(null);
        task.setCommand(command);
        task.setStartedAt(System.currentTimeMillis() / 1000);
        task.setFinishedAt(null);
        task.setResultPreview("");
        task.setOutputFile(relativeOutputPath);

        tasks.put(taskId, task);
        persistTask(taskId);

        // 提交后台线程执行命令
        executorService.submit(() -> execute(taskId, command));

        // 返回启动信息
        return String.format("Background task %s started: %s (output_file=%s)",
                taskId,
                command.length() > 80 ? command.substring(0, 80) : command,
                relativeOutputPath);
    }

    /**
     * 线程执行体：执行shell命令（复刻Python _execute）
     */
    private void execute(String taskId, String command) {
        BackgroundTask task = tasks.get(taskId);
        String output;
        String status;

        try {
            // 执行shell命令，超时控制
            Process process = new ProcessBuilder("cmd", "/c", command)
                    .directory(WORKDIR.toFile())
                    .redirectErrorStream(true)
                    .start();

            // 超时等待执行完成
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                output = "Error: Timeout (300s)";
                status = "timeout";
            } else {
                // 读取输出
                output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                // 截断最大长度
                if (output.length() > MAX_OUTPUT_LENGTH) {
                    output = output.substring(0, MAX_OUTPUT_LENGTH);
                }
                status = "completed";
            }
        } catch (Exception e) {
            output = "Error: " + e.getMessage();
            status = "error";
        }

       String finaloutput = StrUtil.isEmpty(output) ? "(no output)" : output;
        // 收尾处理
        String preview = preview(finaloutput);
        Path outputPath = outputPath(taskId);

        // 写入日志文件
        try {
            Files.writeString(outputPath, finaloutput, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 更新任务状态
        task.setStatus(status);
        task.setResult(finaloutput);
        task.setFinishedAt(System.currentTimeMillis() / 1000);
        task.setResultPreview(preview);
        persistTask(taskId);

        // 加入通知队列（线程安全）
        lock.lock();
        try {
            Map<String, Object> notif = new HashMap<>();
            notif.put("task_id", taskId);
            notif.put("status", status);
            notif.put("command", command.length() > 80 ? command.substring(0, 80) : command);
            notif.put("preview", preview);
            notif.put("output_file", task.getOutputFile());
            notificationQueue.offer(notif);
        } finally {
            lock.unlock();
        }
    }
    /**
     * 查询任务状态（单个/全部，复刻Python check）
     */
    @AiToolMethod(name = "bg_check", desc = "查询任务状态")
    public String check(JSONObject params) {
        String taskId = params.getStr("task_id");
        if (StrUtil.isNotEmpty(taskId)) {
            BackgroundTask task = tasks.get(taskId);
            if (task == null) {
                return "Error: Unknown task " + taskId;
            }
            // 构建可见字段
            Map<String, Object> visible = new HashMap<>();
            visible.put("id", task.getId());
            visible.put("status", task.getStatus());
            visible.put("command", task.getCommand());
            visible.put("result_preview", task.getResultPreview());
            visible.put("output_file", task.getOutputFile());
            return JSONUtil.toJsonPrettyStr(visible);
        }

        // 列出所有任务
        List<String> lines = new ArrayList<>();
        tasks.forEach((tid, t) -> {
            String cmd = t.getCommand().length() > 60 ? t.getCommand().substring(0, 60) : t.getCommand();
            String preview = StrUtil.isEmpty(t.getResultPreview()) ? "(running)" : t.getResultPreview();
            lines.add(String.format("%s: [%s] %s -> %s", tid, t.getStatus(), cmd, preview));
        });

        return lines.isEmpty() ? "No background tasks." : String.join("\n", lines);
    }

    /**
     * 清空并返回所有通知（复刻Python drain_notifications）
     */
    @AiToolMethod(name = "bg_drain", desc = "清空并返回所有通知")
    public List<Map<String, Object>> drainNotifications() {
        lock.lock();
        try {
            List<Map<String, Object>> notifs = new ArrayList<>(notificationQueue);
            notificationQueue.clear();
            return notifs;
        } finally {
            lock.unlock();
        }
    }
}