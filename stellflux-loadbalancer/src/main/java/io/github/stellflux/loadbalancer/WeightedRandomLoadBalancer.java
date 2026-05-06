package io.github.stellflux.loadbalancer;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** 静态权重随机负载均衡器。 */
public class WeightedRandomLoadBalancer<T extends StellfluxServiceInstance>
        extends AbstractStellfluxLoadBalancer<T> {

    private final RandomGenerator random;

    public WeightedRandomLoadBalancer() {
        this(RandomGenerator.getDefault());
    }

    public WeightedRandomLoadBalancer(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    @Override
    public StellfluxLoadBalancerAlgorithm getAlgorithm() {
        return StellfluxLoadBalancerAlgorithm.WEIGHTED_RANDOM;
    }

    @Override
    protected T doChoose(List<T> instances, StellfluxLoadBalancerRequest request) {
        double totalWeight = instances.stream().mapToDouble(StellfluxServiceInstance::getWeight).sum();
        double ticket = random.nextDouble(totalWeight);
        double cumulativeWeight = 0D;
        for (T instance : instances) {
            cumulativeWeight += instance.getWeight();
            if (ticket < cumulativeWeight) {
                return instance;
            }
        }
        return instances.get(instances.size() - 1);
    }
}
