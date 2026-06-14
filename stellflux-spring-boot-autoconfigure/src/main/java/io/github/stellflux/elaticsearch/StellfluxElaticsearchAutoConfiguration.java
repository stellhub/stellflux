package io.github.stellflux.elaticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import java.util.logging.Logger;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Elaticsearch auto configuration. */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({
    ElasticsearchClient.class,
    RestClient.class,
    StellfluxElaticsearchFactory.class
})
@ConditionalOnBean(OpenTelemetry.class)
@EnableConfigurationProperties(StellfluxElaticsearchProperties.class)
public class StellfluxElaticsearchAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxElaticsearchAutoConfiguration.class.getName());

    /**
     * 注册 Elaticsearch 客户端工厂。
     *
     * @param openTelemetry OpenTelemetry 实例
     * @return Elaticsearch 客户端工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxElaticsearchFactory stellfluxElaticsearchFactory(OpenTelemetry openTelemetry) {
        return new StellfluxElaticsearchFactory(openTelemetry);
    }

    /**
     * 注册底层 RestClient。
     *
     * @param factory Elaticsearch 客户端工厂
     * @param properties Elaticsearch 配置
     * @return RestClient
     */
    @Bean
    @ConditionalOnMissingBean
    public RestClient stellfluxElaticsearchRestClient(
            StellfluxElaticsearchFactory factory, StellfluxElaticsearchProperties properties) {
        return factory.createRestClient(properties.toOptions());
    }

    /**
     * 注册 ElasticsearchTransport。
     *
     * @param factory Elaticsearch 客户端工厂
     * @param restClient 底层 RestClient
     * @return ElasticsearchTransport
     */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchTransport stellfluxElaticsearchTransport(
            StellfluxElaticsearchFactory factory, RestClient restClient) {
        return factory.createTransport(restClient);
    }

    /**
     * 注册官方同步客户端。
     *
     * @param factory Elaticsearch 客户端工厂
     * @param transport ElasticsearchTransport
     * @return ElasticsearchClient
     */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchClient elasticsearchClient(
            StellfluxElaticsearchFactory factory, ElasticsearchTransport transport) {
        return factory.createElasticsearchClient(transport);
    }

    /**
     * 注册官方异步客户端。
     *
     * @param factory Elaticsearch 客户端工厂
     * @param transport ElasticsearchTransport
     * @return ElasticsearchAsyncClient
     */
    @Bean
    @ConditionalOnMissingBean
    public ElasticsearchAsyncClient elasticsearchAsyncClient(
            StellfluxElaticsearchFactory factory, ElasticsearchTransport transport) {
        return factory.createElasticsearchAsyncClient(transport);
    }

    /**
     * 注册带 Stellflux telemetry 的 Elaticsearch 客户端门面。
     *
     * @param factory Elaticsearch 客户端工厂
     * @param client 官方同步客户端
     * @param asyncClient 官方异步客户端
     * @param properties Elaticsearch 配置
     * @return Stellflux Elaticsearch 客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxElaticsearchClient stellfluxElaticsearchClient(
            StellfluxElaticsearchFactory factory,
            ElasticsearchClient client,
            ElasticsearchAsyncClient asyncClient,
            StellfluxElaticsearchProperties properties) {
        return factory.createTelemetryClient(client, asyncClient, properties.toOptions());
    }

    /**
     * 记录 Elaticsearch starter 启动日志。
     *
     * @param properties Elaticsearch 配置
     * @param moduleInfoMeterProvider 模块指标注册器
     * @return 启动日志探针
     */
    @Bean("stellfluxElaticsearchStarterStartupLogger")
    public SmartInitializingSingleton stellfluxElaticsearchStarterStartupLogger(
            StellfluxElaticsearchProperties properties,
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule(
                        "stellflux-elaticsearch", StellfluxElaticsearchFactory.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-elaticsearch started successfully"
                                    + ", endpoints="
                                    + properties.getEndpoints()
                                    + ", telemetry=true");
        };
    }
}
