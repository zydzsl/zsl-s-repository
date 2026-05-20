package zsl.agent.entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BackgroundTask {
    private String id;
    private String status;
    private String result;
    private String command;
    private Long startedAt;
    private Long finishedAt;
    private String resultPreview;
    private String outputFile;
}
