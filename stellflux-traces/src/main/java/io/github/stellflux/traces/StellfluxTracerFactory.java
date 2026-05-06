package io.github.stellflux.traces;

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
}
