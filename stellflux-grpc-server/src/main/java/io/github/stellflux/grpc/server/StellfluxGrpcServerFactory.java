package io.github.stellflux.grpc.server;

import io.github.stellflux.grpc.server.internal.StellfluxGrpcServerTelemetryInterceptorAdapter;
import io.grpc.ServerInterceptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.opentelemetry.api.OpenTelemetry;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Factory for building gRPC server builders. */
public class StellfluxGrpcServerFactory {

    private final List<StellfluxGrpcServerInterceptor> interceptors;

    public StellfluxGrpcServerFactory() {
        this(null, List.of());
    }

    public StellfluxGrpcServerFactory(OpenTelemetry openTelemetry) {
        this(openTelemetry, List.of());
    }

    public StellfluxGrpcServerFactory(
            OpenTelemetry openTelemetry, List<StellfluxGrpcServerInterceptor> interceptors) {
        this.interceptors = initializeInterceptors(openTelemetry, interceptors);
    }

    /**
     * 根据配置创建 NettyServerBuilder。
     *
     * @param options gRPC 服务端配置
     * @return NettyServerBuilder
     */
    public NettyServerBuilder create(StellfluxGrpcServerOptions options) {
        NettyServerBuilder builder = createBuilder(options);
        applyTransportOptions(builder, options);
        resolveInterceptors(options).forEach(builder::intercept);
        return builder;
    }

    /**
     * 解析当前服务端配置对应的原生拦截器列表。
     *
     * @param options 服务端配置
     * @return 原生拦截器列表
     */
    List<ServerInterceptor> resolveInterceptors(StellfluxGrpcServerOptions options) {
        StellfluxGrpcServerInterceptorContext context = createInterceptorContext(options);
        return this.interceptors.stream()
                .filter(interceptor -> interceptor.supports(context))
                .map(interceptor -> interceptor.createInterceptor(context))
                .toList();
    }

    /**
     * 创建基础的 NettyServerBuilder。
     *
     * @param options gRPC 服务端配置
     * @return NettyServerBuilder
     */
    protected NettyServerBuilder createBuilder(StellfluxGrpcServerOptions options) {
        if (hasText(options.getBindAddress())) {
            return NettyServerBuilder.forAddress(
                    new InetSocketAddress(options.getBindAddress().trim(), options.getPort()));
        }
        return NettyServerBuilder.forPort(options.getPort());
    }

    /**
     * 应用 gRPC 传输层配置。
     *
     * @param builder NettyServerBuilder
     * @param options gRPC 服务端配置
     */
    protected void applyTransportOptions(
            NettyServerBuilder builder, StellfluxGrpcServerOptions options) {
        applyPositive(options.getMaxInboundMessageSize(), builder::maxInboundMessageSize);
        applyPositive(options.getMaxInboundMetadataSize(), builder::maxInboundMetadataSize);
        applyPositive(options.getFlowControlWindow(), builder::flowControlWindow);
        applyPositive(
                options.getMaxConcurrentCallsPerConnection(), builder::maxConcurrentCallsPerConnection);
        applyDuration(options.getKeepAliveTime(), builder::keepAliveTime);
        applyDuration(options.getKeepAliveTimeout(), builder::keepAliveTimeout);
        applyDuration(options.getMaxConnectionIdle(), builder::maxConnectionIdle);
        applyDuration(options.getMaxConnectionAge(), builder::maxConnectionAge);
        applyDuration(options.getMaxConnectionAgeGrace(), builder::maxConnectionAgeGrace);
        applyDuration(options.getPermitKeepAliveTime(), builder::permitKeepAliveTime);
        if (options.isPermitKeepAliveWithoutCalls()) {
            builder.permitKeepAliveWithoutCalls(true);
        }
    }

    StellfluxGrpcServerInterceptorContext createInterceptorContext(
            StellfluxGrpcServerOptions options) {
        return StellfluxGrpcServerInterceptorContext.from(options);
    }

    private void applyPositive(Integer value, java.util.function.IntConsumer consumer) {
        if (value != null && value > 0) {
            consumer.accept(value);
        }
    }

    private void applyDuration(Duration duration, DurationConfigurer durationConfigurer) {
        if (duration != null && !duration.isNegative() && !duration.isZero()) {
            durationConfigurer.configure(duration.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<StellfluxGrpcServerInterceptor> initializeInterceptors(
            OpenTelemetry openTelemetry, List<StellfluxGrpcServerInterceptor> interceptors) {
        List<StellfluxGrpcServerInterceptor> configured =
                interceptors == null ? List.of() : interceptors;
        java.util.ArrayList<StellfluxGrpcServerInterceptor> resolved =
                new java.util.ArrayList<>(configured.size() + (openTelemetry == null ? 0 : 1));
        if (openTelemetry != null) {
            resolved.add(new StellfluxGrpcServerTelemetryInterceptorAdapter(openTelemetry));
        }
        resolved.addAll(configured);
        resolved.sort(Comparator.comparingInt(StellfluxGrpcServerInterceptor::getOrder));
        return List.copyOf(resolved);
    }

    @FunctionalInterface
    private interface DurationConfigurer {

        void configure(long value, TimeUnit unit);
    }
}
