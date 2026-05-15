package io.github.stellflux.examples.opentelemetry;

import io.github.stellflux.log.StellfluxLoggerFactory;
import io.github.stellflux.log.bridge.StellfluxLogger;
import io.github.stellflux.log.model.StellfluxSeverity;
import io.github.stellflux.metrics.StellfluxMeterFactory;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.github.stellflux.traces.StellfluxTracerFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;

/** OpenTelemetry HTTP 观测示例服务。 */
@Service
public class OpenTelemetryObservationService {

    private static final String INSTRUMENTATION_SCOPE = "stellflux.examples.opentelemetry";
    private static final String ARTIFACT_ID = "stellflux-opentelemetry-example";
    private static final AttributeKey<String> SIGNAL_ATTRIBUTE =
            AttributeKey.stringKey("example.signal");
    private static final AttributeKey<String> SCENARIO_ATTRIBUTE =
            AttributeKey.stringKey("example.scenario");
    private static final Logger JUL_LOGGER =
            Logger.getLogger(OpenTelemetryObservationService.class.getName());

    private final StellfluxOpenTelemetryRuntime runtime;
    private final Tracer tracer;
    private final StellfluxLogger stellfluxLogger;
    private final LongCounter requestCounter;
    private final LongCounter logCounter;
    private final DoubleHistogram valueHistogram;
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalLogs = new AtomicLong();
    private final AtomicLong totalMetrics = new AtomicLong();
    private final AtomicLong lastMetricValueMillis = new AtomicLong();

    public OpenTelemetryObservationService(
            OpenTelemetry openTelemetry, StellfluxOpenTelemetryRuntime runtime) {
        this.runtime = runtime;
        this.tracer =
                new StellfluxTracerFactory()
                        .create(
                                openTelemetry,
                                INSTRUMENTATION_SCOPE,
                                ARTIFACT_ID,
                                StellfluxOpenTelemetryExampleApplication.class);
        this.stellfluxLogger =
                new StellfluxLoggerFactory()
                        .createLogger(
                                openTelemetry,
                                INSTRUMENTATION_SCOPE,
                                ARTIFACT_ID,
                                StellfluxOpenTelemetryExampleApplication.class,
                                runtime.getConfig());
        Meter meter =
                new StellfluxMeterFactory()
                        .create(
                                openTelemetry,
                                INSTRUMENTATION_SCOPE,
                                ARTIFACT_ID,
                                StellfluxOpenTelemetryExampleApplication.class);
        StellfluxMeterFactory meterFactory = new StellfluxMeterFactory();
        this.requestCounter =
                meterFactory.createCounter(
                        meter, "opentelemetry_example_requests_total", "Total OpenTelemetry example requests.");
        this.logCounter =
                meterFactory.createCounter(
                        meter, "opentelemetry_example_logs_total", "Total OpenTelemetry example logs.");
        this.valueHistogram =
                meterFactory.createHistogram(
                        meter,
                        "opentelemetry_example_observation_value",
                        "ms",
                        "Observed value in the OpenTelemetry example.");
    }

