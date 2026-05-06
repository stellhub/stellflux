package io.github.stellflux.grpc.client.internal;

import io.github.stellflux.metrics.StellfluxMeterFactory;
import io.github.stellflux.metrics.StellfluxMetricNames;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
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
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;

/** gRPC 客户端 telemetry 拦截器。 */
public class StellfluxGrpcClientTelemetryInterceptor implements ClientInterceptor {

    private static final String INSTRUMENTATION_SCOPE_NAME = "io.github.stellflux.grpc.client";

    private static final StellfluxMeterFactory METER_FACTORY = new StellfluxMeterFactory();

    private static final TextMapSetter<Metadata> METADATA_SETTER =
            (carrier, key, v) -> carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), v);

    private final Tracer tracer;

    private final LongCounter requestCounter;

    private final DoubleHistogram durationHistogram;

    private final OpenTelemetry openTelemetry;

    private final String host;

    private final int port;

    public StellfluxGrpcClientTelemetryInterceptor(
            OpenTelemetry openTelemetry, String host, int port) {
        this.openTelemetry = openTelemetry;
        this.host = host;
        this.port = port;
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE_NAME);
        Meter meter = openTelemetry.getMeter(INSTRUMENTATION_SCOPE_NAME);
        this.requestCounter =
                METER_FACTORY.createCounter(
                        meter, StellfluxMetricNames.GRPC_CLIENT_REQUESTS, "Total gRPC client requests");
        this.durationHistogram =
                METER_FACTORY.createHistogram(
                        meter, StellfluxMetricNames.GRPC_CLIENT_DURATION, "ms", "gRPC client call duration");
    }

    /**
     * 为 gRPC 客户端调用创建 span 并记录请求指标。
     *
     * @param method 方法描述
     * @param callOptions 调用配置
     * @param next 下游 Channel
     * @return 带 telemetry 的 ClientCall
     * @param <ReqT> 请求类型
     * @param <RespT> 响应类型
     */
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        ClientCall<ReqT, RespT> delegate = next.newCall(method, callOptions);
        return new ForwardingClientCall.SimpleForwardingClientCall<>(delegate) {

            private Span span;
            private Context context;
            private long startNanos;

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                this.span =
                        tracer.spanBuilder(method.getFullMethodName()).setSpanKind(SpanKind.CLIENT).startSpan();
                this.context = Context.current().with(this.span);
                this.startNanos = System.nanoTime();
                populateSpanAttributes(this.span, method);
                openTelemetry
                        .getPropagators()
                        .getTextMapPropagator()
                        .inject(this.context, headers, METADATA_SETTER);
                try (Scope ignored = this.context.makeCurrent()) {
                    super.start(
                            new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(
                                    responseListener) {
                                @Override
                                public void onHeaders(Metadata headers) {
                                    try (Scope innerIgnored = context.makeCurrent()) {
                                        super.onHeaders(headers);
                                    }
                                }

                                @Override
                                public void onMessage(RespT message) {
                                    try (Scope innerIgnored = context.makeCurrent()) {
                                        super.onMessage(message);
                                    }
                                }

                                @Override
                                public void onClose(Status status, Metadata trailers) {
                                    try (Scope innerIgnored = context.makeCurrent()) {
                                        recordCompletion(method, span, startNanos, status, null);
                                        super.onClose(status, trailers);
                                    } finally {
                                        span.end();
                                    }
                                }

                                @Override
                                public void onReady() {
                                    try (Scope innerIgnored = context.makeCurrent()) {
                                        super.onReady();
                                    }
                                }
                            },
                            headers);
                } catch (RuntimeException exception) {
                    recordFailure(method, span, startNanos, exception);
                    this.span.end();
                    throw exception;
                }
            }

            @Override
            public void sendMessage(ReqT message) {
                try (Scope ignored = this.context == null ? null : this.context.makeCurrent()) {
                    super.sendMessage(message);
                }
            }

            @Override
            public void cancel(String message, Throwable cause) {
                if (this.span != null) {
                    if (cause != null) {
                        recordFailure(method, this.span, this.startNanos, cause);
                    } else {
                        recordCompletion(method, this.span, this.startNanos, Status.CANCELLED, null);
                        this.span.setStatus(StatusCode.ERROR);
                    }
                }
                super.cancel(message, cause);
            }
        };
    }

    private void populateSpanAttributes(Span span, MethodDescriptor<?, ?> method) {
        span.setAttribute("rpc.system", "grpc");
        span.setAttribute("rpc.service", serviceName(method));
        span.setAttribute("rpc.method", methodName(method));
        span.setAttribute("server.address", this.host);
        span.setAttribute("server.port", this.port);
    }

    private void recordCompletion(
            MethodDescriptor<?, ?> method,
            Span span,
            long startNanos,
            Status status,
            Throwable throwable) {
        if (throwable != null) {
            span.recordException(throwable);
            span.setStatus(StatusCode.ERROR);
            span.setAttribute("error.type", throwable.getClass().getName());
        } else {
            span.setAttribute("rpc.grpc.status_code", status.getCode().name());
            if (!status.isOk()) {
                span.setStatus(StatusCode.ERROR);
            }
        }
        Attributes metricAttributes = buildMetricAttributes(method, status, throwable);
        requestCounter.add(1, metricAttributes);
        durationHistogram.record((System.nanoTime() - startNanos) / 1_000_000.0d, metricAttributes);
    }

    private void recordFailure(
            MethodDescriptor<?, ?> method, Span span, long startNanos, Throwable throwable) {
        recordCompletion(method, span, startNanos, Status.UNKNOWN, throwable);
    }

    private Attributes buildMetricAttributes(
            MethodDescriptor<?, ?> method, Status status, Throwable throwable) {
        AttributesBuilder attributes = Attributes.builder();
        attributes.put("rpc.system", "grpc");
        attributes.put("rpc.service", serviceName(method));
        attributes.put("rpc.method", methodName(method));
        attributes.put("server.address", this.host);
        attributes.put("server.port", (long) this.port);
        attributes.put("rpc.grpc.status_code", status.getCode().name());
        if (throwable != null) {
            attributes.put("error.type", throwable.getClass().getName());
        }
        return attributes.build();
    }

    private String serviceName(MethodDescriptor<?, ?> method) {
        String fullMethodName = method.getFullMethodName();
        int separator = fullMethodName.lastIndexOf('/');
        return separator > 0 ? fullMethodName.substring(0, separator) : fullMethodName;
    }

    private String methodName(MethodDescriptor<?, ?> method) {
        String fullMethodName = method.getFullMethodName();
        int separator = fullMethodName.lastIndexOf('/');
        return separator >= 0 ? fullMethodName.substring(separator + 1) : fullMethodName;
    }
}
