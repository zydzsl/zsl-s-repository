package zsl.agent.client;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import zsl.agent.entry.TeamMessage;
import zsl.agent.entry.compactState;
import zsl.agent.funtions.*;
import zsl.agent.utils.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class ClientCron {
    // ==================== 配置与依赖 ====================
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final boolean enableThinking = false;
    private final int maxTokens = 6000;
    private final int maxRecoveryAttempts = 3;


    // 公共组件
    public final subClient subClient = new subClient();
    public static SkillRegistry skillRegistry = new SkillRegistry();
    public static PermissionManager permissionManager = new PermissionManager("default");
    public static MemoryManager memoryManager = new MemoryManager();
    public static BackGroundTask backgroundTask = new BackGroundTask();
    public static final MessageBus messageBus = new MessageBus(TeamContain.INBOX_DIR);
    private static MCPToolRouter mcpRouter = new MCPToolRouter();

    // 状态
    public final String currentDir = System.getProperty("user.dir");
    public final compactState compact_state = new compactState();
    private List<Map<String, Object>> history = new ArrayList<>();

    // ==================== 构造器与 Builder ====================
    private ClientCron(Builder builder) {
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl;
        this.model = builder.model;
        this.httpClient = HttpClient.newHttpClient();
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
        public ClientCron build() { return new ClientCron(this); }
    }

    // ==================== 公共：消息添加方法 ====================
    private void addMsg(String role, String content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        history.add(msg);
    }

    private void addMsg(String role, String content, String toolCallId) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("tool_call_id", toolCallId);
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

    private void addMsg(String role, String content, Object toolCalls, String reasoning_content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        if (toolCalls != null) {
            msg.put("tool_calls", toolCalls);
        }
        msg.put("reasoning_content", reasoning_content);
        history.add(msg);
    }

    // ==================== 公共：System Prompt 构建 ====================
    private String buildSystemPrompt() {
        return String.format("""
            You are a 私人 agent at %s .可以使用mcp工具来查询天气，不要使用没必要的工具，回复尽量简洁
            Use load_skill when a task needs specialized instructions before you act.Skills available:{%s}.
            ###
            %s.
            """, currentDir, skillRegistry.describe_available(), memoryManager.buildSystemPrompt());
    }

    // ==================== 公共：HTTP 请求与响应处理 ====================
    private JSONObject buildRequestBody(List<Map<String, Object>> messages) {
        JSONObject requestBody = new JSONObject();
        requestBody.set("model", model);
        requestBody.set("messages", messages);
        requestBody.set("tools", ToolConstants.TOOLS);
        requestBody.set("enable_thinking", enableThinking);
        requestBody.set("max_tokens", maxTokens);
        return requestBody;
    }

    private HttpResponse<String> sendHttpRequest(JSONObject requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // ==================== 公共：对话上下文管理 ====================
    private void injectExternalMessages(List<Map<String, Object>> targetHistory) {
        // 1. 注入团队消息
        List<TeamMessage> teamMessages = messageBus.readInboxUser("leader");
        for (TeamMessage teamMessage : teamMessages) {
            addMsg("user", "<inbox>\n" + teamMessage.getContent() + "\n</inbox>");
        }

        // 2. 注入后台任务结果
        List<Map<String, Object>> backgroundResults = backgroundTask.drainNotifications();
        if (!backgroundResults.isEmpty()) {
            addMsg("user", "<background-results>\n" + backgroundResults.toString() + "\n</background-results>");
        }
    }

    private void compactHistoryIfNeeded(List<Map<String, Object>> targetHistory, compactState state, String focus) throws IOException {
        compact.summarize_tool_results(targetHistory);
        if (compact.estimate_context_size(targetHistory) > 80000L) {
//            List<Map<String, Object>> maps = compact.compact_history(state, targetHistory, focus);
//            targetHistory.clear();
//            targetHistory.addAll(maps);
            System.out.println("> 对话内容已经压缩 <");
        }
    }

    // ==================== 公共：工具调用执行 ====================
    private String executeToolCalls(JSONArray toolCallsArray, List<Map<String, Object>> targetHistory,
                                    boolean skipUserConfirm) throws IOException {
        StringBuilder finalOutput = new StringBuilder();
        boolean manualCompact = false;
        String compactFocus = "None";

        for (int i = 0; i < toolCallsArray.size(); i++) {
            JSONObject toolCall = toolCallsArray.getJSONObject(i);
            JSONObject function = toolCall.getJSONObject("function");
            String toolName = function.getStr("name");
            JSONObject args = JSONUtil.parseObj(function.getStr("arguments"));
            String toolCallId = toolCall.getStr("id");

            // 权限检查
            Map<String, String> decision = permissionManager.check(toolName, args);
            String output;

            if ("deny".equals(decision.get("behavior"))) {
                output = "Permission denied: " + decision.get("reason");
                System.out.println(output);
            } else if ("ask".equals(decision.get("behavior"))) {
                if (skipUserConfirm) {
                    output = "> 定时任务无法确认，跳过: " + toolName;
                } else if (permissionManager.askUser(toolName, args)) {
                    if ("compact".equals(toolName)) {
                        compactFocus = args.getStr("focus");
                        manualCompact = true;
                        output = "对话已经压缩...";
                        System.out.println("> 压缩对话 <");
                    }else {
                        if (mcpRouter.isMcpTool(toolName)) {
                            // 1. 是MCP工具 → 交给MCP路由执行
                            output =  mcpRouter.call(toolName, args);
                        }else {
                            ToolHanderBean.ToolExecutor executor = ToolHanderBean.TOOL_MAP.get(toolName);
                            output = executor.execute(args, toolName);
                        }
                    }

                } else {
                    output = "> Permission denied by user for " + toolName;
                }
            } else {
                ToolHanderBean.ToolExecutor executor = ToolHanderBean.TOOL_MAP.get(toolName);
                output = executor.execute(args, toolName);
                System.out.println("> " + toolName + ":");
                System.out.println(output.length() > 200 ? output.substring(0, 200) : output);
            }

            // 添加工具结果到历史
            addMsg("tool",output, toolCallId);
            finalOutput.append(output).append("\n");
            // TodoManager 逻辑
            if ("todo".equals(toolName)) {
                TodoManager.rounds_since_update();
            }
//            else {
//                TodoManager.note_round_without_update();
//                String reminder = TodoManager.reminder();
//                addMsg( "user", reminder);
//            }
        }

//        if (manualCompact || compact.estimate_context_size(targetHistory) > 80000L) {
//            System.out.println("> 压缩对话 <");
//            List<Map<String, Object>> compacted = compact.compact_history(compact_state, targetHistory, compactFocus);
//            targetHistory.clear();
//            targetHistory.addAll(compacted);
//            addMsg("user", "对话已经压缩...,请继续工作");
//            System.out.println("> 对话内容已经压缩 <");
//
//        }
        return finalOutput.toString();
    }

    // ==================== 核心：用户多轮对话 ====================
    public String chat(String prompt) {
        AtomicInteger recoveryAttempts = new AtomicInteger(0);
        AtomicInteger outputRecoveryCount = new AtomicInteger(0);

        // 初始化对话

        addMsg("system", buildSystemPrompt());
        addMsg("user", prompt);

        try {
            while (true) {
                if (recoveryAttempts.get() > maxRecoveryAttempts) {
                    return "出现错误，请重新启动服务";
                }

                // 1. 注入外部消息
                injectExternalMessages(this.history);

                // 2. 对话压缩
                compactHistoryIfNeeded(this.history, compact_state, "None");

                // 3. 构建并发送请求
                JSONObject requestBody = buildRequestBody(this.history);
                HttpResponse<String> response;
                try {
                    response = sendHttpRequest(requestBody);
                } catch (Exception e) {
                    if (handleRequestError(e, recoveryAttempts)) {
                        continue;
                    }
                    throw e;
                }

                // 4. 解析响应
                JSONObject body = JSONUtil.parseObj(response.body());
                JSONObject choice = body.getJSONArray("choices").getJSONObject(0);
                if(choice == null){
                    return "出现错误，请重新启动服务";
                }
                JSONObject message = choice.getJSONObject("message");
                String stopReason = choice.getStr("finish_reason", "");

                // 5. 处理 max_tokens 超限
                if ("length".equals(stopReason)) {
                    if (handleMaxTokensLimit(outputRecoveryCount)) {
                        continue;
                    }
                }
                outputRecoveryCount.set(0);

                if (message.containsKey("reasoning_content")) {
                    addMsg(message.getStr("role"), message.getStr("content"), message.get("tool_calls"), message.getStr("reasoning_content"));
                }else
                    addMsg(message.getStr("role"), message.getStr("content"), message.get("tool_calls"));

                // 6. 保存 AI 回复


                // 7. 无工具调用，直接返回
                if (message.get("tool_calls") == null) {
                    return message.getStr("content");
                }

                // 8. 执行工具调用
                JSONArray toolCallsArray = message.getJSONArray("tool_calls");
                executeToolCalls(toolCallsArray, this.history, false);
            }
        } catch (Exception e) {
            System.err.println("===== Chat 崩溃堆栈（必看！） =====");
            e.printStackTrace(); // 这行是关键！
            System.err.println("================================");
            // ===================================
            return "请求失败：" + e.getMessage();
        }
    }

    // ==================== 核心：定时任务单轮对话 ====================
    public String singleTurnChat(String prompt) {

//        String systemPrompt = """
//        你是一个定时调度助手，只需要完成用户发布的任务，简洁友好即可，不要使用多余的工具，完成任务就结束对话。
//        可以使用mcp工具来查询天气，不要使用没必要的工具，回复尽量简洁
//            Use load_skill when a task needs specialized instructions before you act.Skills available:{%s}.
//        """;
//        String systemPrompt = String.format("""
//            你是一个定时调度助手，只需要完成用户发布的任务，简洁友好即可，不要使用多余的工具，完成任务就结束对话。可以使用mcp工具来查询天气，不要使用没必要的工具，回复尽量简洁.
//            Current directory: %s
//            Use load_skill when a task needs specialized instructions before you act.Skills available:{%s}.
//            ###
//            """,currentDir,skillRegistry.describe_available());
        AtomicInteger recoveryAttempts = new AtomicInteger(0);
        AtomicInteger outputRecoveryCount = new AtomicInteger(0);

        // 单轮专用：临时历史列表（使用 ArrayList，和主 chat 一致）
//        List<Map<String, Object>> tempHistory = new ArrayList<>();

        // 1. 初始化 System Prompt（使用 HashMap，避免 Map.of 的 null 限制）
//        Map<String, Object> sysMsg = new HashMap<>();
//        sysMsg.put("role", "system");
//        sysMsg.put("content", systemPrompt);
//        tempHistory.add(sysMsg);
//
//        // 2. 添加用户 Prompt
//        Map<String, Object> userMsg = new HashMap<>();
//        userMsg.put("role", "user");
//        userMsg.put("content", prompt);
//        tempHistory.add(userMsg);
        addMsg("user", prompt);

        try {
            while (true) {
                if (recoveryAttempts.get() > maxRecoveryAttempts) {
                    return "出现错误，请重新启动服务";
                }

                // 1. 注入外部消息
                injectExternalMessages(this.history);

                // 2. 对话压缩
                compactHistoryIfNeeded(this.history, compact_state, "None");

                // 3. 构建并发送请求
                JSONObject requestBody = buildRequestBody(this.history);
                HttpResponse<String> response;
                try {
                    response = sendHttpRequest(requestBody);
                } catch (Exception e) {
                    if (handleRequestError(e, recoveryAttempts)) {
                        continue;
                    }
                    throw e;
                }

                // 4. 解析响应
                JSONObject body = JSONUtil.parseObj(response.body());
                JSONObject choice = body.getJSONArray("choices").getJSONObject(0);
                if(choice == null){
                    return "出现错误，请重新启动服务";
                }
                JSONObject message = choice.getJSONObject("message");
                String stopReason = choice.getStr("finish_reason", "");

                // 5. 处理 max_tokens 超限
                if ("length".equals(stopReason)) {
                    if (handleMaxTokensLimit(outputRecoveryCount)) {
                        continue;
                    }
                }
                outputRecoveryCount.set(0);

                if (message.containsKey("reasoning_content")) {
                    addMsg(message.getStr("role"), message.getStr("content"), message.get("tool_calls"), message.getStr("reasoning_content"));
                }else
                    addMsg(message.getStr("role"), message.getStr("content"), message.get("tool_calls"));

                // 6. 保存 AI 回复


                // 7. 无工具调用，直接返回
                if (message.get("tool_calls") == null) {
                    return message.getStr("content");
                }

                // 8. 执行工具调用
                JSONArray toolCallsArray = message.getJSONArray("tool_calls");
                executeToolCalls(toolCallsArray, this.history, false);
            }
        } catch (Exception e) {
            System.err.println("===== Chat 崩溃堆栈（必看！） =====");
            e.printStackTrace(); // 这行是关键！
            System.err.println("================================");
            // ===================================
            return "请求失败：" + e.getMessage();
        }
    }

    // ==================== 辅助：错误处理 ====================
    private boolean handleRequestError(Exception e, AtomicInteger recoveryAttempts) throws InterruptedException, IOException {
        String errorBody = e.getMessage().toLowerCase();
        boolean isOverlongPrompt = errorBody.contains("overlong_prompt") || (errorBody.contains("prompt") && errorBody.contains("long"));

        if (isOverlongPrompt) {
            compactHistoryIfNeeded(this.history, compact_state, "None");
            System.out.println("> 对话内容已经压缩 <");
            Thread.sleep(500);
            return true;
        }

        if (e instanceof ConnectException || e instanceof HttpTimeoutException) {
            System.out.println("> 尝试重新连接 <");
            recoveryAttempts.incrementAndGet();
            Thread.sleep(500);
            return true;
        }

        return false;
    }

    private boolean handleMaxTokensLimit(AtomicInteger outputRecoveryCount) {
        outputRecoveryCount.incrementAndGet();
        if (outputRecoveryCount.get() <= 3) {
            System.out.printf("[Recovery] max_tokens hit(%d/3. Injecting continuation...%n", outputRecoveryCount.get());
            addMsg("user", "Output limit hit. Continue directly from where you stopped \n" +
                    "no recap, no repetition. Pick up mid-sentence if needed.");
            return true;
        }
        return false;
    }

    // ==================== 辅助：单轮对话 System Prompt ====================
// 单轮对话专用的 System Prompt
    private String buildSingleTurnSystemPrompt() {
        String prompt = String.format("""
        You are a coding agent performing a scheduled background task.
        Current directory: %s
        Skills available: {%s}.
        Be concise and focus on the task.
        """, currentDir, skillRegistry.describe_available());
        // 空值保护
        return prompt != null ? prompt : "You are a helpful assistant.";
    }
}