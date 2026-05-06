package io.github.stellflux.grpc.client;

import io.github.stellflux.grpc.client.internal.StellfluxGrpcClientTelemetryInterceptor;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.api.OpenTelemetry;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** Factory for building gRPC channels. */
public class StellfluxGrpcChannelFactory {

    private static final Logger LOGGER = Logger.getLogger(StellfluxGrpcChannelFactory.class.getName());

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
        ResolvedGrpcTarget target = resolveTarget(options);
        ManagedChannelBuilder<?> builder =
                ManagedChannelBuilder.forAddress(target.host(), target.port());
        if (options.isPlaintext()) {
            builder.usePlaintext();
        }
        if (this.openTelemetry != null) {
            builder.intercept(
                    new StellfluxGrpcClientTelemetryInterceptor(
                            this.openTelemetry, target.host(), target.port()));
        }
        ManagedChannel channel = builder.build();
        LOGGER.info(() -> buildInitializationLog(options, target));
        return channel;
    }

    /**
     * 解析当前 gRPC 连接应命中的目标地址。
     *
     * @param options gRPC 客户端配置
     * @return 解析后的目标地址
     */
    ResolvedGrpcTarget resolveTarget(StellfluxGrpcClientOptions options) {
        if (isDirectMode(options)) {
            return new ResolvedGrpcTarget(options.getHost(), options.getPort());
        }
        if (options.getServiceInstanceSupplier() == null || options.getLoadBalancer() == null) {
            return new ResolvedGrpcTarget(options.getHost(), options.getPort());
        }
        StellfluxLoadBalancerRequest defaultRequest =
                options.getLoadBalancerRequest() == null
                        ? StellfluxLoadBalancerRequest.empty()
                        : options.getLoadBalancerRequest();
        StellfluxLoadBalancerRequest effectiveRequest =
                StellfluxLoadBalancerRequest.builder()
                        .serviceId(options.getServiceId())
                        .hashKey(defaultRequest.getHashKey())
                        .attributes(defaultRequest.getAttributes())
                        .build();
        List<StellfluxServiceInstance> instances =
                options.getServiceInstanceSupplier().getInstances(effectiveRequest);
        if (instances == null || instances.isEmpty()) {
            throw new IllegalStateException(
                    "No available service instances for serviceId=" + effectiveRequest.getServiceId());
        }
        StellfluxServiceInstance selected =
                options
                        .getLoadBalancer()
                        .choose(instances, effectiveRequest)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Failed to choose service instance for serviceId="
                                                        + effectiveRequest.getServiceId()));
        return new ResolvedGrpcTarget(selected.getHost(), selected.getPort());
    }

    private boolean isDirectMode(StellfluxGrpcClientOptions options) {
        return options.getHost() != null && !options.getHost().isBlank() && options.getPort() > 0;
    }

    private String buildInitializationLog(
            StellfluxGrpcClientOptions options, ResolvedGrpcTarget target) {
        StellfluxLoadBalancerRequest defaultRequest =
                options.getLoadBalancerRequest() == null
                        ? StellfluxLoadBalancerRequest.empty()
                        : options.getLoadBalancerRequest();
        String mode = isDirectMode(options) ? "direct" : "discovery";
        return "Initialized StellfluxGrpcChannel"
                + " mode=" + mode
                + ", serviceId=" + safeText(options.getServiceId())
                + ", namespace=" + safeText(options.getNamespace())
                + ", directHost=" + safeText(options.getHost())
                + ", directPort=" + options.getPort()
                + ", resolvedTarget=" + target.host() + ":" + target.port()
                + ", plaintext=" + options.isPlaintext()
                + ", loadBalancer=" + resolveLoadBalancer(options)
                + ", supplier=" + resolveSupplier(options)
                + ", requestHashKey=" + safeText(defaultRequest.getHashKey())
                + ", requestAttributes=" + formatAttributes(defaultRequest.getAttributes())
                + ", telemetryEnabled=" + (this.openTelemetry != null);
    }

    private String resolveLoadBalancer(StellfluxGrpcClientOptions options) {
        if (options.getLoadBalancer() == null || options.getLoadBalancer().getAlgorithm() == null) {
            return "<none>";
        }
        return options.getLoadBalancer().getAlgorithm().name();
    }

    private String resolveSupplier(StellfluxGrpcClientOptions options) {
        if (options.getServiceInstanceSupplier() == null) {
            return "<none>";
        }
        return options.getServiceInstanceSupplier().getClass().getName();
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "<none>" : value;
    }

    private String formatAttributes(Map<String, String> attributes) {
        return attributes == null || attributes.isEmpty() ? "{}" : attributes.toString();
    }

    record ResolvedGrpcTarget(String host, int port) {}
}
