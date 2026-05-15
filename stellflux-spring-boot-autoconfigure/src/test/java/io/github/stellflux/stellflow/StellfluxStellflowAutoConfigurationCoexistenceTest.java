package io.github.stellflux.stellflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.stellflow.consumer.StellfluxStellflowConsumerFactory;
import io.github.stellflux.stellflow.producer.StellfluxStellflowProducerFactory;
import io.github.stellhub.stellflow.sdk.client.StellflowClientFactory;
import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumer;
import io.github.stellhub.stellflow.sdk.producer.ProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.RoundRobinProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class StellfluxStellflowAutoConfigurationCoexistenceTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(StellfluxStellflowAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldAllowProducerAndConsumerToCoexist() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.bootstrap-servers=127.0.0.1:9092",
                        "stellflux.stellflow.client-id=order-client",
                        "stellflux.stellflow.consumer.group-id=order-service")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellflowProducer.class);
                            assertThat(context).hasSingleBean(StellflowConsumer.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowProducerFactory.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowConsumerFactory.class);
                            assertThat(context).getBeans(StellflowClientOptions.class).hasSize(1);
                            assertThat(context).getBeans(StellflowClientFactory.class).hasSize(1);
                            assertThat(context)
                                    .hasBean("stellfluxStellflowClientOptions")
                                    .hasBean("stellfluxStellflowClientFactory");

                            StellflowClientOptions options =
                                    context.getBean(
                                            "stellfluxStellflowClientOptions",
                                            StellflowClientOptions.class);
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
                        "stellflux.stellflow.producer.acks=1",
                        "stellflux.stellflow.producer.timeout-ms=1500",
                        "stellflux.stellflow.producer.max-batch-records=32",
                        "stellflux.stellflow.producer.partitioner=round-robin",
                        "stellflux.stellflow.consumer.group-id=order-service",
                        "stellflux.stellflow.consumer.member-id=member-a",
                        "stellflux.stellflow.consumer.session-timeout-ms=12000",
                        "stellflux.stellflow.consumer.heartbeat-interval=2s",
                        "stellflux.stellflow.consumer.fetch-max-bytes=65536",
                        "stellflux.stellflow.consumer.offset-commit-metadata=test")
                .run(
                        context -> {
                            StellflowClientOptions options =
                                    context.getBean(
                                            "stellfluxStellflowClientOptions",
                                            StellflowClientOptions.class);
                            assertThat(options.bootstrapServers()).hasSize(2);
                            assertThat(options.clientId()).isEqualTo("order-client");
                            assertThat(options.networkThreads()).isEqualTo(2);
                            assertThat(options.requestTimeout()).hasSeconds(3);
                            assertThat(options.retryPolicy().maxAttempts()).isEqualTo(5);
                            assertThat(options.retryPolicy().backoff().toMillis()).isEqualTo(200);
                            assertThat(options.producerOptions().acks()).isEqualTo((short) 1);
                            assertThat(options.producerOptions().timeoutMs()).isEqualTo(1500);
                            assertThat(options.producerOptions().maxBatchRecords()).isEqualTo(32);
                            assertThat(options.producerOptions().partitioner())
                                    .isInstanceOf(RoundRobinProducerPartitioner.class);
                            assertThat(options.consumerOptions().groupId()).isEqualTo("order-service");
                            assertThat(options.consumerOptions().memberId()).isEqualTo("member-a");
                            assertThat(options.consumerOptions().sessionTimeoutMs()).isEqualTo(12000);
                            assertThat(options.consumerOptions().heartbeatInterval()).hasSeconds(2);
                            assertThat(options.consumerOptions().fetchMaxBytes()).isEqualTo(65536);
                            assertThat(options.consumerOptions().offsetCommitMetadata()).isEqualTo("test");
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
                                    context.getBean(
                                            "stellfluxStellflowClientOptions",
                                            StellflowClientOptions.class);
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
    void shouldSkipProducerWhenDisabled() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.bootstrap-servers=127.0.0.1:9092",
                        "stellflux.stellflow.producer.enabled=false")
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
                .withPropertyValues("stellflux.stellflow.bootstrap-servers=127.0.0.1:9092")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellflowProducer.class);
                            assertThat(context).doesNotHaveBean(StellflowConsumer.class);
                            assertThat(context).doesNotHaveBean(StellfluxStellflowConsumerFactory.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldSkipConsumerWhenDisabled() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.bootstrap-servers=127.0.0.1:9092",
                        "stellflux.stellflow.consumer.enabled=false",
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
