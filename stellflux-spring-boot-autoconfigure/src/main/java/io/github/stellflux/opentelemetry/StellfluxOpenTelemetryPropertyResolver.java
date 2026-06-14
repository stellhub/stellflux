package io.github.stellflux.opentelemetry;

import io.github.stellflux.opentelemetry.internal.EnvParsers;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.ClassUtils;

/** OpenTelemetry 配置解析器。 */
final class StellfluxOpenTelemetryPropertyResolver {

    private static final String PREFIX = "stellflux.opentelemetry";
    private static final String RESOURCE_PREFIX = PREFIX + ".resource";
    private static final String RETRY_PREFIX = PREFIX + ".retry";
    private static final String HEADERS_PREFIX = PREFIX + ".headers";
    private static final String RESOURCE_ATTRIBUTES_PREFIX = PREFIX + ".resource-attributes";
    private static final String DEFAULT_PROPERTIES_SOURCE_NAME = "defaultProperties";
    private static final String CONFIGURATION_PROPERTIES_SOURCE_NAME = "configurationProperties";
    private static final ConversionService CONVERSION_SERVICE =
            ApplicationConversionService.getSharedInstance();

    private final ConfigurableEnvironment environment;
    private final ClassLoader classLoader;
    private final StellfluxOpenTelemetryProperties defaults = new StellfluxOpenTelemetryProperties();

    StellfluxOpenTelemetryPropertyResolver(
            ConfigurableEnvironment environment, ClassLoader classLoader) {
        this.environment = environment;
        this.classLoader = classLoader;
    }

    /**
     * 解析是否启用 OpenTelemetry。
     *
     * @return 是否启用
     */
    public boolean resolveEnabled() {
        return resolveBoolean(PREFIX + ".enabled", defaults.isEnabled());
    }

