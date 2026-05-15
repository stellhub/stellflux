package io.github.stellflux.stellflow.producer;

import io.github.stellhub.stellflow.sdk.client.StellflowClientFactory;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducer;
import lombok.RequiredArgsConstructor;

/** Stellflow 生产者工厂。 */
@RequiredArgsConstructor
public class StellfluxStellflowProducerFactory {

    private final StellflowClientFactory clientFactory;

    /**
     * 创建默认 Stellflow 生产者。
     *
     * @return Stellflow 生产者
     */
    public StellflowProducer createProducer() {
        return this.clientFactory.createProducer();
    }

    /**
     * 创建指定客户端标识的 Stellflow 生产者。
     *
     * @param clientId 客户端标识
     * @return Stellflow 生产者
     */
    public StellflowProducer createProducer(String clientId) {
        return this.clientFactory.createProducer(clientId);
    }
}
