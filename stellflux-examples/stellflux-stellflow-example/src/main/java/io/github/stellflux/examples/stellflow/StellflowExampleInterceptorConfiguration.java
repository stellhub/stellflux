package io.github.stellflux.examples.stellflow;

import io.github.stellflux.stellflow.consumer.StellflowConsumerContext;
import io.github.stellflux.stellflow.consumer.StellflowConsumerInterceptor;
import io.github.stellflux.stellflow.producer.StellflowProducerContext;
import io.github.stellflux.stellflow.producer.StellflowProducerInterceptor;
import io.github.stellhub.stellflow.sdk.producer.RecordMetadata;
import java.util.logging.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stellflow 示例拦截器配置。 */
@Configuration(proxyBeanMethods = false)
public class StellflowExampleInterceptorConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellflowExampleInterceptorConfiguration.class.getName());

    /**
     * 注册生产者日志拦截器。
     *
     * @return 生产者拦截器
     */
    @Bean
    public StellflowProducerInterceptor exampleProducerInterceptor() {
        return new StellflowProducerInterceptor() {
            @Override
            public boolean beforeSend(StellflowProducerContext context) {
                LOGGER.info(
                        () ->
                                "Stellflow producer interceptor beforeSend topic="
                                        + context.getMessage().topic()
                                        + ", key="
                                        + context.getMessage().keyAsString());
                return true;
            }

            @Override
            public void afterSend(StellflowProducerContext context, RecordMetadata metadata) {
                LOGGER.info(
                        () ->
                                "Stellflow producer interceptor afterSend topic="
                                        + metadata.topic()
                                        + ", partition="
                                        + metadata.partition()
                                        + ", offset="
                                        + metadata.baseOffset());
            }
        };
    }

    /**
     * 注册消费者日志拦截器。
     *
     * @return 消费者拦截器
     */
    @Bean
    public StellflowConsumerInterceptor exampleConsumerInterceptor() {
        return new StellflowConsumerInterceptor() {
            @Override
            public boolean beforeConsume(StellflowConsumerContext context) {
                LOGGER.info(
                        () ->
                                "Stellflow consumer interceptor beforeConsume groupId="
                                        + context.getGroupId()
                                        + ", topic="
                                        + context.getMessage().topic()
                                        + ", key="
                                        + context.getMessage().keyAsString());
                return true;
            }

            @Override
            public void afterConsume(StellflowConsumerContext context) {
                LOGGER.info(
                        () ->
                                "Stellflow consumer interceptor afterConsume groupId="
                                        + context.getGroupId()
                                        + ", topic="
                                        + context.getMessage().topic()
                                        + ", offset="
                                        + context.getMessage().offset());
            }
        };
    }
}
