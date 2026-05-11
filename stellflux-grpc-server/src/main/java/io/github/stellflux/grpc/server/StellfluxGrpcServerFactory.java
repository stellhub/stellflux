package io.github.stellflux.grpc.server;

import io.github.stellflux.grpc.server.internal.StellfluxGrpcServerTelemetryInterceptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.opentelemetry.api.OpenTelemetry;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Factory for building gRPC server builders. */
public class StellfluxGrpcServerFactory {

    private final OpenTelemetry openTelemetry;

    public StellfluxGrpcServerFactory() {
        this(null);
    }

    public StellfluxGrpcServerFactory(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
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
        if (this.openTelemetry != null) {
            builder.intercept(
                    new StellfluxGrpcServerTelemetryInterceptor(this.openTelemetry, options.getPort()));
        }
        return builder;
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

    @FunctionalInterface
    private interface DurationConfigurer {

        void configure(long value, TimeUnit unit);
    }
}
