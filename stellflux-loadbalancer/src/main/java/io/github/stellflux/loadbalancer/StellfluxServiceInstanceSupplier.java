package io.github.stellflux.loadbalancer;

import java.util.List;

/** 服务实例提供器。 */
@FunctionalInterface
public interface StellfluxServiceInstanceSupplier<T extends StellfluxServiceInstance> {

    /**
     * 根据请求上下文返回候选实例列表。
     *
     * @param request 负载均衡请求上下文
     * @return 候选实例列表
     */
    List<T> getInstances(StellfluxLoadBalancerRequest request);
}
