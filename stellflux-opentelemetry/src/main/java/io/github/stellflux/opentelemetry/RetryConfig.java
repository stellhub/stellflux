package io.github.stellflux.opentelemetry;

import java.time.Duration;
import lombok.Builder;
import lombok.Getter;

/** OTLP 导出重试配置。 */
@Getter
@Builder(toBuilder = true)
public class RetryConfig {

    @Builder.Default private final boolean enabled = true;

    @Builder.Default private final Duration initialInterval = Duration.ofSeconds(5);

    @Builder.Default private final Duration maxInterval = Duration.ofSeconds(30);

    @Builder.Default private final Duration maxElapsedTime = Duration.ofMinutes(1);
}
