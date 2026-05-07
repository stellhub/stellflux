package io.github.stellflux.grpc.server;

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;

/** gRPC 服务暴露元数据。 */
public record StellfluxGrpcServiceRegistration(
        String beanName,
        String grpcServiceName,
        String serviceId,
        int order,
        BindableService bindableService,
        ServerServiceDefinition serviceDefinition) {}
