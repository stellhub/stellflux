package io.github.stellflux.loadbalancer;

import java.util.List;
import java.util.Optional;

/** 负载均衡器抽象基类。 */
abstract class AbstractStellfluxLoadBalancer<T extends StellfluxServiceInstance>
        implements StellfluxLoadBalancer<T> {

    @Override
    public final Optional<T> choose(List<T> instances, StellfluxLoadBalancerRequest request) {
        if (instances == null || instances.isEmpty()) {
            return Optional.empty();
        }
        if (instances.size() == 1) {
            return Optional.of(instances.get(0));
        }
        return Optional.of(
                doChoose(instances, request == null ? StellfluxLoadBalancerRequest.empty() : request));
    }

    /**
     * 执行实际实例选择。
     *
     * @param instances 候选实例
     * @param request 请求上下文
     * @return 选中的实例
     */
    protected abstract T doChoose(List<T> instances, StellfluxLoadBalancerRequest request);

    /**
     * 解析服务分组键。
     *
     * @param request 请求上下文
     * @return 服务分组键
     */
    protected String resolveServiceScope(StellfluxLoadBalancerRequest request) {
        if (request.getServiceId() != null && !request.getServiceId().isBlank()) {
            return request.getServiceId();
        }
        return "__default__";
    }

    /**
     * 解析哈希键。
     *
     * @param request 请求上下文
     * @return 哈希键
     */
    protected String resolveHashKey(StellfluxLoadBalancerRequest request) {
        if (request.getHashKey() != null && !request.getHashKey().isBlank()) {
            return request.getHashKey();
        }
        return resolveServiceScope(request);
    }

    /**
     * 比较两个实例的稳定顺序。
     *
     * @param left 左实例
     * @param right 右实例
     * @return 比较结果
     */
    protected int compareStable(T left, T right) {
        int result = left.getInstanceId().compareTo(right.getInstanceId());
        if (result != 0) {
            return result;
        }
        result = left.getHost().compareTo(right.getHost());
        if (result != 0) {
            return result;
        }
        return Integer.compare(left.getPort(), right.getPort());
    }
}
