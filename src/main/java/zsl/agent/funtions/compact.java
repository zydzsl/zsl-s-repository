package zsl.agent.funtions;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import zsl.agent.entry.compactState;
import zsl.agent.utils.ToolConstants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static zsl.agent.config.Cantains.*;


@Component
public class compact {

    private static final int PERSIST_THRESHOLD = 30000; // 3万字符阈值
    private static final int PREVIEW_CHARS = 2000;     // 预览截取长度

    private static final Path WORKDIR = Paths.get(System.getProperty("user.dir"));
    private static final Path TOOL_RESULTS_DIR = Paths.get("tool_results");

    // 类加载时自动创建文件夹（没有就新建，有就跳过）
    static {
        try {
            Files.createDirectories(TOOL_RESULTS_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    // 替换阈值
    private static final int MAX_CONTENT_LENGTH = 8000;
    // 替换文本
    private static final String COMPACTED_TIP = "[Earlier tool result compacted. Re-run the tool if you need full detail.]";
    // 对话保存目录（对应 Python TRANSCRIPT_DIR）
    private static final Path TRANSCRIPT_DIR = Paths.get(".transcripts");
    private static HttpClient httpClient = HttpClient.newHttpClient();


    public static Long estimate_context_size(List<Map<String, Object>> message) {
         return (long) message.stream()
            .filter(map -> map != null && !map.isEmpty())
            .flatMap(map -> map.entrySet().stream())
            .mapToInt(entry -> {
                int keyLen = entry.getKey() != null ? entry.getKey().length() : 0;
                int valueLen = entry.getValue() != null ? entry.getValue().toString().length() : 0;
                return keyLen + valueLen;
            })
            .sum();
        }
    public static String persistLargeOutput(String output, String toolUseId) {
        // 空值直接返回
        if (output == null || output.isEmpty()) {
            return output;
        }
        // ============== 1. 内容≤3万字符，直接返回原文 ==============
        if (output.length() <= PERSIST_THRESHOLD) {
            return output;
        }
        try {
            // ============== 2. 内容＞3万字符，开始处理 ==============
            // 递归创建文件夹（不存在则创建，对应 mkdir(parents=True, exist_ok=True)）
            Files.createDirectories(TOOL_RESULTS_DIR);
            // 构建文件路径：tool_results/[toolUseId].txt
            Path storedPath = TOOL_RESULTS_DIR.resolve(toolUseId + ".txt");
            // ============== 3. 文件不存在则写入完整内容 ==============
            if (!Files.exists(storedPath)) {
                Files.writeString(storedPath, output, StandardCharsets.UTF_8);
            }
            // ============== 4. 截取前2000字符作为预览 ==============
            String preview = output.substring(0, PREVIEW_CHARS);
            // ============== 5. 计算相对路径（对应 relative_to） ==============
            Path relPath = WORKDIR.relativize(storedPath);
            // ============== 6. 返回预览+路径提示（和Python返回格式一致） ==============
            return String.format("""
                内容过长已保存至本地文件：%s
                内容预览（前2000字符）：
                %s
                """, relPath, preview);
        } catch (Exception e) {
            // 异常兜底：返回预览内容
            return "文件保存失败，内容预览：" + output.substring(0, PREVIEW_CHARS);
        }
    }

    public static List<Map<String, Object>> summarize_tool_results(List<Map<String, Object>> tool_result_blocks) {
        tool_result_blocks.forEach(block -> {
            // 1. 只处理 tool
            if (!"tool".equals(block.get("role"))) {
                return;
            }
            // 2. 拿字段
            Object contentObj = block.get("content");
            Object toolCallIdObj = block.get("tool_call_id");
            // 3. 必须有 toolCallId 才保存（防崩溃）
            if (toolCallIdObj == null) {
                return;
            }
            String content = contentObj != null ? contentObj.toString() : "";
            String toolCallId = toolCallIdObj.toString();
            if (content.length() > MAX_CONTENT_LENGTH) {
                try {
                    String timestamp = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    Path filePath = TOOL_RESULTS_DIR.resolve(toolCallId + "_" + timestamp + ".txt");
                    Files.writeString(filePath, content, StandardCharsets.UTF_8);
                    block.put("content", COMPACTED_TIP+"具体保存的地址是："+filePath.toString());
                } catch (Exception e) {
                    System.err.println("工具结果保存失败：" + e.getMessage());
                }
            }
        });

        return tool_result_blocks;
    }

    public static Path write_transcript(List<Map<String, Object>> messages) throws IOException {
        System.out.println("正在保存对话记录...");
        System.out.println(TRANSCRIPT_DIR);
        // 1. 创建目录（不存在则递归创建，存在则跳过）
        Files.createDirectories(TRANSCRIPT_DIR);
        // 2. 生成唯一文件名：transcript_时间戳.jsonl（和 Python time.time() 一致）
        long timestamp = System.currentTimeMillis() / 1000;
        Path filePath = TRANSCRIPT_DIR.resolve("transcript_" + timestamp + ".jsonl");
        // 3. 逐行写入 JSONL 文件（自动关闭流，对应 Python with open）
        try (var writer = Files.newBufferedWriter(filePath)) {
            for (Map<String, Object> message : messages) {
                // 转 JSON 字符串 + 换行（对应 json.dumps + \n）
                String jsonLine = JSONUtil.toJsonStr(message);
                writer.write(jsonLine);
                writer.newLine(); // 换行
            }
        }catch (Exception e) {
            throw new RuntimeException("写入对话记录失败", e);
        }
        return filePath;
    }

    public static String compact_history(compactState compact_state, List<Map<String, Object>> messages,String focus) throws IOException {
        System.out.println("正在保存对话记录...");
        Path transcriptPath = write_transcript(messages);
        // 打印保存路径（对应print）
        System.out.println("[transcript saved: " + transcriptPath.toAbsolutePath() + "]");
        // ===================== 2. 子AI总结历史（调用你的总结方法） =====================
        String summary = null;
        try {
            summary = summarizeHistory(messages);

        } catch (Exception e) {
            throw new RuntimeException("总结文本失败");
        }
        if (focus != null && !focus.isBlank()) {
            summary += "\n\nFocus to preserve next: " + focus;
        }
        // ===================== 4. 如果有最近文件，追加文件列表 =====================
        List<String> recentFiles = compact_state.getRecentFiles();
        if (recentFiles != null && !recentFiles.isEmpty()) {
            // 拼接格式：- 文件路径\n- 文件路径
            String recentLines = String.join("\n", recentFiles.stream()
                    .map(path -> "- " + path)
                    .toList());
            summary += "\n\nRecent files to reopen if needed:\n" + recentLines;
        }
        // ===================== 5. 更新状态 =====================
        compact_state.setHas_compacted(true);
        compact_state.setLast_summary(summary);
        // ===================== 6. 构造返回结果（单条user消息） =====================

        List<Map<String, Object>> resultMessage = new ArrayList<>();
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "user");
        String content = "This conversation was compacted so the agent can continue working.\n\n" + summary;
        msg.put("content", content);

        return content;
    }

    public static List<Map<String, Object>> compact_history(compactState compact_state, List<Map<String, Object>> messages) throws IOException {
        System.out.println("正在保存对话记录...");
        Path transcriptPath = write_transcript(messages);
        // 打印保存路径（对应print）
        System.out.println("[transcript saved: " + transcriptPath.toAbsolutePath() + "]");
        // ===================== 2. 子AI总结历史（调用你的总结方法） =====================
        String summary = null;
        try {
            summary = summarizeHistory(messages);
        } catch (Exception e) {
            throw new RuntimeException("总结文本失败");
        }
        // ===================== 4. 如果有最近文件，追加文件列表 =====================
        List<String> recentFiles = compact_state.getRecentFiles();
        if (recentFiles != null && !recentFiles.isEmpty()) {
            // 拼接格式：- 文件路径\n- 文件路径
            String recentLines = String.join("\n", recentFiles.stream()
                    .map(path -> "- " + path)
                    .toList());
            summary += "\n\nRecent files to reopen if needed:\n" + recentLines;
        }

        // ===================== 5. 更新状态 =====================
        compact_state.setHas_compacted(true);
        compact_state.setLast_summary(summary);

        // ===================== 6. 构造返回结果（单条user消息） =====================
        List<Map<String, Object>> resultMessage = new ArrayList<>();
        resultMessage.add(new HashMap<>());
        resultMessage.get(0).put("role", "user");
        String content = "This conversation was compacted so the agent can continue working.\n\n" + summary;
        resultMessage.get(0).put("content", content);

        return resultMessage;
    }

    private static String summarizeHistory(List<Map<String, Object>> messages) throws Exception {
        String content = JSONUtil.toJsonStr(messages);
        // 安全截取（避免字符串越界）
        int maxLength = Math.min(80000, content.length());
        content = content.substring(0, maxLength);

        // ========== 修复：正确的Prompt格式 ==========
        String prompt = String.format("""
            请总结以下编程助手对话，要求：
            1. 保留当前目标、关键结论、修改的文件
            2. 保留待办工作、用户约束和偏好
            3. 精简总结，严格控制在4000 token内
            4. 简洁具体，无废话
            对话内容：%s
            """, content);

        List<Map<String, Object>> requestMessages = new ArrayList<>();
        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        requestMessages.add(userMsg);

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", "deepseek-v4-flash");
        requestBody.set("messages", requestMessages);
        requestBody.set("tools", ToolConstants.SubTOOLS);
        requestBody.set("enable_thinking", false);
        // 修复：降低max_tokens，安全阈值
        requestBody.set("max_tokens", 4096);

        // 修复：添加请求超时+异常处理
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 修复：安全解析JSON
        JSONObject body = JSONUtil.parseObj(response.body());
         String summary =body.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getStr("content", "总结内容为空");
        System.out.println("总结结果：" + summary);
        return summary;
    }

}
