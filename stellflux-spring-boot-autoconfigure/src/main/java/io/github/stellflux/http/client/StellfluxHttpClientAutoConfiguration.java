package io.github.stellflux.http.client;

import io.opentelemetry.api.OpenTelemetry;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** HTTP client auto configuration. */
@AutoConfiguration
@ConditionalOnClass({OkHttpClient.class, StellfluxHttpClient.class})
@EnableConfigurationProperties(StellfluxHttpClientProperties.class)
public class StellfluxHttpClientAutoConfiguration {

    /**
     * 注册 HTTP Client 工厂。
     *
     * @return HTTP Client 工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxHttpClientFactory stellfluxHttpClientFactory(OpenTelemetry openTelemetry) {
        return new StellfluxHttpClientFactory(openTelemetry);
    }

    /**
     * 注册 OkHttpClient 注解扫描器。
     *
     * @return 注解扫描器
     */
    @Bean
    public static StellfluxOkHttpClientBeanDefinitionRegistryPostProcessor
            stellfluxOkHttpClientBeanDefinitionRegistryPostProcessor() {
        return new StellfluxOkHttpClientBeanDefinitionRegistryPostProcessor();
    }

}
