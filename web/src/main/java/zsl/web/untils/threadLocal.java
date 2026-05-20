package zsl.web.untils;

import java.util.Map;

public class threadLocal {
    //线程本地存储
    private static final ThreadLocal<String> threadLocal = new ThreadLocal<>();
    //设置
    public static void set(String name){
        threadLocal.set(name);
    }

    //获取
    public static String get(){
        return threadLocal.get();
    }

    //删除
    public static void remove(){
        threadLocal.remove();
    }
}
