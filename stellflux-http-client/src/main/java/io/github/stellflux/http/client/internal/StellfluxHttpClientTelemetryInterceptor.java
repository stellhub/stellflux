package io.github.stellflux.http.client.internal;

import io.github.stellflux.metrics.StellfluxMeterFactory;
import io.github.stellflux.metrics.StellfluxMetricNames;
import io.github.stellflux.opentelemetry.log.StellfluxAccessLogEmitter;
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
import io.opentelemetry.context.propagation.TextMapSetter;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** OkHttp 客户端 telemetry 拦截器。 */
public class StellfluxHttpClientTelemetryInterceptor implements Interceptor {

    private static final String INSTRUMENTATION_SCOPE_NAME = "io.github.stellflux.http.client";

    private static final String ACCESS_LOG_SCOPE_NAME = "io.github.stellflux.http.client.access";

    private static final String ACCESS_LOG_EVENT_NAME = "http.client.request";

    private static final StellfluxMeterFactory METER_FACTORY = new StellfluxMeterFactory();

    private static final TextMapSetter<Request.Builder> REQUEST_SETTER =
            (carrier, key, v) -> carrier.header(key, v);

    private final OpenTelemetry openTelemetry;

    private final Tracer tracer;

    private final LongCounter requestCounter;

    private final DoubleHistogram durationHistogram;

    private final StellfluxAccessLogEmitter accessLogEmitter;

    public StellfluxHttpClientTelemetryInterceptor(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME);
        this.accessLogEmitter =
                new StellfluxAccessLogEmitter(openTelemetry, ACCESS_LOG_SCOPE_NAME, ACCESS_LOG_EVENT_NAME);
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE_NAME);
        this.requestCounter =
                METER_FACTORY.createCounter(
                        meter, StellfluxMetricNames.HTTP_CLIENT_REQUESTS, "Total HTTP client requests");
        this.durationHistogram =
                METER_FACTORY.createHistogram(
                        meter, StellfluxMetricNames.HTTP_CLIENT_DURATION, "ms", "HTTP client request duration");
    }

    /**
     * 为 HTTP 请求创建 span 并记录请求指标。
     *
     * @param chain OkHttp 调用链
     * @return HTTP 响应
     * @throws IOException 网络异常
     */
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Span span =
                tracer
                        .spanBuilder(request.method() + " " + request.url().encodedPath())
                        .setSpanKind(SpanKind.CLIENT)
                        .startSpan();
        long startNanos = System.nanoTime();
        Context context = Context.current().with(span);
        Request.Builder requestBuilder = request.newBuilder();
        openTelemetry
                .getPropagators()
                .getTextMapPropagator()
                .inject(context, requestBuilder, REQUEST_SETTER);
        Request instrumentedRequest = requestBuilder.build();
        populateSpanAttributes(span, instrumentedRequest);

        try (Scope ignored = context.makeCurrent()) {
            Response response = chain.proceed(instrumentedRequest);
            span.setAttribute("http.response.status_code", response.code());
            if (response.code() >= 400) {
                span.setStatus(StatusCode.ERROR);
            }
            recordMetrics(startNanos, instrumentedRequest, response.code(), null);
            emitAccessLog(context, instrumentedRequest, response.code(), null);
            return response;
        } catch (IOException exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            span.setAttribute("error.type", exception.getClass().getName());
            recordMetrics(startNanos, instrumentedRequest, null, exception);
            emitAccessLog(context, instrumentedRequest, null, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private void populateSpanAttributes(Span span, Request request) {
        span.setAttribute("http.request.method", request.method());
        span.setAttribute("url.full", request.url().toString());
        span.setAttribute("server.address", request.url().host());
        span.setAttribute("server.port", request.url().port());
        span.setAttribute("network.protocol.name", request.url().scheme());
    }

    private void recordMetrics(
            long startNanos, Request request, Integer statusCode, IOException exception) {
        AttributesBuilder attributes = Attributes.builder();
        attributes.put("http.request.method", request.method());
        attributes.put("server.address", request.url().host());
        attributes.put("server.port", request.url().port());
        if (statusCode != null) {
            attributes.put("http.response.status_code", (long) statusCode);
        }
        if (exception != null) {
            attributes.put("error.type", exception.getClass().getName());
        }
        Attributes metricAttributes = attributes.build();
        requestCounter.add(1, metricAttributes);
        durationHistogram.record((System.nanoTime() - startNanos) / 1_000_000.0d, metricAttributes);
    }

    private void emitAccessLog(
            Context context, Request request, Integer statusCode, IOException exception) {
        accessLogEmitter.emit(
                context,
                "HTTP client request completed",
                builder -> {
                    builder.setAttribute(AttributeKey.stringKey("http.request.method"), request.method());
                    builder.setAttribute(AttributeKey.stringKey("url.full"), request.url().toString());
                    builder.setAttribute(AttributeKey.stringKey("url.path"), request.url().encodedPath());
                    builder.setAttribute(AttributeKey.stringKey("url.scheme"), request.url().scheme());
                    builder.setAttribute(AttributeKey.stringKey("server.address"), request.url().host());
                    builder.setAttribute(AttributeKey.longKey("server.port"), (long) request.url().port());
                    if (statusCode != null) {
                        builder.setAttribute(
                                AttributeKey.longKey("http.response.status_code"), statusCode.longValue());
                    }
                    if (exception != null) {
                        builder.setAttribute(
                                AttributeKey.stringKey("error.type"), exception.getClass().getName());
                    }
                });
    }
}
