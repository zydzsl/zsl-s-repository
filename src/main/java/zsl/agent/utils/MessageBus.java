package zsl.agent.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import zsl.agent.config.AiToolMethod;
import zsl.agent.entry.TeamMessage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Java版 MessageBus
 * 功能：基于JSONL文件的进程间/组员间消息通信
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Component
public class MessageBus {
    // getter
    // 收件箱根目录
    private Path inboxDir = Path.of(".team", "inbox");
    /**
     * 发送单条消息
     * 对齐Python send() 方法
     */
    public String send(String sender, String to, String content,
                       TeamContain.MsgType msgType, Map<String, Object> extra) {
        // 1. 消息类型校验（等价原Python VALID_MSG_TYPES判断）
        if (msgType == null) {
            return "Error: Invalid type 'null'. Valid: [MESSAGE, BROADCAST, SHUTDOWN_REQUEST, SHUTDOWN_RESPONSE, PLAN_APPROVAL, PLAN_APPROVAL_RESPONSE]";
        }
        try {
            // 2. 构建消息对象
            TeamMessage msg = new TeamMessage();
            msg.setType(msgType.name().toLowerCase()); // 转小写，兼容原格式
            msg.setFrom(sender);
            msg.setContent(content);
            msg.setTimestamp(System.currentTimeMillis() / 1000); // 秒级时间戳
            msg.setExtra(extra);

            // 3. 目标收件箱文件路径：{to}.jsonl
            Path userInbox = inboxDir.resolve(to + ".jsonl");

            // 4. 追加写入JSONL文件（等价Python a模式写入）
            String jsonLine = JSONUtil.toJsonStr(msg) + "\n";
            Files.writeString(userInbox, jsonLine,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);

            return "Sent " + msgType.name().toLowerCase() + " to " + to;
        } catch (Exception e) {
            return "Error: 发送消息失败 - " + e.getMessage();
        }
    }

    /**
     * 重载：默认消息类型（普通消息）
     */
    @AiToolMethod(name = "send_message", desc = "发送消息")
    public String send(JSONObject params) {
        String sender = params.getStr("sender");
        String to = params.getStr("to");
        String content = params.getStr("content");
        return send(sender, to, content, TeamContain.MsgType.MESSAGE, null);
    }


    /**
     * 读取并清空收件箱
     * 对齐Python read_inbox() 方法
     */
    @AiToolMethod(name = "read_inbox", desc = "读取收件箱")
    public String readInbox(JSONObject params) {
        String name = params.getStr("name");
        Path userInbox = inboxDir.resolve(name + ".jsonl");
        List<TeamMessage> messages = new ArrayList<>();
        try {
            // 文件不存在直接返回空列表
            if (!Files.exists(userInbox)) {
                return JSONUtil.toJsonStr(messages);
            }
            // 读取所有行并解析JSON
            List<String> lines = Files.readAllLines(userInbox);
            for (String line : lines) {
                if (!line.isBlank()) {
                    TeamMessage msg = JSONUtil.toBean(line, TeamMessage.class);
                    messages.add(msg);
                }
            }
            // 清空文件（等价原Python write_text("")）
            Files.writeString(userInbox, "");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return JSONUtil.toJsonStr(messages);
    }

    public List<TeamMessage> readInboxUser(String name) {
        Path userInbox = inboxDir.resolve(name + ".jsonl");
        List<TeamMessage> messages = new ArrayList<>();
        try {
            // 文件不存在直接返回空列表
            if (!Files.exists(userInbox)) {
                return messages;
            }
            // 读取所有行并解析JSON
            List<String> lines = Files.readAllLines(userInbox);
            for (String line : lines) {
                if (!line.isBlank()) {
                    TeamMessage msg = JSONUtil.toBean(line, TeamMessage.class);
                    messages.add(msg);
                }
            }
            // 清空文件（等价原Python write_text("")）
            Files.writeString(userInbox, "");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return messages;
    }

    /**
     * 广播消息
     * 对齐Python broadcast() 方法
     */
    public String broadcast(String sender, String content, List<String> teammates) {
        int count = 0;
        for (String name : teammates) {
            if (!name.equals(sender)) {
                send(sender, name, content, TeamContain.MsgType.BROADCAST, null);
                count++;
            }
        }
        return "Broadcast to " + count + " teammates";
    }
}