//package zsl.agent;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.context.ConfigurableApplicationContext;
//import zsl.agent.client.Client;
//import zsl.agent.client.ClientCron;
//import zsl.agent.entry.OpenAiTool;
//import zsl.agent.funtions.CronScheduler;
//import zsl.agent.funtions.McpBootstrap;
//import zsl.agent.utils.MCPToolRouter;
//import zsl.agent.utils.PluginLoader;
//import zsl.agent.utils.ToolPoolBuilder;
//
//import java.util.List;
//import java.util.Scanner;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.locks.ReentrantLock;
//
//import static zsl.agent.config.Cantains.*;
//
//@SpringBootApplication
//public class AgentApplication {
//
//
//    public static void main(String[] args) {
//
//        SpringApplication.run(AgentApplication.class);
//        // 【修改 2】接收 Spring 返回的上下文
//        ConfigurableApplicationContext context = SpringApplication.run(AgentApplication.class, args);
//
//        // 【修改 3】从 Spring 容器里获取 CronScheduler Bean
//        CronScheduler cronScheduler = context.getBean(CronScheduler.class);
//
//        // 【修改 4】用从 Spring 拿到的这个对象启动
//        cronScheduler.start();
//
//        PluginLoader pluginLoader = new PluginLoader();
//        MCPToolRouter mcpRouter = new MCPToolRouter();
//
//        // 2. 一键初始化MCP（调用工具类）
//        McpBootstrap.init(pluginLoader, mcpRouter);
//
//        System.out.println("初始化完成！");
//        // 1. 核心：引入 Lane 互斥锁
//        ReentrantLock laneLock = new ReentrantLock();
//
//        // 2. 初始化 Client
//        ClientCron client = ClientCron.builder()
//                .apiKey(apiKey)
//                .baseUrl(baseUrl)
//                .model(model)
//                .build();
//
//        // =====================================================
//        // 3. 【关键修复】独立后台线程：专门检查定时任务队列（每秒一次）
//        // =====================================================
//        ScheduledExecutorService cronChecker = Executors.newSingleThreadScheduledExecutor();
//        cronChecker.scheduleAtFixedRate(() -> {
//            try {
//                List<String> cronResults = cronScheduler.drainNotifications();
//                if (!cronResults.isEmpty()) {
//                    System.out.println("\n=====================================");
//                    System.out.println("> [定时任务] 检测到触发信号");
//
//                    // 非阻塞拿锁，用户优先
//                    if (laneLock.tryLock()) {
//                        try {
//                            for (String cronPrompt : cronResults) {
//                                System.out.println("> [定时任务] 执行中...");
//                                String aiResponse = client.singleTurnChat(cronPrompt);
//                                System.out.println("> [定时任务] AI 回复：");
//                                System.out.println(aiResponse);
//                            }
//                        } finally {
//                            laneLock.unlock();
//                        }
//                    } else {
//                        System.out.println("> [定时任务] 主车道被用户占用，稍后重试");
//                    }
//                    System.out.println("=====================================\n");
//                    // 打印提示符，让用户知道可以继续输入
//                    System.out.print("\033[36m\033[1ms01 >> \033[0m");
//                }
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }, 0, 1, TimeUnit.SECONDS); // 每秒检查一次
//
//        // =====================================================
//        // 4. 主线程：只负责处理用户输入（不再被定时任务阻塞）
//        // =====================================================
//        Scanner scanner = new Scanner(System.in);
//        final String BLUE = "\033[36m";
//        final String BOLD = "\033[1m";
//        final String RESET = "\033[0m";
//
//        System.out.println("=====================================");
//        System.out.println("  Agent 系统启动成功！");
//        System.out.println("  输入 /help 查看命令，输入 q 退出");
//        System.out.println("=====================================\n");
//
//        while (true) {
//            try {
//                System.out.print(BLUE + BOLD + "s01 >> " + RESET);
//                String query = scanner.nextLine().trim();
//
//                if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit") || query.isEmpty()) {
//                    System.out.println("程序退出~");
//                    cronScheduler.stop();
//                    cronChecker.shutdown(); // 关闭定时检查线程
//                    break;
//                }
//
//                if (query.startsWith("/")) {
//                    handleReplCommand(query, cronScheduler);
//                    continue;
//                }
//
//                // 用户输入：阻塞拿锁（必须拿到）
//                System.out.println("> [用户] 等待执行...");
//                laneLock.lock();
//                try {
//                    System.out.println("> [用户] 执行中...");
//                    String response = client.chat(query);
//                    System.out.println("AI回复: " + response);
//                } finally {
//                    laneLock.unlock();
//                }
//
//            } catch (Exception e) {
//                e.printStackTrace();
//                break;
//            }
//        }
//        scanner.close();
//    }
//
//    private static void handleReplCommand(String cmd, CronScheduler scheduler) {
//        if ("/help".equals(cmd)) {
//            System.out.println("命令列表：");
//            System.out.println("  /cron-list  -- 列出所有定时任务");
//            System.out.println("  /help       -- 查看帮助");
//            System.out.println("  /exit       -- 退出程序");
//        } else if ("/cron-list".equals(cmd)) {
//            System.out.println(scheduler.listTasks());
//        } else {
//            System.out.println("未知命令，输入 /help 查看帮助");
//        }
//    }
//}

