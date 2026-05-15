package io.github.stellflux.examples.threadpool;

/** 线程池更新请求。 */
public record ThreadPoolUpdateRequest(Integer corePoolSize, Integer maximumPoolSize) {}