    /**
     * 返回示例当前状态。
     *
     * @return 状态响应
     */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("module", ARTIFACT_ID);
        status.put("serviceName", runtime.getConfig().getServiceName());
        status.put("logsEnabled", runtime.getConfig().isLogsEnabled());
        status.put("metricsEnabled", runtime.getConfig().isMetricsEnabled());
        status.put("tracesEnabled", runtime.getConfig().isTracesEnabled());
        status.put("logsOutput", runtime.getConfig().getLogsOutput());
        status.put("protocol", runtime.getConfig().getProtocol());
        status.put("endpoint", runtime.getConfig().getEndpoint());
        status.put("metricExportInterval", runtime.getConfig().getMetricExportInterval().toString());
        status.put("metrics", metricSnapshot());
        status.put(
                "endpoints",
                Map.of(
                        "trace", "GET /api/opentelemetry/trace?operation=checkout",
                        "log", "POST /api/opentelemetry/logs",
                        "metrics", "POST /api/opentelemetry/metrics",
                        "verify", "POST /api/opentelemetry/verify"));
        return status;
    }

    /**
     * 创建一次链路观测。
     *
     * @param operation 业务操作名
     * @return 链路响应
     */
    public Map<String, Object> trace(String operation) {
        String scenario = normalize(operation, "manual-trace");
        return observe("trace", scenario, () -> Map.of("operation", scenario));
    }

    /**
     * 创建一次日志观测。
     *
     * @param request 日志请求
     * @return 日志响应
     */
    public Map<String, Object> log(LogObservationRequest request) {
        LogObservationRequest effectiveRequest =
                request == null ? new LogObservationRequest(null, null) : request;
        return observe(
                "log",
                effectiveRequest.effectiveLevel().toLowerCase(),
                () -> {
                    emitLog(effectiveRequest.effectiveLevel(), effectiveRequest.effectiveMessage());
                    long total = totalLogs.incrementAndGet();
                    logCounter.add(
                            1,
                            Attributes.of(
                                    SIGNAL_ATTRIBUTE,
                                    "log",
                                    AttributeKey.stringKey("log.level"),
                                    effectiveRequest.effectiveLevel()));
                    return Map.of(
                            "message", effectiveRequest.effectiveMessage(),
                            "level", effectiveRequest.effectiveLevel(),
                            "totalLogs", total);
                });
    }

    /**
     * 创建一次指标观测。
     *
     * @param request 指标请求
     * @return 指标响应
     */
    public Map<String, Object> metric(MetricObservationRequest request) {
        MetricObservationRequest effectiveRequest =
                request == null ? new MetricObservationRequest(null, null) : request;
        return observe(
                "metric",
                effectiveRequest.effectiveName(),
                () -> {
                    recordMetric(effectiveRequest.effectiveName(), effectiveRequest.effectiveValue());
                    return Map.of(
                            "metricName",
                            "opentelemetry_example_observation_value",
                            "label",
                            effectiveRequest.effectiveName(),
                            "value",
                            effectiveRequest.effectiveValue(),
                            "snapshot",
                            metricSnapshot());
                });
    }

    /**
     * 一次性验证 trace、log 和 metrics。
     *
     * @param scenario 验证场景
     * @return 验证响应
     */
    public Map<String, Object> verify(String scenario) {
        String effectiveScenario = normalize(scenario, "manual-verify");
        return observe(
                "verify",
                effectiveScenario,
                () -> {
                    emitLog("INFO", "verify observability scenario=" + effectiveScenario);
                    long simulatedLatency = ThreadLocalRandom.current().nextLong(15, 120);
                    recordMetric(effectiveScenario, simulatedLatency);
                    return Map.of(
                            "scenario",
                            effectiveScenario,
                            "logMessage",
                            "verify observability scenario=" + effectiveScenario,
                            "metricValue",
                            simulatedLatency,
                            "metrics",
                            metricSnapshot());
                });
    }

    private Map<String, Object> observe(
            String signal, String scenario, ObservationCallback callback) {
        long startedAt = System.nanoTime();
        Span span =
                tracer
                        .spanBuilder("opentelemetry-example-" + signal)
                        .setAttribute(SIGNAL_ATTRIBUTE, signal)
                        .setAttribute(SCENARIO_ATTRIBUTE, scenario)
                        .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            requestCounter.add(1, Attributes.of(SIGNAL_ATTRIBUTE, signal));
            long total = totalRequests.incrementAndGet();
            Map<String, Object> payload = new LinkedHashMap<>(callback.observe());
            payload.put("signal", signal);
            payload.put("scenario", scenario);
            payload.put("traceId", span.getSpanContext().getTraceId());
            payload.put("spanId", span.getSpanContext().getSpanId());
            payload.put("totalRequests", total);
            payload.put("durationMs", Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            span.setAttribute("example.total_requests", total);
            return payload;
        } catch (RuntimeException ex) {
            span.recordException(ex);
            span.setAttribute("error", true);
            throw ex;
        } finally {
            span.end();
        }
    }

    private void emitLog(String level, String message) {
        String normalizedLevel = normalize(level, "INFO").toUpperCase();
        JUL_LOGGER.info(
                () -> "OpenTelemetry example log level=" + normalizedLevel + ", message=" + message);
        if ("ERROR".equals(normalizedLevel)) {
            stellfluxLogger.log(
                    StellfluxSeverity.ERROR,
                    message,
                    Map.of("example.signal", "log", "example.level", normalizedLevel),
                    null,
                    null);
            return;
        }
        if ("WARN".equals(normalizedLevel)) {
            stellfluxLogger.warn(message);
            return;
        }
        if ("DEBUG".equals(normalizedLevel)) {
            stellfluxLogger.debug(message);
            return;
        }
        stellfluxLogger.info(message);
    }

    private void recordMetric(String label, double value) {
        totalMetrics.incrementAndGet();
        lastMetricValueMillis.set(Math.round(value));
        valueHistogram.record(
                value,
                Attributes.of(SIGNAL_ATTRIBUTE, "metric", AttributeKey.stringKey("example.label"), label));
    }

    private Map<String, Object> metricSnapshot() {
        return Map.of(
                "totalRequests", totalRequests.get(),
                "totalLogs", totalLogs.get(),
                "totalMetrics", totalMetrics.get(),
                "lastMetricValue", lastMetricValueMillis.get());
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @FunctionalInterface
    private interface ObservationCallback {

        Map<String, Object> observe();
    }
}
