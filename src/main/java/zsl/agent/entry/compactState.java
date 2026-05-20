package zsl.agent.entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class compactState {
//    recent_files: list[str] = field(default_factory=list)
    public boolean has_compacted = false;
    public String last_summary = "";
    private List<String> recentFiles = new ArrayList<>();
}
