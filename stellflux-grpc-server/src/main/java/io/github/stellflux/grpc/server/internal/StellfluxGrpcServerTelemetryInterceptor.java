package io.github.stellflux.grpc.server.internal;

import io.github.stellflux.metrics.StellfluxMeterFactory;
import io.github.stellflux.metrics.StellfluxMetricNames;
import io.github.stellflux.opentelemetry.log.StellfluxAccessLogEmitter;
import io.github.stellflux.opentelemetry.scope.StellfluxTelemetryScopeFactory;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
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
import io.opentelemetry.context.propagation.TextMapGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/** gRPC 服务端 telemetry 拦截器。 */
public class StellfluxGrpcServerTelemetryInterceptor implements ServerInterceptor {

    private static final String INSTRUMENTATION_SCOPE_NAME = "io.github.stellflux.grpc.server";

    private static final String ACCESS_LOG_SCOPE_NAME = "io.github.stellflux.grpc.server.access";

    private static final String ACCESS_LOG_EVENT_NAME = "rpc.server.request";

    private static final String ARTIFACT_ID = "stellflux-grpc-server";

    private static final StellfluxMeterFactory METER_FACTORY = new StellfluxMeterFactory();

    private static final TextMapGetter<Metadata> METADATA_GETTER =
            new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(Metadata carrier) {
                    return carrier.keys();
                }

