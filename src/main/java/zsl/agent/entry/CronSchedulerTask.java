package zsl.agent.entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CronSchedulerTask {
    private String id;
    private String cron;
    private String prompt;
    private boolean recurring;
    private boolean durable;
    private long createdAt;
    private long lastFired;
    private int jitterOffset;
}
