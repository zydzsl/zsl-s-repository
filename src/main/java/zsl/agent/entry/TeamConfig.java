package zsl.agent.entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamConfig {
    private   String TEAM_NAME;
    private List<Teammember> TEAM_MEMBERS;
}
