package io.github.stellflux.http.client;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.StellfluxServiceInstanceSupplier;
import lombok.Getter;
import lombok.Setter;

/** HTTP client options. */
@Getter
@Setter
public class StellfluxHttpClientOptions {

    /** Base URL. */
    private String baseUrl = "";

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

    /** Connect timeout in milliseconds. */
    private long connectTimeoutMillis = 3_000L;

    /** Read timeout in milliseconds. */
    private long readTimeoutMillis = 5_000L;

    /** Write timeout in milliseconds. */
    private long writeTimeoutMillis = 5_000L;

    /** Call timeout in milliseconds. */
    private long callTimeoutMillis;

    /** Ping interval in milliseconds. */
    private long pingIntervalMillis;

    /** Retry on connection failure. */
    private boolean retryOnConnectionFailure = true;

    /** Follow redirects. */
    private boolean followRedirects = true;

    /** Follow SSL redirects. */
    private boolean followSslRedirects = true;
}
