package zsl.agent.funtions;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;
import zsl.agent.config.AiToolMethod;
import zsl.agent.entry.TeamConfig;
import zsl.agent.entry.Teammember;
import zsl.agent.utils.TeamContain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;


@Component
public class TeammateManager {

    private static final Path teamDir = TeamContain.TEAM_DIR;
    private static final Path configPath = teamDir.resolve("config.json");
    // 修复：删除多余分号，规范定义
    public static TeamConfig teamConfig = new TeamConfig();

    private static final ExecutorService teammateThreadPool;
    // 静态线程存储：存储Future，支持停止线程
    private static final Map<String, Future<?>> threads = new HashMap<>();

    // ===================== 静态初始化 =====================
    static {
        try {
            // 1. 自动创建目录
            Files.createDirectories(teamDir);
            // 2. 加载配置文件
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                teamConfig = JSONUtil.toBean(json, TeamConfig.class);
            }
            // 3. 初始化线程池（守护线程）
            ThreadFactory threadFactory = r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("Teammate-" + Thread.currentThread().getId());
                return t;
            };

            teammateThreadPool = new ThreadPoolExecutor(
                    5, 10, 1, TimeUnit.SECONDS,
                    new SynchronousQueue<>(), threadFactory
            );

        } catch (Exception e) {
            throw new RuntimeException("TeammateManager初始化失败", e);
        }
    }

    // ===================== 保存配置 =====================
    private void saveConfig() {
        try {
            String json = JSONUtil.toJsonStr(teamConfig);
            Files.writeString(configPath, json);
            System.out.println("保存配置成功");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===================== 查找成员（修复空指针） =====================
    private Teammember findMember(String name) {
        // 防御性判断：杜绝空指针
        List<Teammember> members = teamConfig.getTEAM_MEMBERS();
        if (members == null || members.isEmpty()) {
            return null;
        }
        for (Teammember member : members) {
            if (member.getName().equals(name)) {
                return member;
            }
        }
        return null;
    }

    // ===================== 核心：创建/激活队员 =====================
    @AiToolMethod(name = "spawn_teammate", desc = "创建/激活一个队员")
    public String spawn(JSONObject params) {
        String name = params.getStr("name");
        String role = params.getStr("role");
        String prompt = params.getStr("prompt");
        Teammember member = findMember(name);
        if (member != null) {
            // 状态校验：仅空闲/关闭可启动
            if (!"idle".equals(member.getStatus()) && !"shutdown".equals(member.getStatus())) {
                return "Error: '" + name + "' is currently " + member.getStatus();
            }
            member.setStatus("working");
            member.setRole(role);
            saveConfig();
            return "智能体'" + name + "'已激活，状态更新为working";
        }
        // 新建成员
        member = new Teammember(name, role, "working");
        // 防御性初始化列表
        if (teamConfig.getTEAM_MEMBERS() == null) {
            teamConfig.setTEAM_MEMBERS(new ArrayList<>());
        }
        teamConfig.getTEAM_MEMBERS().add(member);
        saveConfig();
        // 线程池提交任务
        Future<?> future = teammateThreadPool.submit(() -> teammateLoop(name, role, prompt));
        threads.put(name, future);
        return "Spawned '" + name + "' (role: " + role + ")";
    }

    // ===================== 队员线程循环 =====================
    private void teammateLoop(String name, String role, String prompt) {
        System.out.println("智能体启动：" + name + " | 角色：" + role);
        // 你的业务逻辑
        subClient client = new subClient();
        client.subchat(prompt,name,role);
        // 执行完成后设置为空闲
        Teammember member = findMember(name);
        if (member != null && !"shutdown".equals(member.getStatus())) {
            member.setStatus("idle");
            saveConfig();
        }
    }

    // ===================== 【新增】列出所有队员（对应工具调用） =====================
    @AiToolMethod(name = "list_teammates", desc = "列出所有队员")
    public List<Map<String, String>> listTeammates(JSONObject params) {
        List<Map<String, String>> result = new ArrayList<>();
        List<Teammember> members = teamConfig.getTEAM_MEMBERS();

        // 空指针防护
        if (members == null || members.isEmpty()) {
            return result;
        }

        for (Teammember member : members) {
            Map<String, String> map = new HashMap<>();
            map.put("name", member.getName());
            map.put("role", member.getRole());
            map.put("status", member.getStatus());
            result.add(map);
        }
        return result;
    }

    // ===================== 优雅关闭线程池 =====================
    public static void shutdown() {
        try {
            // 停止所有任务
            threads.values().forEach(future -> future.cancel(true));
            teammateThreadPool.shutdown();
            if (!teammateThreadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                teammateThreadPool.shutdownNow();
            }
            System.out.println("线程池已关闭");
        } catch (Exception e) {
            teammateThreadPool.shutdownNow();
        }
    }
}