package io.github.stellflux.loadbalancer;

import java.util.ArrayList;
import java.util.List;

/** Maglev 负载均衡器。 */
public class MaglevLoadBalancer<T extends StellfluxServiceInstance>
        extends AbstractStellfluxLoadBalancer<T> {

    private final int tableSize;

    public MaglevLoadBalancer() {
        this(65537);
    }

    public MaglevLoadBalancer(int tableSize) {
        if (tableSize <= 1) {
            throw new IllegalArgumentException("tableSize must be greater than one");
        }
        this.tableSize = tableSize;
    }

    @Override
    public StellfluxLoadBalancerAlgorithm getAlgorithm() {
        return StellfluxLoadBalancerAlgorithm.MAGLEV;
    }

    @Override
    protected T doChoose(List<T> instances, StellfluxLoadBalancerRequest request) {
        List<T> expandedBackends = expandBackends(instances);
        int backendCount = expandedBackends.size();
        int[] offsets = new int[backendCount];
        int[] skips = new int[backendCount];
        int[] next = new int[backendCount];
        @SuppressWarnings("unchecked")
        T[] lookup = (T[]) new StellfluxServiceInstance[this.tableSize];

        for (int i = 0; i < backendCount; i++) {
            T backend = expandedBackends.get(i);
            offsets[i] =
                    (int)
                            (HashingSupport.positiveHash64(backend.getInstanceId() + "#offset") % this.tableSize);
            skips[i] =
                    (int)
                                    (HashingSupport.positiveHash64(backend.getInstanceId() + "#skip")
                                            % (this.tableSize - 1))
                            + 1;
        }

        int filled = 0;
        while (filled < this.tableSize) {
            for (int i = 0; i < backendCount && filled < this.tableSize; i++) {
                int position = (offsets[i] + next[i] * skips[i]) % this.tableSize;
                while (lookup[position] != null) {
                    next[i]++;
                    position = (offsets[i] + next[i] * skips[i]) % this.tableSize;
                }
                lookup[position] = expandedBackends.get(i);
                next[i]++;
                filled++;
            }
        }

        int requestSlot =
                (int) (HashingSupport.positiveHash64(resolveHashKey(request)) % this.tableSize);
        return lookup[requestSlot];
    }

    private List<T> expandBackends(List<T> instances) {
        int totalWeight = instances.stream().mapToInt(StellfluxServiceInstance::getWeight).sum();
        int maxReplicaBudget = Math.max(instances.size(), 128);
        List<T> backends = new ArrayList<>();
        for (T instance : instances) {
            int replicas;
            if (totalWeight <= maxReplicaBudget) {
                replicas = instance.getWeight();
            } else {
                replicas =
                        Math.max(
                                1, (int) Math.round((instance.getWeight() * 1D / totalWeight) * maxReplicaBudget));
            }
            for (int i = 0; i < replicas; i++) {
                backends.add(instance);
            }
        }
        return backends;
    }
}
