package zsl.agent.utils;

@FunctionalInterface
public interface StreamResponseHandler {
    /**
     * 收到新token时调用
     * @param token 本次收到的文本片段
     */
    void onNext(String token);

    /**
     * 流式响应完成时调用
     */
    default void onComplete() {}

    /**
     * 发生错误时调用
     * @param e 异常对象
     */
    default void onError(Throwable e) {}
}
