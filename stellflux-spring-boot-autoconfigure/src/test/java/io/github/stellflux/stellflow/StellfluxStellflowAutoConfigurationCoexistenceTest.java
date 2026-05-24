package io.github.stellflux.stellflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.stellflow.consumer.StellflowConsumerOperations;
import io.github.stellflux.stellflow.consumer.StellfluxStellflowConsumerFactory;
import io.github.stellflux.stellflow.listener.StellfluxStellflowListenerContainerManager;
import io.github.stellflux.stellflow.producer.StellflowProducerOperations;
import io.github.stellflux.stellflow.producer.StellfluxStellflowProducerFactory;
import io.github.stellhub.stellflow.sdk.client.StellflowClientFactory;
import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumer;
import io.github.stellhub.stellflow.sdk.producer.ProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.RoundRobinProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducer;
import io.opentelemetry.api.OpenTelemetry;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

class StellfluxStellflowAutoConfigurationCoexistenceTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxStellflowAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldAllowProducerAndConsumerToCoexist() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.bootstrap-servers=127.0.0.1:9092",
                        "stellflux.stellflow.client-id=order-client",
                        "stellflux.stellflow.producer.topic-configs[orders.created].enabled=true",
                        "stellflux.stellflow.consumer.topic-configs[orders.created].enabled=true",
                        "stellflux.stellflow.consumer.group-id=order-service")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellflowProducer.class);
                            assertThat(context).hasSingleBean(StellflowConsumer.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowProducerFactory.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowConsumerFactory.class);
                            assertThat(context).hasSingleBean(StellflowTemplate.class);
                            assertThat(context).hasSingleBean(StellflowProducerOperations.class);
                            assertThat(context).hasSingleBean(StellflowConsumerOperations.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowListenerContainerManager.class);
                            assertThat(context).getBeans(StellflowClientOptions.class).hasSize(1);
                            assertThat(context).getBeans(StellflowClientFactory.class).hasSize(1);
                            assertThat(context)
                                    .hasBean("stellfluxStellflowClientOptions")
                                    .hasBean("stellfluxStellflowClientFactory");

                            StellflowClientOptions options =
                                    context.getBean("stellfluxStellflowClientOptions", StellflowClientOptions.class);
                            assertThat(options.bootstrapServers()).hasSize(1);
                            assertThat(options.clientId()).isEqualTo("order-client");
                            assertThat(options.consumerOptions().groupId()).isEqualTo("order-service");
                        });
    }

    @Test
    void shouldBindProducerAndConsumerOptions() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.bootstrap-servers=127.0.0.1:9092,127.0.0.1:9093",
                        "stellflux.stellflow.client-id=order-client",
                        "stellflux.stellflow.network-threads=2",
                        "stellflux.stellflow.request-timeout=3s",
                        "stellflux.stellflow.retry.max-attempts=5",
                        "stellflux.stellflow.retry.backoff=200ms",
                        "stellflux.stellflow.producer.topic-configs[orders.created].auto-create-topics=true",
                        "stellflux.stellflow.producer.topic-configs[orders.created].auto-create-topic-partition-count=5",
                        "stellflux.stellflow.producer.topic-configs[orders.paid].enabled=true",
                        "stellflux.stellflow.producer.topic-configs[orders.audit].auto-create-topics=false",
                        "stellflux.stellflow.producer.acks=1",
                        "stellflux.stellflow.producer.timeout-ms=1500",
                        "stellflux.stellflow.producer.max-batch-records=32",
                        "stellflux.stellflow.producer.auto-create-topics=true",
                        "stellflux.stellflow.producer.auto-create-topic-partition-count=3",
                        "stellflux.stellflow.producer.partitioner=round-robin",
                        "stellflux.stellflow.consumer.topic-configs[orders.created].enabled=true",
                        "stellflux.stellflow.consumer.topic-configs[orders.created].group-id=orders-created-service",
                        "stellflux.stellflow.consumer.topic-configs[orders.created].member-id=orders-created-member",
                        "stellflux.stellflow.consumer.topic-configs[orders.created].session-timeout-ms=15000",
                        "stellflux.stellflow.consumer.topic-configs[orders.created].heartbeat-interval=5s",
                        "stellflux.stellflow.consumer.topic-configs[orders.created].fetch-max-bytes=32768",
                        "stellflux.stellflow.consumer.topic-configs[orders.created].offset-commit-metadata=topic-test",
                        "stellflux.stellflow.consumer.topic-configs[orders.created].poll-timeout=6s",
                        "stellflux.stellflow.consumer.topic-configs[orders.cancelled].enabled=true",
                        "stellflux.stellflow.consumer.group-id=order-service",
                        "stellflux.stellflow.consumer.member-id=member-a",
                        "stellflux.stellflow.consumer.session-timeout-ms=12000",
                        "stellflux.stellflow.consumer.heartbeat-interval=2s",
                        "stellflux.stellflow.consumer.fetch-max-bytes=65536",
                        "stellflux.stellflow.consumer.offset-commit-metadata=test",
                        "stellflux.stellflow.consumer.poll-timeout=4s")
                .run(
                        context -> {
                            StellflowClientOptions options =
                                    context.getBean("stellfluxStellflowClientOptions", StellflowClientOptions.class);
                            StellfluxStellflowProperties properties =
                                    context.getBean(StellfluxStellflowProperties.class);
                            assertThat(options.bootstrapServers()).hasSize(2);
                            assertThat(options.clientId()).isEqualTo("order-client");
                            assertThat(options.networkThreads()).isEqualTo(2);
                            assertThat(options.requestTimeout()).hasSeconds(3);
                            assertThat(options.retryPolicy().maxAttempts()).isEqualTo(5);
                            assertThat(options.retryPolicy().backoff().toMillis()).isEqualTo(200);
                            assertThat(options.producerOptions().acks()).isEqualTo((short) 1);
                            assertThat(options.producerOptions().timeoutMs()).isEqualTo(1500);
                            assertThat(options.producerOptions().maxBatchRecords()).isEqualTo(32);
                            assertThat(options.producerOptions().autoCreateTopics()).isFalse();
                            assertThat(options.producerOptions().autoCreateTopicPartitionCount()).isEqualTo(3);
                            assertThat(options.producerOptions().partitioner())
                                    .isInstanceOf(RoundRobinProducerPartitioner.class);
                            assertThat(options.consumerOptions().groupId()).isEqualTo("order-service");
                            assertThat(options.consumerOptions().memberId()).isEqualTo("member-a");
                            assertThat(options.consumerOptions().sessionTimeoutMs()).isEqualTo(12000);
                            assertThat(options.consumerOptions().heartbeatInterval()).hasSeconds(2);
                            assertThat(options.consumerOptions().fetchMaxBytes()).isEqualTo(65536);
                            assertThat(options.consumerOptions().offsetCommitMetadata()).isEqualTo("test");
                            assertThat(properties.getProducer().effectiveTopics())
                                    .containsExactly("orders.created", "orders.paid", "orders.audit");
                            assertThat(properties.getProducer().resolveAutoCreateTopics("orders.created"))
                                    .isTrue();
                            assertThat(properties.getProducer().resolveAutoCreateTopicPartitionCount("orders.created"))
                                    .isEqualTo(5);
                            assertThat(properties.getProducer().resolveAutoCreateTopics("orders.audit"))
                                    .isFalse();
                            assertThat(properties.getProducer().resolveAutoCreateTopics("orders.paid"))
                                    .isTrue();
                            assertThat(properties.getProducer().resolveAutoCreateTopicPartitionCount("orders.paid"))
                                    .isEqualTo(3);
                            assertThat(properties.getConsumer().effectiveTopics())
                                    .containsExactly("orders.created", "orders.cancelled");
                            assertThat(properties.getConsumer().getPollTimeout()).hasSeconds(4);
                            assertThat(properties.getConsumer().resolveGroupId("orders.created"))
                                    .isEqualTo("orders-created-service");
                            assertThat(properties.getConsumer().resolveMemberId("orders.created"))
                                    .isEqualTo("orders-created-member");
                            assertThat(properties.getConsumer().resolveSessionTimeoutMs("orders.created"))
                                    .isEqualTo(15000);
                            assertThat(properties.getConsumer().resolveHeartbeatInterval("orders.created"))
                                    .hasSeconds(5);
                            assertThat(properties.getConsumer().resolveFetchMaxBytes("orders.created"))
                                    .isEqualTo(32768);
                            assertThat(properties.getConsumer().resolveOffsetCommitMetadata("orders.created"))
                                    .isEqualTo("topic-test");
                            assertThat(properties.getConsumer().resolvePollTimeout("orders.created"))
                                    .hasSeconds(6);
                            assertThat(properties.getConsumer().resolveGroupId("orders.cancelled"))
                                    .isEqualTo("order-service");
                        });
    }

    @Test
    void shouldCreateProducerAndConsumerFactoryWhenTopicConfigsHaveOnlyTopicLevelProperties() {
        contextRunner
                .withInitializer(
                        context -> {
                            Resource resource =
                                    new ClassPathResource("stellflow-key-only-topic-config.yaml");
                            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
                            try {
                                loader.load("stellflow-key-only-topic-config", resource)
                                        .forEach(context.getEnvironment().getPropertySources()::addLast);
                            } catch (IOException exception) {
                                throw new IllegalStateException("Failed to load test yaml", exception);
                            }
                        })
                .run(
                        context -> {
                            StellfluxStellflowProperties properties =
                                    context.getBean(StellfluxStellflowProperties.class);
                            assertThat(context).hasSingleBean(StellflowProducer.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowConsumerFactory.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowListenerContainerManager.class);
                            assertThat(context).doesNotHaveBean(StellflowConsumer.class);
                            assertThat(context).doesNotHaveBean(StellflowConsumerOperations.class);
                            assertThat(properties.getProducer().effectiveTopics())
                                    .containsExactly("orders.created");
                            assertThat(properties.getConsumer().effectiveTopics())
                                    .containsExactly("orders.created");
                            assertThat(properties.getConsumer().resolveGroupId("orders.created"))
                                    .isEqualTo("orders-created-service");
                        });
    }

    @Test
    void shouldUseCustomPartitionerBeanWhenConfigured() {
        contextRunner
                .withUserConfiguration(CustomPartitionerConfiguration.class)
                .withPropertyValues(
                        "stellflux.stellflow.bootstrap-servers=127.0.0.1:9092",
                        "stellflux.stellflow.producer.partitioner-bean-name=testPartitioner")
                .run(
                        context -> {
                            StellflowClientOptions options =
                                    context.getBean("stellfluxStellflowClientOptions", StellflowClientOptions.class);
                            assertThat(options.producerOptions().partitioner())
                                    .isSameAs(context.getBean("testPartitioner"));
                        });
    }

    @Test
    void shouldSkipAllWhenBootstrapServersMissing() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(StellflowProducer.class);
                    assertThat(context).doesNotHaveBean(StellflowConsumer.class);
                    assertThat(context).doesNotHaveBean(StellflowClientFactory.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldSkipProducerWhenTopicConfigsMissing() {
        contextRunner
                .withPropertyValues("stellflux.stellflow.bootstrap-servers=127.0.0.1:9092")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellflowProducer.class);
                            assertThat(context).doesNotHaveBean(StellfluxStellflowProducerFactory.class);
                            assertThat(context).hasSingleBean(StellflowClientFactory.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldSkipConsumerWhenGroupIdMissing() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.bootstrap-servers=127.0.0.1:9092",
                        "stellflux.stellflow.producer.topic-configs[orders.created].enabled=true",
                        "stellflux.stellflow.consumer.topic-configs[orders.created].enabled=true")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellflowProducer.class);
                            assertThat(context).doesNotHaveBean(StellflowConsumer.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowConsumerFactory.class);
                            assertThat(context).doesNotHaveBean(StellflowConsumerOperations.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldSkipConsumerWhenTopicConfigsMissing() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.bootstrap-servers=127.0.0.1:9092",
                        "stellflux.stellflow.producer.topic-configs[orders.created].enabled=true",
                        "stellflux.stellflow.consumer.group-id=order-service")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellflowProducer.class);
                            assertThat(context).doesNotHaveBean(StellflowConsumer.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomPartitionerConfiguration {

        @Bean
        ProducerPartitioner testPartitioner() {
            return (topic, key, value, partitions) -> partitions.getFirst();
        }
    }
}
