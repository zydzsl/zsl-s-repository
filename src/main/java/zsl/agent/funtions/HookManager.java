package zsl.agent.funtions;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class HookManager {

    public static final List<String> HOOK_EVENTS = Arrays.asList("PreToolUse", "PostToolUse", "SessionStart");
    public static final int HOOK_TIMEOUT = 30;
    /** 工作区信任标记文件 */
    public static final Path TRUST_MARKER = Paths.get(System.getProperty("user.dir"), ".claude", ".ai_trusted");
    /** 默认工作目录 */
    private static final Path DEFAULT_WORKDIR = Paths.get(System.getProperty("user.dir"));

    // ===================== 成员变量 =====================
    /** 存储所有 Hook 配置：key=事件名，value=钩子列表 */
    private final Map<String, List<JSONObject>> hooks;
    /** SDK 模式标记 */
    private final boolean sdkMode ;
    /** 工作目录 */
    private final Path workdir;

    // ===================== 构造方法 =====================
    public HookManager() {
        this(null, true);
    }

    public HookManager(Path configPath, boolean sdkMode) {
        // 初始化 Hook 容器
        this.hooks = new HashMap<>();
        for (String event : HOOK_EVENTS) {
            this.hooks.put(event, new ArrayList<>());
        }
        this.sdkMode = sdkMode;
        this.workdir = DEFAULT_WORKDIR;

        // 加载配置文件
        configPath = configPath == null ? DEFAULT_WORKDIR.resolve("hooks.json") : configPath;
        loadConfig(configPath);
    }

    /**
     * 加载 hooks.json 配置文件
     */
    private void loadConfig(Path configPath) {
        File configFile = configPath.toFile();
        if (!configFile.exists()) {
            System.out.println("[Hooks] 配置文件不存在：" + configPath);
            return;
        }

        try {
            // 读取并解析 JSON
            String jsonStr = FileUtil.readString(configFile, StandardCharsets.UTF_8);
            JSONObject config = JSONUtil.parseObj(jsonStr);
            JSONObject hooksConfig = config.getJSONObject("hooks");

            // 加载对应事件的 Hook 配置
            for (String event : HOOK_EVENTS) {
                if (hooksConfig != null && hooksConfig.containsKey(event)) {
                    JSONArray array = hooksConfig.getJSONArray(event);
                    List<JSONObject> eventHooks = new ArrayList<>();
                    for (int i = 0; i < array.size(); i++) {
                        eventHooks.add(array.getJSONObject(i));
                    }
                    this.hooks.put(event, eventHooks);
                }
            }
            System.out.println("[Hooks loaded from " + configPath + "]");
        } catch (Exception e) {
            System.err.println("[Hook config error: " + e.getMessage() + "]");
        }
    }

    // ===================== 核心方法 =====================
    /**
     * 检查工作区是否可信（对标原版逻辑）
     */
    private boolean checkWorkspaceTrust() {
        if (sdkMode) {
            return true;
        }
        return TRUST_MARKER.toFile().exists();
    }

    /**
     * 执行指定事件的所有 Hook（核心方法，对标原版 run_hooks）
     * @param event 事件名称：PreToolUse/PostToolUse/SessionStart
     * @param context 上下文参数（工具名、入参、出参等）
     * @return 执行结果：blocked/消息/拦截原因等
     */
    public Map<String, Object> runHooks(String event, Map<String, Object> context) {
        Map<String, Object> result = new HashMap<>();
        result.put("blocked", false);
        result.put("messages", new ArrayList<String>());

        // 信任检查：不信任则不执行任何 Hook
        if (!checkWorkspaceTrust()) {
            return result;
        }

        // 获取当前事件的所有 Hook
        List<JSONObject> hooks = this.hooks.getOrDefault(event, Collections.emptyList());
        for (JSONObject hookDef : hooks) {
            // 1. 匹配器校验（工具名过滤）
            String matcher = hookDef.getStr("matcher");
            if (StrUtil.isNotBlank(matcher) && context != null) {
                String toolName = (String) context.getOrDefault("tool_name", "");
                if (!"*".equals(matcher) && !matcher.equals(toolName)) {
                    continue;
                }
            }

            // 2. 获取命令
            String command = hookDef.getStr("command");
            if (StrUtil.isBlank(command)) {
                continue;
            }

            // 3. 构建环境变量
            Map<String, String> env = new HashMap<>(System.getenv());
            if (context != null) {
                env.put("HOOK_EVENT", event);
                env.put("HOOK_TOOL_NAME", (String) context.getOrDefault("tool_name", ""));
                // 工具入参（截断避免过长）
                Object toolInput = context.get("tool_input");
                if (toolInput != null) {
                    String inputStr = JSONUtil.toJsonStr(toolInput);
                    env.put("HOOK_TOOL_INPUT", StrUtil.sub(inputStr, 0, 10000));
                }

                // 工具出参
                Object toolOutput = context.get("tool_output");
                if (toolOutput != null) {
                    env.put("HOOK_TOOL_OUTPUT", StrUtil.sub(String.valueOf(toolOutput), 0, 10000));
                }
            }

            // 4. 执行 Shell 命令
            try {
                ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
                // Linux/Mac 替换为：new ProcessBuilder("sh", "-c", command)
                pb.directory(workdir.toFile());
                pb.environment().putAll(env);
                pb.redirectErrorStream(true);

                Process process = pb.start();
                // 超时等待
                boolean finished = process.waitFor(HOOK_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    System.out.printf("  [hook:%s] Timeout (%ds)%n", event, HOOK_TIMEOUT);
                    continue;
                }
                // 读取输出
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exitCode = process.exitValue();

                // 5. 处理返回码（对标原版逻辑）
                handleExitCode(exitCode, output, event, result, context);

            } catch (Exception e) {
                System.err.printf("  [hook:%s] Error: %s%n", event, e.getMessage());
            }
        }
        return result;
    }

    /**
     * 处理 Hook 命令返回码（0/1/2）
     */
    private void handleExitCode(int exitCode, String output, String event,
                                Map<String, Object> result, Map<String, Object> context) {
        output = output.strip();
        List<String> messages = (List<String>) result.get("messages");

        switch (exitCode) {
            case 0:
                // 执行成功，打印日志
                if (StrUtil.isNotBlank(output)) {
                    System.out.printf("  [hook:%s] %s%n", event, StrUtil.sub(output, 0, 100));
                }
                // 解析结构化 JSON 输出
                parseStructOutput(output, result, context);
                break;

            case 1:
                // 拦截流程
                result.put("blocked", true);
                String reason = StrUtil.isBlank(output) ? "Blocked by hook" : output;
                result.put("block_reason", reason);
                System.out.printf("  [hook:%s] BLOCKED: %s%n", event, StrUtil.sub(reason, 0, 200));
                break;

            case 2:
                // 注入消息
                if (StrUtil.isNotBlank(output)) {
                    messages.add(output);
                    System.out.printf("  [hook:%s] INJECT: %s%n", event, StrUtil.sub(output, 0, 200));
                }
                break;

            default:
                break;
        }
    }

    /**
     * 解析 Hook 的结构化 JSON 输出（更新入参、权限、上下文）
     */
    private void parseStructOutput(String output, Map<String, Object> result, Map<String, Object> context) {
        try {
            JSONObject hookOutput = JSONUtil.parseObj(output);

            // 更新工具入参
            if (hookOutput.containsKey("updatedInput") && context != null) {
                context.put("tool_input", hookOutput.get("updatedInput"));
            }
            // 添加上下文消息
            if (hookOutput.containsKey("additionalContext")) {
                List<String> messages = (List<String>) result.get("messages");
                messages.add(hookOutput.getStr("additionalContext"));
            }
            // 权限覆写
            if (hookOutput.containsKey("permissionDecision")) {
                result.put("permission_override", hookOutput.get("permissionDecision"));
            }
        } catch (Exception ignored) {
            // 非 JSON 格式，忽略
        }
    }
}