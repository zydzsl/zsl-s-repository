package zsl.agent.utils;

import zsl.agent.utils.CronConstants;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;

public class CronLock {
    private final Path lockPath;

    public CronLock() {
        this(CronConstants.CRON_LOCK_FILE);
    }

    public CronLock(Path lockPath) {
        this.lockPath = lockPath;
    }

    /**
     * 获取进程锁
     * @return true=获取成功 false=已有进程持有锁
     */
    public boolean acquire() {
        try {
            // 锁文件存在，校验PID是否存活
            if (Files.exists(lockPath)) {
                String pidStr = Files.readString(lockPath).trim();
                long pid = Long.parseLong(pidStr);
                // 检查进程是否存活（Java 9+ API）
                boolean processAlive = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
                if (processAlive) {
                    return false;
                }
                // 进程已死，删除过期锁
                Files.deleteIfExists(lockPath);
            }

            // 创建目录并写入当前PID
            Files.createDirectories(lockPath.getParent());
            String currentPid = String.valueOf(ProcessHandle.current().pid());
            Files.writeString(lockPath, currentPid, StandardOpenOption.CREATE);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 释放锁（仅当前进程创建的锁）
     */
    public void release() {
        try {
            if (!Files.exists(lockPath)) {
                return;
            }
            long currentPid = ProcessHandle.current().pid();
            long storedPid = Long.parseLong(Files.readString(lockPath).trim());
            if (currentPid == storedPid) {
                Files.deleteIfExists(lockPath);
            }
        } catch (Exception ignored) {}
    }
}