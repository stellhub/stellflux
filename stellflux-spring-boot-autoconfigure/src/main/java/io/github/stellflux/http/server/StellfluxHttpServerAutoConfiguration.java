package io.github.stellflux.http.server;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.opentelemetry.api.OpenTelemetry;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

/** HTTP server auto configuration. */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(StellfluxHttpServerProperties.class)
public class StellfluxHttpServerAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxHttpServerAutoConfiguration.class.getName());

    @Bean
    @ConditionalOnMissingBean
    public StellfluxHttpRouteTemplateResolver stellfluxHttpRouteTemplateResolver() {
        return new StellfluxHttpRouteTemplateResolver();
    }

    /**
     * 注册 HTTP Server 标记 Bean。
     *
     * @return HTTP Server 标记 Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxHttpServerMarker stellfluxHttpServerMarker() {
        return new StellfluxHttpServerMarker();
    }

    @Bean
    @ConditionalOnBean(OpenTelemetry.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "stellflux.http.server.telemetry",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public StellfluxHttpServerTelemetryFilter stellfluxHttpServerTelemetryFilter(
            OpenTelemetry openTelemetry,
            StellfluxHttpRouteTemplateResolver routeTemplateResolver,
            StellfluxHttpServerProperties properties) {
        return new StellfluxHttpServerTelemetryFilter(
                openTelemetry, routeTemplateResolver, properties.getTelemetry());
    }

    @Bean
    @ConditionalOnBean(StellfluxHttpServerTelemetryFilter.class)
    @ConditionalOnMissingBean(name = "stellfluxHttpServerTelemetryFilterRegistration")
    public FilterRegistrationBean<StellfluxHttpServerTelemetryFilter>
            stellfluxHttpServerTelemetryFilterRegistration(
                    StellfluxHttpServerTelemetryFilter telemetryFilter,
                    StellfluxHttpServerProperties properties) {
        FilterRegistrationBean<StellfluxHttpServerTelemetryFilter> registration =
                new FilterRegistrationBean<>();
        registration.setFilter(telemetryFilter);
        registration.setOrder(properties.getTelemetry().getFilterOrder());
        registration.addUrlPatterns(properties.getTelemetry().getUrlPatterns().toArray(String[]::new));
        return registration;
    }

    /**
     * 记录 HTTP 服务端 starter 启动日志。
     *
     * @param properties HTTP 服务端配置
     * @param telemetryFilterProvider 遥测过滤器提供者
     * @return 启动日志探针
     */
    @Bean("stellfluxHttpServerStarterStartupLogger")
    public SmartInitializingSingleton stellfluxHttpServerStarterStartupLogger(
            StellfluxHttpServerProperties properties,
            ObjectProvider<StellfluxHttpServerTelemetryFilter> telemetryFilterProvider,
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule(
                        "stellflux-spring-boot-autoconfigure", StellfluxHttpServerAutoConfiguration.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-http-server started successfully"
                                    + ", servletType=SERVLET"
                                    + ", telemetryFilterEnabled="
                                    + (telemetryFilterProvider.getIfAvailable() != null)
                                    + ", telemetryUrlPatterns="
                                    + properties.getTelemetry().getUrlPatterns()
                                    + ", telemetryExcludedPaths="
                                    + properties.getTelemetry().getExcludedPaths());
        };
    }
}