package zsl.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import zsl.agent.client.ClientCron2;
import zsl.agent.utils.StreamResponseHandler;
import zsl.agent.funtions.CronScheduler;
import zsl.agent.funtions.McpBootstrap;
import zsl.agent.utils.MCPToolRouter;
import zsl.agent.utils.PluginLoader;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static zsl.agent.config.Cantains.*;


@SpringBootApplication
public class AgentApplication {

    public static void main(String[] args) {
        // ✅ 修复：只启动一次Spring容器
        ConfigurableApplicationContext context = SpringApplication.run(AgentApplication.class, args);

        // 从Spring容器获取定时任务调度器
        CronScheduler cronScheduler = context.getBean(CronScheduler.class);
        cronScheduler.start();

        System.out.println("初始化完成！");

        // 车道互斥锁：保证同一时间只有一个任务在输出（用户优先）
        ReentrantLock laneLock = new ReentrantLock();

        // 初始化Agent客户端
        ClientCron2 client = ClientCron2.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .model(model)
                .build();

        // =====================================================
        // 后台线程：每秒检查一次定时任务队列
        // =====================================================
        ScheduledExecutorService cronChecker = Executors.newSingleThreadScheduledExecutor();
        cronChecker.scheduleAtFixedRate(() -> {
            try {
                List<String> cronResults = cronScheduler.drainNotifications();
                if (!cronResults.isEmpty()) {
                    System.out.println("\n=====================================");
                    System.out.println("> [定时任务] 检测到触发信号");

                    // 非阻塞拿锁：如果用户正在对话，就稍后重试
                    if (laneLock.tryLock()) {
                        try {
                            for (String cronPrompt : cronResults) {
                                System.out.println("> [定时任务] 执行中...");
                                System.out.print("> [定时任务] AI 回复：");

                                // 定时任务也使用流式输出
                                CompletableFuture<Void> future = client.singleTurnChatStream(cronPrompt, new StreamResponseHandler() {
                                    @Override
                                    public void onNext(String token) {
                                        System.out.print(token);
                                    }

                                    @Override
                                    public void onComplete() {
                                        System.out.println();
                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                        System.err.println("\n定时任务执行错误：" + e.getMessage());
                                        e.printStackTrace();
                                    }
                                });

                                // 等待当前定时任务流式输出完成
                                future.get();

                            }
                        } finally {
                            laneLock.unlock();
                        }
                    } else {
                        System.out.println("> [定时任务] 主车道被用户占用，稍后重试");
                    }

                    System.out.println("=====================================\n");
                    // 重新打印提示符，让用户知道可以继续输入
                    System.out.print("\033[36m\033[1ms01 >> \033[0m");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 1, TimeUnit.SECONDS);

        // =====================================================
        // 主线程：处理用户输入（REPL循环）
        // =====================================================
        Scanner scanner = new Scanner(System.in);
        final String BLUE = "\033[36m";
        final String BOLD = "\033[1m";
        final String RESET = "\033[0m";
        final String GREEN = "\033[32m";

        System.out.println("=====================================");
        System.out.println("  Agent 系统启动成功！");
        System.out.println("  输入 /help 查看命令，输入 q 退出");
        System.out.println("=====================================\n");

        while (true) {
            try {
                System.out.print(BLUE + BOLD + "s01 >> " + RESET);
                String query = scanner.nextLine().trim();

                // 退出命令
                if (query.equalsIgnoreCase("q") || query.equalsIgnoreCase("exit")) {
                    System.out.println("程序退出~");
                    cronScheduler.stop();
                    cronChecker.shutdown();
                    break;
                }

                // 空输入跳过
                if (query.isEmpty()) {
                    continue;
                }

                // 处理系统命令
                if (query.startsWith("/")) {
                    handleReplCommand(query, cronScheduler);
                    continue;
                }

                // 用户对话：阻塞拿锁（必须拿到，用户优先）
                System.out.println("> [用户] 等待执行...");
                laneLock.lock();
                try {
                    System.out.println("> [用户] 执行中...");
                    System.out.print(GREEN + "AI回复: " + RESET);

                    // 调用流式对话方法
                    CompletableFuture<Void> future = client.chatStream(query, new StreamResponseHandler() {
                        @Override
                        public void onNext(String token) {
                            // 逐字打印AI回复
                            System.out.print(token);
                        }

                        @Override
                        public void onComplete() {
                            // 回复结束后换行
                            System.out.println();
                        }

                        @Override
                        public void onError(Throwable e) {
                            System.err.println("\n发生错误：" + e.getMessage());
                            e.printStackTrace();
                        }
                    });

                    // 阻塞直到整个对话（包括所有工具调用）完成
                    future.get();

                } finally {
                    laneLock.unlock();
                }

            } catch (Exception e) {
                System.err.println("\n系统错误：" + e.getMessage());
                e.printStackTrace();
                // 发生错误时确保锁被释放
                if (laneLock.isHeldByCurrentThread()) {
                    laneLock.unlock();
                }
            }
        }

        scanner.close();
        context.close();
    }

    private static void handleReplCommand(String cmd, CronScheduler scheduler) {
        if ("/help".equals(cmd)) {
            System.out.println("命令列表：");
            System.out.println("  /cron-list  -- 列出所有定时任务");
            System.out.println("  /help       -- 查看帮助");
            System.out.println("  /exit       -- 退出程序");
        } else if ("/cron-list".equals(cmd)) {
            System.out.println(scheduler.listTasks());
        } else {
            System.out.println("未知命令，输入 /help 查看帮助");
        }
    }
}