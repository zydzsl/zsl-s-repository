package zsl.agent.Strategy;// FullCompactionStrategy.java


import zsl.agent.Strategy.HistoryCompactionStrategy;
import zsl.agent.entry.compactState;
import zsl.agent.utils.CompactUtils;


import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class FullCompactionStrategy implements HistoryCompactionStrategy {


    @Override
    public List<Map<String, Object>> compact(List<Map<String, Object>> messages, compactState compactState, String focus) throws Exception {
        Path transcriptPath = CompactUtils.writeTranscript(messages);
        System.out.println("[transcript saved: " + transcriptPath.toAbsolutePath() + "]");

        String summary;
        try {
            summary = CompactUtils.summarizeHistory(messages);
        } catch (Exception e) {
            throw new RuntimeException("总结文本失败", e);
        }

        summary = CompactUtils.enrichSummary(summary, focus, compactState.getRecentFiles());
        CompactUtils.updateCompactionState(compactState, summary);
        return CompactUtils.wrapSummaryAsUser(summary);
    }
}