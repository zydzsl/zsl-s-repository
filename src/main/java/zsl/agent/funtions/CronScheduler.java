package zsl.agent.funtions;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jdk.jfr.Category;
import org.springframework.stereotype.Component;
import zsl.agent.config.AiToolMethod;
import zsl.agent.entry.CronSchedulerTask;
import zsl.agent.utils.CronConstants;
import zsl.agent.utils.CronExpressionMatcher;
import zsl.agent.utils.CronLock;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class CronScheduler {
    // 任务列表（使用你指定的包装类）
    private final List<CronSchedulerTask> tasks = new CopyOnWriteArrayList<>();
    // 通知队列
    private final BlockingQueue<String> notificationQueue = new LinkedBlockingQueue<>();
    // 后台调度线程
    private ScheduledExecutorService schedulerExecutor;
    // 停止标志
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    // 防止同一分钟重复触发
    private volatile int lastCheckMinute = -1;
    // 进程锁
    private final CronLock cronLock;
    // JSON序列化工具
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public CronScheduler() {
        this.cronLock = new CronLock();
    }

    /**
     * 启动调度器
     */
    public void start() {
        if (!stopped.compareAndSet(false, false)) {
            return;
        }
        // 获取进程锁
        if (!cronLock.acquire()) {
            System.out.println("[Cron] 另一个进程已持有锁，调度器启动失败");
            return;
        }
        // 加载持久化任务
        loadDurableTasks();
        System.out.println("[Cron] 加载持久化任务中...");
        // 启动后台检查线程（每秒执行一次）
        schedulerExecutor = Executors.newSingleThreadScheduledExecutor();
        schedulerExecutor.scheduleAtFixedRate(this::checkLoop, 0, 1, TimeUnit.SECONDS);
        System.out.println("[Cron] 调度器启动成功，加载任务数：" + tasks.size());
    }
    /**
     * 停止调度器
     */
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            if (schedulerExecutor != null) {
                schedulerExecutor.shutdown();
            }
            cronLock.release();
            System.out.println("[Cron] 调度器已停止");
        }
    }
    /**
     * 创建任务
     */
    @AiToolMethod(name = "cron_create", desc = "创建一个定时任务")
    public String createTask(JSONObject params) {
        String cronExpr = params.getStr("cron");
        String prompt = params.getStr("prompt");
        boolean recurring = params.getBool("recurring");
        boolean durable = params.getBool("durable");
        CronSchedulerTask task = new CronSchedulerTask();
        task.setId(UUID.randomUUID().toString().substring(0, 8));
        task.setCron(cronExpr);
        task.setPrompt(prompt);
        task.setRecurring(recurring);
        task.setDurable(durable);
        task.setCreatedAt(System.currentTimeMillis());

        // 计算抖动偏移
        if (recurring) {
            task.setJitterOffset(computeJitter(cronExpr));
        }

        tasks.add(task);
        if (durable) {
            saveDurableTasks();
        }

        String mode = recurring ? "周期性" : "一次性";
        String store = durable ? "持久化" : "内存";
        return String.format("创建任务成功 %s (%s/%s) cron=%s",
                task.getId(), mode, store, cronExpr);
    }

    /**
     * 删除任务
     */
    @AiToolMethod(name = "cron_delete", desc = "删除一个定时任务")
    public String deleteTask(JSONObject params) {
        String taskId = params.getStr("id");
        boolean removed = tasks.removeIf(t -> taskId.equals(t.getId()));
        if (removed) {
            saveDurableTasks();
            return "删除任务成功：" + taskId;
        }
        return "任务不存在：" + taskId;
    }

    /**
     * 列出所有任务
     */
    @AiToolMethod(name = "cron_list", desc = "列出所有定时任务")
    public String listTasks(JSONObject params) {
        if (tasks.isEmpty()) {
            System.out.println("暂无定时任务");
            return "暂无定时任务";
        }
        StringBuilder sb = new StringBuilder();
        for (CronSchedulerTask task : tasks) {
            long ageHours = (System.currentTimeMillis() - task.getCreatedAt()) / 3600000;
            String mode = task.isRecurring() ? "周期性" : "一次性";
            System.out.println(mode);
            String store = task.isDurable() ? "持久化" : "内存";
            sb.append(String.format("  %s  %s  [%s/%s] (%.1f小时前): %s%n",
                    task.getId(), task.getCron(), mode, store,
                    (double) ageHours, task.getPrompt()));
        }
        System.out.println(sb.toString());
        return sb.toString();
    }

    public String listTasks() {
        if (tasks.isEmpty()) {
            return "暂无定时任务";
        }
        StringBuilder sb = new StringBuilder();
        for (CronSchedulerTask task : tasks) {
            long ageHours = (System.currentTimeMillis() - task.getCreatedAt()) / 3600000;
            String mode = task.isRecurring() ? "周期性" : "一次性";
            String store = task.isDurable() ? "持久化" : "内存";
            sb.append(String.format("  %s  %s  [%s/%s] (%.1f小时前): %s%n",
                    task.getId(), task.getCron(), mode, store,
                    (double) ageHours, task.getPrompt()));
        }
        return sb.toString();
    }

    /**
     * 清空并获取所有通知
     */
    public List<String> drainNotifications() {
        List<String> notifications = new ArrayList<>();
        notificationQueue.drainTo(notifications);
        return notifications;
    }

    /**
     * 检测启动期间漏执行的任务
     */
    public List<Map<String, Object>> detectMissedTasks() {
        List<Map<String, Object>> missed = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (CronSchedulerTask task : tasks) {
            Long lastFired = task.getLastFired();
            if (lastFired == null) continue;

            LocalDateTime lastDt = new Date(lastFired).toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime check = lastDt.plusMinutes(1);
            LocalDateTime cap = now.isAfter(lastDt.plusHours(24)) ? lastDt.plusHours(24) : now;

            while (check.isBefore(cap) || check.isEqual(cap)) {
                if (CronExpressionMatcher.matches(task.getCron(), check)) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", task.getId());
                    map.put("cron", task.getCron());
                    map.put("prompt", task.getPrompt());
                    map.put("missedAt", check.toString());
                    missed.add(map);
                    break;
                }
                check = check.plusMinutes(1);
            }
        }
        return missed;
    }

    // ==================== 私有核心逻辑 ====================
    /**
     * 计算抖动偏移（避开0/30分）
     */
    private int computeJitter(String cronExpr) {
        String[] fields = cronExpr.trim().split("\\s+");
        if (fields.length == 0) return 0;
        try {
            int minute = Integer.parseInt(fields[0]);
            if (CronConstants.JITTER_MINUTES.contains(minute)) {
                // 哈希取模生成固定偏移（1-4分钟）
                return (cronExpr.hashCode() % CronConstants.JITTER_OFFSET_MAX) + 1;
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    /**
     * 每秒循环检查
     */
    private void checkLoop() {
        if (stopped.get()) return;
        LocalDateTime now = LocalDateTime.now();
        int currentMinute = now.getHour() * 60 + now.getMinute();

        // 每分钟仅检查一次
        if (currentMinute != lastCheckMinute) {
            lastCheckMinute = currentMinute;
            checkTasks(now);
        }
    }

    /**
     * 检查所有任务是否触发
     */
    private void checkTasks(LocalDateTime now) {
        List<String> expiredIds = new ArrayList<>();
        List<String> firedOneShotIds = new ArrayList<>();

        for (CronSchedulerTask task : tasks) {
            // 自动过期：周期性任务超过7天
            double ageDays = (System.currentTimeMillis() - task.getCreatedAt()) / (86400000.0);
            if (task.isRecurring() && ageDays > CronConstants.AUTO_EXPIRY_DAYS) {
                expiredIds.add(task.getId());
                continue;
            }

            // 应用抖动偏移
            LocalDateTime checkTime = now;
            int jitter = task.getJitterOffset();
            if (jitter > 0) {
                checkTime = now.minus(jitter, ChronoUnit.MINUTES);
            }

            // 匹配Cron表达式
            if (CronExpressionMatcher.matches(task.getCron(), checkTime)) {
                String msg = String.format("""
定时任务触发，请执行以下指令。如需查询天气等信息，请直接调用工具。
指令：%s
""", task.getPrompt());
                notificationQueue.offer(msg);
                task.setLastFired(System.currentTimeMillis());
//                System.out.println("[Cron] 触发任务：" + task.getId());
                // 一次性任务执行后删除
                if (!task.isRecurring()) {
                    firedOneShotIds.add(task.getId());
                }
            }
        }

        // 清理过期/一次性任务
        if (!expiredIds.isEmpty() || !firedOneShotIds.isEmpty()) {
            Set<String> removeIds = new HashSet<>();
            removeIds.addAll(expiredIds);
            removeIds.addAll(firedOneShotIds);
            tasks.removeIf(t -> removeIds.contains(t.getId()));

            expiredIds.forEach(id -> System.out.printf("[Cron] 任务自动过期：%s（超过%d天）%n", id, CronConstants.AUTO_EXPIRY_DAYS));
            firedOneShotIds.forEach(id -> System.out.println("[Cron] 一次性任务执行完成：" + id));
            saveDurableTasks();
        }
    }

    /**
     * 加载持久化任务
     */
    private void loadDurableTasks() {
        try {
            if (!Files.exists(CronConstants.SCHEDULED_TASKS_FILE)) {
                return;
            }
            CronSchedulerTask[] array = OBJECT_MAPPER.readValue(
                    Files.readString(CronConstants.SCHEDULED_TASKS_FILE),
                    CronSchedulerTask[].class
            );
            tasks.clear();
            tasks.addAll(Arrays.asList(array));
        } catch (Exception e) {
            System.err.println("[Cron] 加载持久化任务失败：" + e.getMessage());
        }
    }

    /**
     * 保存持久化任务
     */
    private void saveDurableTasks() {
        try {
            List<CronSchedulerTask> durableTasks = tasks.stream()
                    .filter(CronSchedulerTask::isDurable)
                    .toList();
            Files.createDirectories(CronConstants.SCHEDULED_TASKS_FILE.getParent());
            Files.writeString(CronConstants.SCHEDULED_TASKS_FILE,
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(durableTasks));
        } catch (Exception e) {
            System.err.println("[Cron] 保存持久化任务失败：" + e.getMessage());
        }
    }
}