package io.github.stellflux.stellflow.consumer;

import io.github.stellhub.stellflow.sdk.client.StellflowClientFactory;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumer;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumerOptions;
import lombok.RequiredArgsConstructor;

/** Stellflow 消费者工厂。 */
@RequiredArgsConstructor
public class StellfluxStellflowConsumerFactory {

    private final StellflowClientFactory clientFactory;

    /**
     * 创建默认 Stellflow 消费者。
     *
     * @return Stellflow 消费者
     */
    public StellflowConsumer createConsumer() {
        return this.clientFactory.createConsumer();
    }

    /**
     * 创建指定配置的 Stellflow 消费者。
     *
     * @param options 消费者配置
     * @return Stellflow 消费者
     */
    public StellflowConsumer createConsumer(StellflowConsumerOptions options) {
        return this.clientFactory.createConsumer(options);
    }

    /**
     * 创建指定客户端标识的 Stellflow 消费者。
     *
     * @param clientId 客户端标识
     * @param options 消费者配置
     * @return Stellflow 消费者
     */
    public StellflowConsumer createConsumer(String clientId, StellflowConsumerOptions options) {
        return this.clientFactory.createConsumer(clientId, options);
    }
}
