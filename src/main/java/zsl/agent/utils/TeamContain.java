package zsl.agent.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 系统常量：对齐Python原代码的目录、消息类型
 */
public class TeamContain {
    // 工作目录（可根据实际需求修改）
    public static final Path WORK_DIR = Paths.get(System.getProperty("user.dir"));
    public static final String TOOL_PREFIX = "mcp__";
    public static final String PROTOCOL_VERSION = "2024-11-05";
    public static final String CLIENT_NAME = "teaching-agent";
    public static final String CLIENT_VERSION = "1.0";
    // 团队根目录
    public static final Path TEAM_DIR = WORK_DIR.resolve(".team");
    // 收件箱根目录
    public static final Path INBOX_DIR = TEAM_DIR.resolve("inbox");

    /**
     * 消息类型枚举（严格对齐原Python VALID_MSG_TYPES）
     */
    public enum MsgType {
        MESSAGE,                // 普通消息
        BROADCAST,              // 广播消息
        SHUTDOWN_REQUEST,       // 关闭请求
        SHUTDOWN_RESPONSE,      // 关闭响应
        PLAN_APPROVAL,          // 计划审批
        PLAN_APPROVAL_RESPONSE  // 计划审批响应
    }
}