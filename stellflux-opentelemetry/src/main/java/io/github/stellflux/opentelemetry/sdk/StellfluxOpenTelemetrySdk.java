package io.github.stellflux.opentelemetry.sdk;

import io.github.stellflux.opentelemetry.RetryConfig;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfigLoader;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfigValidator;
import io.github.stellflux.opentelemetry.internal.OpenTelemetryExporterFactory;
import io.github.stellflux.opentelemetry.internal.ResourceBuilderFactory;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Stellflux OpenTelemetry SDK 入口。 */
public final class StellfluxOpenTelemetrySdk {

    private static final Logger LOGGER = Logger.getLogger(StellfluxOpenTelemetrySdk.class.getName());

    private StellfluxOpenTelemetrySdk() {}

    /**
     * 从环境变量创建运行时。
     *
     * @return OpenTelemetry 运行时
     */
    public static StellfluxOpenTelemetryRuntime create() {
        return create(StellfluxOpenTelemetryConfigLoader.load());
    }

    /**
     * 从显式配置创建运行时。
     *
     * @param config OpenTelemetry 配置
     * @return OpenTelemetry 运行时
     */
    public static StellfluxOpenTelemetryRuntime create(StellfluxOpenTelemetryConfig config) {
        StellfluxOpenTelemetryConfigValidator.validate(config);
        Resource resource = ResourceBuilderFactory.create(config);
        OpenTelemetrySdkBuilder sdkBuilder =
                OpenTelemetrySdk.builder()
                        .setPropagators(
                                ContextPropagators.create(
                                        TextMapPropagator.composite(
                                                W3CTraceContextPropagator.getInstance(),
                                                W3CBaggagePropagator.getInstance())));

        SdkLoggerProvider loggerProvider = null;
        if (config.isLogsEnabled()) {
            SdkLoggerProviderBuilder loggerProviderBuilder =
                    SdkLoggerProvider.builder()
                            .setResource(resource)
                            .addLogRecordProcessor(
                                    BatchLogRecordProcessor.builder(
                                                    OpenTelemetryExporterFactory.createLogExporter(config))
                                            .setScheduleDelay(config.getBatchTimeout())
                                            .setExporterTimeout(config.getExportTimeout())
                                            .setMaxExportBatchSize(config.getMaxBatchSize())
                                            .setMaxQueueSize(config.getMaxQueueSize())
                                            .build());
            loggerProvider = loggerProviderBuilder.build();
            sdkBuilder.setLoggerProvider(loggerProvider);
        }

        SdkMeterProvider meterProvider = null;
        if (config.isMetricsEnabled()) {
            SdkMeterProviderBuilder meterProviderBuilder =
                    SdkMeterProvider.builder()
                            .setResource(resource)
                            .registerMetricReader(
                                    PeriodicMetricReader.builder(
                                                    OpenTelemetryExporterFactory.createMetricExporter(config))
                                            .setInterval(config.getMetricExportInterval())
                                            .build());
            meterProvider = meterProviderBuilder.build();
            sdkBuilder.setMeterProvider(meterProvider);
        }

        SdkTracerProvider tracerProvider = null;
        if (config.isTracesEnabled()) {
            SdkTracerProviderBuilder tracerProviderBuilder =
                    SdkTracerProvider.builder()
                            .setResource(resource)
                            .setSampler(
                                    Sampler.parentBased(Sampler.traceIdRatioBased(config.getTraceSampleRatio())))
                            .addSpanProcessor(
                                    BatchSpanProcessor.builder(
                                                    OpenTelemetryExporterFactory.createSpanExporter(config))
                                            .setScheduleDelay(config.getBatchTimeout())
                                            .setExporterTimeout(config.getExportTimeout())
                                            .setMaxExportBatchSize(config.getMaxBatchSize())
                                            .setMaxQueueSize(config.getMaxQueueSize())
                                            .build());
            tracerProvider = tracerProviderBuilder.build();
            sdkBuilder.setTracerProvider(tracerProvider);
        }

        OpenTelemetrySdk sdk = sdkBuilder.build();
        if (config.isRegisterGlobal()) {
            GlobalOpenTelemetry.set(sdk);
        }
        StellfluxOpenTelemetryRuntime runtime =
                new StellfluxOpenTelemetryRuntime(
                        config, sdk, resource, loggerProvider, meterProvider, tracerProvider);
        LOGGER.info(
                () ->
                        "OpenTelemetry initialized"
                                + ", config="
                                + describeConfig(config)
                                + ", resource="
                                + resource.getAttributes().asMap());
        return runtime;
    }

