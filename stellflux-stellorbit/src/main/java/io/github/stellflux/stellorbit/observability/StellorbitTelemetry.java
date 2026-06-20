package io.github.stellflux.stellorbit.observability;

import io.github.stellflux.metrics.StellfluxMeterFactory;
import io.github.stellflux.metrics.StellfluxMetricNames;
import io.github.stellflux.opentelemetry.log.StellfluxAccessLogEmitter;
import io.github.stellflux.opentelemetry.scope.StellfluxTelemetryScopeFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.Map;
import java.util.Objects;

/** StellOrbit 治理能力 OpenTelemetry 观测组件。 */
public class StellorbitTelemetry {

    private static final String INSTRUMENTATION_SCOPE_NAME = "io.github.stellflux.stellorbit";

    private static final String ACCESS_LOG_SCOPE_NAME = "io.github.stellflux.stellorbit.access";

    private static final String ACCESS_LOG_EVENT_NAME = "stellorbit.governance.decision";

    private static final String ARTIFACT_ID = "stellflux-stellorbit";

    private static final AttributeKey<String> CAPABILITY_KEY =
            AttributeKey.stringKey("stellorbit.capability");

    private static final AttributeKey<String> OPERATION_KEY =
            AttributeKey.stringKey("stellorbit.operation");

    private static final AttributeKey<String> OUTCOME_KEY =
            AttributeKey.stringKey("stellorbit.outcome");

    private static final AttributeKey<String> SERVICE_KEY =
            AttributeKey.stringKey("stellorbit.service");

    private static final AttributeKey<String> EVENT_KEY = AttributeKey.stringKey("stellorbit.event");

    private static final AttributeKey<String> ERROR_TYPE_KEY = AttributeKey.stringKey("error.type");

    private static final StellfluxMeterFactory METER_FACTORY = new StellfluxMeterFactory();

    private static final StellorbitTelemetry NOOP = new StellorbitTelemetry();

    private final boolean enabled;

    private final Tracer tracer;

    private final LongCounter decisionCounter;

    private final DoubleHistogram durationHistogram;

    private final StellfluxAccessLogEmitter accessLogEmitter;

    private StellorbitTelemetry() {
        this.enabled = false;
        this.tracer = null;
        this.decisionCounter = null;
        this.durationHistogram = null;
        this.accessLogEmitter = null;
    }

    public StellorbitTelemetry(OpenTelemetry openTelemetry) {
        Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
        this.enabled = true;
        this.tracer =
                StellfluxTelemetryScopeFactory.createTracer(
                        openTelemetry, INSTRUMENTATION_SCOPE_NAME, ARTIFACT_ID, StellorbitTelemetry.class);
        Meter meter =
                METER_FACTORY.create(
                        openTelemetry, INSTRUMENTATION_SCOPE_NAME, ARTIFACT_ID, StellorbitTelemetry.class);
        this.decisionCounter =
                METER_FACTORY.createCounter(
                        meter,
                        StellfluxMetricNames.STELLORBIT_DECISIONS,
                        "Total StellOrbit governance decisions");
        this.durationHistogram =
                METER_FACTORY.createHistogram(
                        meter,
                        StellfluxMetricNames.STELLORBIT_DURATION,
                        "ms",
                        "StellOrbit governance decision duration");
        this.accessLogEmitter =
                new StellfluxAccessLogEmitter(
                        openTelemetry,
                        ACCESS_LOG_SCOPE_NAME,
                        ACCESS_LOG_EVENT_NAME,
                        ARTIFACT_ID,
                        StellorbitTelemetry.class);
    }

    public static StellorbitTelemetry noop() {
        return NOOP;
    }

    /**
     * 开始记录一次治理判定。
     *
     * @param capability 能力名称
     * @param operation 操作名称
     * @param serviceName 服务名
     * @return 观测上下文
     */
    public Observation start(String capability, String operation, String serviceName) {
        String normalizedCapability = normalize(capability, "unknown");
        String normalizedOperation = normalize(operation, "unknown");
        String normalizedServiceName = normalize(serviceName, "unknown");
        if (!enabled) {
            return Observation.noop();
        }
        Span span =
                tracer
                        .spanBuilder("StellOrbit " + normalizedCapability + " " + normalizedOperation)
                        .setSpanKind(SpanKind.INTERNAL)
                        .startSpan();
        span.setAttribute(CAPABILITY_KEY, normalizedCapability);
        span.setAttribute(OPERATION_KEY, normalizedOperation);
        span.setAttribute(SERVICE_KEY, normalizedServiceName);
        Context context = Context.current().with(span);
        Scope scope = context.makeCurrent();
        return new Observation(
                true,
                normalizedCapability,
                normalizedOperation,
                normalizedServiceName,
                span,
                context,
                scope,
                System.nanoTime(),
                decisionCounter,
                durationHistogram,
                accessLogEmitter);
    }

