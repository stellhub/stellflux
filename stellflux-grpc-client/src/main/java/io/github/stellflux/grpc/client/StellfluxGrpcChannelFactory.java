package io.github.stellflux.grpc.client;

import io.github.stellflux.grpc.client.internal.StellfluxGrpcClientTelemetryInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;

/** Factory for building gRPC channels. */
public class StellfluxGrpcChannelFactory {

    private final OpenTelemetry openTelemetry;

    public StellfluxGrpcChannelFactory() {
        this(null);
    }

    public StellfluxGrpcChannelFactory(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    /**
     * 根据配置创建 ManagedChannel。
     *
     * @param options gRPC 客户端配置
     * @return ManagedChannel
     */
    public ManagedChannel create(StellfluxGrpcClientOptions options) {
        ManagedChannelBuilder<?> builder =
                ManagedChannelBuilder.forAddress(options.getHost(), options.getPort());
        if (options.isPlaintext()) {
            builder.usePlaintext();
        }
        if (this.openTelemetry != null) {
            builder.intercept(
                    new StellfluxGrpcClientTelemetryInterceptor(
                            this.openTelemetry, options.getHost(), options.getPort()));
        }
        return builder.build();
    }
}
