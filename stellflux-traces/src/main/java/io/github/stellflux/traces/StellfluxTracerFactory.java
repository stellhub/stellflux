package io.github.stellflux.traces;

import io.github.stellflux.opentelemetry.scope.StellfluxTelemetryScopeFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

/** Stellflux Tracer 工厂。 */
public class StellfluxTracerFactory {

    /**
     * 根据 instrumentation scope 创建 Tracer。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName instrumentation scope 名称
     * @return Tracer
     */
    public Tracer create(OpenTelemetry openTelemetry, String instrumentationScopeName) {
        return openTelemetry.getTracer(instrumentationScopeName);
    }

    /**
     * 根据 instrumentation scope 与模块信息创建 Tracer。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName instrumentation scope 名称
     * @param artifactId Maven artifactId
     * @param anchorClass 模块锚点类型
     * @return Tracer
     */
    public Tracer create(
            OpenTelemetry openTelemetry,
            String instrumentationScopeName,
            String artifactId,
            Class<?> anchorClass) {
        return StellfluxTelemetryScopeFactory.createTracer(
                openTelemetry, instrumentationScopeName, artifactId, anchorClass);
    }
}
