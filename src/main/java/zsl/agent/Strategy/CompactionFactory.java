package zsl.agent.Strategy;

import org.springframework.stereotype.Component;
import zsl.agent.entry.compactState;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class CompactionFactory {
    /**
     * 统一压缩入口
     * @param type         策略类型：full / sliding_window / rolling
     * @param compactState 压缩状态
     * @param messages     消息历史
     * @param focus        可选的焦点说明（null 则不带）
     * @param params       策略参数，如 keepRounds、compactEvery 等
     */
    public List<Map<String, Object>> compact(
            String type,
            compactState compactState,
            List<Map<String, Object>> messages,
            String focus,
            Object... params) throws Exception {

        HistoryCompactionStrategy strategy = createStrategy(type, params);
        return strategy.compact(messages,compactState, focus);
    }
    private HistoryCompactionStrategy createStrategy(String type, Object... params) {
        return switch (type.toLowerCase()) {
            case "full" -> new FullCompactionStrategy();
            case "sliding_window" -> {
                int keep = params.length > 0 ? (int) params[0] : 5;
                yield new SlidingWindowCompactionStrategy(keep);
            }
            case "rolling" -> {
                int every = params.length > 0 ? (int) params[0] : 10;
                int keep = params.length > 1 ? (int) params[1] : 5;
                yield new RollingCompactionStrategy(every, keep);
            }
            default -> throw new IllegalArgumentException("Unknown compaction type: " + type);
        };
    }
}