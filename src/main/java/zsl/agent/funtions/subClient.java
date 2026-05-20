package zsl.agent.funtions;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import zsl.agent.entry.TeamMessage;
import zsl.agent.utils.ToolConstants;
import zsl.agent.utils.ToolHanderBean;
//import zsl.agent.utils.ToolHander;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static zsl.agent.config.Cantains.*;
import static zsl.agent.client.Client.messageBus;

public class subClient {
    private boolean enableThinking = false;
    private int maxTokens = 6000;
    private HttpClient httpClient = HttpClient.newHttpClient();
    @Resource
    private SkillRegistry skillRegistry = new SkillRegistry();
    // 系统提示词
    private final String currentDir = System.getProperty("user.dir");
    private final String SYSTEM = String.format("""
            You are a team lead at %s. Use send_message to communicate. Complete your task..
            Rules you MUST follow:
            1. Only use WINDOWS CMD commands, NEVER use Linux commands like mkdir -p, echo -n.
            2. Once the task is done, immediately STOP calling the bash tool, NEVER retry or repeat commands.
            3. After execution, give a simple summary in Chinese and end the conversation.
            4. Do NOT call tools repeatedly even if the file already exists.
            5.Use load_skill when a task needs specialized instructions before you act.Skills available:{%s}:
            """, currentDir, skillRegistry.describe_available());


    // 对话历史（直接存Map，和你手动写的格式完全一样）
    private final List<Map<String, Object>> history = new ArrayList<>();

    private void addMsg(String role, String content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        history.add(msg);
    }
    private void addMsg(String role, String content, String tool_id) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("tool_call_id", tool_id);
        msg.put("content", content);
        history.add(msg);
    }
    private void addMsg(String role, String content, Object toolCalls) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        if (toolCalls != null) {
            msg.put("tool_calls", toolCalls);
        }
        history.add(msg);
    }

    public void subchat(String prompt, String name, String role) {
        String teammateSystemPrompt = String.format("""
    "你是："+name+"，角色："+role+"。\\n" +
                                                "【铁律1】你绝对不能直接说话！绝对不能直接回复文字！\\n" +
                                                "【铁律2】你必须调用 send_message 工具回复 leader！\\n" +
                                                "【铁律3】send_message 参数：recipient=\\"leader\\"，content=\\"你的回答内容\\"\\n" +
                                                "\"【规则3】不要重复发送消息，发送一次就结束。\\n" +
                                                "【规则4】只用中文，简洁回答。"
                                                "违反以上规则，任务直接失败！"
""", name, role);
        addMsg("system", teammateSystemPrompt);
        addMsg("user", prompt);
        System.out.println("成员启动");
        try {
            while (true) {
//                inbox = BUS.read_inbox(name)
//            for msg in inbox:
//                messages.append({"role": "user", "content": json.dumps(msg)})
                List<TeamMessage> teamMessages = messageBus.readInboxUser(name);
                for (TeamMessage teamMessage : teamMessages) {
                    addMsg("user", "<inbox>\n"+teamMessage.getContent()+"\n<inbox>");
                }
                // 请求体（完全不变，你之前验证过能跑通）
                JSONObject requestBody = new JSONObject();
                requestBody.set("model", model);
                requestBody.set("messages", history);
                requestBody.set("tools", ToolConstants.SubTOOLS);
                requestBody.set("enable_thinking", enableThinking);
                requestBody.set("max_tokens", maxTokens);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/chat/completions"))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject body = JSONUtil.parseObj(response.body());

                JSONObject message = body.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message");
                // 保存AI回复
                addMsg(message.getStr("role"), message.getStr("content"), message.get("tool_calls"));

                if (message.get("tool_calls") == null) {
                    System.out.println("成员节点1");
//                    return message.getStr("content");
                     break;
//                     break;
                }
                JSONArray toolCallsArray = message.getJSONArray("tool_calls");
                if (toolCallsArray != null && !toolCallsArray.isEmpty()) {
                    // 遍历所有工具调用（对应 for tool_call in ai_message.tool_calls）
                    for (int i = 0; i < toolCallsArray.size(); i++) {
                        JSONObject toolCall = toolCallsArray.getJSONObject(i);
                        JSONObject function = toolCall.getJSONObject("function");
                        System.out.println("成员节点2");

                        // 2. 获取工具名
                        String toolName = function.getStr("name");
                        // 3. 解析参数（arguments 是 JSON 字符串）
                        JSONObject args = JSONUtil.parseObj(function.getStr("arguments"));
                        // 4. 执行工具
//                        String output = ToolHander.execute(toolName, args, toolCall.getStr("id"));
                        ToolHanderBean.ToolExecutor executor = ToolHanderBean.TOOL_MAP.get(toolName);
                        String output = executor.execute(args, toolName);
                        System.out.println("> " + toolName + ":");
                        System.out.println(output.length() > 200 ? output.substring(0, 200) : output);
                        // 6. 把工具执行结果加入对话历史（必须加，AI 才能继续对话）
                        addMsg("tool", output, toolCall.getStr("id"));
                        if (toolName.equals("todo")){
                            TodoManager.rounds_since_update();
                        }else {
                            TodoManager.note_round_without_update();
                            String reminder =TodoManager.reminder();
                            addMsg("user", reminder);
                        }
                    }
                }


            }
        } catch (Exception e) {
        }

    }

}
