package io.github.stellflux.grpc.client;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.StellfluxServiceInstanceSupplier;
import lombok.Getter;
import lombok.Setter;

/** gRPC client options. */
@Getter
@Setter
public class StellfluxGrpcClientOptions {

    /** Remote host. */
    private String host = "";

    /** Remote port. */
    private int port;

    /** Target service identifier for service discovery mode. */
    private String serviceId = "";

    /** Namespace for service discovery mode. */
    private String namespace = "";

    /** Endpoint protocol for service discovery mode. */
    private String protocol = "";

    /** Endpoint name for service discovery mode. */
    private String endpointName = "";

    /** Default load balancer request for service discovery mode. */
    private StellfluxLoadBalancerRequest loadBalancerRequest = StellfluxLoadBalancerRequest.empty();

    /** Service instance supplier for service discovery mode. */
    private StellfluxServiceInstanceSupplier<StellfluxServiceInstance> serviceInstanceSupplier;

    /** Load balancer for service discovery mode. */
    private StellfluxLoadBalancer<StellfluxServiceInstance> loadBalancer;

    /** Use plaintext connection. */
    private boolean plaintext = true;
}
