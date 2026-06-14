package io.github.stellflux.stellflow;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.github.stellflux.stellflow.consumer.DefaultStellflowConsumerOperations;
import io.github.stellflux.stellflow.consumer.StellflowConsumerInterceptor;
import io.github.stellflux.stellflow.consumer.StellflowConsumerOperations;
import io.github.stellflux.stellflow.consumer.StellfluxStellflowConsumerFactory;
import io.github.stellflux.stellflow.listener.StellflowListener;
import io.github.stellflux.stellflow.listener.StellfluxStellflowListenerContainerManager;
import io.github.stellflux.stellflow.producer.StellflowProducerInterceptor;
import io.github.stellflux.stellflow.producer.StellflowProducerOperations;
import io.github.stellflux.stellflow.producer.StellflowProducerTopicOptions;
import io.github.stellflux.stellflow.producer.StellfluxStellflowProducerFactory;
import io.github.stellhub.stellflow.sdk.admin.StellflowAdminClient;
import io.github.stellhub.stellflow.sdk.client.RetryPolicy;
import io.github.stellhub.stellflow.sdk.client.StellflowClientFactory;
import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumer;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumerOptions;
import io.github.stellhub.stellflow.sdk.observability.StellflowObservability;
import io.github.stellhub.stellflow.sdk.producer.DefaultProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.ProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.RoundRobinProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducer;
import io.opentelemetry.api.OpenTelemetry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/** Stellflow 自动装配。 */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({
    StellflowProducer.class,
    StellflowConsumer.class,
    StellflowClientFactory.class,
    StellfluxStellflowProducerFactory.class,
    StellfluxStellflowConsumerFactory.class,
    StellflowListener.class
})
@EnableConfigurationProperties(StellfluxStellflowProperties.class)
public class StellfluxStellflowAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellflowAutoConfiguration.class.getName());

    /**
     * 注册 Stellflow 客户端配置。
     *
     * @param properties Stellflow 配置属性
     * @param applicationContext Spring 上下文
     * @param openTelemetryProvider OpenTelemetry 提供者
     * @param customizers 配置自定义器
     * @return Stellflow 客户端配置
     */
    @Bean("stellfluxStellflowClientOptions")
    @ConditionalOnMissingBean(name = "stellfluxStellflowClientOptions")
    @ConditionalOnProperty(
            prefix = "stellflux.stellflow",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnProperty(prefix = "stellflux.stellflow", name = "bootstrap-servers")
    public StellflowClientOptions stellfluxStellflowClientOptions(
            StellfluxStellflowProperties properties,
            ApplicationContext applicationContext,
            ObjectProvider<OpenTelemetry> openTelemetryProvider,
            ObjectProvider<StellfluxStellflowOptionsCustomizer> customizers) {
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
        builder.producerAutoCreateTopics(
                properties.getProducer().hasTopicConfigs()
                        ? false
                        : properties.getProducer().isAutoCreateTopics());
        builder.producerAutoCreateTopicPartitionCount(
                properties.getProducer().getAutoCreateTopicPartitionCount());
        builder.producerPartitioner(resolvePartitioner(properties, applicationContext));
        if (StringUtils.hasText(properties.getConsumer().getGroupId())) {
            builder.consumerOptions(buildConsumerOptions(properties.getConsumer()));
        }
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
    @Bean(value = "stellfluxStellflowClientFactory", destroyMethod = "close")
    @ConditionalOnBean(name = "stellfluxStellflowClientOptions")
    @ConditionalOnMissingBean(name = "stellfluxStellflowClientFactory")
    public StellflowClientFactory stellfluxStellflowClientFactory(StellflowClientOptions options) {
        return StellflowClientFactory.create(options);
    }

    /**
     * 注册 Stellflow 管理客户端。
     *
     * @param clientFactory Stellflow SDK 客户端工厂
     * @return Stellflow 管理客户端
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(name = "stellfluxStellflowClientFactory")
    @ConditionalOnMissingBean
    public StellflowAdminClient stellflowAdminClient(StellflowClientFactory clientFactory) {
        return clientFactory.createAdminClient();
    }

    /**
     * 注册 Stellflow 生产者工厂。
     *
     * @param clientFactory Stellflow SDK 客户端工厂
     * @return Stellflow 生产者工厂
     */
    @Bean
    @ConditionalOnBean(name = "stellfluxStellflowClientFactory")
    @ConditionalOnMissingBean
    @Conditional(ProducerTopicsConfiguredCondition.class)
    public StellfluxStellflowProducerFactory stellfluxStellflowProducerFactory(
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
     * 注册 Stellflow 消费者工厂。
     *
     * @param clientFactory Stellflow SDK 客户端工厂
     * @return Stellflow 消费者工厂
     */
    @Bean
    @ConditionalOnBean(name = "stellfluxStellflowClientFactory")
    @ConditionalOnMissingBean
    @Conditional(ConsumerTopicsConfiguredCondition.class)
    public StellfluxStellflowConsumerFactory stellfluxStellflowConsumerFactory(
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
    @ConditionalOnProperty(prefix = "stellflux.stellflow.consumer", name = "group-id")
    public StellflowConsumer stellflowConsumer(StellfluxStellflowConsumerFactory factory) {
        return factory.createConsumer();
    }

    /**
     * 注册 Stellflow 生产者操作模板。
     *
     * @param producer Stellflow 生产者
     * @param interceptors 生产者拦截器
     * @return 生产者操作模板
     */
    @Bean
    @ConditionalOnBean(StellflowProducer.class)
    @ConditionalOnMissingBean
    public StellflowProducerOperations stellflowProducerOperations(
            StellflowProducer producer,
            StellfluxStellflowProperties properties,
            ObjectProvider<StellflowAdminClient> adminClientProvider,
            ObjectProvider<StellflowProducerInterceptor> interceptors) {
        StellflowAdminClient adminClient =
                properties.getProducer().hasTopicConfigs() ? adminClientProvider.getIfAvailable() : null;
        return new StellflowTemplate(
                producer,
                adminClient,
                topic -> resolveProducerTopicOptions(properties.getProducer(), topic),
                interceptors.orderedStream().toList());
    }

    /**
     * 注册 Stellflow 消费者操作模板。
     *
     * @param consumer Stellflow 消费者
     * @param properties Stellflow 配置属性
     * @param interceptors 消费者拦截器
     * @return 消费者操作模板
     */
    @Bean
    @ConditionalOnBean(StellflowConsumer.class)
    @ConditionalOnMissingBean
    public StellflowConsumerOperations stellflowConsumerOperations(
            StellflowConsumer consumer,
            StellfluxStellflowProperties properties,
            ObjectProvider<StellflowConsumerInterceptor> interceptors) {
        return new DefaultStellflowConsumerOperations(
                consumer,
                buildConsumerOptions(properties.getConsumer()),
                interceptors.orderedStream().toList());
    }

    /**
     * 注册 Stellflow 监听容器管理器。
     *
     * @param applicationContext Spring 上下文
     * @param consumerFactory 消费者工厂
     * @param properties Stellflow 配置属性
     * @param interceptors 消费者拦截器
     * @return 监听容器管理器
     */
    @Bean
    @ConditionalOnBean(StellfluxStellflowConsumerFactory.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "stellflux.stellflow.consumer",
            name = "listener-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public StellfluxStellflowListenerContainerManager stellflowListenerContainerManager(
            ApplicationContext applicationContext,
            StellfluxStellflowConsumerFactory consumerFactory,
            StellfluxStellflowProperties properties,
            ObjectProvider<StellflowConsumerInterceptor> interceptors) {
        List<StellflowConsumerInterceptor> interceptorList = interceptors.orderedStream().toList();
        return new StellfluxStellflowListenerContainerManager(
                applicationContext, consumerFactory, properties, interceptorList);
    }

    /**
     * 记录 Stellflow starter 启动日志。
     *
     * @param properties Stellflow 配置属性
     * @param moduleInfoMeterProvider 模块指标注册器
     * @return 启动日志探针
     */
    @Bean("stellfluxStellflowStartupLogger")
    @ConditionalOnBean(name = "stellfluxStellflowClientFactory")
    public SmartInitializingSingleton stellfluxStellflowStartupLogger(
            StellfluxStellflowProperties properties,
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule("stellflux-stellflow", StellflowClientFactory.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-stellflow started successfully"
                                    + ", bootstrapServers="
                                    + safeText(properties.getBootstrapServers())
                                    + ", clientId="
                                    + safeText(properties.getClientId())
                                    + ", consumerGroupId="
                                    + safeText(properties.getConsumer().getGroupId())
                                    + ", producerTopics="
                                    + safeTopics(properties.getProducer().effectiveTopics())
                                    + ", consumerTopics="
                                    + safeTopics(properties.getConsumer().effectiveTopics())
                                    + ", consumerListenerEnabled="
                                    + properties.getConsumer().isListenerEnabled());
        };
    }

    private ProducerPartitioner resolvePartitioner(
            StellfluxStellflowProperties properties, ApplicationContext applicationContext) {
        String beanName = properties.getProducer().getPartitionerBeanName();
        if (StringUtils.hasText(beanName)) {
            return applicationContext.getBean(beanName, ProducerPartitioner.class);
        }
        if (properties.getProducer().getPartitioner()
                == StellfluxStellflowProperties.PartitionerType.ROUND_ROBIN) {
            return new RoundRobinProducerPartitioner();
        }
        return new DefaultProducerPartitioner();
    }

    private StellflowConsumerOptions buildConsumerOptions(
            StellfluxStellflowProperties.ConsumerProperties properties) {
        return new StellflowConsumerOptions(
                properties.getGroupId(),
                properties.getMemberId(),
                properties.getSessionTimeoutMs(),
                properties.getHeartbeatInterval(),
                properties.getFetchMaxBytes(),
                properties.getOffsetCommitMetadata());
    }

    private StellflowProducerTopicOptions resolveProducerTopicOptions(
            StellfluxStellflowProperties.ProducerProperties properties, String topic) {
        return new StellflowProducerTopicOptions(
                properties.resolveAutoCreateTopics(topic),
                properties.resolveAutoCreateTopicPartitionCount(topic));
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<default>" : value;
    }

    private String safeTopics(Collection<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return "[]";
        }
        return topics.toString();
    }

    static class ProducerTopicsConfiguredCondition extends SpringBootCondition {

        @Override
        public ConditionOutcome getMatchOutcome(
                ConditionContext context, AnnotatedTypeMetadata metadata) {
            List<String> topics =
                    enabledTopicNames(context, "stellflux.stellflow.producer.topic-configs");
            if (!topics.isEmpty()) {
                return ConditionOutcome.match(
                        ConditionMessage.forCondition("Stellflow producer topics")
                                .found("enabled topic config")
                                .items(topics));
            }
            return ConditionOutcome.noMatch(
                    ConditionMessage.forCondition("Stellflow producer topics")
                            .didNotFind("enabled topic config")
                            .atAll());
        }
    }

    static class ConsumerTopicsConfiguredCondition extends SpringBootCondition {

        @Override
        public ConditionOutcome getMatchOutcome(
                ConditionContext context, AnnotatedTypeMetadata metadata) {
            List<String> topics =
                    enabledTopicNames(context, "stellflux.stellflow.consumer.topic-configs");
            if (!topics.isEmpty()) {
                return ConditionOutcome.match(
                        ConditionMessage.forCondition("Stellflow consumer topics")
                                .found("enabled topic config")
                                .items(topics));
            }
            return ConditionOutcome.noMatch(
                    ConditionMessage.forCondition("Stellflow consumer topics")
                            .didNotFind("enabled topic config")
                            .atAll());
        }
    }

    private static List<String> enabledTopicNames(ConditionContext context, String prefix) {
        Binder binder = Binder.get(context.getEnvironment());
        Map<String, Object> configs =
                binder.bind(prefix, Bindable.mapOf(String.class, Object.class)).orElse(Map.of());
        List<String> topics = new ArrayList<>();
        configs.keySet().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(topic -> isTopicEnabled(binder, prefix, topic))
                .forEach(topics::add);
        return List.copyOf(topics);
    }

    private static boolean isTopicEnabled(Binder binder, String prefix, String topic) {
        return binder.bind(prefix + "[" + topic + "].enabled", Boolean.class).orElse(true);
    }
}
