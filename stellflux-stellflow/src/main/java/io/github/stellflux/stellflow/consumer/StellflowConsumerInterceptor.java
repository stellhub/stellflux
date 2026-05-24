package io.github.stellflux.stellflow.consumer;

import io.github.stellflux.stellflow.StellfluxStellflowInterceptorOrder;

/** Stellflow 消费者拦截器。 */
public interface StellflowConsumerInterceptor {

    /**
     * 返回拦截器顺序。
     *
     * @return 顺序值，越小越靠前
     */
    default int getOrder() {
        return StellfluxStellflowInterceptorOrder.USER;
    }

    /**
     * 消费前处理。
     *
     * @param context 消费者上下文
     * @return 是否继续消费当前消息
     */
    default boolean beforeConsume(StellflowConsumerContext context) {
        return true;
    }

    /**
     * 消费成功后处理。
     *
     * @param context 消费者上下文
     */
    default void afterConsume(StellflowConsumerContext context) {}

    /**
     * 消费失败后处理。
     *
     * @param context 消费者上下文
     * @param throwable 异常
     */
    default void onConsumeError(StellflowConsumerContext context, Throwable throwable) {}
}
