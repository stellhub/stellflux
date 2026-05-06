package io.github.stellflux.opentelemetry.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/** Stellflux OpenTelemetry 统一配置模型。 */
@Getter
@Builder(toBuilder = true)
public class StellfluxOpenTelemetryConfig {

    private final String serviceName;

    private final String serviceNamespace;

    private final String serviceVersion;

    private final String serviceInstanceId;

    private final String environment;

    private final String cluster;

    private final String region;

    private final String zone;

    private final String idc;

    private final String hostName;

    private final String hostIp;

    private final String nodeName;

    private final String k8sNamespace;

    private final String podName;

    private final String podUid;

    private final String podIp;

    private final String containerName;

    @Builder.Default private final boolean logsEnabled = true;

    @Builder.Default private final boolean metricsEnabled = true;

    @Builder.Default private final boolean tracesEnabled = true;

    @Builder.Default private final boolean registerGlobal = false;

    @Builder.Default private final String endpoint = "http://localhost:4317";

    @Builder.Default private final String protocol = "grpc";

    @Builder.Default private final String logsOutput = "otlp";

    @Builder.Default private final String logsFormat = "json";

    @Builder.Default private final boolean enableCaller = true;

    @Builder.Default private final boolean enableStacktrace = true;

    @Builder.Default private final Duration batchTimeout = Duration.ofSeconds(5);

    @Builder.Default private final Duration exportTimeout = Duration.ofSeconds(3);

    @Builder.Default private final Duration metricExportInterval = Duration.ofSeconds(30);

    @Builder.Default private final int maxBatchSize = 512;

    @Builder.Default private final int maxQueueSize = 2048;

    @Builder.Default private final double traceSampleRatio = 1.0d;

    @Builder.Default private final String fallbackFilePath = "logs/stellflux-fallback.log";

    @Builder.Default private final RetryConfig retry = RetryConfig.builder().build();

    @Singular("header")
    private final Map<String, String> headers;

    @Singular("resourceAttribute")
    private final Map<String, String> resourceAttributes;

    /**
     * 返回 Header 副本。
     *
     * @return Header 副本
     */
    public Map<String, String> headerSnapshot() {
        return headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers);
    }

    /**
     * 返回 Resource Attributes 副本。
     *
     * @return Resource Attributes 副本
     */
    public Map<String, String> resourceAttributeSnapshot() {
        return resourceAttributes == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(resourceAttributes);
    }
}
