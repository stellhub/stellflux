package io.github.stellflux.stellflow.producer;

import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;

/** Stellflow 生产者客户端配置自定义器。 */
@FunctionalInterface
public interface StellfluxStellflowProducerOptionsCustomizer {

    /**
     * 自定义 Stellflow 生产者客户端配置。
     *
     * @param builder Stellflow 客户端配置构建器
     */
    void customize(StellflowClientOptions.Builder builder);
}