    /**
     * 解析最终 OpenTelemetry 配置。
     *
     * @return 最终配置
     */
    public StellfluxOpenTelemetryConfig resolve() {
        return StellfluxOpenTelemetryConfig.builder()
                .serviceName(resolveServiceName())
                .serviceNamespace(
                        resolveString(
                                RESOURCE_PREFIX + ".service-namespace",
                                "default",
                                "stellflux_OTEL_SERVICE_NAMESPACE",
                                "STELLAR_APP_NAMESPACE"))
                .serviceVersion(
                        resolveString(
                                RESOURCE_PREFIX + ".service-version",
                                "unknown",
                                "stellflux_OTEL_SERVICE_VERSION",
                                "STELLAR_APP_VERSION"))
                .serviceInstanceId(
                        resolveString(
                                RESOURCE_PREFIX + ".service-instance-id",
                                null,
                                "stellflux_OTEL_SERVICE_INSTANCE_ID",
                                "STELLAR_APP_INSTANCE_ID"))
                .environment(resolveEnvironment())
                .cluster(
                        resolveString(
                                RESOURCE_PREFIX + ".k8s-cluster-name",
                                null,
                                "stellflux_OTEL_CLUSTER",
                                "STELLAR_CLUSTER"))
                .region(
                        resolveString(
                                RESOURCE_PREFIX + ".cloud-region", null, "stellflux_OTEL_REGION", "STELLAR_REGION"))
                .zone(
                        resolveString(
                                RESOURCE_PREFIX + ".cloud-availability-zone",
                                null,
                                "stellflux_OTEL_ZONE",
                                "STELLAR_ZONE"))
                .idc(resolveString(RESOURCE_PREFIX + ".idc", null, "stellflux_OTEL_IDC", "STELLAR_IDC"))
                .hostName(
                        resolveString(
                                RESOURCE_PREFIX + ".host-name",
                                null,
                                "stellflux_OTEL_HOST_NAME",
                                "STELLAR_HOST_NAME"))
                .hostIp(
                        resolveString(
                                RESOURCE_PREFIX + ".host-ip", null, "stellflux_OTEL_HOST_IP", "STELLAR_HOST_IP"))
                .nodeName(
                        resolveString(
                                RESOURCE_PREFIX + ".k8s-node-name",
                                null,
                                "stellflux_OTEL_NODE_NAME",
                                "STELLAR_NODE_NAME"))
                .k8sNamespace(
                        resolveString(
                                RESOURCE_PREFIX + ".k8s-namespace-name",
                                null,
                                "stellflux_OTEL_K8S_NAMESPACE",
                                "STELLAR_K8S_NAMESPACE"))
                .podName(
                        resolveString(
                                RESOURCE_PREFIX + ".k8s-pod-name",
                                null,
                                "stellflux_OTEL_POD_NAME",
                                "STELLAR_POD_NAME"))
                .podUid(resolveString(RESOURCE_PREFIX + ".k8s-pod-uid", null, "stellflux_OTEL_POD_UID"))
                .podIp(
                        resolveString(
                                RESOURCE_PREFIX + ".k8s-pod-ip", null, "stellflux_OTEL_POD_IP", "STELLAR_POD_IP"))
                .containerName(
                        resolveString(
                                RESOURCE_PREFIX + ".k8s-container-name",
                                null,
                                "stellflux_OTEL_CONTAINER_NAME",
                                "STELLAR_CONTAINER_NAME"))
                .registerGlobal(resolveBoolean(PREFIX + ".register-global", defaults.isRegisterGlobal()))
                .endpoint(
                        resolveString(
                                PREFIX + ".endpoint",
                                defaults.getEndpoint(),
                                "stellflux_OTEL_ENDPOINT",
                                "OTEL_EXPORTER_OTLP_ENDPOINT"))
                .protocol(
                        resolveString(PREFIX + ".protocol", defaults.getProtocol(), "stellflux_OTEL_PROTOCOL"))
                .logsOutput(
                        resolveString(
                                PREFIX + ".logs-output", defaults.getLogsOutput(), "stellflux_OTEL_LOGS_OUTPUT"))
                .logsFormat(
                        resolveString(
                                PREFIX + ".logs-format", defaults.getLogsFormat(), "stellflux_OTEL_LOGS_FORMAT"))
                .enableCaller(resolveBoolean(PREFIX + ".enable-caller", defaults.isEnableCaller()))
                .enableStacktrace(
                        resolveBoolean(PREFIX + ".enable-stacktrace", defaults.isEnableStacktrace()))
                .batchTimeout(resolveDuration(PREFIX + ".batch-timeout", defaults.getBatchTimeout()))
                .exportTimeout(resolveDuration(PREFIX + ".export-timeout", defaults.getExportTimeout()))
                .metricExportInterval(
                        resolveDuration(PREFIX + ".metric-export-interval", defaults.getMetricExportInterval()))
                .maxBatchSize(resolveInteger(PREFIX + ".max-batch-size", defaults.getMaxBatchSize()))
                .maxQueueSize(resolveInteger(PREFIX + ".max-queue-size", defaults.getMaxQueueSize()))
                .traceSampleRatio(
                        resolveDouble(PREFIX + ".trace-sample-ratio", defaults.getTraceSampleRatio()))
                .fallbackFilePath(
                        resolveString(
                                PREFIX + ".fallback-file-path",
                                defaults.getFallbackFilePath(),
                                "stellflux_OTEL_FALLBACK_FILE_PATH"))
                .retry(resolveRetry())
                .headers(resolveHeaders())
                .resourceAttributes(resolveResourceAttributes())
                .logsEnabled(
                        resolveSignalEnabled(
                                PREFIX + ".logs.enabled",
                                "stellflux_OTEL_LOGS_ENABLED",
                                "io.github.stellflux.log.StellfluxLoggerFactory"))
                .metricsEnabled(
                        resolveSignalEnabled(
                                PREFIX + ".metrics.enabled",
                                "stellflux_OTEL_METRICS_ENABLED",
                                "io.github.stellflux.metrics.StellfluxMeterFactory"))
                .tracesEnabled(
                        resolveSignalEnabled(
                                PREFIX + ".traces.enabled",
                                "stellflux_OTEL_TRACES_ENABLED",
                                "io.github.stellflux.traces.StellfluxTracerFactory"))
                .build();
    }

    private RetryConfig resolveRetry() {
        StellfluxOpenTelemetryProperties.RetryProperties retryDefaults = defaults.getRetry();
        return RetryConfig.builder()
                .enabled(resolveBoolean(RETRY_PREFIX + ".enabled", retryDefaults.isEnabled()))
                .initialInterval(
                        resolveDuration(RETRY_PREFIX + ".initial-interval", retryDefaults.getInitialInterval()))
                .maxInterval(
                        resolveDuration(RETRY_PREFIX + ".max-interval", retryDefaults.getMaxInterval()))
                .maxElapsedTime(
                        resolveDuration(RETRY_PREFIX + ".max-elapsed-time", retryDefaults.getMaxElapsedTime()))
                .build();
    }

