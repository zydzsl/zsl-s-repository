package zsl.agent.utils;

import zsl.agent.utils.CronConstants;

import java.time.LocalDateTime;

public class CronExpressionMatcher {

    /**
     * 校验Cron表达式是否匹配指定时间（对齐Python原版逻辑）
     */
    public static boolean matches(String cronExpr, LocalDateTime dt) {
        String[] fields = cronExpr.trim().split("\\s+");
        if (fields.length != CronConstants.CRON_FIELD_COUNT) {
            return false;
        }

        // 取值：分钟、小时、日、月、周
        int minute = dt.getMinute();
        int hour = dt.getHour();
        int day = dt.getDayOfMonth();
        int month = dt.getMonthValue();
        // Python Cron：0=周日 | Java：1=周一 7=周日 → 转换为统一格式
        int cronDow = dt.getDayOfWeek().getValue() % 7;

        int[] values = {minute, hour, day, month, cronDow};
        int[][] ranges = {{0, 59}, {0, 23}, {1, 31}, {1, 12}, {0, 6}};

        for (int i = 0; i < fields.length; i++) {
            if (!fieldMatches(fields[i], values[i], ranges[i][0], ranges[i][1])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 匹配单个Cron字段
     */
    private static boolean fieldMatches(String field, int value, int min, int max) {
        if ("*".equals(field)) {
            return true;
        }

        String[] parts = field.split(",");
        for (String part : parts) {
            int step = 1;
            String current = part;

            // 处理步长 */N 或 N-M/S
            if (current.contains("/")) {
                String[] stepSplit = current.split("/", 2);
                current = stepSplit[0];
                step = Integer.parseInt(stepSplit[1]);
            }

            if ("*".equals(current)) {
                if ((value - min) % step == 0) {
                    return true;
                }
            } else if (current.contains("-")) {
                // 处理范围 N-M
                String[] rangeSplit = current.split("-", 2);
                int start = Integer.parseInt(rangeSplit[0]);
                int end = Integer.parseInt(rangeSplit[1]);
                if (value >= start && value <= end && (value - start) % step == 0) {
                    return true;
                }
            } else {
                // 精确值匹配
                if (Integer.parseInt(current) == value) {
                    return true;
                }
            }
        }
        return false;
    }
}