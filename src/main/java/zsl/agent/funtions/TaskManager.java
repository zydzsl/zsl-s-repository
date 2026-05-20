package zsl.agent.funtions;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;
import zsl.agent.config.AiToolMethod;
import zsl.agent.entry.Task;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


import static zsl.agent.utils.ToolConstants.WORKDIR;


/**
 * 持久化任务管理器
 * 磁盘级工作图谱，非临时内存数据
 */
@Component
public class TaskManager {
    private int nextId;
    public static final Path TASK_DIR = WORKDIR.resolve(".task");

    // 任务状态常量（与Python完全一致）
    private static final Set<String> VALID_STATUS = new HashSet<>(
            Arrays.asList("pending", "in_progress", "completed", "deleted")
    );
    /**
     * 构造方法：初始化任务目录
     */
    public TaskManager( ) {
        try {
            Files.createDirectories(TASK_DIR);
        } catch (IOException e) {
            throw new RuntimeException("创建任务目录失败", e);
        }
        this.nextId = maxId() + 1;
    }

    /**
     * 获取当前最大任务ID
     */
    private int maxId() {
        try {
            return Files.list(TASK_DIR)
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.startsWith("task_") && name.endsWith(".json"))
                    .map(name -> name.replace("task_", "").replace(".json", ""))
                    .filter(StrUtil::isNumeric)
                    .mapToInt(Integer::parseInt)
                    .max()
                    .orElse(0);
        } catch (IOException e) {
            return 0;
        }
    }
    /**
     * 从磁盘加载任务
     */
    private Task loadTask(int taskId) {
        Path path = TASK_DIR.resolve(String.format("task_%d.json", taskId));
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Task " + taskId + " not found");
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return JSONUtil.toBean(json, Task.class);
        } catch (IOException e) {
            throw new RuntimeException("加载任务失败", e);
        }
    }
    /**
     * 保存任务到磁盘
     */
    private void saveTask(Task task) {
        Path path = TASK_DIR.resolve(String.format("task_%d.json", task.getId()));
        try {
            String json = JSONUtil.toJsonStr(task);
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("保存任务失败", e);
        }
    }
    /**
     * 创建任务
     */
    @AiToolMethod(name = "task_create", desc = "创建一个任务")
    public String create(JSONObject params) {
        String subject = params.getStr("subject");
        String description = params.getStr("description");
        Task task = new Task();
        task.setId(nextId);
        task.setSubject(subject);
        task.setDescription(description);
        task.setStatus("pending");
        task.setBlockedBy(new ArrayList<>());
        task.setBlocks(new ArrayList<>());
        task.setOwner("");

        saveTask(task);
        nextId++;
        return JSONUtil.toJsonStr(task);
    }

    /**
     * 获取单个任务
     */
    @AiToolMethod(name = "task_get", desc = "获取单个任务")
    public String get(JSONObject params) {
        int taskId = params.getInt("task_id");
        Task task = loadTask(taskId);
        return JSONUtil.toJsonStr(task);
    }

    /**
     * 更新任务：状态、所有者、依赖关系
     */
    @AiToolMethod(name = "task_update", desc = "更新任务状态、所有者、依赖关系")
    public String update(JSONObject params) {
        int taskId = params.getInt("task_id");
        String status = params.getStr("status");
        String owner = params.getStr("owner", "");
        List<Integer> addBlockedBy = new ArrayList<>();
        JSONArray addBlockedBy1 = params.getJSONArray("addBlockedBy");
        if (addBlockedBy1 != null){
            addBlockedBy = addBlockedBy1.toList(Integer.class);
        }
        List<Integer> addBlocks = new ArrayList<>();
        JSONArray addBlocks1 = params.getJSONArray("addBlocks");
        if (addBlocks1 != null){
            addBlocks = addBlocks1.toList(Integer.class);
        }
        Task task = loadTask(taskId);

        // 更新所有者
        if (owner != null) {
            task.setOwner(owner);
        }

        // 更新状态
        if (status != null) {
            if (!VALID_STATUS.contains(status)) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
            task.setStatus(status);
            // 任务完成 → 清理所有依赖
            if ("completed".equals(status)) {
                clearDependency(taskId);
            }
        }
        // 更新 blockedBy（去重）
        if (addBlockedBy != null && !addBlockedBy.isEmpty()) {
            Set<Integer> merged = new HashSet<>(task.getBlockedBy());
            merged.addAll(addBlockedBy);
            task.setBlockedBy(new ArrayList<>(merged));
        }
        // 更新 blocks（双向依赖）
        if (addBlocks != null && !addBlocks.isEmpty()) {
            Set<Integer> merged = new HashSet<>(task.getBlocks());
            merged.addAll(addBlocks);
            task.setBlocks(new ArrayList<>(merged));
            // 双向更新：被依赖的任务添加 blockedBy
            for (int blockedId : addBlocks) {
                try {
                    Task blocked = loadTask(blockedId);
                    if (!blocked.getBlockedBy().contains(taskId)) {
                        blocked.getBlockedBy().add(taskId);
                        saveTask(blocked);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        saveTask(task);
        return JSONUtil.toJsonStr(task);
    }
    /**
     * 清理依赖：从所有任务的 blockedBy 中移除已完成的任务ID
     */
    private void clearDependency(int completedId) {
        try {
            Files.list(TASK_DIR)
                    .filter(f -> f.getFileName().toString().startsWith("task_") && f.getFileName().toString().endsWith(".json"))
                    .forEach(f -> {
                        try {
                            String json = Files.readString(f, StandardCharsets.UTF_8);
                            Task task = JSONUtil.toBean(json, Task.class);
                            List<Integer> blockedBy = task.getBlockedBy();
                            if (blockedBy != null && blockedBy.contains(completedId)) {
                                blockedBy.remove(Integer.valueOf(completedId));
                                saveTask(task);
                            }
                        } catch (Exception ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    /**
     * 列出所有任务（格式化输出，与Python完全一致）
     */
    @AiToolMethod(name = "task_list", desc = "列出所有任务")
    public String listAll(JSONObject params) {
        List<Task> tasks = new ArrayList<>();

        // 1. 先判断任务目录是否存在，不存在直接返回
        if (!Files.exists(TASK_DIR)) {
            return "No tasks.(任务目录不存在)";
        }

        // 2. try-with-resources 自动关闭流，修复资源泄漏
        try (var pathStream = Files.list(TASK_DIR)) {
            List<Path> files = pathStream
                    .filter(Files::isRegularFile)
                    .sorted()
                    .collect(Collectors.toList());

            for (Path f : files) {
                String name = f.getFileName().toString();
                // 匹配任务文件
                if (name.startsWith("task_") && name.endsWith(".json")) {
                    try {
                        String json = Files.readString(f, StandardCharsets.UTF_8);
                        // 3. 安全解析JSON，失败跳过文件，不崩溃
                        Task task = JSONUtil.toBean(json, Task.class);
                        if (task != null) {
                            tasks.add(task);
                        }
                    } catch (Exception ignored) {
                        // 单个文件损坏，跳过，不影响整体列表
                    }
                }
            }
        } catch (IOException e) {
            return "获取任务失败：" + e.getMessage();
        }

        // 无任务返回
        if (tasks.isEmpty()) {
            return "No tasks.";
        }

        // 状态标记
        Map<String, String> markerMap = new HashMap<>();
        markerMap.put("pending", "[ ]");
        markerMap.put("in_progress", "[>]");
        markerMap.put("completed", "[x]");
        markerMap.put("deleted", "[-]");

        List<String> lines = new ArrayList<>();
        for (Task t : tasks) {
            // 4. 空值安全处理
            String marker = markerMap.getOrDefault(t.getStatus() == null ? "" : t.getStatus(), "[?]");
            String blocked = "";
            if (t.getBlockedBy() != null && !t.getBlockedBy().isEmpty()) {
                blocked = " (blocked by: " + t.getBlockedBy() + ")";
            }
            String owner = StrUtil.isNotBlank(t.getOwner()) ? " owner=" + t.getOwner() : "";

            // 防止subject为空
            String subject = StrUtil.nullToEmpty(t.getSubject());
            lines.add(String.format("%s #%d: %s%s%s",
                    marker, t.getId(), subject, owner, blocked));
        }

        return String.join("\n", lines);
    }
}