    private Map<String, String> resolveHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.putAll(resolveStructuredMap(PropertySourceLayer.DEFAULT, HEADERS_PREFIX));
        headers.putAll(resolveAliasMap(PropertySourceLayer.ENVIRONMENT, "stellflux_OTEL_HEADERS"));
        headers.putAll(resolveStructuredMap(PropertySourceLayer.ENVIRONMENT, HEADERS_PREFIX));
        headers.putAll(resolveAliasMap(PropertySourceLayer.COMMAND_LINE, "stellflux_OTEL_HEADERS"));
        headers.putAll(resolveStructuredMap(PropertySourceLayer.COMMAND_LINE, HEADERS_PREFIX));
        headers.putAll(resolveStructuredMap(PropertySourceLayer.CONFIGURATION, HEADERS_PREFIX));
        return headers;
    }

    private Map<String, String> resolveResourceAttributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.putAll(
                resolveStructuredMap(PropertySourceLayer.DEFAULT, RESOURCE_ATTRIBUTES_PREFIX));
        attributes.putAll(
                resolveAliasMap(
                        PropertySourceLayer.ENVIRONMENT,
                        "stellflux_OTEL_RESOURCE_ATTRIBUTES",
                        "OTEL_RESOURCE_ATTRIBUTES"));
        attributes.putAll(
                resolveStructuredMap(PropertySourceLayer.ENVIRONMENT, RESOURCE_ATTRIBUTES_PREFIX));
        attributes.putAll(
                resolveAliasMap(
                        PropertySourceLayer.COMMAND_LINE,
                        "stellflux_OTEL_RESOURCE_ATTRIBUTES",
                        "OTEL_RESOURCE_ATTRIBUTES"));
        attributes.putAll(
                resolveStructuredMap(PropertySourceLayer.COMMAND_LINE, RESOURCE_ATTRIBUTES_PREFIX));
        attributes.putAll(
                resolveStructuredMap(PropertySourceLayer.CONFIGURATION, RESOURCE_ATTRIBUTES_PREFIX));
        return attributes;
    }

    private String resolveServiceName() {
        return StellfluxServiceNameResolver.resolve(this.environment);
    }

    private String resolveEnvironment() {
        String environmentName =
                firstNonBlank(
                        findString(
                                PropertySourceLayer.CONFIGURATION,
                                RESOURCE_PREFIX + ".deployment-environment-name"),
                        findString(
                                PropertySourceLayer.COMMAND_LINE,
                                RESOURCE_PREFIX + ".deployment-environment-name",
                                "stellflux_OTEL_ENVIRONMENT",
                                "STELLAR_ENV"),
                        findString(
                                PropertySourceLayer.ENVIRONMENT,
                                RESOURCE_PREFIX + ".deployment-environment-name",
                                "stellflux_OTEL_ENVIRONMENT",
                                "STELLAR_ENV"),
                        findResourceAttribute(PropertySourceLayer.COMMAND_LINE, "deployment.environment.name"),
                        findResourceAttribute(PropertySourceLayer.ENVIRONMENT, "deployment.environment.name"),
                        findString(
                                PropertySourceLayer.DEFAULT, RESOURCE_PREFIX + ".deployment-environment-name"));
        return firstNonBlank(environmentName, "dev");
    }

    private boolean resolveSignalEnabled(String propertyKey, String aliasKey, String className) {
        Boolean resolved = resolveValue(Boolean.class, propertyKey, aliasKey);
        if (resolved != null) {
            return resolved;
        }
        return ClassUtils.isPresent(className, classLoader);
    }

    private boolean resolveBoolean(String propertyKey, boolean defaultValue, String... aliasKeys) {
        Boolean resolved = resolveValue(Boolean.class, propertyKey, aliasKeys);
        return resolved != null ? resolved : defaultValue;
    }

    private Duration resolveDuration(String propertyKey, Duration defaultValue, String... aliasKeys) {
        Duration resolved = resolveValue(Duration.class, propertyKey, aliasKeys);
        return resolved != null ? resolved : defaultValue;
    }

    private int resolveInteger(String propertyKey, int defaultValue, String... aliasKeys) {
        Integer resolved = resolveValue(Integer.class, propertyKey, aliasKeys);
        return resolved != null ? resolved : defaultValue;
    }

    private double resolveDouble(String propertyKey, double defaultValue, String... aliasKeys) {
        Double resolved = resolveValue(Double.class, propertyKey, aliasKeys);
        return resolved != null ? resolved : defaultValue;
    }

    private String resolveString(String propertyKey, String defaultValue, String... aliasKeys) {
        String resolved = resolveValue(String.class, propertyKey, aliasKeys);
        return firstNonBlank(resolved, defaultValue);
    }

    private <T> T resolveValue(Class<T> targetType, String propertyKey, String... aliasKeys) {
        T configurationValue = findValue(PropertySourceLayer.CONFIGURATION, targetType, propertyKey);
        if (configurationValue != null) {
            return configurationValue;
        }
        T commandValue =
                findValue(PropertySourceLayer.COMMAND_LINE, targetType, join(propertyKey, aliasKeys));
        if (commandValue != null) {
            return commandValue;
        }
        T environmentValue =
                findValue(PropertySourceLayer.ENVIRONMENT, targetType, join(propertyKey, aliasKeys));
        if (environmentValue != null) {
            return environmentValue;
        }
        return findValue(PropertySourceLayer.DEFAULT, targetType, propertyKey);
    }

    private String findResourceAttribute(PropertySourceLayer layer, String key) {
        return firstNonBlank(
                resolveAliasMap(layer, "stellflux_OTEL_RESOURCE_ATTRIBUTES", "OTEL_RESOURCE_ATTRIBUTES")
                        .get(key));
    }

    private Map<String, String> resolveAliasMap(PropertySourceLayer layer, String... aliasKeys) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (classify(propertySource) != layer) {
                continue;
            }
            Map<String, String> currentSource = new LinkedHashMap<>();
            for (String aliasKey : aliasKeys) {
                String raw = convertToString(propertySource.getProperty(aliasKey));
                if (raw != null) {
                    currentSource.putAll(EnvParsers.parseKeyValuePairs(raw));
                }
            }
            currentSource.forEach(resolved::putIfAbsent);
        }
        return resolved;
    }

    private Map<String, String> resolveStructuredMap(PropertySourceLayer layer, String prefix) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (classify(propertySource) != layer
                    || !(propertySource instanceof EnumerablePropertySource<?> enumerablePropertySource)) {
                continue;
            }
            Map<String, String> currentSource = new LinkedHashMap<>();
            for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                String normalizedPropertyName = normalizeMapPropertyName(propertyName);
                if (normalizedPropertyName == null || !normalizedPropertyName.startsWith(prefix + ".")) {
                    continue;
                }
                String mapKey = normalizedPropertyName.substring(prefix.length() + 1);
                String value = convertToString(propertySource.getProperty(propertyName));
                if (value != null) {
                    currentSource.put(mapKey, value);
                }
            }
            currentSource.forEach(resolved::putIfAbsent);
        }
        return resolved;
    }

    private String normalizeMapPropertyName(String propertyName) {
        if (propertyName == null || propertyName.isBlank()) {
            return null;
        }
        if (propertyName.startsWith(PREFIX + ".")) {
            return propertyName;
        }
        return null;
    }

    private String findString(PropertySourceLayer layer, String... keys) {
        return findValue(layer, String.class, keys);
    }

    private <T> T findValue(PropertySourceLayer layer, Class<T> targetType, String... keys) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (classify(propertySource) != layer) {
                continue;
            }
            for (String key : keys) {
                Object rawValue = propertySource.getProperty(key);
                if (rawValue == null) {
                    continue;
                }
                if (rawValue instanceof String stringValue && stringValue.isBlank()) {
                    continue;
                }
                return convert(rawValue, targetType);
            }
        }
        return null;
    }

    private <T> T convert(Object rawValue, Class<T> targetType) {
        if (targetType.isInstance(rawValue)) {
            return targetType.cast(rawValue);
        }
        return CONVERSION_SERVICE.convert(rawValue, targetType);
    }

    private String convertToString(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = CONVERSION_SERVICE.convert(rawValue, String.class);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String[] join(String propertyKey, String... aliasKeys) {
        String[] keys = new String[aliasKeys.length + 1];
        keys[0] = propertyKey;
        System.arraycopy(aliasKeys, 0, keys, 1, aliasKeys.length);
        return keys;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private PropertySourceLayer classify(PropertySource<?> propertySource) {
        String name = propertySource.getName();
        if (CONFIGURATION_PROPERTIES_SOURCE_NAME.equals(name)) {
            return PropertySourceLayer.IGNORED;
        }
        if (propertySource instanceof CommandLinePropertySource<?>
                || "commandLineArgs".equals(name)
                || StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME.equals(name)) {
            return PropertySourceLayer.COMMAND_LINE;
        }
        if (propertySource instanceof SystemEnvironmentPropertySource
                || StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME.equals(name)) {
            return PropertySourceLayer.ENVIRONMENT;
        }
        if (DEFAULT_PROPERTIES_SOURCE_NAME.equals(name)) {
            return PropertySourceLayer.DEFAULT;
        }
        return PropertySourceLayer.CONFIGURATION;
    }

    private enum PropertySourceLayer {
        CONFIGURATION,
        COMMAND_LINE,
        ENVIRONMENT,
        DEFAULT,
        IGNORED
    }
}
