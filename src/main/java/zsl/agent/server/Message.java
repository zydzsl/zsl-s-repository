package zsl.agent.server;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Java 版 Message 类
 * 1:1 实现 Python class Message(BaseModel) 的所有功能
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Component
public class Message {
    // 核心字段（和Python完全一致）
    private String content;          // 消息内容
    private String role;        // 消息角色
    private Object tool_calls = "[]";
    private LocalDateTime timestamp; // 时间戳
    private Map<String, Object> metadata;  // 元数据

    public Message(String content, String role, Object o) {
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("role", this.role); // 角色转字符串
        map.put("content", this.content);  // 消息内容
        if (this.tool_calls != null)
            map.put("tool_calls", this.tool_calls);
        return map;
    }
    /**
     * 重写toString（对应 Python __str__）
     * 输出格式：[role] content
     */
    @Override
    public String toString() {
        return "[" + this.role + "] " + this.content;
    }

}