                @Override
                public String get(Metadata carrier, String key) {
                    if (carrier == null) {
                        return null;
                    }
                    return carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
                }
            };

    private final OpenTelemetry openTelemetry;

    private final Tracer tracer;

    private final LongCounter requestCounter;

    private final DoubleHistogram durationHistogram;

    private final StellfluxAccessLogEmitter accessLogEmitter;

    private final int port;

    public StellfluxGrpcServerTelemetryInterceptor(OpenTelemetry openTelemetry, int port) {
        this.openTelemetry = openTelemetry;
        this.port = port;
        this.tracer =
                StellfluxTelemetryScopeFactory.createTracer(
                        openTelemetry,
                        INSTRUMENTATION_SCOPE_NAME,
                        ARTIFACT_ID,
                        StellfluxGrpcServerTelemetryInterceptor.class);
        this.accessLogEmitter =
                new StellfluxAccessLogEmitter(
                        openTelemetry,
                        ACCESS_LOG_SCOPE_NAME,
                        ACCESS_LOG_EVENT_NAME,
                        ARTIFACT_ID,
                        StellfluxGrpcServerTelemetryInterceptor.class);
        Meter meter =
                METER_FACTORY.create(
                        openTelemetry,
                        INSTRUMENTATION_SCOPE_NAME,
                        ARTIFACT_ID,
                        StellfluxGrpcServerTelemetryInterceptor.class);
        this.requestCounter =
                METER_FACTORY.createCounter(
                        meter, StellfluxMetricNames.GRPC_SERVER_REQUESTS, "Total gRPC server requests");
        this.durationHistogram =
                METER_FACTORY.createHistogram(
                        meter, StellfluxMetricNames.GRPC_SERVER_DURATION, "ms", "gRPC server call duration");
    }

    /**
     * 为 gRPC 服务端调用创建 span 并记录请求指标。
     *
     * @param call gRPC 服务端调用
     * @param headers 请求头
     * @param next 下游处理器
     * @return 包装后的 listener
     * @param <ReqT> 请求类型
     * @param <RespT> 响应类型
     */
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        MethodDescriptor<ReqT, RespT> method = call.getMethodDescriptor();
        Context extracted =
                openTelemetry
                        .getPropagators()
                        .getTextMapPropagator()
                        .extract(Context.current(), headers, METADATA_GETTER);
        Span span =
                tracer
                        .spanBuilder(method.getFullMethodName())
                        .setParent(extracted)
                        .setSpanKind(SpanKind.SERVER)
                        .startSpan();
        populateSpanAttributes(span, method);
        Context context = extracted.with(span);
        AtomicBoolean ended = new AtomicBoolean(false);
        long startNanos = System.nanoTime();

        ServerCall<ReqT, RespT> telemetryCall =
                new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        try {
                            finalizeCall(method, call, context, span, startNanos, status, null, ended);
                        } finally {
                            super.close(status, trailers);
                        }
                    }
                };

        ServerCall.Listener<ReqT> delegate;
        try (Scope ignored = context.makeCurrent()) {
            delegate = next.startCall(telemetryCall, headers);
        } catch (RuntimeException exception) {
            finalizeCall(method, call, context, span, startNanos, Status.UNKNOWN, exception, ended);
            throw exception;
        }

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                try (Scope ignored = context.makeCurrent()) {
                    super.onMessage(message);
                }
            }

            @Override
            public void onHalfClose() {
                try (Scope ignored = context.makeCurrent()) {
                    super.onHalfClose();
                } catch (RuntimeException exception) {
                    finalizeCall(method, call, context, span, startNanos, Status.UNKNOWN, exception, ended);
                    throw exception;
                }
            }

            @Override
            public void onCancel() {
                try (Scope ignored = context.makeCurrent()) {
                    finalizeCall(method, call, context, span, startNanos, Status.CANCELLED, null, ended);
                    super.onCancel();
                }
            }

            @Override
            public void onComplete() {
                try (Scope ignored = context.makeCurrent()) {
                    finalizeCall(method, call, context, span, startNanos, Status.OK, null, ended);
                    super.onComplete();
                }
            }

            @Override
            public void onReady() {
                try (Scope ignored = context.makeCurrent()) {
                    super.onReady();
                }
            }
        };
    }

    private void finalizeCall(
            MethodDescriptor<?, ?> method,
            ServerCall<?, ?> call,
            Context context,
            Span span,
            long startNanos,
            Status status,
            Throwable throwable,
            AtomicBoolean ended) {
        if (!ended.compareAndSet(false, true)) {
            return;
        }
        span.setAttribute("rpc.grpc.status_code", status.getCode().name());
        if (throwable != null) {
            span.recordException(throwable);
            span.setStatus(StatusCode.ERROR);
            span.setAttribute("error.type", throwable.getClass().getName());
        } else if (!status.isOk()) {
            span.setStatus(StatusCode.ERROR);
        }
        Attributes metricAttributes = buildMetricAttributes(method, status, throwable);
        requestCounter.add(1, metricAttributes);
        durationHistogram.record((System.nanoTime() - startNanos) / 1_000_000.0d, metricAttributes);
        emitAccessLog(context, call, method, status, throwable);
        span.end();
    }

    private void populateSpanAttributes(Span span, MethodDescriptor<?, ?> method) {
        span.setAttribute("rpc.system", "grpc");
        span.setAttribute("rpc.service", serviceName(method));
        span.setAttribute("rpc.method", methodName(method));
        span.setAttribute("server.port", this.port);
    }

    private Attributes buildMetricAttributes(
            MethodDescriptor<?, ?> method, Status status, Throwable throwable) {
        AttributesBuilder attributes = Attributes.builder();
        attributes.put("rpc.system", "grpc");
        attributes.put("rpc.service", serviceName(method));
        attributes.put("rpc.method", methodName(method));
        attributes.put("server.port", (long) this.port);
        attributes.put("rpc.grpc.status_code", status.getCode().name());
        if (throwable != null) {
            attributes.put("error.type", throwable.getClass().getName());
        }
        return attributes.build();
    }

    private void emitAccessLog(
            Context context,
            ServerCall<?, ?> call,
            MethodDescriptor<?, ?> method,
            Status status,
            Throwable throwable) {
        accessLogEmitter.emit(
                context,
                "gRPC server request completed",
                builder -> {
                    builder.setAttribute(AttributeKey.stringKey("rpc.system"), "grpc");
                    builder.setAttribute(AttributeKey.stringKey("rpc.service"), serviceName(method));
                    builder.setAttribute(AttributeKey.stringKey("rpc.method"), methodName(method));
                    builder.setAttribute(AttributeKey.longKey("server.port"), (long) this.port);
                    builder.setAttribute(
                            AttributeKey.stringKey("rpc.grpc.status_code"), status.getCode().name());
                    putSocketAddress(
                            builder, "server.address", call.getAttributes().get(Grpc.TRANSPORT_ATTR_LOCAL_ADDR));
                    putSocketAddress(
                            builder, "client.address", call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR));
                    if (throwable != null) {
                        builder.setAttribute(
                                AttributeKey.stringKey("error.type"), throwable.getClass().getName());
                    }
                });
    }

    private void putSocketAddress(
            io.opentelemetry.api.logs.LogRecordBuilder builder, String key, SocketAddress address) {
        String value = socketAddressText(address);
        if (value != null && !value.isBlank()) {
            builder.setAttribute(AttributeKey.stringKey(key), value);
        }
    }

    private String socketAddressText(SocketAddress address) {
        if (address instanceof InetSocketAddress inetSocketAddress) {
            return inetSocketAddress.getHostString();
        }
        return address == null ? null : address.toString();
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
