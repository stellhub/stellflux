package io.github.stellflux.threadpool;

import io.github.stellflux.metrics.StellfluxMeterFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.ToLongFunction;

/** 线程池 OpenTelemetry 指标观测组件。 */
public class StellfluxThreadPoolTelemetry implements AutoCloseable {

    private static final String INSTRUMENTATION_SCOPE = "io.github.stellflux.threadpool";

    private static final String ARTIFACT_ID = "stellflux-thread-pool";

    private static final AttributeKey<String> THREAD_POOL_NAME_ATTRIBUTE =
            AttributeKey.stringKey("thread.pool.name");

    private static final AttributeKey<String> THREAD_POOL_TYPE_ATTRIBUTE =
            AttributeKey.stringKey("thread.pool.type");

    private final Map<String, ObservedThreadPool> threadPools = new ConcurrentHashMap<>();

    private final Set<ObservableLongGauge> gauges = ConcurrentHashMap.newKeySet();

    public StellfluxThreadPoolTelemetry(OpenTelemetry openTelemetry) {
        Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
        Meter meter =
                new StellfluxMeterFactory()
                        .create(
                                openTelemetry,
                                INSTRUMENTATION_SCOPE,
                                ARTIFACT_ID,
                                StellfluxThreadPoolTelemetry.class);
        registerGauge(
                meter,
                "thread_pool_active_threads",
                "{thread}",
                "Active thread count.",
                ThreadPoolExecutor::getActiveCount);
        registerGauge(
                meter,
                "thread_pool_pool_size",
                "{thread}",
                "Current thread count.",
                ThreadPoolExecutor::getPoolSize);
        registerGauge(
                meter,
                "thread_pool_core_threads",
                "{thread}",
                "Configured core thread count.",
                ThreadPoolExecutor::getCorePoolSize);
        registerGauge(
                meter,
                "thread_pool_max_threads",
                "{thread}",
                "Configured maximum thread count.",
                ThreadPoolExecutor::getMaximumPoolSize);
        registerGauge(
                meter,
                "thread_pool_largest_threads",
                "{thread}",
                "Largest observed thread count.",
                ThreadPoolExecutor::getLargestPoolSize);
        registerGauge(
                meter,
                "thread_pool_queue_size",
                "{task}",
                "Queued task count.",
                executor -> executor.getQueue().size());
        registerGauge(
                meter,
                "thread_pool_queue_remaining_capacity",
                "{task}",
                "Remaining queue capacity.",
                executor -> executor.getQueue().remainingCapacity());
        registerGauge(
                meter,
                "thread_pool_completed_tasks",
                "{task}",
                "Completed task count.",
                ThreadPoolExecutor::getCompletedTaskCount);
        registerGauge(
                meter,
                "thread_pool_tasks",
                "{task}",
                "Scheduled task count.",
                ThreadPoolExecutor::getTaskCount);
    }

    /**
     * 将线程池加入 OpenTelemetry 指标观测。
     *
     * @param poolName 线程池名称
     * @param executor 线程池执行器
     */
    public void monitor(String poolName, ThreadPoolExecutor executor) {
        String normalizedPoolName = normalizePoolName(poolName);
        Objects.requireNonNull(executor, "executor must not be null");
        threadPools.put(normalizedPoolName, new ObservedThreadPool(normalizedPoolName, executor));
    }

    /**
     * 移除已加入观测的线程池。
     *
     * @param poolName 线程池名称
     * @return 是否移除了线程池
     */
    public boolean remove(String poolName) {
        return threadPools.remove(normalizePoolName(poolName)) != null;
    }

    /**
     * 返回已加入观测的线程池名称。
     *
     * @return 线程池名称集合
     */
    public Set<String> monitoredPoolNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(threadPools.keySet()));
    }

    /**
     * 返回指定线程池的当前快照。
     *
     * @param poolName 线程池名称
     * @return 当前快照
     */
    public StellfluxThreadPoolSnapshot snapshot(String poolName) {
        ObservedThreadPool observedThreadPool = threadPools.get(normalizePoolName(poolName));
        if (observedThreadPool == null) {
            throw new IllegalArgumentException("Thread pool is not monitored: " + poolName);
        }
        ThreadPoolExecutor executor = observedThreadPool.executor();
        return StellfluxThreadPoolSnapshot.builder()
                .poolName(observedThreadPool.poolName())
                .poolType(observedThreadPool.poolType())
                .activeCount(executor.getActiveCount())
                .poolSize(executor.getPoolSize())
                .corePoolSize(executor.getCorePoolSize())
                .maximumPoolSize(executor.getMaximumPoolSize())
                .largestPoolSize(executor.getLargestPoolSize())
                .queueSize(executor.getQueue().size())
                .queueRemainingCapacity(executor.getQueue().remainingCapacity())
                .completedTaskCount(executor.getCompletedTaskCount())
                .taskCount(executor.getTaskCount())
                .shutdown(executor.isShutdown())
                .terminated(executor.isTerminated())
                .build();
    }

    @Override
    public void close() {
        gauges.forEach(ObservableLongGauge::close);
        gauges.clear();
        threadPools.clear();
    }

    private void registerGauge(
            Meter meter,
            String metricName,
            String unit,
            String description,
            ToLongFunction<ThreadPoolExecutor> valueExtractor) {
        ObservableLongGauge gauge =
                meter
                        .gaugeBuilder(metricName)
                        .ofLongs()
                        .setUnit(unit)
                        .setDescription(description)
                        .buildWithCallback(
                                measurement ->
                                        threadPools
                                                .values()
                                                .forEach(
                                                        observedThreadPool ->
                                                                measurement.record(
                                                                        valueExtractor.applyAsLong(observedThreadPool.executor()),
                                                                        observedThreadPool.attributes())));
        gauges.add(gauge);
    }

    private String normalizePoolName(String poolName) {
        if (poolName == null || poolName.isBlank()) {
            throw new IllegalArgumentException("poolName must not be blank");
        }
        return poolName.trim();
    }

    private record ObservedThreadPool(String poolName, ThreadPoolExecutor executor) {

        private String poolType() {
            return executor.getClass().getName();
        }

        private Attributes attributes() {
            return Attributes.of(
                    THREAD_POOL_NAME_ATTRIBUTE, poolName, THREAD_POOL_TYPE_ATTRIBUTE, poolType());
        }
    }
}
