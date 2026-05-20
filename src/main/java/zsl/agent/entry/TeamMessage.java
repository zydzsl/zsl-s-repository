package zsl.agent.entry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamMessage {
    // 消息类型（小写，兼容原Python格式）
    private String type;
    // 发送者
    private String from;
    // 消息内容
    private String content;
    // 时间戳（秒级，和Python time.time()一致）
    private long timestamp;
    // 扩展字段
    private Map<String, Object> extra;

}