package io.github.stellflux.grpc.client.internal;

import io.github.stellflux.grpc.client.StellfluxGrpcClientInterceptor;
import io.github.stellflux.grpc.client.StellfluxGrpcClientInterceptorContext;
import io.github.stellflux.grpc.client.StellfluxGrpcClientInterceptorOrder;
import io.grpc.ClientInterceptor;
import io.opentelemetry.api.OpenTelemetry;

/** gRPC 客户端 telemetry 拦截器适配器。 */
public class StellfluxGrpcClientTelemetryInterceptorAdapter
        implements StellfluxGrpcClientInterceptor {

    private final OpenTelemetry openTelemetry;

    public StellfluxGrpcClientTelemetryInterceptorAdapter(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public int getOrder() {
        return StellfluxGrpcClientInterceptorOrder.FRAMEWORK_OUTER;
    }

    @Override
    public ClientInterceptor createInterceptor(StellfluxGrpcClientInterceptorContext context) {
        return new StellfluxGrpcClientTelemetryInterceptor(
                this.openTelemetry, context.host(), context.port());
    }
}
