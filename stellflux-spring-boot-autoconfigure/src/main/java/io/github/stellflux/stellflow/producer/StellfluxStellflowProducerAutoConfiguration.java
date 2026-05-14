package io.github.stellflux.stellflow.producer;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.github.stellhub.stellflow.sdk.client.RetryPolicy;
import io.github.stellhub.stellflow.sdk.client.StellflowClientFactory;
import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;
import io.github.stellhub.stellflow.sdk.observability.StellflowObservability;
import io.github.stellhub.stellflow.sdk.producer.DefaultProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.ProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.RoundRobinProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducer;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/** Stellflow 生产者自动装配。 */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({
    StellflowProducer.class,
    StellflowClientFactory.class,
    StellfluxStellflowProducerFactory.class
})
@EnableConfigurationProperties(StellfluxStellflowProducerProperties.class)
public class StellfluxStellflowProducerAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellflowProducerAutoConfiguration.class.getName());

    /**
     * 注册 Stellflow 生产者客户端配置。
     *
     * @param properties Stellflow 生产者配置属性
     * @param applicationContext Spring 上下文
     * @param openTelemetryProvider OpenTelemetry 提供者
     * @param customizers 配置自定义器
     * @return Stellflow 客户端配置
     */
    @Bean("stellfluxStellflowProducerClientOptions")
    @ConditionalOnMissingBean(name = "stellfluxStellflowProducerClientOptions")
    @ConditionalOnProperty(
            prefix = "stellflux.stellflow.producer",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnProperty(prefix = "stellflux.stellflow.producer", name = "bootstrap-servers")
    public StellflowClientOptions stellfluxStellflowProducerClientOptions(
            StellfluxStellflowProducerProperties properties,
            ApplicationContext applicationContext,
            ObjectProvider<OpenTelemetry> openTelemetryProvider,
            ObjectProvider<StellfluxStellflowProducerOptionsCustomizer> customizers) {
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
        builder.producerAcks(properties.getProducer().getAcks());
        builder.producerTimeoutMs(properties.getProducer().getTimeoutMs());
        builder.producerMaxBatchRecords(properties.getProducer().getMaxBatchRecords());
        builder.producerPartitioner(resolvePartitioner(properties, applicationContext));
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
    @Bean(value = "stellfluxStellflowProducerClientFactory", destroyMethod = "close")
    @ConditionalOnBean(name = "stellfluxStellflowProducerClientOptions")
    @ConditionalOnMissingBean(name = "stellfluxStellflowProducerClientFactory")
    public StellflowClientFactory stellfluxStellflowProducerClientFactory(
            @Qualifier("stellfluxStellflowProducerClientOptions") StellflowClientOptions options) {
        return StellflowClientFactory.create(options);
    }

    /**
     * 注册 Stellflow 生产者工厂。
     *
     * @param clientFactory Stellflow SDK 客户端工厂
     * @return Stellflow 生产者工厂
     */
    @Bean
    @ConditionalOnBean(name = "stellfluxStellflowProducerClientFactory")
    @ConditionalOnMissingBean
    public StellfluxStellflowProducerFactory stellfluxStellflowProducerFactory(
            @Qualifier("stellfluxStellflowProducerClientFactory")
                    StellflowClientFactory clientFactory) {
        return new StellfluxStellflowProducerFactory(clientFactory);
    }

    /**
     * 注册默认 Stellflow 生产者。
     *
     * @param factory Stellflow 生产者工厂
     * @return Stellflow 生产者
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(StellfluxStellflowProducerFactory.class)
    @ConditionalOnMissingBean
    public StellflowProducer stellflowProducer(StellfluxStellflowProducerFactory factory) {
        return factory.createProducer();
    }

    /**
     * 记录 Stellflow 生产者 starter 启动日志。
     *
     * @param properties Stellflow 生产者配置属性
     * @param moduleInfoMeterProvider 模块指标注册器
     * @return 启动日志探针
     */
    @Bean("stellfluxStellflowProducerStarterStartupLogger")
    @ConditionalOnBean(StellflowProducer.class)
    public SmartInitializingSingleton stellfluxStellflowProducerStarterStartupLogger(
            StellfluxStellflowProducerProperties properties,
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule(
                        "stellflux-stellflow-producer", StellfluxStellflowProducerFactory.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-stellflow-producer started successfully"
                                    + ", bootstrapServers="
                                    + safeText(properties.getBootstrapServers())
                                    + ", clientId="
                                    + safeText(properties.getClientId())
                                    + ", acks="
                                    + properties.getProducer().getAcks()
                                    + ", maxBatchRecords="
                                    + properties.getProducer().getMaxBatchRecords()
                                    + ", partitioner="
                                    + properties.getProducer().getPartitioner());
        };
    }

    private ProducerPartitioner resolvePartitioner(
            StellfluxStellflowProducerProperties properties, ApplicationContext applicationContext) {
        String beanName = properties.getProducer().getPartitionerBeanName();
        if (StringUtils.hasText(beanName)) {
            return applicationContext.getBean(beanName, ProducerPartitioner.class);
        }
        if (properties.getProducer().getPartitioner()
                == StellfluxStellflowProducerProperties.PartitionerType.ROUND_ROBIN) {
            return new RoundRobinProducerPartitioner();
        }
        return new DefaultProducerPartitioner();
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<default>" : value;
    }
}
