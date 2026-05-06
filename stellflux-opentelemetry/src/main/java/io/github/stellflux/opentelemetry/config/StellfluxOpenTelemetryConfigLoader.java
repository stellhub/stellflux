package io.github.stellflux.opentelemetry.config;

import io.github.stellflux.opentelemetry.internal.EnvParsers;
import java.util.Map;

/** 从环境变量装配 OpenTelemetry 配置。 */
public final class StellfluxOpenTelemetryConfigLoader {

    private StellfluxOpenTelemetryConfigLoader() {}

    /**
     * 使用当前进程环境变量加载配置。
     *
     * @return OpenTelemetry 配置
     */
    public static StellfluxOpenTelemetryConfig load() {
        return load(System.getenv());
    }

    /**
     * 使用显式环境变量加载配置。
     *
     * @param env 环境变量
     * @return OpenTelemetry 配置
     */
    public static StellfluxOpenTelemetryConfig load(Map<String, String> env) {
        Map<String, String> otelResourceAttributes =
                EnvParsers.parseOtelResourceAttributes(env.get("OTEL_RESOURCE_ATTRIBUTES"));
        return StellfluxOpenTelemetryConfig.builder()
                .serviceName(
                        firstNonBlank(
                                env.get("stellflux_OTEL_SERVICE_NAME"),
                                env.get("OTEL_SERVICE_NAME"),
                                env.get("STELLAR_APP_NAME"),
                                "unknown-service"))
                .serviceNamespace(
                        firstNonBlank(
                                env.get("stellflux_OTEL_SERVICE_NAMESPACE"),
                                env.get("STELLAR_APP_NAMESPACE"),
                                "default"))
                .serviceVersion(
                        firstNonBlank(
                                env.get("stellflux_OTEL_SERVICE_VERSION"),
                                env.get("STELLAR_APP_VERSION"),
                                "unknown"))
                .serviceInstanceId(
                        firstNonBlank(
                                env.get("stellflux_OTEL_SERVICE_INSTANCE_ID"), env.get("STELLAR_APP_INSTANCE_ID")))
                .environment(
                        firstNonBlank(
                                env.get("stellflux_OTEL_ENVIRONMENT"),
                                otelResourceAttributes.get("deployment.environment.name"),
                                env.get("STELLAR_ENV"),
                                "dev"))
                .cluster(firstNonBlank(env.get("stellflux_OTEL_CLUSTER"), env.get("STELLAR_CLUSTER")))
                .region(firstNonBlank(env.get("stellflux_OTEL_REGION"), env.get("STELLAR_REGION")))
                .zone(firstNonBlank(env.get("stellflux_OTEL_ZONE"), env.get("STELLAR_ZONE")))
                .idc(firstNonBlank(env.get("stellflux_OTEL_IDC"), env.get("STELLAR_IDC")))
                .hostName(firstNonBlank(env.get("stellflux_OTEL_HOST_NAME"), env.get("STELLAR_HOST_NAME")))
                .hostIp(firstNonBlank(env.get("stellflux_OTEL_HOST_IP"), env.get("STELLAR_HOST_IP")))
                .nodeName(firstNonBlank(env.get("stellflux_OTEL_NODE_NAME"), env.get("STELLAR_NODE_NAME")))
                .k8sNamespace(
                        firstNonBlank(
                                env.get("stellflux_OTEL_K8S_NAMESPACE"), env.get("STELLAR_K8S_NAMESPACE")))
                .podName(firstNonBlank(env.get("stellflux_OTEL_POD_NAME"), env.get("STELLAR_POD_NAME")))
                .podUid(firstNonBlank(env.get("stellflux_OTEL_POD_UID")))
                .podIp(firstNonBlank(env.get("stellflux_OTEL_POD_IP"), env.get("STELLAR_POD_IP")))
                .containerName(
                        firstNonBlank(
                                env.get("stellflux_OTEL_CONTAINER_NAME"), env.get("STELLAR_CONTAINER_NAME")))
                .logsEnabled(EnvParsers.parseBoolean(env.get("stellflux_OTEL_LOGS_ENABLED"), true))
                .metricsEnabled(EnvParsers.parseBoolean(env.get("stellflux_OTEL_METRICS_ENABLED"), true))
                .tracesEnabled(EnvParsers.parseBoolean(env.get("stellflux_OTEL_TRACES_ENABLED"), true))
                .registerGlobal(EnvParsers.parseBoolean(env.get("stellflux_OTEL_REGISTER_GLOBAL"), false))
                .endpoint(
                        firstNonBlank(
                                env.get("stellflux_OTEL_ENDPOINT"),
                                env.get("OTEL_EXPORTER_OTLP_ENDPOINT"),
                                "http://localhost:4317"))
                .protocol(firstNonBlank(env.get("stellflux_OTEL_PROTOCOL"), "grpc"))
                .logsOutput(firstNonBlank(env.get("stellflux_OTEL_LOGS_OUTPUT"), "otlp"))
                .logsFormat(firstNonBlank(env.get("stellflux_OTEL_LOGS_FORMAT"), "json"))
                .enableCaller(EnvParsers.parseBoolean(env.get("stellflux_OTEL_ENABLE_CALLER"), true))
                .enableStacktrace(
                        EnvParsers.parseBoolean(env.get("stellflux_OTEL_ENABLE_STACKTRACE"), true))
                .batchTimeout(
                        EnvParsers.parseDuration(
                                env.get("stellflux_OTEL_BATCH_TIMEOUT"),
                                StellfluxOpenTelemetryConfig.builder().build().getBatchTimeout()))
                .exportTimeout(
                        EnvParsers.parseDuration(
                                env.get("stellflux_OTEL_EXPORT_TIMEOUT"),
                                StellfluxOpenTelemetryConfig.builder().build().getExportTimeout()))
                .metricExportInterval(
                        EnvParsers.parseDuration(
                                env.get("stellflux_OTEL_METRIC_EXPORT_INTERVAL"),
                                StellfluxOpenTelemetryConfig.builder().build().getMetricExportInterval()))
                .maxBatchSize(
                        EnvParsers.parseInt(
                                env.get("stellflux_OTEL_MAX_BATCH_SIZE"),
                                StellfluxOpenTelemetryConfig.builder().build().getMaxBatchSize()))
                .maxQueueSize(
                        EnvParsers.parseInt(
                                env.get("stellflux_OTEL_MAX_QUEUE_SIZE"),
                                StellfluxOpenTelemetryConfig.builder().build().getMaxQueueSize()))
                .traceSampleRatio(
                        EnvParsers.parseDouble(
                                env.get("stellflux_OTEL_TRACE_SAMPLE_RATIO"),
                                StellfluxOpenTelemetryConfig.builder().build().getTraceSampleRatio()))
                .fallbackFilePath(
                        firstNonBlank(
                                env.get("stellflux_OTEL_FALLBACK_FILE_PATH"),
                                StellfluxOpenTelemetryConfig.builder().build().getFallbackFilePath()))
                .retry(
                        RetryConfig.builder()
                                .enabled(EnvParsers.parseBoolean(env.get("stellflux_OTEL_RETRY_ENABLED"), true))
                                .initialInterval(
                                        EnvParsers.parseDuration(
                                                env.get("stellflux_OTEL_RETRY_INITIAL_INTERVAL"),
                                                RetryConfig.builder().build().getInitialInterval()))
                                .maxInterval(
                                        EnvParsers.parseDuration(
                                                env.get("stellflux_OTEL_RETRY_MAX_INTERVAL"),
                                                RetryConfig.builder().build().getMaxInterval()))
                                .maxElapsedTime(
                                        EnvParsers.parseDuration(
                                                env.get("stellflux_OTEL_RETRY_MAX_ELAPSED_TIME"),
                                                RetryConfig.builder().build().getMaxElapsedTime()))
                                .build())
                .headers(EnvParsers.parseKeyValuePairs(env.get("stellflux_OTEL_HEADERS")))
                .resourceAttributes(
                        mergeResourceAttributes(
                                EnvParsers.parseKeyValuePairs(env.get("stellflux_OTEL_RESOURCE_ATTRIBUTES")),
                                otelResourceAttributes))
                .build();
    }

    private static Map<String, String> mergeResourceAttributes(
            Map<String, String> explicitAttributes, Map<String, String> otelResourceAttributes) {
        Map<String, String> merged = new java.util.LinkedHashMap<>(explicitAttributes);
        merged.putAll(otelResourceAttributes);
        return merged;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
