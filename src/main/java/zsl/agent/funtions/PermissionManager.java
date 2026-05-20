package zsl.agent.funtions;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import zsl.agent.utils.BashSecurityValidator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class PermissionManager {
    // 运行模式
    public static final List<String> MODES = Arrays.asList("default", "plan", "auto");
    // 只读工具
    public static final Set<String> READ_ONLY_TOOLS = new HashSet<>(Arrays.asList(
            "read_file", "bash_readonly"
    ));
    // 写操作工具
    public static final Set<String> WRITE_TOOLS = new HashSet<>(Arrays.asList(
            "write_file", "edit_file", "bash"
    ));
    // 默认权限规则
    private static final List<Map<String, Object>> DEFAULT_RULES = new ArrayList<>();

    static {
        Map<String, Object> deny1 = new HashMap<>();
        deny1.put("tool", "bash");
        deny1.put("content", "rm -rf /");
        deny1.put("behavior", "deny");
        DEFAULT_RULES.add(deny1);

        Map<String, Object> deny2 = new HashMap<>();
        deny2.put("tool", "bash");
        deny2.put("content", "sudo *");
        deny2.put("behavior", "deny");
        DEFAULT_RULES.add(deny2);

        Map<String, Object> allowRead = new HashMap<>();
        allowRead.put("tool", "read_file");
        allowRead.put("path", "*");
        allowRead.put("behavior", "allow");
        DEFAULT_RULES.add(allowRead);
    }

    // 单例校验器
    public static final BashSecurityValidator bashValidator = new BashSecurityValidator();

    private final String mode;
    private final List<Map<String, Object>> rules;
    private int consecutiveDenials = 0;
    private final int maxConsecutiveDenials = 3;

    public PermissionManager(String mode) {
        this(mode, null);
    }

    public PermissionManager(String mode, List<Map<String, Object>> rules) {
        if (!MODES.contains(mode)) {
            throw new IllegalArgumentException("Unknown mode: " + mode + ". Choose from " + MODES);
        }
        this.mode = mode;
        this.rules = rules != null ? rules : new ArrayList<>(DEFAULT_RULES);
    }

    /**
     * 权限检查核心方法
     */
    public Map<String, String> check(String toolName, Map<String, Object> toolInput) {
        // Step 0: Bash 安全校验
        if ("bash".equals(toolName)) {
            String command = String.valueOf(toolInput.getOrDefault("command", ""));
            // ===================== 核心修改：返回值改为 List<Map> =====================
            List<Map<String, String>> failures = bashValidator.validate(command);

            if (!failures.isEmpty()) {
                Set<String> severe = new HashSet<>(Arrays.asList("sudo", "rm_rf"));
                // ===================== 核心修改：从Map中获取name =====================
                boolean hasSevere = failures.stream().anyMatch(r -> severe.contains(r.get("name")));

                String desc = bashValidator.describeFailures(command);
                if (hasSevere) {
                    return result("deny", "Bash validator: " + desc);
                } else {
                    return result("ask", "Bash validator flagged: " + desc);
                }
            }
        }
        // Step 1: 拒绝规则
        for (Map<String, Object> rule : rules) {
            if (!"deny".equals(rule.get("behavior"))) {
                continue;
            }
            if (matches(rule, toolName, toolInput)) {
                return result("deny", "Blocked by deny rule: " + rule);
            }
        }
        // Step 2: 模式判断
        if ("plan".equals(mode)) {
            if (WRITE_TOOLS.contains(toolName)) {
                return result("deny", "Plan mode: write operations are blocked");
            }
            return result("allow", "Plan mode: read-only allowed");
        }

        if ("auto".equals(mode)) {
            if (READ_ONLY_TOOLS.contains(toolName) || "read_file".equals(toolName)) {
                return result("allow", "Auto mode: read-only tool auto-approved");
            }
        }
        // Step 3: 允许规则
        for (Map<String, Object> rule : rules) {
            if (!"allow".equals(rule.get("behavior"))) {
                continue;
            }
            if (matches(rule, toolName, toolInput)) {
                consecutiveDenials = 0;
                return result("allow", "Matched allow rule: " + rule);
            }
        }
        // Step 4: 默认询问用户
        return result("ask", "No rule matched for " + toolName + ", asking user");
    }

    /**
     * 询问用户是否允许
     */
    public boolean askUser(String toolName, Map<String, Object> toolInput) {
        String preview = JSONUtil.toJsonStr(toolInput);
        if (preview.length() > 200) {
            preview = preview.substring(0, 200);
        }
        System.out.println("\n  [Permission] " + toolName + ": " + preview);
        Scanner scanner = new Scanner(System.in);
        try {
            System.out.print("  Allow? (y/n/always): ");
            String answer = scanner.nextLine().trim().toLowerCase();

            if ("always".equals(answer)) {
                Map<String, Object> rule = new HashMap<>();
                rule.put("tool", toolName);
                rule.put("path", "*");
                rule.put("behavior", "allow");
                rules.add(rule);
                consecutiveDenials = 0;
                return true;
            }
            if ("y".equals(answer) || "yes".equals(answer)) {
                consecutiveDenials = 0;
                return true;
            }
            consecutiveDenials++;
            if (consecutiveDenials >= maxConsecutiveDenials) {
                System.out.println("  [" + consecutiveDenials + " consecutive denials -- consider switching to plan mode]");
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 规则匹配（工具名 + 路径 + 内容）
     */
    private boolean matches(Map<String, Object> rule, String toolName, Map<String, Object> input) {
        // 匹配工具名
        if (rule.containsKey("tool")) {
            String ruleTool = String.valueOf(rule.get("tool"));
            if (!"*".equals(ruleTool) && !ruleTool.equals(toolName)) {
                return false;
            }
        }
        // 匹配路径
        if (rule.containsKey("path")) {
            String rulePath = String.valueOf(rule.get("path"));
            String inputPath = String.valueOf(input.getOrDefault("path", ""));
            if (!"*".equals(rulePath) && !matchGlob(inputPath, rulePath)) {
                return false;
            }
        }
        // 匹配内容（bash 命令）
        if (rule.containsKey("content")) {
            String ruleContent = String.valueOf(rule.get("content"));
            String inputCmd = String.valueOf(input.getOrDefault("command", ""));
            if (!matchGlob(inputCmd, ruleContent)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> result(String behavior, String reason) {
        Map<String, String> res = new HashMap<>();
        res.put("behavior", behavior);
        res.put("reason", reason);
        return res;
    }
    private boolean matchGlob(String str, String pattern) {
        String regex = pattern.replace("*", ".*");
        return str.matches(regex);
    }
    // ===================== 工作空间信任判断 =====================
    public static boolean isWorkspaceTrusted() {
        return isWorkspaceTrusted(Paths.get(System.getProperty("user.dir")));
    }
    public static boolean isWorkspaceTrusted(Path workspace) {
        Path trustMarker = workspace.resolve(".claude/.claude_trusted");
        return FileUtil.exist(trustMarker.toFile());
    }
}