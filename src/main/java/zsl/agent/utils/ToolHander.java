//package zsl.agent.utils;
//import cn.hutool.cron.Scheduler;
//import cn.hutool.json.JSONObject;
//import cn.hutool.json.JSONUtil;
//import jakarta.annotation.Resource;
//import zsl.agent.entry.Teammember;
//import zsl.agent.funtions.*;
//import zsl.agent.entry.PlanItem;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.function.Function;
//
//import static zsl.agent.AgentApplication.cronScheduler;
//import static zsl.agent.client.Client.backgroundTask;
//
//import static zsl.agent.client.Client.memoryManager;
//import static zsl.agent.client.Client.skillRegistry;
//
//

//import cn.hutool.json.JSONObject;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.function.Function;
//
/////**
//// * 工具调度映射（对应Python TOOL_HANDLERS）
//// */
////public class ToolHander {
//    private static final Map<String, Function<JSONObject, String>> HANDLERS = new HashMap<>();
//
//    @Resource
//    private static TaskManager taskManager = new TaskManager();
//    public static final MessageBus messageBus = new MessageBus(TeamContain.INBOX_DIR);
//    private static TeammateManager teammateManager = new TeammateManager();
//
//    static {
//        // bash命令
//        HANDLERS.put("bash", params -> ToolExecutor.runBash(params.getStr("command")));
//        // 读取文件
//        HANDLERS.put("read_file", params -> ToolExecutor.runRead(
//                params.getStr("path"),
//                params.getInt("limit")
//        ));
//        // 写入文件
//        HANDLERS.put("write_file", params -> ToolExecutor.runWrite(
//                params.getStr("path"),
//                params.getStr("content")
//        ));
//        // 编辑文件
//        HANDLERS.put("edit_file", params -> ToolExecutor.runEdit(
//                params.getStr("path"),
//                params.getStr("old_text"),
//                params.getStr("new_text")
//        ));
//        HANDLERS.put("todo", params -> TodoManager.todo(params.getJSONArray("items").toList(PlanItem.class)));
//        HANDLERS.put("load_skill", params -> skillRegistry.load_skill_text(params.getStr("name")));
//        HANDLERS.put("save_memory", params -> memoryManager.saveMemory(
//                params.getStr("name"),
//                params.getStr("description"),
//                params.getStr("type"),
//                params.getStr("content")
//        ));
//        HANDLERS.put("task_create", params -> taskManager.create(
//                params.getStr("subject"),
//                params.getStr("description", "") // 描述为可选参数，默认空字符串
//        ));
//        // 更新任务（状态/负责人/依赖）
//        HANDLERS.put("task_update", params -> taskManager.update(
//                params.getInt("task_id"),
//                params.getStr("status", null),
//                params.getStr("owner", null),
//                params.getJSONArray("addBlockedBy") != null ? params.getJSONArray("addBlockedBy").toList(Integer.class) : null,
//                params.getJSONArray("addBlocks") != null ? params.getJSONArray("addBlocks").toList(Integer.class) : null
//        ));
//        // 列出所有任务
//        HANDLERS.put("task_list", params -> taskManager.listAll());
//        // 获取任务详情
//        HANDLERS.put("task_get", params -> taskManager.get(params.getInt("task_id")));
//        // 后台任务工具
//        HANDLERS.put("bg_run", params -> backgroundTask.run(params.getStr("command")));
//        HANDLERS.put("bg_check", params -> backgroundTask.check(params.getStr("task_id", null)));
//        HANDLERS.put("bg_drain", params -> JSONUtil.toJsonStr(backgroundTask.drainNotifications()));
//        HANDLERS.put("cron_create", params -> {
//            String cron = params.getStr("cron");
//            String prompt = params.getStr("prompt");
//            boolean recurring = params.getBool("recurring", true);
//            boolean durable = params.getBool("durable", false);
//            return cronScheduler.createTask(cron, prompt, recurring, durable);
//        });
//        HANDLERS.put("cron_delete", params -> {
//            String taskId = params.getStr("id");
//            return cronScheduler.deleteTask(taskId);
//        });
//        HANDLERS.put("cron_list", params -> cronScheduler.listTasks());
//        // 注册消息发送工具
//        HANDLERS.put("send_message", params -> {
//            String sender = params.getStr("sender");
//            String to = params.getStr("to");
//            String content = params.getStr("content");
//            return messageBus.send(sender,to, content);
//        });
//        HANDLERS.put("read_inbox", params -> messageBus.readInbox(params.getStr("name")).toString());
//        // 生成队员
//        HANDLERS.put("spawn_teammate", params -> {
//            String name = params.getStr("name");
//            String role = params.getStr("role");
//            String prompt = params.getStr("prompt");
//            return teammateManager.spawn(name, role, prompt);
//        });
//        HANDLERS.put("list_teammates", params -> teammateManager.listTeammates().toString());
//
//    }
//
//    /**
//     * 执行工具
//     * @param toolName 工具名
//     * @param params 参数
//     * @return 执行结果
//     */
//    public static String execute(String toolName, JSONObject params, String toolId) {
//        if(toolName.equals("compact"))
//            return "成功压缩";
//        Function<JSONObject, String> handler = HANDLERS.get(toolName);
//        if (handler == null)
//            return "Error: Unknown tool: " + toolName;
//        return handler.apply(params);
//    }
//}