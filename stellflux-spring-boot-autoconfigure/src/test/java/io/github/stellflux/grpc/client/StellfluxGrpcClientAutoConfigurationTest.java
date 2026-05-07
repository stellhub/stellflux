package io.github.stellflux.grpc.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.ManagedChannel;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;

class StellfluxGrpcClientAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxGrpcClientAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop);

    @Test
    void shouldNotCreateDefaultManagedChannelBean() {
        this.contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StellfluxGrpcChannelFactory.class);
                    assertThat(context).doesNotHaveBean("stellfluxManagedChannel");
                    assertThat(context).doesNotHaveBean(ManagedChannel.class);
                });
    }

    @Test
    void shouldSkipAutoConfigurationWhenGrpcClientCoreClassIsMissing() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(StellfluxGrpcChannelFactory.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxGrpcChannelFactory.class);
                            assertThat(context).hasNotFailed();
                        });
    }
}
