package io.github.stellflux.loadbalancer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 平滑加权轮询负载均衡器。 */
public class WeightedRoundRobinLoadBalancer<T extends StellfluxServiceInstance>
        extends AbstractStellfluxLoadBalancer<T> {

    private final Map<String, Map<String, Long>> stateByService = new HashMap<>();

    @Override
    public StellfluxLoadBalancerAlgorithm getAlgorithm() {
        return StellfluxLoadBalancerAlgorithm.WEIGHTED_ROUND_ROBIN;
    }

    @Override
    protected synchronized T doChoose(List<T> instances, StellfluxLoadBalancerRequest request) {
        String serviceScope = resolveServiceScope(request);
        Map<String, Long> currentWeights =
                stateByService.computeIfAbsent(serviceScope, key -> new HashMap<>());
        Set<String> liveInstanceIds = new HashSet<>();
        long totalWeight = 0L;
        T selected = null;
        long selectedCurrentWeight = Long.MIN_VALUE;

        for (T instance : instances) {
            liveInstanceIds.add(instance.getInstanceId());
            totalWeight += instance.getWeight();
            long updatedCurrentWeight =
                    currentWeights.getOrDefault(instance.getInstanceId(), 0L) + instance.getWeight();
            currentWeights.put(instance.getInstanceId(), updatedCurrentWeight);
            if (selected == null
                    || updatedCurrentWeight > selectedCurrentWeight
                    || (updatedCurrentWeight == selectedCurrentWeight
                            && compareStable(instance, selected) < 0)) {
                selected = instance;
                selectedCurrentWeight = updatedCurrentWeight;
            }
        }

        currentWeights.keySet().removeIf(instanceId -> !liveInstanceIds.contains(instanceId));
        String selectedInstanceId = selected.getInstanceId();
        long totalWeightSnapshot = totalWeight;
        currentWeights.computeIfPresent(
                selectedInstanceId,
                (instanceId, currentWeight) -> currentWeight - totalWeightSnapshot);
        return selected;
    }
}
