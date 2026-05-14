package io.github.stellflux.stellflow.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellhub.stellflow.sdk.client.StellflowClientFactory;
import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StellfluxStellflowConsumerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(StellfluxStellflowConsumerAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldCreateConsumerWhenBootstrapServersAndGroupIdConfigured() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.consumer.bootstrap-servers=127.0.0.1:9092",
                        "stellflux.stellflow.consumer.client-id=order-consumer",
                        "stellflux.stellflow.consumer.network-threads=2",
                        "stellflux.stellflow.consumer.request-timeout=3s",
                        "stellflux.stellflow.consumer.retry.max-attempts=5",
                        "stellflux.stellflow.consumer.retry.backoff=200ms",
                        "stellflux.stellflow.consumer.consumer.group-id=order-service",
                        "stellflux.stellflow.consumer.consumer.member-id=member-a",
                        "stellflux.stellflow.consumer.consumer.session-timeout-ms=12000",
                        "stellflux.stellflow.consumer.consumer.heartbeat-interval=2s",
                        "stellflux.stellflow.consumer.consumer.fetch-max-bytes=65536",
                        "stellflux.stellflow.consumer.consumer.offset-commit-metadata=test")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellflowConsumer.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowConsumerFactory.class);
                            assertThat(context)
                                    .hasBean("stellfluxStellflowConsumerClientFactory")
                                    .hasBean("stellfluxStellflowConsumerClientOptions");

                            StellflowClientOptions options =
                                    context.getBean(
                                            "stellfluxStellflowConsumerClientOptions",
                                            StellflowClientOptions.class);
                            assertThat(options.bootstrapServers()).hasSize(1);
                            assertThat(options.clientId()).isEqualTo("order-consumer");
                            assertThat(options.networkThreads()).isEqualTo(2);
                            assertThat(options.requestTimeout()).hasSeconds(3);
                            assertThat(options.retryPolicy().maxAttempts()).isEqualTo(5);
                            assertThat(options.retryPolicy().backoff().toMillis()).isEqualTo(200);
                            assertThat(options.consumerOptions().groupId()).isEqualTo("order-service");
                            assertThat(options.consumerOptions().memberId()).isEqualTo("member-a");
                            assertThat(options.consumerOptions().sessionTimeoutMs()).isEqualTo(12000);
                            assertThat(options.consumerOptions().heartbeatInterval()).hasSeconds(2);
                            assertThat(options.consumerOptions().fetchMaxBytes()).isEqualTo(65536);
                            assertThat(options.consumerOptions().offsetCommitMetadata()).isEqualTo("test");
                        });
    }

    @Test
    void shouldSkipConsumerWhenBootstrapServersMissing() {
        contextRunner
                .withPropertyValues("stellflux.stellflow.consumer.consumer.group-id=order-service")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellflowConsumer.class);
                            assertThat(context).doesNotHaveBean(StellflowClientFactory.class);
                            assertThat(context).doesNotHaveBean(StellfluxStellflowConsumerFactory.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldSkipConsumerWhenGroupIdMissing() {
        contextRunner
                .withPropertyValues("stellflux.stellflow.consumer.bootstrap-servers=127.0.0.1:9092")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellflowConsumer.class);
                            assertThat(context).doesNotHaveBean(StellflowClientFactory.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldSkipConsumerWhenDisabled() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.consumer.enabled=false",
                        "stellflux.stellflow.consumer.bootstrap-servers=127.0.0.1:9092",
                        "stellflux.stellflow.consumer.consumer.group-id=order-service")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellflowConsumer.class);
                            assertThat(context).doesNotHaveBean(StellflowClientFactory.class);
                            assertThat(context).hasNotFailed();
                        });
    }
}
