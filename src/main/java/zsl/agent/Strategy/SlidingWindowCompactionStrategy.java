package zsl.agent.Strategy;

import zsl.agent.entry.compactState;
import zsl.agent.utils.CompactUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SlidingWindowCompactionStrategy implements HistoryCompactionStrategy {
    private final int keepRounds;
    public SlidingWindowCompactionStrategy(int keepRounds) {
        this.keepRounds = keepRounds;
    }
    @Override
    public List<Map<String, Object>> compact(List<Map<String, Object>> messages, compactState compactState, String focus) throws Exception {
        CompactUtils.writeTranscript(messages);

        int keepCount = keepRounds * 2; // 假设每轮 user+assistant
        if (messages.size() <= keepCount) {
            return messages; // 无需压缩
        }
        List<Map<String, Object>> recent = messages.subList(messages.size() - keepCount, messages.size());
        List<Map<String, Object>> old = messages.subList(0, messages.size() - keepCount);
        String summary;
        try {
            summary = CompactUtils.summarizeHistory(old);
        } catch (Exception e) {
            throw new RuntimeException("总结旧历史失败", e);
        }
        summary = CompactUtils.enrichSummary(summary, focus, compactState.getRecentFiles());
        CompactUtils.updateCompactionState(compactState, summary);

        List<Map<String, Object>> result = new ArrayList<>(CompactUtils.wrapSummaryAsUser(summary));
        result.addAll(recent);
        return result;
    }
}
