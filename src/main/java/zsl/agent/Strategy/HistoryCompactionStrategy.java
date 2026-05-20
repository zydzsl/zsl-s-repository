package zsl.agent.Strategy;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import zsl.agent.entry.compactState;

public interface HistoryCompactionStrategy {
    /**
     * 执行压缩，返回新的消息列表（已替换掉旧历史）
     * @param messages 原始消息历史
     * @param compactState 压缩状态（可携带最近文件等元信息）
     * @param focus 用户指定的关注点
     * @return 压缩后的新消息列表（用于替换上下文）
     */
    List<Map<String, Object>> compact(List<Map<String, Object>> messages,
                                      compactState compactState,
                                      String focus) throws Exception;
}