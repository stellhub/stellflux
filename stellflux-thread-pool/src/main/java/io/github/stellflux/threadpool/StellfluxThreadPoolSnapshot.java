package io.github.stellflux.threadpool;

import lombok.Builder;
import lombok.Value;

/** 线程池指标快照。 */
@Value
@Builder
public class StellfluxThreadPoolSnapshot {

    String poolName;

    String poolType;

    int activeCount;

    int poolSize;

    int corePoolSize;

    int maximumPoolSize;

    int largestPoolSize;

    int queueSize;

    int queueRemainingCapacity;

    long completedTaskCount;

    long taskCount;

    boolean shutdown;

    boolean terminated;
}
