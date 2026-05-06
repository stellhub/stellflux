package io.github.stellflux.grpc.server;

import io.github.stellflux.grpc.server.internal.StellfluxGrpcServerTelemetryInterceptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.opentelemetry.api.OpenTelemetry;

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
        NettyServerBuilder builder = NettyServerBuilder.forPort(options.getPort());
        if (this.openTelemetry != null) {
            builder.intercept(
                    new StellfluxGrpcServerTelemetryInterceptor(this.openTelemetry, options.getPort()));
        }
        return builder;
    }
}
