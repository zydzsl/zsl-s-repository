package zsl.web.interceptor;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

//配置类 添加拦截器
@Configuration
public class interceptconfig implements WebMvcConfigurer {
    private loginintercept loginintercept;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginintercept).addPathPatterns("/**");
    }
}
