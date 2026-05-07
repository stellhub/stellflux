package io.github.stellflux.opentelemetry;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stellflux.opentelemetry")
@Getter
@Setter
public class StellfluxOpenTelemetryProperties {

    private boolean enabled = true;

    private boolean registerGlobal = false;

    private String endpoint = "http://localhost:4317";

    private String protocol = "grpc";

    private String logsOutput = "otlp";

    private String logsFormat = "json";

    private boolean enableCaller = true;

    private boolean enableStacktrace = true;

    private Duration batchTimeout = Duration.ofSeconds(5);

    private Duration exportTimeout = Duration.ofSeconds(3);

    private Duration metricExportInterval = Duration.ofSeconds(30);

    private int maxBatchSize = 512;

    private int maxQueueSize = 2048;

    private double traceSampleRatio = 1.0d;

    private String fallbackFilePath = "logs/stellflux-fallback.log";

    private RetryProperties retry = new RetryProperties();

    private SignalProperties logs = new SignalProperties();

    private SignalProperties metrics = new SignalProperties();

    private SignalProperties traces = new SignalProperties();

    private final Map<String, String> headers = new LinkedHashMap<>();

    private final Map<String, String> resourceAttributes = new LinkedHashMap<>();

    private ResourceProperties resource = new ResourceProperties();

    @Getter
    @Setter
    public static class SignalProperties {
        private Boolean enabled;
    }

    @Getter
    @Setter
    public static class RetryProperties {
        private boolean enabled = true;
        private Duration initialInterval = Duration.ofSeconds(5);
        private Duration maxInterval = Duration.ofSeconds(30);
        private Duration maxElapsedTime = Duration.ofMinutes(1);
    }

    @Getter
    @Setter
    public static class ResourceProperties {
        private String serviceName;
        private String serviceNamespace;
        private String serviceVersion;
        private String serviceInstanceId;
        private String deploymentEnvironmentName;
        private String k8sClusterName;
        private String cloudRegion;
        private String cloudAvailabilityZone;
        private String idc;
        private String hostName;
        private String hostIp;
        private String k8sNodeName;
        private String k8sNamespaceName;
        private String k8sPodName;
        private String k8sPodUid;
        private String k8sPodIp;
        private String k8sContainerName;
    }
}
