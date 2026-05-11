package io.github.stellflux.grpc.server.internal;

import io.github.stellflux.grpc.server.StellfluxGrpcServerInterceptor;
import io.github.stellflux.grpc.server.StellfluxGrpcServerInterceptorContext;
import io.github.stellflux.grpc.server.StellfluxGrpcServerInterceptorOrder;
import io.grpc.ServerInterceptor;
import io.opentelemetry.api.OpenTelemetry;

/** gRPC 服务端 telemetry 拦截器适配器。 */
public class StellfluxGrpcServerTelemetryInterceptorAdapter
        implements StellfluxGrpcServerInterceptor {

    private final OpenTelemetry openTelemetry;

    public StellfluxGrpcServerTelemetryInterceptorAdapter(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public int getOrder() {
        return StellfluxGrpcServerInterceptorOrder.FRAMEWORK_OUTER;
    }

    @Override
    public ServerInterceptor createInterceptor(StellfluxGrpcServerInterceptorContext context) {
        return new StellfluxGrpcServerTelemetryInterceptor(this.openTelemetry, context.port());
    }
}
