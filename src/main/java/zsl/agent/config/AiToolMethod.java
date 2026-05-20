package zsl.agent.config;

import java.lang.annotation.*;

@Target(ElementType.METHOD) // 注解打在方法上！
@Retention(RetentionPolicy.RUNTIME)
public @interface AiToolMethod {
    // AI调用的工具名（唯一）
    String name();
    // 工具描述（给大模型看）
    String desc();
}
