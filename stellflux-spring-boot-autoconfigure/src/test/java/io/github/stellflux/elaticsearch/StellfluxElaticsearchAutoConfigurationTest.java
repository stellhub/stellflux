package io.github.stellflux.elaticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import io.opentelemetry.api.OpenTelemetry;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StellfluxElaticsearchAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(StellfluxElaticsearchAutoConfiguration.class))
                    .withBean(OpenTelemetry.class, OpenTelemetry::noop)
                    .withPropertyValues("stellflux.elaticsearch.endpoints=http://localhost:9200");

    @Test
    void shouldCreateElaticsearchClientsWithOpenTelemetry() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(StellfluxElaticsearchProperties.class);
                    assertThat(context).hasSingleBean(StellfluxElaticsearchFactory.class);
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context).hasSingleBean(ElasticsearchTransport.class);
                    assertThat(context).hasSingleBean(ElasticsearchClient.class);
                    assertThat(context).hasSingleBean(ElasticsearchAsyncClient.class);
                    assertThat(context).hasSingleBean(StellfluxElaticsearchClient.class);
                    assertThat(context.getBean(StellfluxElaticsearchClient.class).client())
                            .isSameAs(context.getBean(ElasticsearchClient.class));
                });
    }

    @Test
    void shouldUseExistingRestClient() {
        RestClient restClient = RestClient.builder(HttpHost.create("http://localhost:9200")).build();
        contextRunner
                .withBean(RestClient.class, () -> restClient)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(RestClient.class);
                            assertThat(context.getBean(RestClient.class)).isSameAs(restClient);
                            assertThat(context).hasSingleBean(StellfluxElaticsearchClient.class);
                        });
    }

    @Test
    void shouldSkipAutoConfigurationWhenOpenTelemetryBeanMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(StellfluxElaticsearchAutoConfiguration.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxElaticsearchFactory.class);
                            assertThat(context).doesNotHaveBean(StellfluxElaticsearchClient.class);
                            assertThat(context).hasNotFailed();
                        });
    }

    @Test
    void shouldSkipAutoConfigurationWhenElaticsearchClientIsMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(ElasticsearchClient.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(StellfluxElaticsearchFactory.class);
                            assertThat(context).doesNotHaveBean(StellfluxElaticsearchClient.class);
                            assertThat(context).hasNotFailed();
                        });
    }
}
