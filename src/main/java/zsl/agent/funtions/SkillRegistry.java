package zsl.agent.funtions;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Component;
import zsl.agent.config.AiToolMethod;
import zsl.agent.entry.SkillDocument;
import zsl.agent.entry.SkillManifest;

import java.io.File;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class SkillRegistry {
    private final String Skillsdir = "src/main/resources/skills";
    private final Map<String, SkillDocument> skills = new HashMap<>();

    public SkillRegistry() {
        loadSkills();
    }

    private static final Pattern FRONTMATTER_REGEX = Pattern.compile(
            "^---\\n(.*?)\\n---\\n(.*)",
            Pattern.DOTALL
    );

    private void loadSkills() {
        // 👇 完全和你 MCP 一样：写死绝对路径，直接读真实文件
        File skillsDir = new File("E:\\VS\\agent\\skills");

        // 👇 和你 MCP 一模一样的判断
        if (!skillsDir.exists() || !skillsDir.isDirectory()) {
            System.err.println("错误：技能文件夹不存在 → " + skillsDir.getAbsolutePath());
            return;
        }

        try {
            // 递归读取所有 .md 技能文件
            Files.walk(skillsDir.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .forEach(path -> {
                        try {
                            String text = Files.readString(path, StandardCharsets.UTF_8);
                            Map<String, Object> skillMap = this.loadSkill(text);
                            SkillManifest skillManifest = BeanUtil.mapToBean(skillMap, SkillManifest.class, true);
                            skillManifest.setPath(path.toString());
                            String body = (String) skillMap.get("body");
                            skills.put(skillManifest.getName(), new SkillDocument(skillManifest, body));
                        } catch (Exception e) {
                            System.err.println("技能加载失败：" + path);
                        }
                    });
        } catch (IOException e) {
            System.err.println("遍历技能目录失败：" + e.getMessage());
        }
    }

    private Map<String, Object> loadSkill(String skilltext) {
        Map<String, Object> resultMap = new HashMap<>();
        Matcher matcher = FRONTMATTER_REGEX.matcher(skilltext);

        // 没有匹配到头部：直接把全文放入body，返回空元数据
        if (!matcher.find()) {
            resultMap.put("body", skilltext);
            return resultMap;
        }
        // 解析元数据区域
        String metaContent = matcher.group(1).trim();
        String body = matcher.group(2);
        // 正文存入map
        resultMap.put("body", body);

        // 按行解析元数据键值对
        String[] lines = metaContent.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            // 跳过空行、无冒号的无效行
            if (line.isEmpty() || !line.contains(":")) {
                continue;
            }
            // 按第一个冒号分割（对应Python split(: ,1)）
            String[] keyValue = line.split(":", 2);
            String key = keyValue[0].trim();
            String value = keyValue.length > 1 ? keyValue[1].trim() : "";
            // 元数据直接放入map
            resultMap.put(key, value);
        }

        return resultMap;
    }

    public String describe_available() {
        return skills.keySet().stream()
                .sorted() // 排序
                .map(skillname -> {
                    SkillManifest manifest = skills.get(skillname).getSkillManifest();
                    return "- " + manifest.getName() + ": " + manifest.getDescription();
                })
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @AiToolMethod(name = "load_skill", desc = "Load a skill by name")
    public String load_skill_text(JSONObject params) {
        String name = params.getStr("name");

        // 1. 校验 name
        if (name == null || name.isBlank()) {
            return "Error: 技能名称不能为空";
        }

        // 2. 校验 skills 是否为空
        if (skills.isEmpty()) {
            return "No skills available";
        }

        // 3. 判空：技能是否存在
        SkillDocument skillDoc = skills.get(name);
        if (skillDoc == null) {
            return "Error: 未找到技能 '" + name + "'";
        }
        String body = skillDoc.getBody();

        // ========== 新增：如果内容太长，只返回预览 ==========
        int maxLength = 5000; // 限制 5000 字符
        if (body != null && body.length() > maxLength) {
            return String.format("""
                    技能 '%s' 加载成功（内容过长，仅显示前 %d 字符）：
                    %s
                    ...（内容已截断，完整内容请查看本地文件）
                    """, name, maxLength, body.substring(0, maxLength));
        }
        // =================================================

        // 5. 返回完整正文
        return body;
    }
}
