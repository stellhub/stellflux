package io.github.stellflux.stellflow.consumer;

import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;

/** Stellflow 消费者客户端配置自定义器。 */
@FunctionalInterface
public interface StellfluxStellflowConsumerOptionsCustomizer {

    /**
     * 自定义 Stellflow 消费者客户端配置。
     *
     * @param builder Stellflow 客户端配置构建器
     */
    void customize(StellflowClientOptions.Builder builder);
}
