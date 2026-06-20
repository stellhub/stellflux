package io.github.stellflux.stellorbit.ratelimit.annotation;

/** 注解式限流配额申请模式。 */
public enum RateLimitAcquireMode {
    /** 配额不足时立即拒绝。 */
    REJECTING,

    /** 配额不足时阻塞等待直到获得配额或超时。 */
    BLOCKING
}
