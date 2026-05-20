package zsl.agent.utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class BashSecurityValidator {

    // ===================== 核心改动 =====================
    // 用 List<Map> 存储规则：每个Map包含 name 和 pattern 两个key
    private static final List<Map<String, String>> VALIDATORS = new ArrayList<>();

    static {
        // 规则1：shell特殊符号
        VALIDATORS.add(createRule("shell_metachar", "[;&|`$]"));
        // 规则2：sudo提权
        VALIDATORS.add(createRule("sudo", "\\bsudo\\b"));
        // 规则3：rm递归删除
        VALIDATORS.add(createRule("rm_rf", "\\brm\\s+(-[a-zA-Z]*)?r"));
        // 规则4：命令嵌套
        VALIDATORS.add(createRule("cmd_substitution", "\\$\\("));
        // 规则5：修改IFS变量
        VALIDATORS.add(createRule("ifs_injection", "\\bIFS\\s*="));
    }

    // ===================== 校验方法（逻辑不变） =====================
    public List<Map<String, String>> validate(String command) {
        List<Map<String, String>> failures = new ArrayList<>();
        // 遍历Map类型的规则
        for (Map<String, String> rule : VALIDATORS) {
            String pattern = rule.get("pattern");
            if (Pattern.compile(pattern).matcher(command).find()) {
                failures.add(rule);
            }
        }
        return failures;
    }

    // 是否安全
    public boolean isSafe(String command) {
        return validate(command).isEmpty();
    }

    // 描述失败原因
    public String describeFailures(String command) {
        List<Map<String, String>> failures = validate(command);
        if (failures.isEmpty()) {
            return "No issues detected";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Security flags: ");
        for (int i = 0; i < failures.size(); i++) {
            Map<String, String> rule = failures.get(i);
            sb.append(rule.get("name")).append(" (pattern: ").append(rule.get("pattern")).append(")");
            if (i < failures.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    // ===================== 工具方法：快速创建规则Map =====================
    private static Map<String, String> createRule(String name, String pattern) {
        Map<String, String> rule = new HashMap<>();
        rule.put("name", name);
        rule.put("pattern", pattern);
        return rule;
    }
}