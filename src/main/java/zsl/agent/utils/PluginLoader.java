package zsl.agent.utils;

import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.stereotype.Component;
import zsl.agent.entry.McpServer;
import zsl.agent.entry.Plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static zsl.agent.utils.TeamContain.WORK_DIR;

@Component
@Data
public class PluginLoader {
    private final Map<String, Plugin> plugins = new HashMap<>();

    @PostConstruct
    public void scan() {
        // ✅ 强制写死绝对路径，jar 包 100% 能读到
        File pluginFile = new File("E:\\VS\\agent\\.claude-plugin\\plugin.json");

        if (!pluginFile.exists()) {
            System.out.println("[MCP] 未找到插件文件：" + pluginFile.getAbsolutePath());
            return;
        }

        try {
            String json = Files.readString(pluginFile.toPath());
            Plugin plugin = JSONUtil.toBean(json, Plugin.class);
            String name = plugin.getName() == null ? "default" : plugin.getName();
            plugins.put(name, plugin);
//            System.out.println("[MCP] 插件加载成功：" + pluginFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[MCP] 插件加载失败：" + e.getMessage());
        }
    }

    public Map<String, McpServer> getMcpServers() {
        Map<String, McpServer> allServers = new HashMap<>();
        for (Plugin plugin : plugins.values()) {
            if (plugin.getMcpServers() != null) {
                allServers.putAll(plugin.getMcpServers());
            }
        }
        return allServers;
    }
}
