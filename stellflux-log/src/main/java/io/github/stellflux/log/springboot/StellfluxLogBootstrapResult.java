package io.github.stellflux.log.springboot;

import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.opentelemetry.api.OpenTelemetry;
import lombok.Builder;
import lombok.Getter;

/** Spring Boot 日志初始化结果。 */
@Getter
@Builder
public class StellfluxLogBootstrapResult {

    private final StellfluxLogBootstrapMode mode;

    private final boolean installedLogbackBridge;

    private final StellfluxOpenTelemetryConfig config;

    private final OpenTelemetry openTelemetry;
}
