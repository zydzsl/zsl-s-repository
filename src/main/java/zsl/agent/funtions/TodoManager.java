package zsl.agent.funtions;

import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Component;
import zsl.agent.config.AiToolMethod;
import zsl.agent.entry.PlanItem;
import zsl.agent.entry.PlanningState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TodoManager {

    private static PlanningState planningState = new PlanningState(new ArrayList<>(), 0);
    private static int max_rounds = 5;
    private static final Map<String, String> STATUS_MARKER = Map.of(
            "pending", "[ ]",
            "in_progress", "[>]",
            "completed", "[x]"
    );
    public static void rounds_since_update(){
        planningState.setRounds_since_update(0);
    }

    @AiToolMethod(name = "todo", desc = "Create a session plan")
    public static String todo(JSONObject params) {
        List<PlanItem> plan_items = params.getJSONArray("items").toList(PlanItem.class);

        if(plan_items.size() > 12){
            throw new IllegalArgumentException("Keep the session plan short (max 12 items)");
        }
        List<PlanItem> normalized = new ArrayList<>();
        int in_progress_count = 0;

        for (int index = 0; index < plan_items.size(); index++) {
            PlanItem rawItem =  plan_items.get(index);

            // 2. 提取字段（对应Python的get/strip/lower）
            String content =rawItem.getContent ().trim();
            String status = rawItem.getStatus().toLowerCase();
            String activeForm = rawItem.getActive_form().trim();
            // 3. 内容非空校验
            if (content.isEmpty()) {
                throw new IllegalArgumentException("Item " + index + ": content required");
            }

            // 4. 状态合法性校验
            if (!List.of("pending", "in_progress", "completed").contains(status)) {
                throw new IllegalArgumentException("Item " + index + ": invalid status '" + status + "'");
            }
            // 5. 统计进行中数量
            if ("in_progress".equals(status)) {
                in_progress_count++;
            }
            normalized.add(rawItem);
        }
        if (in_progress_count > 1)
            throw new IllegalArgumentException("Only one item can be in progress at a time");
        planningState = new PlanningState(normalized, 0);

        return render(params);
    }

    @AiToolMethod(name = "render", desc = "Render the session plan")
    public static String render(JSONObject params) {
        if (planningState.getItems().isEmpty()){
            return "No session plan loaded";
        }
        List<String> lines = planningState.getItems().stream()
                .map(item -> {
                    // 拼接图标+内容
                    String line = STATUS_MARKER.get(item.getStatus()) + " " + item.getContent();
                    // 进行中且有activeForm：追加描述（和Python逻辑一致）
                    if ("in_progress".equals(item.getStatus()) && !item.getActive_form().isBlank()) {
                        line += " (" + item.getActive_form() + ")";
                    }
                    return line;
                }).collect(Collectors.toList());

        // 3. Stream流统计完成数量（替代Python的sum生成器）
        long completedCount =  planningState.getItems().stream()
                .filter(item -> "completed".equals(item.getStatus()))
                .count();
        // 4. 追加进度行（和Python格式一致）
        lines.add("\n(" + completedCount + "/" + planningState.getItems().size() + " completed)");
        // 5. 用换行拼接所有行（替代Python的"\n".join(lines)）
        return String.join("\n", lines);
    }

    public static String reminder(){
        if (planningState.getItems().isEmpty()){
            return  "No session plan loaded";
        }
        if(planningState.getRounds_since_update() <= max_rounds){
            return "No reminder needed";
        }
        return  "<reminder>Refresh your current plan before continuing.</reminder>";
    }

    public static void note_round_without_update(){
        planningState.setRounds_since_update(planningState.getRounds_since_update() + 1);
    }
}
