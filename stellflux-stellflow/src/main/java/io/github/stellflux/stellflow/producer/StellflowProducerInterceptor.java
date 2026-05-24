package io.github.stellflux.stellflow.producer;

import io.github.stellflux.stellflow.StellfluxStellflowInterceptorOrder;
import io.github.stellhub.stellflow.sdk.producer.RecordMetadata;

/** Stellflow 生产者拦截器。 */
public interface StellflowProducerInterceptor {

    /**
     * 返回拦截器顺序。
     *
     * @return 顺序值，越小越靠前
     */
    default int getOrder() {
        return StellfluxStellflowInterceptorOrder.USER;
    }

    /**
     * 发送前处理。
     *
     * @param context 生产者上下文
     * @return 是否继续发送
     */
    default boolean beforeSend(StellflowProducerContext context) {
        return true;
    }

    /**
     * 发送成功后处理。
     *
     * @param context 生产者上下文
     * @param metadata 发送结果元数据
     */
    default void afterSend(StellflowProducerContext context, RecordMetadata metadata) {}

    /**
     * 发送失败后处理。
     *
     * @param context 生产者上下文
     * @param throwable 异常
     */
    default void onSendError(StellflowProducerContext context, Throwable throwable) {}
}