    private static String describeConfig(StellfluxOpenTelemetryConfig config) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("serviceName", config.getServiceName());
        values.put("serviceNamespace", config.getServiceNamespace());
        values.put("serviceVersion", config.getServiceVersion());
        values.put("serviceInstanceId", config.getServiceInstanceId());
        values.put("environment", config.getEnvironment());
        values.put("cluster", config.getCluster());
        values.put("region", config.getRegion());
        values.put("zone", config.getZone());
        values.put("idc", config.getIdc());
        values.put("hostName", config.getHostName());
        values.put("hostIp", config.getHostIp());
        values.put("nodeName", config.getNodeName());
        values.put("k8sNamespace", config.getK8sNamespace());
        values.put("podName", config.getPodName());
        values.put("podUid", config.getPodUid());
        values.put("podIp", config.getPodIp());
        values.put("containerName", config.getContainerName());
        values.put("logsEnabled", config.isLogsEnabled());
        values.put("metricsEnabled", config.isMetricsEnabled());
        values.put("tracesEnabled", config.isTracesEnabled());
        values.put("registerGlobal", config.isRegisterGlobal());
        values.put("endpoint", config.getEndpoint());
        values.put("protocol", config.getProtocol());
        values.put("logsOutput", config.getLogsOutput());
        values.put("logsFormat", config.getLogsFormat());
        values.put("enableCaller", config.isEnableCaller());
        values.put("enableStacktrace", config.isEnableStacktrace());
        values.put("batchTimeout", config.getBatchTimeout());
        values.put("exportTimeout", config.getExportTimeout());
        values.put("metricExportInterval", config.getMetricExportInterval());
        values.put("maxBatchSize", config.getMaxBatchSize());
        values.put("maxQueueSize", config.getMaxQueueSize());
        values.put("traceSampleRatio", config.getTraceSampleRatio());
        values.put("fallbackFilePath", config.getFallbackFilePath());
        values.put("retry", describeRetry(config.getRetry()));
        values.put("headers", maskSensitiveEntries(config.headerSnapshot()));
        values.put("resourceAttributes", config.resourceAttributeSnapshot());
        return values.toString();
    }

    private static Map<String, Object> describeRetry(RetryConfig retryConfig) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (retryConfig == null) {
            return values;
        }
        values.put("enabled", retryConfig.isEnabled());
        values.put("initialInterval", retryConfig.getInitialInterval());
        values.put("maxInterval", retryConfig.getMaxInterval());
        values.put("maxElapsedTime", retryConfig.getMaxElapsedTime());
        return values;
    }

    private static Map<String, String> maskSensitiveEntries(Map<String, String> values) {
        Map<String, String> masked = new LinkedHashMap<>();
        values.forEach(
                (key, v) -> {
                    if (key == null) {
                        return;
                    }
                    String normalizedKey = key.toLowerCase(Locale.ROOT);
                    if (normalizedKey.contains("authorization")
                            || normalizedKey.contains("token")
                            || normalizedKey.contains("secret")
                            || normalizedKey.contains("password")
                            || normalizedKey.contains("apikey")
                            || normalizedKey.contains("api-key")) {
                        masked.put(key, "******");
                        return;
                    }
                    masked.put(key, v);
                });
        return masked;
    }
}
