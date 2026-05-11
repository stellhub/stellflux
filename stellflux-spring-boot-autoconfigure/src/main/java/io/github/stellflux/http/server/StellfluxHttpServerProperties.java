package io.github.stellflux.http.server;

import io.github.stellflux.stellmap.registration.StellfluxRegistrationProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;

/** HTTP server properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.http.server")
public class StellfluxHttpServerProperties {

    /** HTTP 服务端 telemetry 配置。 */
    private final TelemetryProperties telemetry = new TelemetryProperties();

    /** HTTP 服务对外暴露端点配置。 */
    private final EndpointProperties endpoint = new EndpointProperties();

    /** HTTP 服务注册配置。 */
    private final StellfluxRegistrationProperties registration =
            new StellfluxRegistrationProperties();

    /** HTTP telemetry properties. */
    @Getter
    @Setter
    public static class TelemetryProperties {

        /** Whether telemetry filter is enabled. */
        private boolean enabled = true;

        /** Telemetry filter order. */
        private int filterOrder = Ordered.HIGHEST_PRECEDENCE + 10;

        /** URL patterns matched by telemetry filter. */
        private List<String> urlPatterns = new ArrayList<>(List.of("/*"));

        /** Excluded request paths for telemetry filter. */
        private List<String> excludedPaths = new ArrayList<>();

        /** Whether write traceparent response header. */
        private boolean responseTraceHeaderEnabled = true;
    }

    /** HTTP endpoint properties. */
    @Getter
    @Setter
    public static class EndpointProperties {

        /** Advertised protocol for service registration. */
        private String protocol = "http";

        /** Advertised port for service registration. */
        private Integer advertisedPort;

        /** Advertised path for service registration. */
        private String path = "";
    }
}
