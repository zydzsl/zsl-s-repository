package zsl.agent.entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Task {
    private int id;
    private String subject;
    private String description;
    private String status;
    private List<Integer> blockedBy;
    private List<Integer> blocks;
    private String owner;
}
