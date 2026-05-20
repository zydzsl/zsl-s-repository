package zsl.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zsl.web.pojo.Result;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHander {
    @ExceptionHandler(Exception.class)

    public Result error(Exception e){
        log.error("全局异常处理{}",e.getMessage());
        return Result.error("服务器异常");
    }
}
