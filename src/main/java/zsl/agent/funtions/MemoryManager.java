package zsl.agent.funtions;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Component;
import zsl.agent.config.AiToolMethod;
import zsl.agent.entry.Memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MemoryManager {
    // ==================== 【改成固定绝对路径】 ====================
    public static final Path MEMORY_DIR = Paths.get("E:\\VS\\agent\\.memory");
    public static final Path MEMORY_INDEX = MEMORY_DIR.resolve("MEMORY.md");

    // 其他不变
    public static final List<String> MEMORY_TYPES = Arrays.asList("user", "feedback", "project", "reference");
    public static final int MAX_INDEX_LINES = 200;
    public static final String MEMORY_GUIDANCE = """
            When to save memories:
            - User states a preference ("I like tabs", "always use pytest") -> type: user
            - User corrects you ("don't do X", "that was wrong because...") -> type: feedback
            - You learn a project fact that is not easy to infer from current code alone
              (for example: a rule exists because of compliance, or a legacy module must
              stay untouched for business reasons) -> type: project
            - You learn where an external resource lives (ticket board, dashboard, docs URL)
              -> type: reference
            
            When NOT to save:
            - Anything easily derivable from code (function signatures, file structure, directory layout)
            - Temporary task state (current branch, open PR numbers, current TODOs)
            - Secrets or credentials (API keys, passwords)
            """;

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)",
            Pattern.DOTALL
    );

    private final Path memoryDir;
    private final Map<String, Memory> memories;

    // 构造方法直接用固定路径
    public MemoryManager() {
        this.memoryDir = MEMORY_DIR;
        this.memories = new HashMap<>();
        try {
            loadAll();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

//    // 废弃旧构造，避免乱传路径
//    @Deprecated
//    public MemoryManager(Path memoryDir) {
//        this();
//    }

    // ==================== 以下代码完全不变，保持你原来逻辑 ====================
    public void loadAll() throws IOException {
        memories.clear();
        if (!Files.exists(memoryDir)) {
            return;
        }
        try (var stream = Files.newDirectoryStream(memoryDir, "*.md")) {
            for (Path mdFile : stream) {
                String fileName = mdFile.getFileName().toString();
                if ("MEMORY.md".equals(fileName)) {
                    continue;
                }
                String content = Files.readString(mdFile, StandardCharsets.UTF_8);
                Map<String, String> parsed = parseFrontmatter(content);
                if (parsed == null) {
                    continue;
                }
                Memory memory = BeanUtil.toBean(parsed, Memory.class);
                String stem = fileName.substring(0, fileName.lastIndexOf("."));
                if (StrUtil.isBlank(memory.getName())) {
                    memory.setName(stem);
                }
                memory.setFile(fileName);
                memories.put(memory.getName(), memory);
            }
            int count = memories.size();
            if (count > 0) {
//                System.out.printf("[Memory loaded: %d memories from %s]%n", count, memoryDir);
            }
        }
    }

    public String loadMemoryPrompt() {
        if (memories.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Memories (persistent across sessions)\n\n");
        for (String memType : MEMORY_TYPES) {
            List<Memory> typedMemories = memories.values().stream()
                    .filter(mem -> memType.equals(mem.getType()))
                    .toList();
            if (typedMemories.isEmpty()) continue;
            sb.append("## [").append(memType).append("]\n");
            typedMemories.forEach(mem -> {
                sb.append("### ").append(mem.getName()).append(": ").append(mem.getDescription()).append("\n");
//                String content = mem.getContent().trim();
//                if (!content.isEmpty()) {
//                    sb.append(content).append("\n");
//                }
                sb.append("\n");
            });
        }
        return sb.toString();
    }

    @AiToolMethod(name = "save_memory", desc = "保存记忆")
    public String saveMemory(JSONObject params) {
        String name = params.getStr("name");
        String description = params.getStr("memo");
        String memType = params.getStr("type");
        String content = params.getStr("content");
        if (!MEMORY_TYPES.contains(memType)) {
            return "Error: type must be one of " + MEMORY_TYPES;
        }
        String safeName = sanitizeFileName(name);
        if (safeName.isBlank()) {
            return "Error: invalid memory name";
        }

        try {
            Files.createDirectories(memoryDir);
            String frontmatter = String.format("""
                            ---
                            name: %s
                            description: %s
                            type: %s
                            ---
                            %s
                            """,
                    name, description, memType, content
            );
            String fileName = safeName + ".md";
            Path filePath = memoryDir.resolve(fileName);
            Files.writeString(filePath, frontmatter, StandardCharsets.UTF_8);

            Memory memory = new Memory();
            memory.setName(name);
            memory.setDescription(description);
            memory.setType(memType);
            memory.setContent(content);
            memory.setFile(fileName);
            memories.put(name, memory);
            rebuildIndex();
            return "Saved memory '" + name + "' [" + memType + "] to " + filePath;
        } catch (IOException e) {
            return "Error saving memory: " + e.getMessage();
        }
    }

    private void rebuildIndex() throws IOException {
        Files.createDirectories(memoryDir);
        List<String> lines = new ArrayList<>();
        lines.add("# Memory Index");
        lines.add("");
        for (Memory mem : memories.values()) {
            lines.add("- " + mem.getName() + ": " + mem.getDescription() + " [" + mem.getType() + "]");
            if (lines.size() >= MAX_INDEX_LINES) {
                lines.add("... (truncated at " + MAX_INDEX_LINES + " lines)");
                break;
            }
        }
        String indexContent = String.join("\n", lines) + "\n";
        Files.writeString(MEMORY_INDEX, indexContent, StandardCharsets.UTF_8);
    }

    private Map<String, String> parseFrontmatter(String text) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        String header = matcher.group(1);
        String body = matcher.group(2);
        Map<String, String> result = new HashMap<>();
        result.put("content", body.trim());
        for (String line : header.split("\n")) {
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                String key = parts[0].trim();
                String value = parts.length > 1 ? parts[1].trim() : "";
                result.put(key, value);
            }
        }
        return result;
    }

    private String sanitizeFileName(String name) {
        return name.toLowerCase().replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public String buildSystemPrompt() {
        List<String> parts = new ArrayList<>();
        String memorySection = this.loadMemoryPrompt();
        if (StrUtil.isNotBlank(memorySection)) {
            parts.add(memorySection);
        }
        parts.add(MEMORY_GUIDANCE);
        return String.join("\n\n", parts);
    }
}