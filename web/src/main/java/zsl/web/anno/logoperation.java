package zsl.web.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//方法注解 Target 表示注解作用在方法上
@Target(ElementType.METHOD)
//Retention 表示注解在运行时生效
@Retention(RetentionPolicy.RUNTIME)
public @interface logoperation {

}
