package io.github.stellflux.opentelemetry;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetrySdk;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import java.util.logging.Logger;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.ConfigurableEnvironment;

@AutoConfiguration
@ConditionalOnClass(OpenTelemetry.class)
@EnableConfigurationProperties(StellfluxOpenTelemetryProperties.class)
@Conditional(StellfluxOpenTelemetryEnabledCondition.class)
public class StellfluxOpenTelemetryAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxOpenTelemetryAutoConfiguration.class.getName());

    /**
     * 初始化全局唯一的 OpenTelemetry 运行时。
     *
     * @param applicationContext Spring 上下文
     * @param environment OpenTelemetry 配置
     * @return OpenTelemetry 运行时
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public StellfluxOpenTelemetryRuntime stellfluxOpenTelemetryRuntime(
            ApplicationContext applicationContext, ConfigurableEnvironment environment) {
        StellfluxOpenTelemetryPropertyResolver resolver =
                new StellfluxOpenTelemetryPropertyResolver(
                        environment, applicationContext.getClassLoader());
        StellfluxOpenTelemetryConfig config = resolver.resolve();
        return StellfluxOpenTelemetrySdk.create(config);
    }

    /**
     * 暴露 OpenTelemetrySdk Bean。
     *
     * @param runtime OpenTelemetry 运行时
     * @return OpenTelemetrySdk
     */
    @Bean(
            name = {"openTelemetry", "openTelemetrySdk"},
            destroyMethod = "")
    @ConditionalOnMissingBean
    public OpenTelemetrySdk openTelemetrySdk(StellfluxOpenTelemetryRuntime runtime) {
        return runtime.getOpenTelemetrySdk();
    }

    /**
     * 暴露 Resource Bean。
     *
     * @param runtime OpenTelemetry 运行时
     * @return Resource
     */
    @Bean
    @ConditionalOnMissingBean
    public Resource otelResource(StellfluxOpenTelemetryRuntime runtime) {
        return runtime.getResource();
    }

    /**
     * 暴露 Stellflux 模块清单指标注册器。
     *
     * @param openTelemetry OpenTelemetry 实例
     * @return 模块清单指标注册器
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public StellfluxModuleInfoMeter stellfluxModuleInfoMeter(OpenTelemetry openTelemetry) {
        return new StellfluxModuleInfoMeter(openTelemetry);
    }

    /**
     * 记录 OpenTelemetry starter 启动日志。
     *
     * @param runtime OpenTelemetry 运行时
     * @return 启动日志探针
     */
    @Bean("stellfluxOpenTelemetryStarterStartupLogger")
    public SmartInitializingSingleton stellfluxOpenTelemetryStarterStartupLogger(
            StellfluxOpenTelemetryRuntime runtime, StellfluxModuleInfoMeter moduleInfoMeter) {
        return () ->
                {
                    moduleInfoMeter.registerModule(
                            "stellflux-opentelemetry", StellfluxOpenTelemetrySdk.class);
                    moduleInfoMeter.registerModule(
                            "stellflux-spring-boot-autoconfigure",
                            StellfluxOpenTelemetryAutoConfiguration.class);
                    LOGGER.info(
                            () ->
                                    "Starter stellflux-spring-boot-starter-opentelemetry started successfully"
                                            + ", enabled=true"
                                            + ", serviceName=" + runtime.getConfig().getServiceName()
                                            + ", serviceNamespace="
                                            + runtime.getConfig().getServiceNamespace()
                                            + ", endpoint=" + runtime.getConfig().getEndpoint()
                                            + ", protocol=" + runtime.getConfig().getProtocol()
                                            + ", registerGlobal=" + runtime.getConfig().isRegisterGlobal()
                                            + ", logsEnabled=" + runtime.getConfig().isLogsEnabled()
                                            + ", metricsEnabled=" + runtime.getConfig().isMetricsEnabled()
                                            + ", tracesEnabled=" + runtime.getConfig().isTracesEnabled()
                                            + ", metricExportInterval="
                                            + runtime.getConfig().getMetricExportInterval()
                                            + ", traceSampleRatio="
                                            + runtime.getConfig().getTraceSampleRatio());
                };
    }

    /**
     * 记录 metrics starter 启动日志。
     *
     * @param runtime OpenTelemetry 运行时
     * @return 启动日志探针
     */
    @Bean("stellfluxMetricsStarterStartupLogger")
    public SmartInitializingSingleton stellfluxMetricsStarterStartupLogger(
            StellfluxOpenTelemetryRuntime runtime, StellfluxModuleInfoMeter moduleInfoMeter) {
        return () -> {
            if (runtime.getConfig().isMetricsEnabled()) {
                moduleInfoMeter.registerModule("stellflux-metrics", StellfluxModuleInfoMeter.class);
                LOGGER.info(
                        () ->
                                "Starter stellflux-spring-boot-starter-metrics started successfully"
                                        + ", exportInterval=" + runtime.getConfig().getMetricExportInterval()
                                        + ", endpoint=" + runtime.getConfig().getEndpoint()
                                        + ", protocol=" + runtime.getConfig().getProtocol());
            }
        };
    }

    /**
     * 记录 traces starter 启动日志。
     *
     * @param runtime OpenTelemetry 运行时
     * @return 启动日志探针
     */
    @Bean("stellfluxTracesStarterStartupLogger")
    public SmartInitializingSingleton stellfluxTracesStarterStartupLogger(
            StellfluxOpenTelemetryRuntime runtime, StellfluxModuleInfoMeter moduleInfoMeter) {
        return () -> {
            if (runtime.getConfig().isTracesEnabled()) {
                moduleInfoMeter.registerModule(
                        "stellflux-traces", io.github.stellflux.traces.StellfluxTracerFactory.class);
                LOGGER.info(
                        () ->
                                "Starter stellflux-spring-boot-starter-traces started successfully"
                                        + ", traceSampleRatio=" + runtime.getConfig().getTraceSampleRatio()
                                        + ", endpoint=" + runtime.getConfig().getEndpoint()
                                        + ", protocol=" + runtime.getConfig().getProtocol());
            }
        };
    }

}
