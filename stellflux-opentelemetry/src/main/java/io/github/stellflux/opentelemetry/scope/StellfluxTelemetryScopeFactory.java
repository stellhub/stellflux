package io.github.stellflux.opentelemetry.scope;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;

/** Stellflux telemetry scope 工厂。 */
public final class StellfluxTelemetryScopeFactory {

    private StellfluxTelemetryScopeFactory() {}

    /**
     * 创建带模块版本的 Tracer。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName scope 名称
     * @param artifactId Maven artifactId
     * @param anchorClass 模块锚点类型
     * @return Tracer
     */
    public static Tracer createTracer(
            OpenTelemetry openTelemetry,
            String instrumentationScopeName,
            String artifactId,
            Class<?> anchorClass) {
        return openTelemetry
                .getTracerProvider()
                .tracerBuilder(instrumentationScopeName)
                .setInstrumentationVersion(
                        StellfluxModuleVersionResolver.resolve(artifactId, anchorClass))
                .build();
    }

    /**
     * 创建带模块版本的 Meter。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName scope 名称
     * @param artifactId Maven artifactId
     * @param anchorClass 模块锚点类型
     * @return Meter
     */
    public static Meter createMeter(
            OpenTelemetry openTelemetry,
            String instrumentationScopeName,
            String artifactId,
            Class<?> anchorClass) {
        return openTelemetry
                .getMeterProvider()
                .meterBuilder(instrumentationScopeName)
                .setInstrumentationVersion(
                        StellfluxModuleVersionResolver.resolve(artifactId, anchorClass))
                .build();
    }

    /**
     * 创建带模块版本的 Logger。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName scope 名称
     * @param artifactId Maven artifactId
     * @param anchorClass 模块锚点类型
     * @return Logger
     */
    public static Logger createLogger(
            OpenTelemetry openTelemetry,
            String instrumentationScopeName,
            String artifactId,
            Class<?> anchorClass) {
        return openTelemetry
                .getLogsBridge()
                .loggerBuilder(instrumentationScopeName)
                .setInstrumentationVersion(
                        StellfluxModuleVersionResolver.resolve(artifactId, anchorClass))
                .build();
    }
}
