package zsl.agent.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Set;

public class CronConstants {
    // 锁文件路径
    public static final Path CRON_LOCK_FILE = Paths.get(System.getProperty("user.dir"), ".claude", "cron.lock");
    // 周期性任务自动过期天数
    public static final int AUTO_EXPIRY_DAYS = 7;
    // 需要避开的分钟数
    public static final Set<Integer> JITTER_MINUTES = Set.of(0, 30);
    // 抖动偏移最大分钟数
    public static final int JITTER_OFFSET_MAX = 4;
    // 持久化任务文件路径 例如当前目录/.claude/scheduled_tasks
    public static final Path SCHEDULED_TASKS_FILE = Paths.get(System.getProperty("user.dir"), ".claude", "scheduled_tasks.json");
    // Cron表达式字段数
    public static final int CRON_FIELD_COUNT = 5;
}