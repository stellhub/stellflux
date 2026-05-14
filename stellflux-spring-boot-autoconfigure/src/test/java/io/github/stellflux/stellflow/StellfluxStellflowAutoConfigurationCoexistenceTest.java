package io.github.stellflux.stellflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.stellflow.consumer.StellfluxStellflowConsumerAutoConfiguration;
import io.github.stellflux.stellflow.consumer.StellfluxStellflowConsumerFactory;
import io.github.stellflux.stellflow.producer.StellfluxStellflowProducerAutoConfiguration;
import io.github.stellflux.stellflow.producer.StellfluxStellflowProducerFactory;
import io.github.stellhub.stellflow.sdk.client.StellflowClientFactory;
import io.github.stellhub.stellflow.sdk.client.StellflowClientOptions;
import io.github.stellhub.stellflow.sdk.consumer.StellflowConsumer;
import io.github.stellhub.stellflow.sdk.producer.StellflowProducer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StellfluxStellflowAutoConfigurationCoexistenceTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    StellfluxStellflowProducerAutoConfiguration.class,
                                    StellfluxStellflowConsumerAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldAllowProducerAndConsumerToCoexist() {
        contextRunner
                .withPropertyValues(
                        "stellflux.stellflow.producer.bootstrap-servers=127.0.0.1:9092",
                        "stellflux.stellflow.consumer.bootstrap-servers=127.0.0.1:9093",
                        "stellflux.stellflow.consumer.consumer.group-id=order-service")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(StellflowProducer.class);
                            assertThat(context).hasSingleBean(StellflowConsumer.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowProducerFactory.class);
                            assertThat(context).hasSingleBean(StellfluxStellflowConsumerFactory.class);
                            assertThat(context).getBeans(StellflowClientOptions.class).hasSize(2);
                            assertThat(context).getBeans(StellflowClientFactory.class).hasSize(2);
                            assertThat(context)
                                    .hasBean("stellfluxStellflowProducerClientOptions")
                                    .hasBean("stellfluxStellflowConsumerClientOptions");
                        });
    }
}
