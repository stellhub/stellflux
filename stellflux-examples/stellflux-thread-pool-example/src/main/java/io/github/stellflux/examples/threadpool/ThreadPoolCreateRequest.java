package io.github.stellflux.examples.threadpool;

/** 线程池创建请求。 */
public record ThreadPoolCreateRequest(
        String poolName,
        Integer corePoolSize,
        Integer maximumPoolSize,
        Integer queueCapacity,
        Long keepAliveSeconds) {}
