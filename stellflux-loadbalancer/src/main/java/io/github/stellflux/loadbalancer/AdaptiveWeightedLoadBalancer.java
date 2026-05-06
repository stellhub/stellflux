package io.github.stellflux.loadbalancer;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** 自适应权重负载均衡器。 */
public class AdaptiveWeightedLoadBalancer<T extends StellfluxServiceInstance>
        extends AbstractStellfluxLoadBalancer<T> {

    private final RandomGenerator random;

    public AdaptiveWeightedLoadBalancer() {
        this(RandomGenerator.getDefault());
    }

    public AdaptiveWeightedLoadBalancer(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    @Override
    public StellfluxLoadBalancerAlgorithm getAlgorithm() {
        return StellfluxLoadBalancerAlgorithm.ADAPTIVE_WEIGHTED;
    }

    @Override
    protected T doChoose(List<T> instances, StellfluxLoadBalancerRequest request) {
        double totalWeight = 0D;
        for (T instance : instances) {
            totalWeight += effectiveWeight(instance);
        }
        double ticket = random.nextDouble(totalWeight);
        double cumulativeWeight = 0D;
        for (T instance : instances) {
            cumulativeWeight += effectiveWeight(instance);
            if (ticket < cumulativeWeight) {
                return instance;
            }
        }
        return instances.get(instances.size() - 1);
    }

    private double effectiveWeight(T instance) {
        return Math.max(1D, instance.getWeight()) / (1D + instance.getActiveRequests());
    }
}
