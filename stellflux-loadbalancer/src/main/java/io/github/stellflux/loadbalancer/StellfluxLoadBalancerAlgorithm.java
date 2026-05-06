package io.github.stellflux.loadbalancer;

/** 负载均衡算法类型。 */
public enum StellfluxLoadBalancerAlgorithm {
    LEAST_REQUEST,
    WEIGHTED_ROUND_ROBIN,
    CONSISTENT_HASH,
    RING_HASH,
    MAGLEV,
    WEIGHTED_RANDOM,
    ADAPTIVE_WEIGHTED
}
