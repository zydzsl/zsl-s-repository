package zsl.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;
import zsl.web.untils.JWTtools;

import java.io.IOException;
@Slf4j
@WebFilter("/*")
public class Tokenfilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        log.info("请求开始");
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse Response = (HttpServletResponse) servletResponse;
//获得请求路径
        String url = request.getRequestURI();// /emps/login
        if(url.contains("/login")) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        String token = request.getHeader("token");
        if(token == null||token.isEmpty()){
            Response.setStatus(401);
            return;
        }

        if(JWTtools.checkJWT(token)){
            filterChain.doFilter(servletRequest, servletResponse);
        }else{
            Response.setStatus(401);
            return;
        }
    }
}
