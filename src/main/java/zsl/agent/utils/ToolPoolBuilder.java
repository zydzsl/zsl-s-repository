package zsl.agent.utils;

import zsl.agent.entry.OpenAiTool;
import zsl.agent.utils.MCPToolRouter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ToolPoolBuilder {

    // 你自己的本地原生工具（对应 Python NATIVE_TOOLS）
    public static final List<OpenAiTool> TOOLS = new ArrayList<>();

    /**
     * 合并原生工具 + MCP 工具
     * 原生工具优先级更高
     */
    public static List<OpenAiTool> buildToolPool(MCPToolRouter mcpRouter) {
        // 1. 先把所有【原生本地工具】放进去
        List<OpenAiTool> allTools = new ArrayList<>(TOOLS);

        // 2. 拿到所有MCP工具
        List<OpenAiTool> mcpTools = mcpRouter.getAllTools();
        System.out.println("MCP工具：" + mcpTools.size());

        // 3. 记录已经存在的工具名（用于去重）
        Set<String> nativeNames = new HashSet<>();
        for (OpenAiTool tool : allTools) {
            nativeNames.add(tool.function.name);
        }

        // 4. 把MCP工具加进去（不重复添加）
        for (OpenAiTool tool : mcpTools) {
            String toolName = tool.function.name;
            System.out.println("MCP工具：" + toolName);
            // 如果原生工具里没有这个名字，才添加
            if (!nativeNames.contains(toolName)) {
                allTools.add(tool);
            }
        }

        return allTools;
    }
}