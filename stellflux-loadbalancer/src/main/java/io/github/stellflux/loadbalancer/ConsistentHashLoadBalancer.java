package io.github.stellflux.loadbalancer;

import java.util.List;

/** 基于加权 Rendezvous Hash 的一致性哈希负载均衡器。 */
public class ConsistentHashLoadBalancer<T extends StellfluxServiceInstance>
        extends AbstractStellfluxLoadBalancer<T> {

    @Override
    public StellfluxLoadBalancerAlgorithm getAlgorithm() {
        return StellfluxLoadBalancerAlgorithm.CONSISTENT_HASH;
    }

    @Override
    protected T doChoose(List<T> instances, StellfluxLoadBalancerRequest request) {
        String hashKey = resolveHashKey(request);
        T selected = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (T instance : instances) {
            long hash = HashingSupport.hash64(hashKey + '\u0000' + instance.getInstanceId());
            double score =
                    Math.max(1D, instance.getWeight()) / -Math.log(HashingSupport.toUnitInterval(hash));
            if (selected == null
                    || score > bestScore
                    || (Double.compare(score, bestScore) == 0 && compareStable(instance, selected) < 0)) {
                selected = instance;
                bestScore = score;
            }
        }
        return selected;
    }
}
