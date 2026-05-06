package io.github.stellflux.log.model;

import io.opentelemetry.api.logs.Severity;
import java.util.logging.Level;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** SDK 内部严重级别。 */
@Getter
@RequiredArgsConstructor
public enum StellfluxSeverity {
    TRACE(Severity.TRACE),
    DEBUG(Severity.DEBUG),
    INFO(Severity.INFO),
    WARN(Severity.WARN),
    ERROR(Severity.ERROR),
    FATAL(Severity.FATAL);

    private final Severity otelSeverity;

    /**
     * 将 JUL Level 映射到 SDK 级别。
     *
     * @param level JUL Level
     * @return SDK 级别
     */
    public static StellfluxSeverity fromJulLevel(Level level) {
        if (level == null) {
            return INFO;
        }
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return ERROR;
        }
        if (level.intValue() >= Level.WARNING.intValue()) {
            return WARN;
        }
        if (level.intValue() >= Level.INFO.intValue()) {
            return INFO;
        }
        if (level.intValue() >= Level.FINE.intValue()) {
            return DEBUG;
        }
        return TRACE;
    }
}
