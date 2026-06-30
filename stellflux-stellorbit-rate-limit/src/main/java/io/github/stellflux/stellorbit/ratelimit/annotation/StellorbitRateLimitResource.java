package io.github.stellflux.stellorbit.ratelimit.annotation;

import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标识需要由 StellOrbit 限流保护的方法资源。 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StellorbitRateLimitResource {

    /** 治理限流规则 key，等价于 key。 */
    String value() default "";

    /** 治理限流规则 key，优先级高于 value。 */
    String key() default "";

    /** 治理目标服务名，默认使用 stellflux.stellorbit.target-service 或 spring.application.name。 */
    String serviceName() default "";

    /** 限流配额申请模式。 */
    RateLimitAcquireMode mode() default RateLimitAcquireMode.REJECTING;

    /** 阻塞式限流最大等待时间，单位毫秒；小于等于 0 表示不限制等待时间。 */
    long timeoutMillis() default 0L;

    /** 本次请求消耗的配额数量。 */
    long cost() default 1L;

    /** 传递给分布式限流服务端的资源名，默认使用治理限流规则 key。 */
    String resource() default "";

    /** 传递给分布式限流服务端的方法名，默认使用 Java 方法名。 */
    String method() default "";

    /** 租户 ID。 */
    String tenantId() default "";

    /** 用户 ID。 */
    String userId() default "";

    /** 鉴权上下文 ID。 */
    String authContextId() default "";

    /** 流量等级。 */
    String trafficClass() default "";

    /** 流量标签。 */
    String trafficTag() default "";

    /** 限流拒绝 fallback 方法名，支持原方法参数、原方法参数加异常、仅异常或无参数。 */
    String fallback() default "";

    /** fallback 方法所在类型，默认在当前 Bean 上查找；指定后优先从 Spring 容器获取该类型 Bean。 */
    Class<?> fallbackClass() default Void.class;

    /** 未配置 fallback 时抛出的异常类型，默认携带限流请求和限流判定结果。 */
    Class<? extends RuntimeException> exceptionClass() default
            StellorbitRateLimitRejectedException.class;
}
