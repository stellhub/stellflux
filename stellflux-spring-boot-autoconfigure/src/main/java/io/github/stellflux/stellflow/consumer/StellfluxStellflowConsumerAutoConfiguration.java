package io.github.stellflux.stellflow.consumer;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.github.stellhub.stellflow.sdk.client.RetryPolicy;
import io.github.stellhub.stellflow.sdk.client.StellflowClientFactory;
import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumer;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumerOptions;
import io.github.stellhub.stellflow.sdk.observability.StellflowObservability;
import io.opentelemetry.api.OpenTelemetry;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/** Stellflow 消费者自动装配。 */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({
    StellflowConsumer.class,
    StellflowClientFactory.class,
    StellfluxStellflowConsumerFactory.class
})
@EnableConfigurationProperties(StellfluxStellflowConsumerProperties.class)
public class StellfluxStellflowConsumerAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellflowConsumerAutoConfiguration.class.getName());

    /**
     * 注册 Stellflow 消费者客户端配置。
     *
     * @param properties Stellflow 消费者配置属性
     * @param openTelemetryProvider OpenTelemetry 提供者
     * @param customizers 配置自定义器
     * @return Stellflow 客户端配置
     */
    @Bean("stellfluxStellflowConsumerClientOptions")
    @ConditionalOnMissingBean(name = "stellfluxStellflowConsumerClientOptions")
    @ConditionalOnProperty(
            prefix = "stellflux.stellflow.consumer",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnProperty(
            prefix = "stellflux.stellflow.consumer",
            name = {"bootstrap-servers", "consumer.group-id"})
    public StellflowClientOptions stellfluxStellflowConsumerClientOptions(
            StellfluxStellflowConsumerProperties properties,
            ObjectProvider<OpenTelemetry> openTelemetryProvider,
            ObjectProvider<StellfluxStellflowConsumerOptionsCustomizer> customizers) {
        StellflowClientOptions.Builder builder = StellflowClientOptions.builder();
        builder.bootstrapServers(properties.getBootstrapServers());
        if (StringUtils.hasText(properties.getClientId())) {
            builder.clientId(properties.getClientId());
        }
        if (properties.getNetworkThreads() > 0) {
            builder.networkThreads(properties.getNetworkThreads());
        }
        if (properties.getMaxFrameLength() > 0) {
            builder.maxFrameLength(properties.getMaxFrameLength());
        }
        builder.requestTimeout(properties.getRequestTimeout());
        builder.retryPolicy(
                new RetryPolicy(
                        properties.getRetry().getMaxAttempts(), properties.getRetry().getBackoff()));
        builder.consumerOptions(buildConsumerOptions(properties.getConsumer()));
        OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable();
        if (openTelemetry != null) {
            builder.observability(StellflowObservability.create(openTelemetry));
        }
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    /**
     * 注册 Stellflow SDK 客户端工厂。
     *
     * @param options Stellflow 客户端配置
     * @return Stellflow SDK 客户端工厂
     */
    @Bean(value = "stellfluxStellflowConsumerClientFactory", destroyMethod = "close")
    @ConditionalOnBean(name = "stellfluxStellflowConsumerClientOptions")
    @ConditionalOnMissingBean(name = "stellfluxStellflowConsumerClientFactory")
    public StellflowClientFactory stellfluxStellflowConsumerClientFactory(
            @Qualifier("stellfluxStellflowConsumerClientOptions") StellflowClientOptions options) {
        return StellflowClientFactory.create(options);
    }

    /**
     * 注册 Stellflow 消费者工厂。
     *
     * @param clientFactory Stellflow SDK 客户端工厂
     * @return Stellflow 消费者工厂
     */
    @Bean
    @ConditionalOnBean(name = "stellfluxStellflowConsumerClientFactory")
    @ConditionalOnMissingBean
    public StellfluxStellflowConsumerFactory stellfluxStellflowConsumerFactory(
            @Qualifier("stellfluxStellflowConsumerClientFactory")
                    StellflowClientFactory clientFactory) {
        return new StellfluxStellflowConsumerFactory(clientFactory);
    }

    /**
     * 注册默认 Stellflow 消费者。
     *
     * @param factory Stellflow 消费者工厂
     * @return Stellflow 消费者
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(StellfluxStellflowConsumerFactory.class)
    @ConditionalOnMissingBean
    public StellflowConsumer stellflowConsumer(StellfluxStellflowConsumerFactory factory) {
        return factory.createConsumer();
    }

    /**
     * 记录 Stellflow 消费者 starter 启动日志。
     *
     * @param properties Stellflow 消费者配置属性
     * @param moduleInfoMeterProvider 模块指标注册器
     * @return 启动日志探针
     */
    @Bean("stellfluxStellflowConsumerStarterStartupLogger")
    @ConditionalOnBean(StellflowConsumer.class)
    public SmartInitializingSingleton stellfluxStellflowConsumerStarterStartupLogger(
            StellfluxStellflowConsumerProperties properties,
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule(
                        "stellflux-stellflow-consumer", StellfluxStellflowConsumerFactory.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-stellflow-consumer started successfully"
                                    + ", bootstrapServers="
                                    + safeText(properties.getBootstrapServers())
                                    + ", clientId="
                                    + safeText(properties.getClientId())
                                    + ", groupId="
                                    + safeText(properties.getConsumer().getGroupId())
                                    + ", sessionTimeoutMs="
                                    + properties.getConsumer().getSessionTimeoutMs()
                                    + ", fetchMaxBytes="
                                    + properties.getConsumer().getFetchMaxBytes());
        };
    }

    private StellflowConsumerOptions buildConsumerOptions(
            StellfluxStellflowConsumerProperties.ConsumerProperties properties) {
        return new StellflowConsumerOptions(
                properties.getGroupId(),
                properties.getMemberId(),
                properties.getSessionTimeoutMs(),
                properties.getHeartbeatInterval(),
                properties.getFetchMaxBytes(),
                properties.getOffsetCommitMetadata());
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<default>" : value;
    }
}
