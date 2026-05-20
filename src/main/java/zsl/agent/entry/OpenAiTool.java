package zsl.agent.entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenAiTool {
    public String type = "function";
    public Function function = new Function();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Function {
        public String name;
        public String description;
        public Map<String, Object> parameters;


    }
}
