package io.github.stellflux.grpc.client.annotation;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancerAlgorithm;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明一个基于 serviceId 的 gRPC 客户端。 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcClient {

    /**
     * Bean name alias.
     *
     * @return bean name
     */
    String value() default "";

    /**
     * Explicit bean name.
     *
     * @return bean name
     */
    String beanName() default "";

    /**
     * StellMap service identifier.
     *
     * @return service identifier
     */
    String serviceId() default "";

    /**
     * Namespace for StellMap watch.
     *
     * @return namespace
     */
    String namespace() default "";

    /**
     * Static host for direct mode.
     *
     * @return host
     */
    String host() default "";

    /**
     * Static port for direct mode.
     *
     * @return port
     */
    int port() default 0;

    /**
     * Use plaintext connection.
     *
     * @return whether plaintext is enabled
     */
    boolean plaintext() default true;

    /**
     * Load balancer algorithm override.
     *
     * @return load balancer algorithm
     */
    StellfluxLoadBalancerAlgorithm loadBalancer() default
            StellfluxLoadBalancerAlgorithm.LEAST_REQUEST;
}
