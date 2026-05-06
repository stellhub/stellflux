package io.github.stellflux.loadbalancer;

import java.util.List;
import java.util.Optional;

/** 通用负载均衡器接口。 */
public interface StellfluxLoadBalancer<T extends StellfluxServiceInstance> {

    /**
     * 获取算法类型。
     *
     * @return 算法类型
     */
    StellfluxLoadBalancerAlgorithm getAlgorithm();

    /**
     * 从候选实例中选择一个目标实例。
     *
     * @param instances 候选实例列表
     * @param request 负载均衡请求上下文
     * @return 选中的实例
     */
    Optional<T> choose(List<T> instances, StellfluxLoadBalancerRequest request);
}
