package io.github.stellflux.stellorbit.circuitbreaker;

import java.util.function.Supplier;

/** StellOrbit 熔断执行器。 */
public interface StellorbitCircuitBreakerExecutor {

    /**
     * 在熔断保护下执行调用。
     *
     * @param request 熔断请求
     * @param supplier 业务调用
     * @return 业务调用结果
     * @param <T> 返回类型
     */
    <T> T execute(StellorbitCircuitBreakerRequest request, Supplier<T> supplier);
}
