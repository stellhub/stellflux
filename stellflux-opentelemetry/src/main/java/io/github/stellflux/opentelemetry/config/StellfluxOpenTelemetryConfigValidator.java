package io.github.stellflux.opentelemetry.config;

/** OpenTelemetry 配置校验器。 */
public final class StellfluxOpenTelemetryConfigValidator {

    private StellfluxOpenTelemetryConfigValidator() {}

    /**
     * 校验配置合法性。
     *
     * @param config OpenTelemetry 配置
     */
    public static void validate(StellfluxOpenTelemetryConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (isBlank(config.getServiceName())) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        boolean requiresEndpoint =
                config.isMetricsEnabled()
                        || config.isTracesEnabled()
                        || (config.isLogsEnabled() && "otlp".equalsIgnoreCase(config.getLogsOutput()));
        if (requiresEndpoint && isBlank(config.getEndpoint())) {
            throw new IllegalArgumentException("endpoint must not be blank when signals are enabled");
        }
        if (config.getMaxBatchSize() <= 0 || config.getMaxQueueSize() <= 0) {
            throw new IllegalArgumentException("maxBatchSize and maxQueueSize must be positive");
        }
        if (config.getMaxBatchSize() > config.getMaxQueueSize()) {
            throw new IllegalArgumentException("maxBatchSize must not exceed maxQueueSize");
        }
        if (config.getTraceSampleRatio() < 0.0d || config.getTraceSampleRatio() > 1.0d) {
            throw new IllegalArgumentException("traceSampleRatio must be between 0 and 1");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
