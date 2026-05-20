package zsl.agent.entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Memory {
    private String name;
    private String description;
    private String type;
    private String content;
    private String file;
}
