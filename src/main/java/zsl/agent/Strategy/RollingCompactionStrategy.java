package zsl.agent.Strategy;

import zsl.agent.entry.compactState;
import zsl.agent.utils.CompactUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RollingCompactionStrategy implements HistoryCompactionStrategy {

    private final int compactEvery;  // 触发阈值（用户消息轮次数）
    private final int keepRecent;    // 保留最近轮次

    public RollingCompactionStrategy(int compactEvery, int keepRecent) {
        this.compactEvery = compactEvery;
        this.keepRecent = keepRecent;
    }
    @Override
    public List<Map<String, Object>> compact(List<Map<String, Object>> messages, compactState compactState, String focus) throws Exception {
        int totalTurns = countUserTurns(messages);
        if (totalTurns < compactEvery) {
            return messages; // 未达到压缩条件
        }

        int keepMessages = keepRecent * 2;
        if (messages.size() <= keepMessages) {
            return messages;
        }

        List<Map<String, Object>> recent = messages.subList(messages.size() - keepMessages, messages.size());
        List<Map<String, Object>> old = messages.subList(0, messages.size() - keepMessages);

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
    private int countUserTurns(List<Map<String, Object>> messages) {
        return (int) messages.stream()
                .filter(m -> "user".equals(m.get("role")))
                .count();
    }

}
