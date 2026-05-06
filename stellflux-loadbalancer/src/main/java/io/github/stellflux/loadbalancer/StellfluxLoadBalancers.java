package io.github.stellflux.loadbalancer;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** 负载均衡器工厂。 */
public final class StellfluxLoadBalancers {

    private StellfluxLoadBalancers() {}

    /**
     * 创建生产默认的最少请求负载均衡器。
     *
     * @return 最少请求负载均衡器
     */
    public static <T extends StellfluxServiceInstance> StellfluxLoadBalancer<T> productionDefault() {
        return new LeastRequestLoadBalancer<>();
    }

    /**
     * 创建指定随机源的最少请求负载均衡器。
     *
     * @param random 随机源
     * @return 最少请求负载均衡器
     */
    public static <T extends StellfluxServiceInstance> StellfluxLoadBalancer<T> productionDefault(
            RandomGenerator random) {
        return new LeastRequestLoadBalancer<>(random);
    }

    /**
     * 创建保守基线的加权轮询负载均衡器。
     *
     * @return 加权轮询负载均衡器
     */
    public static <T extends StellfluxServiceInstance>
            StellfluxLoadBalancer<T> conservativeBaseline() {
        return new WeightedRoundRobinLoadBalancer<>();
    }

    /**
     * 创建会话亲和负载均衡器。
     *
     * @return 一致性哈希负载均衡器
     */
    public static <T extends StellfluxServiceInstance> StellfluxLoadBalancer<T> sessionAffinity() {
        return new ConsistentHashLoadBalancer<>();
    }

    /**
     * 创建缓存友好的环哈希负载均衡器。
     *
     * @return 环哈希负载均衡器
     */
    public static <T extends StellfluxServiceInstance> StellfluxLoadBalancer<T> cacheRingHash() {
        return new RingHashLoadBalancer<>();
    }

    /**
     * 创建缓存友好的 Maglev 负载均衡器。
     *
     * @return Maglev 负载均衡器
     */
    public static <T extends StellfluxServiceInstance> StellfluxLoadBalancer<T> cacheMaglev() {
        return new MaglevLoadBalancer<>();
    }

    /**
     * 创建静态权重随机负载均衡器。
     *
     * @return 静态权重随机负载均衡器
     */
    public static <T extends StellfluxServiceInstance> StellfluxLoadBalancer<T> weighted() {
        return new WeightedRandomLoadBalancer<>();
    }

    /**
     * 创建指定随机源的静态权重随机负载均衡器。
     *
     * @param random 随机源
     * @return 静态权重随机负载均衡器
     */
    public static <T extends StellfluxServiceInstance> StellfluxLoadBalancer<T> weighted(
            RandomGenerator random) {
        return new WeightedRandomLoadBalancer<>(random);
    }

    /**
     * 创建自适应权重负载均衡器。
     *
     * @return 自适应权重负载均衡器
     */
    public static <T extends StellfluxServiceInstance> StellfluxLoadBalancer<T> adaptiveWeighted() {
        return new AdaptiveWeightedLoadBalancer<>();
    }

    /**
     * 创建指定随机源的自适应权重负载均衡器。
     *
     * @param random 随机源
     * @return 自适应权重负载均衡器
     */
    public static <T extends StellfluxServiceInstance> StellfluxLoadBalancer<T> adaptiveWeighted(
            RandomGenerator random) {
        return new AdaptiveWeightedLoadBalancer<>(random);
    }

    /**
     * 根据算法类型创建负载均衡器。
     *
     * @param algorithm 算法类型
     * @return 负载均衡器
     */
    public static <T extends StellfluxServiceInstance> StellfluxLoadBalancer<T> of(
            StellfluxLoadBalancerAlgorithm algorithm) {
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        return switch (algorithm) {
            case LEAST_REQUEST -> new LeastRequestLoadBalancer<>();
            case WEIGHTED_ROUND_ROBIN -> new WeightedRoundRobinLoadBalancer<>();
            case CONSISTENT_HASH -> new ConsistentHashLoadBalancer<>();
            case RING_HASH -> new RingHashLoadBalancer<>();
            case MAGLEV -> new MaglevLoadBalancer<>();
            case WEIGHTED_RANDOM -> new WeightedRandomLoadBalancer<>();
            case ADAPTIVE_WEIGHTED -> new AdaptiveWeightedLoadBalancer<>();
        };
    }
}
