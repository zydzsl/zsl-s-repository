package zsl.agent.client;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import zsl.agent.entry.TeamMessage;
import zsl.agent.entry.compactState;
import zsl.agent.funtions.*;
import zsl.agent.utils.MessageBus;
import zsl.agent.utils.TeamContain;
import zsl.agent.utils.ToolConstants;
//import zsl.agent.utils.ToolHander;
import zsl.agent.utils.ToolHanderBean;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;




public class Client {
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private HttpClient httpClient;

    private final boolean enableThinking = false;
    private final int maxTokens = 6000;


    public subClient subClient = new subClient();
    public static SkillRegistry skillRegistry  = new SkillRegistry();
    public static PermissionManager permissionManager = new PermissionManager("default");
    public static MemoryManager memoryManager = new MemoryManager();
    public final String currentDir = System.getProperty("user.dir");
    public final compactState compact_state = new compactState();
    public static BackGroundTask backgroundTask = new BackGroundTask();
    public static final MessageBus messageBus = new MessageBus(TeamContain.INBOX_DIR);

    private List<Map<String, Object>> history = new ArrayList<>();

    // 私有构造器，仅由 Builder 调用
    private Client(Builder builder) {
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl;
        this.model = builder.model;
        this.httpClient = HttpClient.newHttpClient();
    }

    // ===================== 核心：直接构造标准消息（和你能跑通的格式一致） =====================
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

