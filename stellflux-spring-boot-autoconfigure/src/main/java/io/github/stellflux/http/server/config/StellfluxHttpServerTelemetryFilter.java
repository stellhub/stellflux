package io.github.stellflux.http.server.config;

import io.github.stellflux.metrics.StellfluxMeterFactory;
import io.github.stellflux.metrics.StellfluxMetricNames;
import io.opentelemetry.api.OpenTelemetry;
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

    public StellfluxHttpServerTelemetryFilter(
            OpenTelemetry openTelemetry, StellfluxHttpRouteTemplateResolver routeTemplateResolver) {
        // 保存 OpenTelemetry 根对象，后续提取和传播上下文时都要使用它。
        this.openTelemetry = openTelemetry;
        // 保存路由模板解析器，后面收尾时要用它生成更稳定的路由名。
        this.routeTemplateResolver = routeTemplateResolver;
        // 创建 HTTP 服务端专用的 Tracer，用来生成服务端 span。
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME);
        // 基于同一个 scope 获取 Meter，用来创建服务端指标。
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE_NAME);
        // 创建请求总数计数器，统计所有进入服务端的 HTTP 请求次数。
        this.requestCounter =
                METER_FACTORY.createCounter(
                        meter, StellfluxMetricNames.HTTP_SERVER_REQUESTS, "Total HTTP server requests");
        // 创建耗时直方图，记录每次 HTTP 请求的处理耗时。
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
        // 从入站 HTTP 请求头中提取上游传递过来的 trace 上下文。
        Context extracted =
                openTelemetry
                        .getPropagators()
                        .getTextMapPropagator()
                        .extract(Context.current(), request, REQUEST_GETTER);
        // 创建并启动一个 SERVER span，并把提取出来的上下文作为它的父上下文。
        Span span =
                tracer
                        .spanBuilder(request.getMethod() + " " + request.getRequestURI())
                        .setParent(extracted)
                        .setSpanKind(SpanKind.SERVER)
                        .startSpan();
        // 把这个新建 span 绑定进 Context，供后续过滤器链和业务逻辑继续使用。
        Context context = extracted.with(span);
        // 记录请求开始时间，后续统计延迟指标时要用。
        long startNanos = System.nanoTime();
        // 先把状态码默认设成 200，等过滤器链执行完再拿真实状态。
        int statusCode = 200;
        // 预留异常变量，方便 finally 阶段统一补充失败信息。
        Throwable throwable = null;

        // 给 span 写入基础 HTTP 请求属性。
        populateRequestAttributes(span, request);
        // 把当前 trace 上下文写回响应头，方便下游继续串联链路。
        W3CTraceContextPropagator.getInstance().inject(context, response, RESPONSE_SETTER);

        // 在执行后续 servlet 过滤器链期间，把当前请求 span 设置为生效上下文。
        try (Scope ignored = context.makeCurrent()) {
            // 执行后续过滤器和控制器逻辑。
            filterChain.doFilter(request, response);
            // 请求处理结束后，读取最终响应状态码。
            statusCode = response.getStatus();
        } catch (IOException | ServletException | RuntimeException exception) {
            // 保存抛出的异常，后面构建指标属性时会带上它。
            throwable = exception;
            // 优先使用响应里已经设置的错误码，否则回退成 500。
            statusCode = response.getStatus() >= 400 ? response.getStatus() : 500;
            // 把异常信息记录到 span 上，便于后续排障。
            span.recordException(exception);
            // 把 span 标记为错误状态，告诉 tracing 后端这是一次失败请求。
            span.setStatus(StatusCode.ERROR);
            // 补充异常类型属性，便于查询和聚合。
            span.setAttribute("error.type", exception.getClass().getName());
            throw exception;
        } finally {
            // 无论成功还是失败，都在 finally 里完成本次请求的 telemetry 收尾。
            finalizeRequest(request, span, startNanos, statusCode, throwable);
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
            HttpServletRequest request, Span span, long startNanos, int statusCode, Throwable throwable) {
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
}
