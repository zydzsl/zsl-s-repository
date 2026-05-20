package zsl.agent.utils;

import cn.hutool.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import zsl.agent.config.AiToolMethod;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 必须加，Spring 强制管理
@Component
public class ToolHanderBean implements ApplicationContextAware {
    private static MCPToolRouter mcpRouter = new MCPToolRouter();

    // 全局工具Map
    public static final Map<String, ToolExecutor> TOOL_MAP = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        // 获取所有 Spring 管理的 Bean
        Map<String, Object> beans = applicationContext.getBeansOfType(Object.class);

        for (Object bean : beans.values()) {
            Class<?> clazz = bean.getClass();
            // 遍历所有方法
            for (Method method : clazz.getDeclaredMethods()) {
                AiToolMethod anno = method.getAnnotation(AiToolMethod.class);
                if (anno == null) continue;

                // 只注册 public 方法
                if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                TOOL_MAP.put(anno.name(), new ToolExecutor(bean, method, anno.desc()));
            }
        }
    }
    // 执行器
    public static class ToolExecutor {
        public final Object bean;
        public final Method method;
        public final String desc;

        public ToolExecutor(Object bean, Method method, String desc) {
            this.bean = bean;
            this.method = method;
            this.desc = desc;
        }

        public String execute(JSONObject params, String toolName) {
            try {
                return (String) method.invoke(bean, params);
            } catch (Exception e) {
                System.err.println("===== 工具执行错误 =====");
                e.printStackTrace();
                return "工具执行异常：" + e.getMessage();
            }
        }
    }
}