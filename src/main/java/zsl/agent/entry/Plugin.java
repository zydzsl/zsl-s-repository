package zsl.agent.entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Plugin {
    private String name;
    private String version;
    private Map<String, McpServer> mcpServers;
}
