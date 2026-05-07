package io.github.stellflux.http.client;

import io.opentelemetry.api.OpenTelemetry;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Logger;
import org.springframework.beans.factory.SmartInitializingSingleton;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** HTTP client auto configuration. */
@AutoConfiguration
@ConditionalOnClass({OkHttpClient.class, StellfluxHttpClient.class, StellfluxHttpClientFactory.class})
@EnableConfigurationProperties(StellfluxHttpClientProperties.class)
public class StellfluxHttpClientAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxHttpClientAutoConfiguration.class.getName());

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

    /**
     * 记录 HTTP Client starter 启动日志。
     *
     * @param properties HTTP Client 配置
     * @return 启动日志探针
     */
    @Bean("stellfluxHttpClientStarterStartupLogger")
    public SmartInitializingSingleton stellfluxHttpClientStarterStartupLogger(
            StellfluxHttpClientProperties properties) {
        return () ->
                LOGGER.info(
                        () ->
                                "Starter stellflux-spring-boot-starter-http-client started successfully"
                                        + ", configuredClients=" + properties.getClients().size()
                                        + ", clients=" + summarizeClients(properties.getClients()));
    }

    private String summarizeClients(Map<String, StellfluxHttpClientProperties.ClientProperties> clients) {
        if (clients == null || clients.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        clients.forEach(
                (serviceId, client) ->
                        joiner.add(
                                serviceId
                                        + "={mode=" + resolveMode(client.getBaseUrl())
                                        + ", namespace=" + safeText(client.getNamespace())
                                        + ", baseUrl=" + safeText(client.getBaseUrl())
                                        + ", loadBalancer=" + client.getLoadBalancer()
                                        + ", connectTimeoutMillis=" + client.getConnectTimeoutMillis()
                                        + ", readTimeoutMillis=" + client.getReadTimeoutMillis()
                                        + ", writeTimeoutMillis=" + client.getWriteTimeoutMillis()
                                        + ", callTimeoutMillis=" + client.getCallTimeoutMillis()
                                        + ", pingIntervalMillis=" + client.getPingIntervalMillis()
                                        + ", retryOnConnectionFailure=" + client.isRetryOnConnectionFailure()
                                        + ", followRedirects=" + client.isFollowRedirects()
                                        + ", followSslRedirects=" + client.isFollowSslRedirects()
                                        + "}"));
        return joiner.toString();
    }

    private String resolveMode(String baseUrl) {
        return baseUrl != null && !baseUrl.isBlank() ? "direct" : "discovery";
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<default>" : value;
    }
}
