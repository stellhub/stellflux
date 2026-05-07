package io.github.stellflux.http.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.http.client.annotation.OkHttpClient;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;

class StellfluxOkHttpClientBeanDefinitionRegistryPostProcessorTest {

    @Test
    void shouldRegisterHttpClientBeanForAnnotatedInterface() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TestApplication.class);
        context.refresh();

        try {
            assertThat(context.containsBean("demoHttpClient")).isTrue();
            assertThat(context.getBean("demoHttpClient")).isInstanceOf(StellfluxHttpClient.class);
        } finally {
            context.close();
        }
    }

    @SpringBootConfiguration
    @AutoConfigurationPackage
    @org.springframework.boot.autoconfigure.ImportAutoConfiguration(
            StellfluxHttpClientAutoConfiguration.class)
    static class TestApplication {

        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }
    }

    @OkHttpClient(beanName = "demoHttpClient", baseUrl = "http://127.0.0.1:18080")
    interface DemoHttpClient {}
}
