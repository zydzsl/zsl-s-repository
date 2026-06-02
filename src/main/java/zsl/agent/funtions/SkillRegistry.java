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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class SkillRegistry {
    // 保留你原来的绝对路径，和 MCP 一致
    private final File skillsDir = new File("E:\\VS\\agent\\skills");
    private final Map<String, SkillDocument> skills = new HashMap<>();

    public SkillRegistry() {
        loadSkills();
    }

    // ✅ 终极正则：支持 Windows(\r\n)、Linux(\n)、BOM、末尾无换行
    private static final Pattern FRONTMATTER_REGEX = Pattern.compile(
            "^\\uFEFF?---\\r?\\n(.*?)\\r?\\n---(?:\\r?\\n|$)(.*)",
            Pattern.DOTALL
    );

    private void loadSkills() {
        if (!skillsDir.exists() || !skillsDir.isDirectory()) {
            System.err.println("❌ 技能文件夹不存在 → " + skillsDir.getAbsolutePath());
            return;
        }

        System.out.println("🔍 开始扫描技能目录：" + skillsDir.getAbsolutePath());

        try {
            Files.walk(skillsDir.toPath())
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("SKILL.md")) // 只读 SKILL.md
                    .forEach(path -> {
                        try {
                            String text = Files.readString(path, StandardCharsets.UTF_8);
                            Map<String, Object> skillMap = this.loadSkill(text);

                            // ✅ 关键修复：把 ignoreError 改成 false，转换失败直接抛异常
                            SkillManifest skillManifest = BeanUtil.mapToBean(skillMap, SkillManifest.class, false);
                            skillManifest.setPath(path.toString());
                            // ✅ 强制校验 name 字段
                            String skillName = skillManifest.getName();
                            if (skillName == null || skillName.isBlank()) {
                                System.err.println("❌ 技能缺少 name 字段，跳过：" + path);
                                return;
                            }

                            String body = (String) skillMap.get("body");
                            skills.put(skillName, new SkillDocument(skillManifest, body));

                        } catch (Exception e) {
                            System.err.println("❌ 技能加载失败：" + path);
                            System.err.println("   错误原因：" + e.getMessage());
                            e.printStackTrace();
                        }
                    });

            System.out.println("\n✅ 技能加载完成，共加载 " + skills.size() + " 个有效技能");

        } catch (IOException e) {
            System.err.println("❌ 遍历技能目录失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, Object> loadSkill(String skilltext) {
        Map<String, Object> resultMap = new HashMap<>();

        // ✅ 自动去掉 Windows 记事本加的 BOM 字符
        if (skilltext.startsWith("\uFEFF")) {
            skilltext = skilltext.substring(1);
        }

        Matcher matcher = FRONTMATTER_REGEX.matcher(skilltext);

        if (!matcher.find()) {
            System.err.println("❌ 未匹配到 frontmatter 头部");
            System.err.println("   文件前100字符：" + skilltext.substring(0, Math.min(100, skilltext.length())));
            resultMap.put("body", skilltext);
            return resultMap;
        }

        String metaContent = matcher.group(1).trim();
        String body = matcher.group(2).trim();
        resultMap.put("body", body);

        // 解析元数据键值对
        String[] lines = metaContent.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || !line.contains(":")) {
                continue;
            }

            String[] keyValue = line.split(":", 2);
            String key = keyValue[0].trim();
            String value = keyValue.length > 1 ? keyValue[1].trim() : "";

            // 自动去掉值前后的引号
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            resultMap.put(key, value);
        }

        return resultMap;
    }

    public String describe_available() {
        if (skills.isEmpty()) {
            return "当前没有可用的技能";
        }

        return skills.keySet().stream()
                .filter(name -> name != null && !name.isBlank())
                .sorted()
                .map(skillName -> {
                    SkillManifest manifest = skills.get(skillName).getSkillManifest();
                    return "- " + manifest.getName() + ": " + manifest.getDescription();
                })
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @AiToolMethod(name = "load_skill", desc = "Load a skill by name")
    public String load_skill_text(JSONObject params) {
        String name = params.getStr("name");

        if (name == null || name.isBlank()) {
            return "Error: 技能名称不能为空";
        }

        SkillDocument skillDoc = skills.get(name);
        if (skillDoc == null) {
            return "Error: 未找到技能 '" + name + "'\n可用技能：\n" + describe_available();
        }

        String body = skillDoc.getBody();
        int maxLength = 5000;

        if (body != null && body.length() > maxLength) {
            return String.format("""
                    技能 '%s' 加载成功（内容过长，仅显示前 %d 字符）：
                    %s
                    ...（内容已截断，完整内容请查看本地文件：%s）
                    """, name, maxLength, body.substring(0, maxLength), skillDoc.getSkillManifest().getPath());
        }

        return body;
    }
}