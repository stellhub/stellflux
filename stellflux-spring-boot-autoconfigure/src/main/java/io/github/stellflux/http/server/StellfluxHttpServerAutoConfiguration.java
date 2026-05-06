package io.github.stellflux.http.server;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.DispatcherServlet;

/** HTTP server auto configuration. */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "stellflux.http.server",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(StellfluxHttpServerProperties.class)
public class StellfluxHttpServerAutoConfiguration {

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
    public StellfluxHttpServerTelemetryFilter stellfluxHttpServerTelemetryFilter(
            OpenTelemetry openTelemetry, StellfluxHttpRouteTemplateResolver routeTemplateResolver) {
        return new StellfluxHttpServerTelemetryFilter(openTelemetry, routeTemplateResolver);
    }

    @Bean
    @ConditionalOnBean(StellfluxHttpServerTelemetryFilter.class)
    @ConditionalOnMissingBean(name = "stellfluxHttpServerTelemetryFilterRegistration")
    public FilterRegistrationBean<StellfluxHttpServerTelemetryFilter>
            stellfluxHttpServerTelemetryFilterRegistration(
                    StellfluxHttpServerTelemetryFilter telemetryFilter) {
        FilterRegistrationBean<StellfluxHttpServerTelemetryFilter> registration =
                new FilterRegistrationBean<>();
        registration.setFilter(telemetryFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
