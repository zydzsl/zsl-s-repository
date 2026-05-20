package zsl.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect  //切面AOP类
@Component
public class aopcontroller {


    //@Around("annotation(zsl.web.anno.logoperation)") 在方法上添加注解即可
    @Around("execution(* zsl.web.controller.*.*(..))")
    public Object realtime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();

        //获得目标对象
        Object target = pjp.getTarget();
        //获得目标类
       String  className = pjp.getTarget().getClass().getName();
       //获得方法名
        String methodName = pjp.getSignature().getName();
        //获得参数
        Object[] args = pjp.getArgs();

        //执行方法
        Object result = pjp.proceed();

        long end = System.currentTimeMillis();
        log.info("耗时："+(end-start));
        return result;
    }
}
