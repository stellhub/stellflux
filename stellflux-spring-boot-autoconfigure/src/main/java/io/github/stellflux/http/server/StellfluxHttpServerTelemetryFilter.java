package io.github.stellflux.http.server;

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
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import org.springframework.web.filter.OncePerRequestFilter;

/** HTTP 服务端 telemetry filter。 */
public class StellfluxHttpServerTelemetryFilter extends OncePerRequestFilter {

    private static final String INSTRUMENTATION_SCOPE_NAME = "io.github.stellflux.http.server";

    private static final String ACCESS_LOG_SCOPE_NAME = "io.github.stellflux.http.server.access";

    private static final String ACCESS_LOG_EVENT_NAME = "http.server.request";

    private static final StellfluxMeterFactory METER_FACTORY = new StellfluxMeterFactory();

    private static final TextMapGetter<HttpServletRequest> REQUEST_GETTER =
            new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(HttpServletRequest carrier) {
                    Enumeration<String> names = carrier.getHeaderNames();
                    if (names == null) {
                        return Collections.emptyList();
                    }
                    return Collections.list(names);
                }

                @Override
                public String get(HttpServletRequest carrier, String key) {
                    return carrier == null ? null : carrier.getHeader(key);
                }
            };

    private static final TextMapSetter<HttpServletResponse> RESPONSE_SETTER =
            (carrier, key, v) -> {
                if (carrier != null) {
                    carrier.setHeader(key, v);
                }
            };

    private final OpenTelemetry openTelemetry;

    private final Tracer tracer;

    private final LongCounter requestCounter;

    private final DoubleHistogram durationHistogram;

    private final StellfluxHttpRouteTemplateResolver routeTemplateResolver;

    private final StellfluxAccessLogEmitter accessLogEmitter;

    public StellfluxHttpServerTelemetryFilter(
            OpenTelemetry openTelemetry, StellfluxHttpRouteTemplateResolver routeTemplateResolver) {
        this.openTelemetry = openTelemetry;
        this.routeTemplateResolver = routeTemplateResolver;
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME);
        this.accessLogEmitter =
                new StellfluxAccessLogEmitter(openTelemetry, ACCESS_LOG_SCOPE_NAME, ACCESS_LOG_EVENT_NAME);
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE_NAME);
        this.requestCounter =
                METER_FACTORY.createCounter(
                        meter, StellfluxMetricNames.HTTP_SERVER_REQUESTS, "Total HTTP server requests");
        this.durationHistogram =
                METER_FACTORY.createHistogram(
                        meter, StellfluxMetricNames.HTTP_SERVER_DURATION, "ms", "HTTP server request duration");
    }

    /**
     * 解析上游上下文，创建服务端 span，并记录请求指标。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException IO 异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Context extracted =
                openTelemetry
                        .getPropagators()
                        .getTextMapPropagator()
                        .extract(Context.current(), request, REQUEST_GETTER);
        Span span =
                tracer
                        .spanBuilder(request.getMethod() + " " + request.getRequestURI())
                        .setParent(extracted)
                        .setSpanKind(SpanKind.SERVER)
                        .startSpan();
        Context context = extracted.with(span);
        long startNanos = System.nanoTime();
        int statusCode = 200;
        Throwable throwable = null;

        populateRequestAttributes(span, request);
        W3CTraceContextPropagator.getInstance().inject(context, response, RESPONSE_SETTER);

        try (Scope ignored = context.makeCurrent()) {
            filterChain.doFilter(request, response);
            statusCode = response.getStatus();
        } catch (IOException | ServletException | RuntimeException exception) {
            throwable = exception;
            statusCode = response.getStatus() >= 400 ? response.getStatus() : 500;
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
            span.setAttribute("error.type", exception.getClass().getName());
            throw exception;
        } finally {
            finalizeRequest(request, context, span, startNanos, statusCode, throwable);
        }
    }

    private void populateRequestAttributes(Span span, HttpServletRequest request) {
        span.setAttribute("http.request.method", request.getMethod());
        span.setAttribute("url.path", request.getRequestURI());
        span.setAttribute("server.address", request.getServerName());
        span.setAttribute("server.port", request.getServerPort());
        span.setAttribute("network.protocol.version", request.getProtocol());
    }

    private void finalizeRequest(
            HttpServletRequest request,
            Context context,
            Span span,
            long startNanos,
            int statusCode,
            Throwable throwable) {
        String route = routeTemplateResolver.resolve(request, statusCode);
        span.updateName(request.getMethod() + " " + route);
        span.setAttribute("http.route", route);
        span.setAttribute("http.response.status_code", statusCode);
        if (throwable == null && statusCode >= 400) {
            span.setStatus(StatusCode.ERROR);
        }
        Attributes metricAttributes = buildMetricAttributes(request, route, statusCode, throwable);
        requestCounter.add(1, metricAttributes);
        durationHistogram.record((System.nanoTime() - startNanos) / 1_000_000.0d, metricAttributes);
        emitAccessLog(context, request, route, statusCode, throwable);
        span.end();
    }

    private Attributes buildMetricAttributes(
            HttpServletRequest request, String route, int statusCode, Throwable throwable) {
        AttributesBuilder attributes = Attributes.builder();
        attributes.put("http.request.method", request.getMethod());
        attributes.put("http.route", route);
        attributes.put("server.address", request.getServerName());
        attributes.put("server.port", request.getServerPort());
        attributes.put("http.response.status_code", statusCode);
        if (throwable != null) {
            attributes.put("error.type", throwable.getClass().getName());
        }
        return attributes.build();
    }

    private void emitAccessLog(
            Context context,
            HttpServletRequest request,
            String route,
            int statusCode,
            Throwable throwable) {
        accessLogEmitter.emit(
                context,
                "HTTP server request completed",
                builder -> {
                    builder.setAttribute(AttributeKey.stringKey("http.request.method"), request.getMethod());
                    builder.setAttribute(AttributeKey.stringKey("url.path"), request.getRequestURI());
                    builder.setAttribute(AttributeKey.stringKey("url.scheme"), request.getScheme());
                    builder.setAttribute(AttributeKey.stringKey("http.route"), route);
                    builder.setAttribute(AttributeKey.stringKey("server.address"), request.getServerName());
                    builder.setAttribute(AttributeKey.longKey("server.port"), (long) request.getServerPort());
                    builder.setAttribute(
                            AttributeKey.stringKey("network.protocol.version"), request.getProtocol());
                    builder.setAttribute(
                            AttributeKey.longKey("http.response.status_code"), (long) statusCode);
                    if (request.getRemoteAddr() != null && !request.getRemoteAddr().isBlank()) {
                        builder.setAttribute(AttributeKey.stringKey("client.address"), request.getRemoteAddr());
                    }
                    if (throwable != null) {
                        builder.setAttribute(
                                AttributeKey.stringKey("error.type"), throwable.getClass().getName());
                    }
                });
    }
}
