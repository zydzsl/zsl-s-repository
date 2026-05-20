package zsl.agent.utils;

import zsl.agent.client.McpClient;
import zsl.agent.entry.OpenAiTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MCPToolRouter {

    // 静态存储：serverName -> McpClient（你的原有设计）
    private static final Map<String, McpClient> clients = new HashMap<>();

    /**
     * 注册MCP客户端（修复核心错误）
     */
    public void registerClient(McpClient client) {
        // key = 服务名(weather/filesystem)，value = 客户端
        clients.put(client.getServerName(), client);
    }

    /**
     * 判断是否为MCP工具（规则：mcp__ 开头）
     */
    public boolean isMcpTool(String toolName) {
        return toolName.startsWith("mcp__");
    }

    /**
     * 路由调用MCP工具（你的原有逻辑：mcp__server__tool 格式）
     */
    public  String call(String toolName, Map<String, Object> arguments) {
        // 分割格式：mcp__{serverName}__{actualTool}
        String[] parts = toolName.split("__", 3);
        if (parts.length != 3) {
            return "Error: 无效的MCP工具名称: " + toolName;
        }

        String serverName = parts[1];
        String actualTool = parts[2];

        McpClient client = clients.get(serverName);
        if (client == null) {
            return "Error: MCP服务不存在: " + serverName;
        }
        String result =client.callTool(actualTool, arguments);
        System.out.println("MCP: " + result);

        // 调用MCP客户端的工具执行方法
        return result;
    }

    /**
     * 获取所有MCP提供的OpenAI格式工具（静态方法，供全局调用）
     */
    public  List<OpenAiTool> getAllTools() {
        List<OpenAiTool> tools = new ArrayList<>();

        // 等待 clients 被注册（解决启动顺序问题）
        int waitCount = 0;
        while (clients.isEmpty() && waitCount < 20) {
            try {
                Thread.sleep(100); // 等待 MCP 注册
                waitCount++;
            } catch (InterruptedException e) {
                break;
            }
        }

        for (McpClient client : clients.values()) {
            tools.addAll(client.getOpenAiTools());
        }
        return tools;
    }

    public static void clear() {
        clients.clear();
    }
}