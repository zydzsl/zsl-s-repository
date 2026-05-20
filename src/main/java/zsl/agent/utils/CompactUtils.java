// CompactUtils.java
package zsl.agent.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static zsl.agent.config.Cantains.*;

public class CompactUtils {

    private static final int PERSIST_THRESHOLD = 30000;
    private static final int PREVIEW_CHARS = 2000;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final String COMPACTED_TIP = "[Earlier tool result compacted. Re-run the tool if you need full detail.]";

    private static final Path WORKDIR = Paths.get(System.getProperty("user.dir"));
    private static final Path TOOL_RESULTS_DIR = Paths.get("tool_results");
    private static final Path TRANSCRIPT_DIR = Paths.get(".transcripts");

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    static {
        try {
            Files.createDirectories(TOOL_RESULTS_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 估算上下文大小 */
    public static long estimateContextSize(List<Map<String, Object>> messages) {
        return messages.stream()
                .filter(Objects::nonNull)
                .flatMap(map -> map.entrySet().stream())
                .mapToInt(entry -> {
                    int keyLen = entry.getKey() != null ? entry.getKey().length() : 0;
                    int valueLen = entry.getValue() != null ? entry.getValue().toString().length() : 0;
                    return keyLen + valueLen;
                })
                .sum();
    }

    /** 持久化过大的工具输出 */
    public static String persistLargeOutput(String output, String toolUseId) {
        if (output == null || output.isEmpty()) return output;
        if (output.length() <= PERSIST_THRESHOLD) return output;

        try {
            Files.createDirectories(TOOL_RESULTS_DIR);
            Path storedPath = TOOL_RESULTS_DIR.resolve(toolUseId + ".txt");
            if (!Files.exists(storedPath)) {
                Files.writeString(storedPath, output, StandardCharsets.UTF_8);
            }
            String preview = output.substring(0, PREVIEW_CHARS);
            Path relPath = WORKDIR.relativize(storedPath);
            return String.format("""
                    内容过长已保存至本地文件：%s
                    内容预览（前2000字符）：
                    %s
                    """, relPath, preview);
        } catch (Exception e) {
            return "文件保存失败，内容预览：" + output.substring(0, PREVIEW_CHARS);
        }
    }

    /** 压缩过长的工具结果块 */
    public static List<Map<String, Object>> summarizeToolResults(List<Map<String, Object>> toolResultBlocks) {
        toolResultBlocks.forEach(block -> {
            if (!"tool".equals(block.get("role"))) return;
            Object contentObj = block.get("content");
            Object toolCallIdObj = block.get("tool_call_id");
            if (toolCallIdObj == null) return;

            String content = contentObj != null ? contentObj.toString() : "";
            String toolCallId = toolCallIdObj.toString();
            if (content.length() > MAX_CONTENT_LENGTH) {
                try {
                    String timestamp = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    Path filePath = TOOL_RESULTS_DIR.resolve(toolCallId + "_" + timestamp + ".txt");
                    Files.writeString(filePath, content, StandardCharsets.UTF_8);
                    block.put("content", COMPACTED_TIP);
                } catch (Exception e) {
                    System.err.println("工具结果保存失败：" + e.getMessage());
                }
            }
        });
        return toolResultBlocks;
    }

    /** 保存对话抄本，返回文件路径 */
    public static Path writeTranscript(List<Map<String, Object>> messages) throws IOException {
        System.out.println("正在保存对话记录...");
        Files.createDirectories(TRANSCRIPT_DIR);
        long timestamp = System.currentTimeMillis() / 1000;
        Path filePath = TRANSCRIPT_DIR.resolve("transcript_" + timestamp + ".jsonl");
        try (var writer = Files.newBufferedWriter(filePath)) {
            for (Map<String, Object> message : messages) {
                String jsonLine = JSONUtil.toJsonStr(message);
                writer.write(jsonLine);
                writer.newLine();
            }
        }
        return filePath;
    }

    /** 调用子 AI 对消息历史进行总结 */
    public static String summarizeHistory(List<Map<String, Object>> messages) throws Exception {
        String content = JSONUtil.toJsonStr(messages);
        int maxLength = Math.min(80000, content.length());
        content = content.substring(0, maxLength);

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
        requestBody.set("max_tokens", 4096);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject body = JSONUtil.parseObj(response.body());
        return body.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getStr("content", "总结内容为空");
    }

    /** 将焦点说明和最近文件列表拼接到摘要后面 */
    public static String enrichSummary(String summary, String focus, List<String> recentFiles) {
        if (focus != null && !focus.isBlank()) {
            summary += "\n\nFocus to preserve next: " + focus;
        }
        if (recentFiles != null && !recentFiles.isEmpty()) {
            String recentLines = recentFiles.stream()
                    .map(p -> "- " + p)
                    .collect(Collectors.joining("\n"));
            summary += "\n\nRecent files to reopen if needed:\n" + recentLines;
        }
        return summary;
    }

    /** 把最终摘要打包成单条 user 消息（也可改为 system） */
    public static List<Map<String, Object>> wrapSummaryAsUser(String summary) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", "This conversation was compacted so the agent can continue working.\n\n" + summary);
        return Collections.singletonList(msg);
    }

    /** 更新压缩状态 */
    public static void updateCompactionState(compactState state, String summary) {
        state.setHas_compacted(true);
        state.setLast_summary(summary);
    }
}