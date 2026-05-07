package io.github.stellflux.examples.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.logging.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** OpenTelemetry 示例启动逻辑。 */
@Component
public class OpenTelemetryExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER = Logger.getLogger(OpenTelemetryExampleRunner.class.getName());

    private final OpenTelemetry openTelemetry;

    public OpenTelemetryExampleRunner(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    /**
     * 启动后创建一个演示 span。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        Tracer tracer = openTelemetry.getTracer("stellflux-opentelemetry-example");
        Span span = tracer.spanBuilder("startup-demo-span").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            LOGGER.info(
                    () ->
                            "Created demo span traceId="
                                    + span.getSpanContext().getTraceId()
                                    + ", spanId="
                                    + span.getSpanContext().getSpanId());
        } finally {
            span.end();
        }
    }
}
