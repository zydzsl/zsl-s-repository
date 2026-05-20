package zsl.agent.funtions;

import cn.hutool.core.collection.CollectionUtil;
import zsl.agent.client.McpClient;
import zsl.agent.entry.McpServer;
import zsl.agent.entry.OpenAiTool;
import zsl.agent.utils.MCPToolRouter;
import zsl.agent.utils.PluginLoader;
import zsl.agent.utils.ToolPoolBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 启动工具类
 * 对应 Python 逻辑：扫描插件 → 连接MCP服务 → 注册路由 → 统计工具
 */
public class McpBootstrap {

    public static List<OpenAiTool> allTools = new ArrayList<>();

    /**
     * 初始化MCP（核心方法，直接调用）
     * @param pluginLoader 插件加载器
     * @param mcpRouter 工具路由
     * @return 初始化是否成功
     */
    public static boolean init(PluginLoader pluginLoader, MCPToolRouter mcpRouter) {
        try {
            // 1. 扫描插件（对应 plugin_loader.scan()）
            pluginLoader.scan();

            // 2. 获取所有MCP服务
            Map<String, McpServer> allMcpServers = pluginLoader.getMcpServers();
            if (CollectionUtil.isEmpty(allMcpServers)) {
                System.out.println("[MCP] 未找到任何MCP服务配置");
                return false;
            }

            // 打印加载的插件名
            List<String> pluginNames = new ArrayList<>(pluginLoader.getPlugins().keySet());
            System.out.println("[Plugins loaded: " + String.join(", ", pluginNames) + "]");

            // 3. 遍历所有MCP服务，创建客户端并连接
            int connectedCount = 0;
            for (Map.Entry<String, McpServer> entry : allMcpServers.entrySet()) {
                String serverName = entry.getKey();
                McpServer config = entry.getValue();

                try {
                    // 创建MCP客户端（自动握手连接）
                    McpClient mcpClient = new McpClient(
                            serverName,
                            config.getCommand(),
                            config.getArgs(),
                            config.getEnv()
                    );

//                    // 获取工具列表
//                    mcpClient.listTools();

                    // 注册到路由
                    mcpRouter.registerClient(mcpClient);

                    System.out.println("[MCP] Connected to " + serverName);
                    connectedCount++;
                } catch (Exception e) {
                    System.err.println("[MCP] 连接失败：" + serverName + "，原因：" + e.getMessage());
                }
            }

            // 4. 统计并打印工具数量
            int mcpToolCount = mcpRouter.getAllTools().size();
            allTools = mcpRouter.getAllTools();



            System.out.println("tools (" + mcpToolCount + " from MCP)]");
            return connectedCount > 0;
        } catch (Exception e) {
            System.err.println("[MCP] 初始化失败：" + e.getMessage());
            return false;
        }
    }
}