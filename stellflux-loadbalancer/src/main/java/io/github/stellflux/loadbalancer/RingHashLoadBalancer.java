package io.github.stellflux.loadbalancer;

import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

/** 环哈希负载均衡器。 */
public class RingHashLoadBalancer<T extends StellfluxServiceInstance>
        extends AbstractStellfluxLoadBalancer<T> {

    private final int pointsPerWeight;

    public RingHashLoadBalancer() {
        this(128);
    }

    public RingHashLoadBalancer(int pointsPerWeight) {
        if (pointsPerWeight <= 0) {
            throw new IllegalArgumentException("pointsPerWeight must be greater than zero");
        }
        this.pointsPerWeight = pointsPerWeight;
    }

    @Override
    public StellfluxLoadBalancerAlgorithm getAlgorithm() {
        return StellfluxLoadBalancerAlgorithm.RING_HASH;
    }

    @Override
    protected T doChoose(List<T> instances, StellfluxLoadBalancerRequest request) {
        NavigableMap<Long, T> ring = new TreeMap<>();
        for (T instance : instances) {
            int virtualNodes = Math.max(1, instance.getWeight()) * this.pointsPerWeight;
            for (int i = 0; i < virtualNodes; i++) {
                String vnodeKey = instance.getInstanceId() + '#' + i;
                long hash = HashingSupport.positiveHash64(vnodeKey);
                while (ring.containsKey(hash)) {
                    hash++;
                }
                ring.put(hash, instance);
            }
        }
        long requestHash = HashingSupport.positiveHash64(resolveHashKey(request));
        Entry<Long, T> entry = ring.ceilingEntry(requestHash);
        return entry != null ? entry.getValue() : ring.firstEntry().getValue();
    }
}
