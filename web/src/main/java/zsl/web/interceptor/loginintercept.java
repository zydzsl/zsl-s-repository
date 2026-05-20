package zsl.web.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.coyote.Response;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import zsl.web.untils.JWTtools;

@Component
public class loginintercept implements HandlerInterceptor {

//方法运行之后运行
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }
    //方法运行之前运行，true为放行，false为拦截
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String url = request.getRequestURI();// /emps/login

        if(url.contains("/login")) {
            return true;
        }
        String token = request.getHeader("token");
        if(token == null||token.isEmpty()){
            response.setStatus(401);
            return false;
        }

        if(JWTtools.checkJWT(token)){
            return true;
        }else{
            response.setStatus(401);
            return false;
        }
    }
}
