package zsl.agent.entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SkillManifest {

    private String name;
    private String description;
    private String path;
}
