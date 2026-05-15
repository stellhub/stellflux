package io.github.stellflux.examples.threadpool;

/** 线程池任务提交请求。 */
public record ThreadPoolTaskRequest(Integer taskCount, Long workMillis) {}