    // 核心聊天方法
    public String chat(String prompt) {

        int MAX_RECOVERY_ATTEMPTS = 0;
        int max_output_recovery_count=0;
        String SYSTEM = String.format("""
            You are a coding agent at %s and your name is leader.
            You can schedule future work with cron_create. Tasks fire automatically and their prompts are injected into the conversation.
            Use load_skill when a task needs specialized instructions before you act.Skills available:{%s}.
            ###
            %s.
            """, currentDir,skillRegistry.describe_available(),memoryManager.buildSystemPrompt());
        addMsg("system", SYSTEM);
        addMsg("user", prompt);

        try {
            while (true) {
                if(MAX_RECOVERY_ATTEMPTS>3){
                    return "出现错误，请重新启动服务";
                }

                List<TeamMessage> teamMessages = messageBus.readInboxUser("leader");
                for (TeamMessage teamMessage : teamMessages) {
                    addMsg("user", "<inbox>\n"+teamMessage.getContent()+"\n<inbox>");
                }
                List<Map<String, Object>> backgroundResults = backgroundTask.drainNotifications();
                if(!backgroundResults.isEmpty())
                    addMsg("user","<background-results>\n"+backgroundResults.toString()+"\n</background-results>");

//                List<String> cronResults = cronScheduler.drainNotifications();
//                if(!cronResults.isEmpty()) {
//                    addMsg("user", "<cron-results>\n" + cronResults.toString() + "\n</cron-results>");
//                    System.out.println("> 获取后台任务结果 <");
//                }
//对话压缩状态
                boolean manual_compact = false;
                String compact_focus = "None";
                compact.summarize_tool_results(history);
                if (compact.estimate_context_size(history) > 80000L) {
                    compact.compact_history(compact_state, history);
                    System.out.println("> 对话内容已经压缩 <");
                }
                JSONObject requestBody = new JSONObject();
                requestBody.set("model", model);
                requestBody.set("messages", history);
                requestBody.set("tools", ToolConstants.TOOLS);
                requestBody.set("enable_thinking", enableThinking);
                requestBody.set("max_tokens", maxTokens);

                HttpResponse<String> response = null;
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/chat/completions"))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                            .build();

                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                }
                catch (Exception e) {
                    String errorBody = e.getMessage().toLowerCase();
                    boolean isOverlongPrompt = errorBody.contains("overlong_prompt") || (errorBody.contains("prompt") && errorBody.contains("long"));
                    if (isOverlongPrompt) {
                        compact.compact_history(compact_state, history);
                        System.out.println("> 对话内容已经压缩 <");
                        //休眠一秒钟
                        Thread.sleep(500);
                        continue;
                    }
                    if (e instanceof ConnectException || e instanceof HttpTimeoutException) {
                        System.out.println("> 尝试重新连接 <");
                        MAX_RECOVERY_ATTEMPTS++;
                        Thread.sleep(500);
                        continue;
                    }
                }
                JSONObject body = JSONUtil.parseObj(response.body());
                JSONObject choice = body.getJSONArray("choices").getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");
                String stopReason = choice.getStr("finish_reason", "");
                if ("length".equals(stopReason)) {
                    max_output_recovery_count++;
                    if (max_output_recovery_count<=3){
                        System.out.printf("[Recovery] max_tokens hit("+max_output_recovery_count+"/3."+" Injecting continuation...%n");
                        addMsg("user", "Output limit hit. Continue directly from where you stopped \n"+
                                "no recap, no repetition. Pick up mid-sentence if needed.");
                        continue;
                    }
                }
                max_output_recovery_count = 0;
                // 保存AI回复
                addMsg(message.getStr("role"), message.getStr("content"), message.get("tool_calls"));

                if (message.get("tool_calls") == null) {
                    return message.getStr("content");
                }
                // ========== 【Java 版】遍历执行工具调用（完全对齐你的 Python 代码） ==========
                JSONArray toolCallsArray = message.getJSONArray("tool_calls");
                if (toolCallsArray != null && !toolCallsArray.isEmpty()) {
                    // 遍历所有工具调用（对应 for tool_call in ai_message.tool_calls）
                    for (int i = 0; i < toolCallsArray.size(); i++) {
                        JSONObject toolCall = toolCallsArray.getJSONObject(i);
                        JSONObject function = toolCall.getJSONObject("function");

                        // 2. 获取工具名
                        String toolName = function.getStr("name");
                        // 3. 解析参数（arguments 是 JSON 字符串）
                        JSONObject args = JSONUtil.parseObj(function.getStr("arguments"));
                        Map<String, String> decision = permissionManager.check(toolName, args);
                        if(decision.containsKey("behavior") && decision.get("behavior").equals("deny")) {
                            String output = "Permission denied: " + decision.get("reason");
                            System.out.println(output);
                            addMsg("tool", output, toolCall.getStr("id"));
                        }else if(decision.containsKey("behavior") && decision.get("behavior").equals("ask"))
                        {
                            if(permissionManager.askUser(toolName, args)){
                                if (toolName.equals("compact")) {
                                    compact_focus = args.getStr("focus");
                                    manual_compact = true;
                                }
//                                String output = ToolHander.execute(toolName, args, toolCall.getStr("id"));
                                ToolHanderBean.ToolExecutor executor = ToolHanderBean.TOOL_MAP.get(toolName);
                                String result = executor.execute(args, toolName);
                                addMsg("tool", result, function.getStr("id"));
                            }else {
                                String output = "> Permission denied by user for " + toolName;
                                addMsg("tool", output, toolCall.getStr("id"));
                            }
                        }else {
                            // 4. 执行工具
                            ToolHanderBean.ToolExecutor executor = ToolHanderBean.TOOL_MAP.get(toolName);
                            String output = executor.execute(args, toolName);
//                            String output = ToolHander.execute(toolName, args, toolCall.getStr("id"));
                            System.out.println("> " + toolName + ":");
                            System.out.println(output.length() > 200 ? output.substring(0, 200) : output);
                            addMsg("tool", output, toolCall.getStr("id"));
                        }
                        if (manual_compact || compact.estimate_context_size(history) > 80000L) {
                            System.out.println("> 压缩对话 <");

                            compact.compact_history(compact_state, history, compact_focus);
                        }

                        if (toolName == "todo") {
                            TodoManager.rounds_since_update();
                        } else {
                            TodoManager.note_round_without_update();
                            String reminder = TodoManager.reminder();
                            addMsg("user", reminder);
                        }
                    }
                }

            }
        } catch (Exception e) {
            return "请求失败：" + e.getMessage();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String apiKey;
        private String baseUrl;
        private String model;

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Client build() { return new Client(this); }
    }
}

//{
//  "id": "chatcmpl-xxx",
//  "object": "chat.completion",
//  "created": 1740000000,
//  "model": "gpt-3.5-turbo-0125",
//  "choices": [
//    {
//      "index": 0,
//      "message": {
//        "role": "assistant",
//        "content": null,
//        "tool_calls": [
//          {
//            "id": "call_xxx",
//            "type": "function",
//            "function": {
//              "name": "save_memory",
//              "arguments": "{\"name\":\"test\",\"description\":\"test\"}"
//            }，
//····       {
//            "id": "call_xxx",
//            "type": "function",
//            "function": {
//              "name": "save_memory",
//              "arguments": "{\"name\":\"test\",\"description\":\"test\"}"
//            }
//
//        ]
//      },
//      "finish_reason": "tool_calls"
//    }
//  ]
//}