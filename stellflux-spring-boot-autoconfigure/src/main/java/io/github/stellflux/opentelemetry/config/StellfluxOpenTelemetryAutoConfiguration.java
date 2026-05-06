package io.github.stellflux.opentelemetry.config;

import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetrySdk;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;

@AutoConfiguration
@ConditionalOnClass(OpenTelemetry.class)
@EnableConfigurationProperties(StellfluxOpenTelemetryProperties.class)
@ConditionalOnProperty(
        prefix = "stellflux.opentelemetry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class StellfluxOpenTelemetryAutoConfiguration {

    private static final String LOG_CLASS_NAME = "io.github.stellflux.log.StellfluxLoggerFactory";
    private static final String METRICS_CLASS_NAME =
            "io.github.stellflux.metrics.StellfluxMeterFactory";
    private static final String TRACES_CLASS_NAME =
            "io.github.stellflux.traces.StellfluxTracerFactory";

    /**
     * 初始化全局唯一的 OpenTelemetry 运行时。
     *
     * @param applicationContext Spring 上下文
     * @param properties OpenTelemetry 配置
     * @return OpenTelemetry 运行时
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public StellfluxOpenTelemetryRuntime stellfluxOpenTelemetryRuntime(
            ApplicationContext applicationContext, StellfluxOpenTelemetryProperties properties) {
        ClassLoader classLoader = applicationContext.getClassLoader();
        StellfluxOpenTelemetryProperties.ResourceProperties resource = properties.getResource();
        io.github.stellflux.opentelemetry.config.StellfluxOpenTelemetryConfig config =
                io.github.stellflux.opentelemetry.config.StellfluxOpenTelemetryConfig.builder()
                        .serviceName(firstNonBlank(resource.getServiceName(), "unknown-service"))
                        .serviceNamespace(firstNonBlank(resource.getServiceNamespace(), "default"))
                        .serviceVersion(firstNonBlank(resource.getServiceVersion(), "unknown"))
                        .serviceInstanceId(firstNonBlank(resource.getServiceInstanceId()))
                        .environment(firstNonBlank(resource.getDeploymentEnvironmentName(), "dev"))
                        .cluster(resource.getK8sClusterName())
                        .region(resource.getCloudRegion())
                        .zone(resource.getCloudAvailabilityZone())
                        .hostName(resource.getHostName())
                        .hostIp(resource.getHostIp())
                        .nodeName(resource.getK8sNodeName())
                        .k8sNamespace(resource.getK8sNamespaceName())
                        .podName(resource.getK8sPodName())
                        .podUid(resource.getK8sPodUid())
                        .podIp(resource.getK8sPodIp())
                        .containerName(resource.getK8sContainerName())
                        .registerGlobal(properties.isRegisterGlobal())
                        .endpoint(properties.getEndpoint())
                        .protocol(properties.getProtocol())
                        .logsOutput(properties.getLogsOutput())
                        .logsFormat(properties.getLogsFormat())
                        .enableCaller(properties.isEnableCaller())
                        .enableStacktrace(properties.isEnableStacktrace())
                        .batchTimeout(properties.getBatchTimeout())
                        .exportTimeout(properties.getExportTimeout())
                        .metricExportInterval(properties.getMetricExportInterval())
                        .maxBatchSize(properties.getMaxBatchSize())
                        .maxQueueSize(properties.getMaxQueueSize())
                        .traceSampleRatio(properties.getTraceSampleRatio())
                        .fallbackFilePath(properties.getFallbackFilePath())
                        .retry(
                                io.github.stellflux.opentelemetry.config.RetryConfig.builder()
                                        .enabled(properties.getRetry().isEnabled())
                                        .initialInterval(properties.getRetry().getInitialInterval())
                                        .maxInterval(properties.getRetry().getMaxInterval())
                                        .maxElapsedTime(properties.getRetry().getMaxElapsedTime())
                                        .build())
                        .headers(new java.util.LinkedHashMap<>(properties.getHeaders()))
                        .resourceAttributes(new java.util.LinkedHashMap<>(properties.getResourceAttributes()))
                        .logsEnabled(
                                resolveSignalEnabled(
                                        properties.getLogs().getEnabled(), classLoader, LOG_CLASS_NAME))
                        .metricsEnabled(
                                resolveSignalEnabled(
                                        properties.getMetrics().getEnabled(), classLoader, METRICS_CLASS_NAME))
                        .tracesEnabled(
                                resolveSignalEnabled(
                                        properties.getTraces().getEnabled(), classLoader, TRACES_CLASS_NAME))
                        .build();
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

    private boolean resolveSignalEnabled(
            Boolean explicitValue, ClassLoader classLoader, String className) {
        if (explicitValue != null) {
            return explicitValue;
        }
        return ClassUtils.isPresent(className, classLoader);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
