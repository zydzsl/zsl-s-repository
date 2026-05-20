package zsl.agent.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import org.springframework.stereotype.Component;
import zsl.agent.config.AiToolMethod;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static zsl.agent.utils.ToolConstants.DANGEROUS_COMMANDS;
import static zsl.agent.utils.ToolConstants.WORKDIR;

@Component
public class ToolExecutor{

    public static Path safePath(String path) {
        Path resolvePath = WORKDIR.resolve(path).normalize();
        if (!resolvePath.startsWith(WORKDIR)) {
            throw new SecurityException("Path escapes workspace: " + path);
        }
        return resolvePath;
    }

//    @AiToolMethod(name = "read_file", desc = "Read a file")
//    public static String runRead(String path, Integer limit) {
//        try {
//            Path filePath = safePath(path);
//            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
//            if (limit != null && limit < lines.size()) {
//                List<String> subLines = lines.subList(0, limit);
//                subLines.add("... (" + (lines.size() - limit) + " more lines)");
//                lines = subLines;
//            }
//            String content = String.join("\n", lines);
//            return content.length() > 50000 ? content.substring(0, 50000) : content;
//        } catch (Exception e) {
//            return "Error: " + e.getMessage();
//        }
//    }

    @AiToolMethod(name = "read_file", desc = "Read a file")
    public String runRead(JSONObject params) {
        try {
            String path = params.getStr("path");
            Integer limit = params.getInt("limit");
            Path filePath = safePath(path);
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
//            if (limit != null && limit < lines.size()) {
//                List<String> subLines = lines.subList(0, limit);
//                subLines.add("... (" + (lines.size() - limit) + " more lines)");
//                lines = subLines;
//            }
            String content = String.join("\n", lines);
            return content.length() > 7000 ? content.substring(0, 7000) : content;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @AiToolMethod(name = "write_file", desc = "Write a file")
    public String runWrite(JSONObject params) {
        String path = params.getStr("path");
        String content = params.getStr("content");

        try {
            Path filePath = safePath(path);
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content, StandardCharsets.UTF_8);
            return "Wrote " + content.getBytes(StandardCharsets.UTF_8).length + " bytes to " + path;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @AiToolMethod(name = "edit_file", desc = "Edit a file")
    public String runEdit(JSONObject params) {
        String path = params.getStr("path");
        String oldText = params.getStr("oldText");
        String newText = params.getStr("newText");
        try {
            Path filePath = safePath(path);
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            if (!content.contains(oldText)) {
                return "Error: Text not found in " + path;
            }
            String newContent = content.replaceFirst(oldText, newText);
            Files.writeString(filePath, newContent, StandardCharsets.UTF_8);
            return "Edited " + path;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }


    @AiToolMethod(name = "bash", desc = "Run a bash command")
    public String runBash(JSONObject params) {
        String command = params.getStr("command");

        for (String dangerous : DANGEROUS_COMMANDS) {
            if (command.contains(dangerous)) {
                return "Error: Dangerous command blocked";
            }
        }
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.directory(WORKDIR.toFile());
            pb.redirectErrorStream(true);

            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("/bin/bash", "-c", command);
            }

            process = pb.start();
            StringBuilder output = new StringBuilder();

            // =========================
            // 关键修复：开独立线程读流
            // =========================
            Process finalProcess = process;
            Thread readThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(finalProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        System.out.println("[Command Output] " + line);
                    }
                } catch (Exception ignored) {}
            });
            readThread.start();

            // 等待进程结束（不会被流阻塞）
            int exitCode = process.waitFor();
            readThread.join(); // 等待读取完最后一点输出

            String result = output.toString().trim();
            System.out.println("===== 命令执行完成，退出码：" + exitCode + " =====");

            if (exitCode != 0) {
                return "执行失败（码：" + exitCode + "）\n" + result;
            }
            return result.isEmpty() ? "(无输出)" : result.substring(0, Math.min(result.length(), 50000));

        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        } finally {
            if (process != null) process.destroy();
        }
    }
}