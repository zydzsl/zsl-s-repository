package zsl.agent.client;

import cn.hutool.json.JSONUtil;
import zsl.agent.entry.OpenAiTool;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class McpClient {
    private final String serverName;
    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final BufferedReader errorReader;
    private List<Map<String, Object>> tools = new ArrayList<>();
    private final AtomicInteger requestId = new AtomicInteger(1);

    // 存储等待响应的回调（用于异步处理，这里简化：同步请求通过阻塞队列更好，但为兼容原逻辑，暂保持简单）
    private final Map<Integer, CompletableFuture<Map<String, Object>>> pendingRequests = new ConcurrentHashMap<>();
    private volatile boolean running = true;
    private Thread readerThread;

    public McpClient(String serverName, String command, String[] args, Map<String, String> env) throws IOException {
        this.serverName = serverName;
        List<String> commandLine = new ArrayList<>();

        // Windows 兼容 npx
        if ("npx".equals(command)) {
            commandLine.add("cmd");
            commandLine.add("/c");
            commandLine.add("npx");
        } else {
            commandLine.add(command);
        }

        if (args != null) {
            commandLine.addAll(Arrays.asList(args));
        }

        ProcessBuilder pb = new ProcessBuilder(commandLine);
        Map<String, String> processEnv = pb.environment();
        processEnv.putAll(System.getenv());
        if (env != null) {
            processEnv.putAll(env);
        }

        this.process = pb.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

        // 启动错误监听
        new Thread(this::readErrorStream, "MCP-Error-" + serverName).start();
        // 启动响应/通知读取线程
        startReaderThread();

        handshake();      // 握手 + 发送 initialized
        listTools();      // 获取工具列表
    }

    // 启动独立线程持续读取服务器消息
    private void startReaderThread() {
        readerThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    Map<String, Object> msg = JSONUtil.toBean(line, Map.class);
                    if (msg.containsKey("id")) {
                        // 这是一个响应（对应某个请求）
                        Integer id = (Integer) msg.get("id");
                        CompletableFuture<Map<String, Object>> future = pendingRequests.remove(id);
                        if (future != null) {
                            future.complete(msg);
                        }
                    } else if (msg.containsKey("method")) {
                        // 这是一个通知（服务器主动发送）
                        handleNotification(msg);
                    } else {
                        // 未知消息，忽略或打印
                        System.err.println("[MCP] Unknown message: " + line);
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("[MCP] Reader thread error: " + e.getMessage());
                }
            }
        }, "MCP-Reader-" + serverName);
        readerThread.start();
    }

    // 处理服务器通知（可根据需要扩展）
    private void handleNotification(Map<String, Object> notification) {
        String method = (String) notification.get("method");
        // 示例：打印日志，实际可按需处理
        System.out.println("[MCP] Notification: " + method + " -> " + notification.get("params"));
    }

    // 发送请求并同步等待响应（改造为使用 CompletableFuture）
    private Map<String, Object> sendRequest(String method, Map<String, Object> params) throws InterruptedException {
        int id = requestId.getAndIncrement();
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        if (params != null) {
            request.put("params", params);
        }
        String json = JSONUtil.toJsonStr(request) + "\n";
        try {
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            System.err.println("[MCP] Send error: " + e.getMessage());
            return null;
        }

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        // 设置超时（可选）
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingRequests.remove(id);
            System.err.println("[MCP] Request timeout: " + method);
            return null;
        } catch (Exception e) {
            pendingRequests.remove(id);
            System.err.println("[MCP] Request error: " + e.getMessage());
            return null;
        }
    }

    // 发送通知（无需等待响应）
    private void sendNotification(String method, Map<String, Object> params) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("jsonrpc", "2.0");
            notification.put("method", method);
            if (params != null) {
                notification.put("params", params);
            }
            String json = JSONUtil.toJsonStr(notification) + "\n";
            writer.write(json);
            writer.flush();
        } catch (IOException e) {
            System.err.println("[MCP] Send notification error: " + e.getMessage());
        }
    }

    // 握手初始化（符合标准）
    private void handshake() {
        try {
            // 1. 构建 initialize 参数
            Map<String, Object> params = new HashMap<>();
            params.put("protocolVersion", "2024-11-05"); // 当前最新版本
            params.put("capabilities", Map.of()); // 客户端能力，暂时为空
            params.put("clientInfo", Map.of(
                    "name", "ZSL-MCPClient",
                    "version", "1.0.0"
            ));

            Map<String, Object> resp = sendRequest("initialize", params);
            if (resp == null || resp.containsKey("error")) {
                System.err.println("[MCP] Initialize failed: " + resp);
                return;
            }
            // 可选：解析服务器能力
            Object resultObj = resp.get("result");
            if (resultObj instanceof Map) {
                Map<String, Object> result = (Map<String, Object>) resultObj;
                Map<String, Object> serverCapabilities = (Map<String, Object>) result.get("capabilities");
                System.out.println("[MCP] Server capabilities: " + serverCapabilities);
            }

            // 2. 发送 initialized 通知
            sendNotification("initialized", null);
            System.out.println("[MCP] Handshake completed with " + serverName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[MCP] Handshake interrupted");
        }
    }

    // 获取工具列表（适配新的 sendRequest）
    public void listTools() {
        System.out.println("[MCP] Getting tools from " + serverName);
        try {
            Map<String, Object> resp = sendRequest("tools/list", null);
            if (resp == null) return;
            Object result = resp.get("result");
            if (!(result instanceof Map)) return;
            Map<String, Object> resultMap = (Map<String, Object>) result;
            Object toolsObj = resultMap.get("tools");
            if (toolsObj instanceof List) {
                this.tools = (List<Map<String, Object>>) toolsObj;
                System.out.println("[MCP] " + serverName + " loaded " + this.tools.size() + " tools");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // 调用MCP工具（给Router用）
    public String callTool(String toolName, Map<String, Object> arguments) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("name", toolName);
            params.put("arguments", arguments == null ? new HashMap<>() : arguments);
            Map<String, Object> resp = sendRequest("tools/call", params);
            if (resp == null) {
                return "{\"error\":\"MCP服务无响应\"}";
            }
            if (resp.containsKey("error")) {
                return JSONUtil.toJsonStr(resp.get("error"));
            }
            Object result = resp.get("result");
            return result != null ? JSONUtil.toJsonStr(result) : "{\"text\":\"执行成功\"}";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "{\"error\":\"调用被中断\"}";
        }
    }

    // 转换为OpenAI格式工具
    public List<OpenAiTool> getOpenAiTools() {
        List<OpenAiTool> list = new ArrayList<>();
        for (Map<String, Object> toolMap : tools) {
            OpenAiTool tool = new OpenAiTool();
            tool.setType("function");
            OpenAiTool.Function func = new OpenAiTool.Function();
            func.setName("mcp__" + serverName + "__" + toolMap.get("name"));
            func.setDescription((String) toolMap.get("description"));
            func.setParameters((Map<String, Object>) toolMap.get("inputSchema"));
            tool.setFunction(func);
            list.add(tool);
        }
        return list;
    }

    private void readErrorStream() {
        try {
            String line;
            while ((line = errorReader.readLine()) != null) {
                if (!line.contains("deprecated")) {
                    System.err.println("[MCP-ERROR] " + serverName + ": " + line);
                }
            }
        } catch (Exception ignored) {}
    }

    // 优雅关闭客户端
    public void close() {
        running = false;
        try {
            if (readerThread != null) readerThread.interrupt();
            writer.close();
            reader.close();
            errorReader.close();
            process.destroyForcibly();
        } catch (IOException e) {
            // ignore
        }
    }

    public String getServerName() {
        return serverName;
    }

    public List<Map<String, Object>> getTools() {
        return tools;
    }
}