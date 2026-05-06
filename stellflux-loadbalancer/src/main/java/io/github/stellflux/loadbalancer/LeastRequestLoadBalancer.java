package io.github.stellflux.loadbalancer;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** 最少请求 P2C 负载均衡器。 */
public class LeastRequestLoadBalancer<T extends StellfluxServiceInstance>
        extends AbstractStellfluxLoadBalancer<T> {

    private final RandomGenerator random;

    public LeastRequestLoadBalancer() {
        this(RandomGenerator.getDefault());
    }

    public LeastRequestLoadBalancer(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    @Override
    public StellfluxLoadBalancerAlgorithm getAlgorithm() {
        return StellfluxLoadBalancerAlgorithm.LEAST_REQUEST;
    }

    @Override
    protected T doChoose(List<T> instances, StellfluxLoadBalancerRequest request) {
        int firstIndex = random.nextInt(instances.size());
        int secondIndex = random.nextInt(instances.size() - 1);
        if (secondIndex >= firstIndex) {
            secondIndex++;
        }
        T first = instances.get(firstIndex);
        T second = instances.get(secondIndex);
        return better(first, second);
    }

    private T better(T first, T second) {
        double firstScore = score(first);
        double secondScore = score(second);
        int comparison = Double.compare(firstScore, secondScore);
        if (comparison < 0) {
            return first;
        }
        if (comparison > 0) {
            return second;
        }
        if (first.getWeight() != second.getWeight()) {
            return first.getWeight() > second.getWeight() ? first : second;
        }
        return compareStable(first, second) <= 0 ? first : second;
    }

    private double score(T instance) {
        return (instance.getActiveRequests() + 1D) / Math.max(1, instance.getWeight());
    }
}