    /**
     * 记录没有明确调用边界的治理事件。
     *
     * @param capability 能力名称
     * @param eventName 事件名称
     * @param attributes 事件属性
     */
    public void event(String capability, String eventName, Map<String, String> attributes) {
        if (!enabled) {
            return;
        }
        Map<String, String> safeAttributes = attributes == null ? Map.of() : attributes;
        accessLogEmitter.emit(
                Context.current(),
                "StellOrbit governance event",
                builder -> {
                    builder.setAttribute(CAPABILITY_KEY, normalize(capability, "unknown"));
                    builder.setAttribute(EVENT_KEY, normalize(eventName, "unknown"));
                    safeAttributes.forEach(
                            (key, value) ->
                                    builder.setAttribute(AttributeKey.stringKey(key), normalize(value, "")));
                });
    }

    private static String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /** 单次治理判定的观测上下文。 */
    public static final class Observation implements AutoCloseable {

        private final boolean enabled;

        private final String capability;

        private final String operation;

        private final String serviceName;

        private final Span span;

        private final Context context;

        private final Scope scope;

        private final long startNanos;

        private final LongCounter decisionCounter;

        private final DoubleHistogram durationHistogram;

        private final StellfluxAccessLogEmitter accessLogEmitter;

        private boolean finished;

        private Observation(
                boolean enabled,
                String capability,
                String operation,
                String serviceName,
                Span span,
                Context context,
                Scope scope,
                long startNanos,
                LongCounter decisionCounter,
                DoubleHistogram durationHistogram,
                StellfluxAccessLogEmitter accessLogEmitter) {
            this.enabled = enabled;
            this.capability = capability;
            this.operation = operation;
            this.serviceName = serviceName;
            this.span = span;
            this.context = context;
            this.scope = scope;
            this.startNanos = startNanos;
            this.decisionCounter = decisionCounter;
            this.durationHistogram = durationHistogram;
            this.accessLogEmitter = accessLogEmitter;
        }

        private static Observation noop() {
            return new Observation(false, null, null, null, null, null, null, 0L, null, null, null);
        }

        /**
         * 记录成功判定。
         *
         * @param outcome 判定结果
         * @param attributes 附加属性
         */
        public void success(String outcome, Map<String, String> attributes) {
            finish(outcome, attributes, null);
        }

        /**
         * 记录异常判定。
         *
         * @param outcome 判定结果
         * @param attributes 附加属性
         * @param throwable 异常
         */
        public void error(String outcome, Map<String, String> attributes, Throwable throwable) {
            finish(outcome, attributes, throwable);
        }

        @Override
        public void close() {
            if (!enabled) {
                return;
            }
            if (!finished) {
                finish("unfinished", Map.of(), null);
            }
            scope.close();
        }

        private void finish(String outcome, Map<String, String> attributes, Throwable throwable) {
            if (!enabled || finished) {
                return;
            }
            finished = true;
            String normalizedOutcome = normalize(outcome, "unknown");
            Map<String, String> safeAttributes = attributes == null ? Map.of() : attributes;
            long elapsedNanos = System.nanoTime() - startNanos;
            Attributes metricAttributes = metricAttributes(normalizedOutcome, throwable);
            decisionCounter.add(1, metricAttributes);
            durationHistogram.record(elapsedNanos / 1_000_000.0d, metricAttributes);
            span.setAttribute(OUTCOME_KEY, normalizedOutcome);
            safeAttributes.forEach((key, value) -> span.setAttribute(key, normalize(value, "")));
            if (throwable != null) {
                span.recordException(throwable);
                span.setStatus(StatusCode.ERROR);
                span.setAttribute(ERROR_TYPE_KEY, throwable.getClass().getName());
            }
            emitLog(normalizedOutcome, safeAttributes, throwable);
            span.end();
        }

        private Attributes metricAttributes(String outcome, Throwable throwable) {
            AttributesBuilder builder =
                    Attributes.builder()
                            .put(CAPABILITY_KEY, capability)
                            .put(OPERATION_KEY, operation)
                            .put(OUTCOME_KEY, outcome)
                            .put(SERVICE_KEY, serviceName);
            if (throwable != null) {
                builder.put(ERROR_TYPE_KEY, throwable.getClass().getName());
            }
            return builder.build();
        }

        private void emitLog(String outcome, Map<String, String> attributes, Throwable throwable) {
            accessLogEmitter.emit(
                    context,
                    "StellOrbit governance decision completed",
                    builder -> {
                        builder.setAttribute(CAPABILITY_KEY, capability);
                        builder.setAttribute(OPERATION_KEY, operation);
                        builder.setAttribute(OUTCOME_KEY, outcome);
                        builder.setAttribute(SERVICE_KEY, serviceName);
                        attributes.forEach(
                                (key, value) ->
                                        builder.setAttribute(AttributeKey.stringKey(key), normalize(value, "")));
                        if (throwable != null) {
                            builder.setAttribute(ERROR_TYPE_KEY, throwable.getClass().getName());
                        }
                    });
        }
    }
}
