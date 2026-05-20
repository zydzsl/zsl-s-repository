package zsl.agent.client;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class ClientCron2 {
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
    private ClientCron2(Builder builder) {
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
        public ClientCron2 build() { return new ClientCron2(this); }
    }

    // ==================== 公共：消息添加方法（修复：支持指定目标历史） ====================
    private void addMsg(String role, String content) {
        addMsg(this.history, role, content);
    }

    private void addMsg(List<Map<String, Object>> target, String role, String content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        target.add(msg);
    }

    private void addMsg(String role, String content, String toolCallId) {
        addMsg(this.history, role, content, toolCallId);
    }

    private void addMsg(List<Map<String, Object>> target, String role, String content, String toolCallId) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("tool_call_id", toolCallId);
        msg.put("content", content);
        target.add(msg);
    }

    private void addMsg(String role, String content, Object toolCalls) {
        addMsg(this.history, role, content, toolCalls);
    }

    private void addMsg(List<Map<String, Object>> target, String role, String content, Object toolCalls) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        if (toolCalls != null) {
            msg.put("tool_calls", toolCalls);
        }
        target.add(msg);
    }

    private void addMsg(String role, String content, Object toolCalls, String reasoning_content) {
        addMsg(this.history, role, content, toolCalls, reasoning_content);
    }

    private void addMsg(List<Map<String, Object>> target, String role, String content, Object toolCalls, String reasoning_content) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        if (toolCalls != null) {
            msg.put("tool_calls", toolCalls);
        }
        msg.put("reasoning_content", reasoning_content);
        target.add(msg);
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

    /**
     * 发送流式请求（终极修复null问题）
     * @param requestBody 请求体
     * @param handler 流式回调
     * @return 完整的message对象（包含tool_calls）
     */
    private CompletableFuture<JSONObject> sendStreamRequest(JSONObject requestBody, StreamResponseHandler handler) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                .build();

        CompletableFuture<JSONObject> future = new CompletableFuture<>();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        String errorBody = response.body().collect(Collectors.joining("\n"));
                        future.completeExceptionally(new RuntimeException(
                                "API请求失败，状态码：" + response.statusCode() + "，响应：" + errorBody));
                        handler.onError(new RuntimeException("API请求失败，状态码：" + response.statusCode()));
                        return;
                    }

                    // 用于收集完整的响应内容
                    JSONObject fullMessage = new JSONObject();
                    StringBuilder contentBuilder = new StringBuilder();
                    StringBuilder reasoningBuilder = new StringBuilder();
                    JSONArray toolCallsBuilder = new JSONArray();
                    AtomicReference<String> finishReasonRef = new AtomicReference<>("");

                    response.body().forEach(line -> {
                        try {
                            line = line.trim();
                            if (line.isEmpty()) return;
                            if (!line.startsWith("data:")) return;

                            String data = line.substring(5).trim();
                            if ("[DONE]".equals(data)) {
                                // 流结束，构建完整的message对象
                                fullMessage.put("role", "assistant");
                                fullMessage.put("finish_reason", finishReasonRef.get());
                                if (contentBuilder.length() > 0) {
                                    fullMessage.put("content", contentBuilder.toString());
                                }
                                if (reasoningBuilder.length() > 0) {
                                    fullMessage.put("reasoning_content", reasoningBuilder.toString());
                                }
                                if (!toolCallsBuilder.isEmpty()) {
                                    fullMessage.put("tool_calls", toolCallsBuilder);
                                }
                                future.complete(fullMessage);
                                handler.onComplete();
                                return;
                            }

                            JSONObject chunk = JSONUtil.parseObj(data);
                            JSONArray choices = chunk.getJSONArray("choices");
                            if (choices.isEmpty()) return;

                            JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                            String finishReason = choices.getJSONObject(0).getStr("finish_reason");
                            if (finishReason != null && !finishReason.isEmpty()) {
                                finishReasonRef.set(finishReason);
                            }

                            // ==================== 终极修复：使用Hutool的isNull()方法 ====================
                            // 处理普通文本内容（只有当content不是JSON null时才处理）
                            if (delta.containsKey("content") && !delta.isNull("content")) {
                                String content = delta.getStr("content");
                                // 双重保险：防止某些特殊情况返回字符串"null"
                                if (!"null".equals(content)) {
                                    contentBuilder.append(content);
                                    handler.onNext(content);
                                }
                            }

                            // 处理思考过程（同样使用isNull()）
                            if (delta.containsKey("reasoning_content") && !delta.isNull("reasoning_content")) {
                                String reasoning = delta.getStr("reasoning_content");
                                if (!"null".equals(reasoning)) {
                                    reasoningBuilder.append(reasoning);
                                    // handler.onNext(reasoning); // 取消注释即可流式输出思考过程
                                }
                            }
                            // ========================================================================

                            // 处理工具调用（增量拼接arguments）
                            if (delta.containsKey("tool_calls") && !delta.isNull("tool_calls")) {
                                JSONArray deltaToolCalls = delta.getJSONArray("tool_calls");
                                for (int i = 0; i < deltaToolCalls.size(); i++) {
                                    JSONObject deltaToolCall = deltaToolCalls.getJSONObject(i);
                                    int index = deltaToolCall.getInt("index");

                                    // 确保工具调用列表有足够的元素
                                    while (toolCallsBuilder.size() <= index) {
                                        toolCallsBuilder.add(new JSONObject());
                                    }

                                    JSONObject toolCall = toolCallsBuilder.getJSONObject(index);

                                    // 合并id和type
                                    if (deltaToolCall.containsKey("id") && !deltaToolCall.isNull("id")) {
                                        toolCall.put("id", deltaToolCall.getStr("id"));
                                    }
                                    if (deltaToolCall.containsKey("type") && !deltaToolCall.isNull("type")) {
                                        toolCall.put("type", deltaToolCall.getStr("type"));
                                    }

                                    // 合并function信息
                                    if (deltaToolCall.containsKey("function") && !deltaToolCall.isNull("function")) {
                                        JSONObject deltaFunction = deltaToolCall.getJSONObject("function");
                                        if (!toolCall.containsKey("function")) {
                                            toolCall.put("function", new JSONObject());
                                        }
                                        JSONObject function = toolCall.getJSONObject("function");

                                        if (deltaFunction.containsKey("name") && !deltaFunction.isNull("name")) {
                                            function.put("name", deltaFunction.getStr("name"));
                                        }
                                        if (deltaFunction.containsKey("arguments") && !deltaFunction.isNull("arguments")) {
                                            // arguments是逐步返回的字符串，需要拼接完整
                                            String args = deltaFunction.getStr("arguments");
                                            if (function.containsKey("arguments")) {
                                                function.put("arguments", function.getStr("arguments") + args);
                                            } else {
                                                function.put("arguments", args);
                                            }
                                        }
                                    }
                                }
                            }

                        } catch (Exception e) {
                            future.completeExceptionally(new RuntimeException("解析流式响应失败：" + e.getMessage(), e));
                            handler.onError(e);
                        }
                    });
                })
                .exceptionally(e -> {
                    future.completeExceptionally(e);
                    handler.onError(e);
                    return null;
                });

        return future;
    }
    // ==================== 公共：对话上下文管理（修复：正确操作目标历史） ====================
    private void injectExternalMessages(List<Map<String, Object>> targetHistory) {
        // 1. 注入团队消息
        List<TeamMessage> teamMessages = messageBus.readInboxUser("leader");
        for (TeamMessage teamMessage : teamMessages) {
            addMsg(targetHistory, "user", "<inbox>\n" + teamMessage.getContent() + "\n</inbox>");
        }

        // 2. 注入后台任务结果
        List<Map<String, Object>> backgroundResults = backgroundTask.drainNotifications();
        if (!backgroundResults.isEmpty()) {
            addMsg(targetHistory, "user", "<background-results>\n" + backgroundResults.toString() + "\n</background-results>");
        }
    }

    private void compactHistoryIfNeeded(List<Map<String, Object>> targetHistory, compactState state, String focus) throws IOException {
        compact.summarize_tool_results(targetHistory);
        if (compact.estimate_context_size(targetHistory) > 80000L) {
//            List<Map<String, Object>> maps = compact.compact_history(state, targetHistory, focus);
            String compacted = compact.compact_history(compact_state, targetHistory, focus);
            targetHistory.clear();
            addMsg(targetHistory, "user", compacted);
            System.out.println("> 对话内容已压缩 <");
        }
    }

    // ==================== 公共：工具调用执行（修复：正确操作目标历史） ====================
    private String executeToolCalls(JSONArray toolCallsArray, List<Map<String, Object>> targetHistory, boolean skipUserConfirm) throws IOException {
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
                System.out.println("\n" + output); // 增加换行，和前面的流式输出分开
            } else if ("ask".equals(decision.get("behavior"))) {
                if (skipUserConfirm) {
                    output = "> 定时任务无法确认，跳过: " + toolName;
                    System.out.println("\n" + output);
                } else {
                    // 关键：在权限提示前换行，避免打断流式输出
                    System.out.println();
                    if (permissionManager.askUser(toolName, args)) {
                        if ("compact".equals(toolName)) {
                            compactFocus = args.getStr("focus");
                            manualCompact = true;
                            output = "对话已经压缩...";
                            System.out.println("> 压缩对话 <");
                        } else {
                            if (mcpRouter.isMcpTool(toolName)) {
                                // 1. 是MCP工具 → 交给MCP路由执行
                                output = mcpRouter.call(toolName, args);
                            } else {
                                ToolHanderBean.ToolExecutor executor = ToolHanderBean.TOOL_MAP.get(toolName);
                                output = executor.execute(args, toolName);
                            }
                            // 工具执行结果也换行输出
//                            System.out.println("> " + toolName + " 执行结果：");
//                            System.out.println(output.length() > 200 ? output.substring(0, 200) + "..." : output);
                        }
                    } else {
                        output = "> Permission denied by user for " + toolName;
                        System.out.println(output);
                    }
                }
            } else {
                ToolHanderBean.ToolExecutor executor = ToolHanderBean.TOOL_MAP.get(toolName);
                output = executor.execute(args, toolName);
                System.out.println("\n> " + toolName + " 执行结果：");
                System.out.println(output.length() > 200 ? output.substring(0, 200) + "..." : output);
            }

            // 添加工具结果到历史
            addMsg(targetHistory, "tool", output, toolCallId);
            finalOutput.append(output).append("\n");
            // TodoManager 逻辑
            if ("todo".equals(toolName)) {
                TodoManager.rounds_since_update();
            }
        }

        if (manualCompact || compact.estimate_context_size(targetHistory) > 80000L) {
            System.out.println("> 压缩对话 <");
            String compacted = compact.compact_history(compact_state, targetHistory, compactFocus);
            targetHistory.clear();
            addMsg(targetHistory, "user", compacted);
            System.out.println("> 对话内容已经压缩 <");

        }
        return finalOutput.toString();
    }

    // ==================== 核心：同步多轮对话（保留原方法，完全兼容） ====================
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
                    if (handleRequestError(e, recoveryAttempts, this.history)) {
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
                    if (handleMaxTokensLimit(outputRecoveryCount, this.history)) {
                        continue;
                    }
                }
                outputRecoveryCount.set(0);

                // 6. 添加消息到历史
                if (message.containsKey("reasoning_content")) {
                    addMsg(message.getStr("role"), message.getStr("content"), message.get("tool_calls"), message.getStr("reasoning_content"));
                }else
                    addMsg(message.getStr("role"), message.getStr("content"), message.get("tool_calls"));

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
            e.printStackTrace();
            System.err.println("================================");
            return "请求失败：" + e.getMessage();
        }
    }

    // ==================== 核心：流式多轮对话（新增） ====================
    public CompletableFuture<Void> chatStream(String prompt, StreamResponseHandler handler) {
        CompletableFuture<Void> completionFuture = new CompletableFuture<>();
        AtomicInteger recoveryAttempts = new AtomicInteger(0);
        AtomicInteger outputRecoveryCount = new AtomicInteger(0);

        // 初始化对话
        addMsg("system", buildSystemPrompt());
        addMsg("user", prompt);

        CompletableFuture.runAsync(() -> {
            try {
                while (true) {
                    if (recoveryAttempts.get() > maxRecoveryAttempts) {
                        RuntimeException e = new RuntimeException("出现错误，请重新启动服务");
                        handler.onError(e);
                        completionFuture.completeExceptionally(e);
                        return;
                    }

                    // 1. 注入外部消息
                    injectExternalMessages(this.history);

                    // 2. 对话压缩
                    compactHistoryIfNeeded(this.history, compact_state, "None");

                    // 3. 构建并发送流式请求
                    JSONObject requestBody = buildRequestBody(this.history);
                    requestBody.set("stream", true); // 关键：开启流式输出

                    CompletableFuture<JSONObject> messageFuture;
                    try {
                        messageFuture = sendStreamRequest(requestBody, handler);
                    } catch (Exception e) {
                        if (handleRequestError(e, recoveryAttempts, this.history)) {
                            continue;
                        }
                        handler.onError(e);
                        completionFuture.completeExceptionally(e);
                        return;
                    }

                    // 4. 等待完整的message（工具调用需要完整信息）
                    JSONObject message;
                    try {
                        message = messageFuture.get();
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (handleRequestError((Exception) cause, recoveryAttempts, this.history)) {
                            continue;
                        }
                        handler.onError(e);
                        completionFuture.completeExceptionally(e);
                        return;
                    }

                    // 5. 处理 max_tokens 超限
                    String stopReason = message.getStr("finish_reason", "");
                    if ("length".equals(stopReason)) {
                        if (handleMaxTokensLimit(outputRecoveryCount, this.history)) {
                            continue;
                        }
                    }
                    outputRecoveryCount.set(0);

                    // 6. 添加完整消息到历史
                    if (message.containsKey("reasoning_content")) {
                        addMsg(message.getStr("role"), message.getStr("content"), message.get("tool_calls"), message.getStr("reasoning_content"));
                    } else {
                        addMsg(message.getStr("role"), message.getStr("content"), message.get("tool_calls"));
                    }

                    // 7. 无工具调用，结束流式响应
                    if (message.get("tool_calls") == null) {
                        handler.onComplete();
                        completionFuture.complete(null);
                        return;
                    }

                    // 8. 执行工具调用（同步执行，完成后继续下一轮对话）
                    JSONArray toolCallsArray = message.getJSONArray("tool_calls");
                    executeToolCalls(toolCallsArray, this.history, false);
                }
            } catch (Exception e) {
                System.err.println("===== ChatStream 崩溃堆栈（必看！） =====");
                e.printStackTrace();
                System.err.println("======================================");
                handler.onError(e);
                completionFuture.completeExceptionally(e);
            }
        });

        return completionFuture;
    }

    // ==================== 核心：同步单轮对话（修复：使用独立历史） ====================
    public String singleTurnChat(String prompt) {
        AtomicInteger recoveryAttempts = new AtomicInteger(0);
        AtomicInteger outputRecoveryCount = new AtomicInteger(0);

        // 单轮专用：独立临时历史列表（修复：不再污染全局历史）
        List<Map<String, Object>> tempHistory = new ArrayList<>();

        // 1. 初始化 System Prompt
        addMsg(tempHistory, "system", buildSingleTurnSystemPrompt());

        // 2. 添加用户 Prompt
        addMsg(tempHistory, "user", prompt);

        try {
            while (true) {
                if (recoveryAttempts.get() > maxRecoveryAttempts) {
                    return "出现错误，请重新启动服务";
                }

                // 1. 注入外部消息
                injectExternalMessages(tempHistory);

                // 2. 对话压缩
                compactHistoryIfNeeded(tempHistory, compact_state, "None");

                // 3. 构建并发送请求
                JSONObject requestBody = buildRequestBody(tempHistory);
                HttpResponse<String> response;
                try {
                    response = sendHttpRequest(requestBody);
                } catch (Exception e) {
                    if (handleRequestError(e, recoveryAttempts, tempHistory)) {
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
                    if (handleMaxTokensLimit(outputRecoveryCount, tempHistory)) {
                        continue;
                    }
                }
                outputRecoveryCount.set(0);

                // 6. 添加消息到临时历史
                if (message.containsKey("reasoning_content")) {
                    addMsg(tempHistory, message.getStr("role"), message.getStr("content"), message.get("tool_calls"), message.getStr("reasoning_content"));
                } else {
                    addMsg(tempHistory, message.getStr("role"), message.getStr("content"), message.get("tool_calls"));
                }

                // 7. 无工具调用，直接返回
                if (message.get("tool_calls") == null) {
                    return message.getStr("content");
                }

                // 8. 执行工具调用（单轮任务自动跳过用户确认）
                JSONArray toolCallsArray = message.getJSONArray("tool_calls");
                executeToolCalls(toolCallsArray, tempHistory, true);
            }
        } catch (Exception e) {
            System.err.println("===== SingleTurnChat 崩溃堆栈（必看！） =====");
            e.printStackTrace();
            System.err.println("============================================");
            return "请求失败：" + e.getMessage();
        }
    }

    // ==================== 核心：流式单轮对话（新增） ====================
    public CompletableFuture<Void> singleTurnChatStream(String prompt, StreamResponseHandler handler) {
        AtomicInteger recoveryAttempts = new AtomicInteger(0);
        AtomicInteger outputRecoveryCount = new AtomicInteger(0);
        CompletableFuture<Void> completionFuture = new CompletableFuture<>();

        // 单轮专用：独立临时历史列表
        List<Map<String, Object>> tempHistory = new ArrayList<>();

        // 1. 初始化 System Prompt
        addMsg(tempHistory, "system", buildSingleTurnSystemPrompt());

        // 2. 添加用户 Prompt
        addMsg(tempHistory, "user", prompt);

        CompletableFuture.runAsync(() -> {
            try {
                while (true) {
                    if (recoveryAttempts.get() > maxRecoveryAttempts) {
                        handler.onError(new RuntimeException("出现错误，请重新启动服务"));
                        completionFuture.completeExceptionally(null);
                        return;
                    }

                    // 1. 注入外部消息
                    injectExternalMessages(tempHistory);

                    // 2. 对话压缩
                    compactHistoryIfNeeded(tempHistory, compact_state, "None");

                    // 3. 构建并发送流式请求
                    JSONObject requestBody = buildRequestBody(tempHistory);
                    requestBody.set("stream", true); // 关键：开启流式输出

                    CompletableFuture<JSONObject> messageFuture;
                    try {
                        messageFuture = sendStreamRequest(requestBody, handler);
                    } catch (Exception e) {
                        if (handleRequestError(e, recoveryAttempts, tempHistory)) {
                            completionFuture.completeExceptionally(e);
                            continue;
                        }
                        handler.onError(e);
                        return;
                    }

                    // 4. 等待完整的message
                    JSONObject message;
                    try {
                        message = messageFuture.get();
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        if (handleRequestError((Exception) cause, recoveryAttempts, tempHistory)) {
                            continue;
                        }
                        handler.onError(e);
                        completionFuture.completeExceptionally(e);
                        return;
                    }

                    // 5. 处理 max_tokens 超限
                    String stopReason = message.getStr("finish_reason", "");
                    if ("length".equals(stopReason)) {
                        if (handleMaxTokensLimit(outputRecoveryCount, tempHistory)) {
                            continue;
                        }
                    }
                    outputRecoveryCount.set(0);

                    // 6. 添加完整消息到临时历史
                    if (message.containsKey("reasoning_content")) {
                        addMsg(tempHistory, message.getStr("role"), message.getStr("content"), message.get("tool_calls"), message.getStr("reasoning_content"));
                    } else {
                        addMsg(tempHistory, message.getStr("role"), message.getStr("content"), message.get("tool_calls"));
                    }

                    // 7. 无工具调用，结束流式响应
                    if (message.get("tool_calls") == null) {
                        completionFuture.complete(null);
                        return;
                    }

                    // 8. 执行工具调用（单轮任务自动跳过用户确认）
                    JSONArray toolCallsArray = message.getJSONArray("tool_calls");
                    executeToolCalls(toolCallsArray, tempHistory, false);
                }
            } catch (Exception e) {
                System.err.println("===== SingleTurnChatStream 崩溃堆栈（必看！） =====");
                e.printStackTrace();
                System.err.println("================================================");
                handler.onError(e);
            }
        });
        return completionFuture;
    }

    // ==================== 辅助：错误处理（修复：支持指定目标历史） ====================
    private boolean handleRequestError(Exception e, AtomicInteger recoveryAttempts, List<Map<String, Object>> targetHistory) throws InterruptedException, IOException {
        String errorBody = e.getMessage().toLowerCase();
        boolean isOverlongPrompt = errorBody.contains("overlong_prompt") || (errorBody.contains("prompt") && errorBody.contains("long"));

        if (isOverlongPrompt) {
            compactHistoryIfNeeded(targetHistory, compact_state, "None");
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

    private boolean handleMaxTokensLimit(AtomicInteger outputRecoveryCount, List<Map<String, Object>> targetHistory) {
        outputRecoveryCount.incrementAndGet();
        if (outputRecoveryCount.get() <= 3) {
            System.out.printf("[Recovery] max_tokens hit(%d/3. Injecting continuation...%n", outputRecoveryCount.get());
            addMsg(targetHistory, "user", "Output limit hit. Continue directly from where you stopped \n" +
                    "no recap, no repetition. Pick up mid-sentence if needed.");
            return true;
        }
        return false;
    }

    // ==================== 辅助：单轮对话 System Prompt ====================
    private String buildSingleTurnSystemPrompt() {
        String prompt = String.format("""
        You are a coding agent performing a scheduled background task.
        Current directory: %s
        Skills available: {%s}.
        Be concise and focus on the task.
        可以使用mcp工具来查询天气，不要使用没必要的工具，回复尽量简洁
        """, currentDir, skillRegistry.describe_available());
        return prompt != null ? prompt : "You are a helpful assistant.";
    }
}