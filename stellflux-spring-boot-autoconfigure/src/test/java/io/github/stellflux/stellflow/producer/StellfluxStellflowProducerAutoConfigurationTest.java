package io.github.stellflux.stellflow.producer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellhub.stellflow.sdk.client.StellflowClientFactory;
import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;
import io.github.stellhub.stellflow.sdk.producer.ProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.RoundRobinProducerPartitioner;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class StellfluxStellflowProducerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(StellfluxStellflowProducerAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldCreateProducerWhenBootstrapServersConfigured() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.producer.bootstrap-servers=127.0.0.1:9092,127.0.0.1:9093",
                        "stellflux.stellflow.producer.client-id=order-producer",
                        "stellflux.stellflow.producer.network-threads=2",
                        "stellflux.stellflow.producer.request-timeout=3s",
                        "stellflux.stellflow.producer.retry.max-attempts=5",
                        "stellflux.stellflow.producer.retry.backoff=200ms",
                        "stellflux.stellflow.producer.producer.acks=1",
                        "stellflux.stellflow.producer.producer.timeout-ms=1500",
                        "stellflux.stellflow.producer.producer.max-batch-records=32",
                        "stellflux.stellflow.producer.producer.partitioner=round-robin")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellflowProducer.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowProducerFactory.class);
                            assertThat(context)
                                    .hasBean("stellfluxStellflowProducerClientFactory")
                                    .hasBean("stellfluxStellflowProducerClientOptions");

                            StellflowClientOptions options =
                                    context.getBean(
                                            "stellfluxStellflowProducerClientOptions",
                                            StellflowClientOptions.class);
                            assertThat(options.bootstrapServers()).hasSize(2);
                            assertThat(options.clientId()).isEqualTo("order-producer");
                            assertThat(options.networkThreads()).isEqualTo(2);
                            assertThat(options.requestTimeout()).hasSeconds(3);
                            assertThat(options.retryPolicy().maxAttempts()).isEqualTo(5);
                            assertThat(options.retryPolicy().backoff().toMillis()).isEqualTo(200);
                            assertThat(options.producerOptions().acks()).isEqualTo((short) 1);
                            assertThat(options.producerOptions().timeoutMs()).isEqualTo(1500);
                            assertThat(options.producerOptions().maxBatchRecords()).isEqualTo(32);
                            assertThat(options.producerOptions().partitioner())
                                    .isInstanceOf(RoundRobinProducerPartitioner.class);
                        });
    }

    @Test
    void shouldUseCustomPartitionerBeanWhenConfigured() {
        contextRunner
                .withUserConfiguration(CustomPartitionerConfiguration.class)
                .withPropertyValues(
                        "stellflux.stellflow.producer.bootstrap-servers=127.0.0.1:9092",
                        "stellflux.stellflow.producer.producer.partitioner-bean-name=testPartitioner")
                .run(
                        context -> {
                            StellflowClientOptions options =
                                    context.getBean(
                                            "stellfluxStellflowProducerClientOptions",
                                            StellflowClientOptions.class);
                            assertThat(options.producerOptions().partitioner())
                                    .isSameAs(context.getBean("testPartitioner"));
                        });
    }

    @Test
    void shouldSkipProducerWhenBootstrapServersMissing() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(StellflowProducer.class);
                    assertThat(context).doesNotHaveBean(StellflowClientFactory.class);
                    assertThat(context).doesNotHaveBean(StellfluxStellflowProducerFactory.class);
                    assertThat(context).hasNotFailed();
                });
    }

    @Test
    void shouldSkipProducerWhenDisabled() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.producer.enabled=false",
                        "stellflux.stellflow.producer.bootstrap-servers=127.0.0.1:9092")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellflowProducer.class);
                            assertThat(context).doesNotHaveBean(StellflowClientFactory.class);
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
