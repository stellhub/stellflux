package io.github.stellflux.examples.threadpool;

import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.github.stellflux.threadpool.StellfluxThreadPoolSnapshot;
import io.github.stellflux.threadpool.StellfluxThreadPoolTelemetry;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** 线程池 CRUD 和 OpenTelemetry 指标观测服务。 */
@Service
public class ThreadPoolObservationService {

    private static final String ARTIFACT_ID = "stellflux-thread-pool-example";

    private final Environment environment;

    private final StellfluxOpenTelemetryRuntime runtime;

    private final StellfluxThreadPoolTelemetry telemetry;

    private final Map<String, ThreadPoolExecutor> executors = new ConcurrentHashMap<>();

    public ThreadPoolObservationService(
            Environment environment,
            StellfluxOpenTelemetryRuntime runtime,
            StellfluxThreadPoolTelemetry telemetry) {
        this.environment = environment;
        this.runtime = runtime;
        this.telemetry = telemetry;
        create(defaultCreateRequest());
    }

    /**
     * 返回线程池示例状态。
     *
     * @return 示例状态
     */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("module", ARTIFACT_ID);
        status.put("logsEnabled", runtime.getConfig().isLogsEnabled());
        status.put("metricsEnabled", runtime.getConfig().isMetricsEnabled());
        status.put("tracesEnabled", runtime.getConfig().isTracesEnabled());
        status.put("metricExportInterval", runtime.getConfig().getMetricExportInterval().toString());
        status.put("managedPools", executors.keySet());
        status.put("monitoredPools", telemetry.monitoredPoolNames());
        status.put("metrics", metrics().get("pools"));
        status.put(
                "endpoints",
                Map.of(
                        "create", "POST /api/thread-pool/pools",
                        "get", "GET /api/thread-pool/pools/{poolName}",
                        "update", "PUT /api/thread-pool/pools/{poolName}",
                        "delete", "DELETE /api/thread-pool/pools/{poolName}",
                        "submitTasks", "POST /api/thread-pool/pools/{poolName}/tasks",
                        "metrics", "GET /api/thread-pool/metrics"));
        return status;
    }

    /**
     * 创建线程池并加入指标观测。
     *
     * @param request 创建请求
     * @return 创建结果
     */
    public Map<String, Object> create(ThreadPoolCreateRequest request) {
        ThreadPoolCreateRequest effectiveRequest = request == null ? defaultCreateRequest() : request;
        String poolName = normalizePoolName(effectiveRequest.poolName());
        int corePoolSize = positiveOrDefault(effectiveRequest.corePoolSize(), defaultCorePoolSize());
        int maximumPoolSize =
                positiveOrDefault(effectiveRequest.maximumPoolSize(), defaultMaximumPoolSize());
        int queueCapacity = positiveOrDefault(effectiveRequest.queueCapacity(), defaultQueueCapacity());
        long keepAliveSeconds =
                positiveOrDefault(effectiveRequest.keepAliveSeconds(), defaultKeepAliveSeconds());
        validatePoolSize(corePoolSize, maximumPoolSize);

        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(
                        corePoolSize,
                        maximumPoolSize,
                        keepAliveSeconds,
                        TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(queueCapacity),
                        runnable -> {
                            Thread thread = new Thread(runnable);
                            thread.setName(poolName + "-" + thread.threadId());
                            return thread;
                        });
        ThreadPoolExecutor previous = executors.putIfAbsent(poolName, executor);
        if (previous != null) {
            executor.shutdownNow();
            throw new IllegalArgumentException("Thread pool already exists: " + poolName);
        }
        telemetry.monitor(poolName, executor);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("operation", "create");
        response.put("pool", snapshot(poolName));
        return response;
    }

    /**
     * 查看线程池详情。
     *
     * @param poolName 线程池名称
     * @return 线程池详情
     */
    public Map<String, Object> get(String poolName) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("operation", "get");
        response.put("pool", snapshot(poolName));
        return response;
    }

    /**
     * 更新线程池 core/max 配置。
     *
     * @param poolName 线程池名称
     * @param request 更新请求
     * @return 更新结果
     */
    public Map<String, Object> update(String poolName, ThreadPoolUpdateRequest request) {
        String normalizedPoolName = normalizePoolName(poolName);
        ThreadPoolExecutor executor = requireExecutor(normalizedPoolName);
        int targetCorePoolSize =
                request == null || request.corePoolSize() == null
                        ? executor.getCorePoolSize()
                        : positiveOrDefault(request.corePoolSize(), executor.getCorePoolSize());
        int targetMaximumPoolSize =
                request == null || request.maximumPoolSize() == null
                        ? executor.getMaximumPoolSize()
                        : positiveOrDefault(request.maximumPoolSize(), executor.getMaximumPoolSize());
        validatePoolSize(targetCorePoolSize, targetMaximumPoolSize);

        if (targetMaximumPoolSize < executor.getCorePoolSize()) {
            executor.setCorePoolSize(targetCorePoolSize);
            executor.setMaximumPoolSize(targetMaximumPoolSize);
        } else {
            executor.setMaximumPoolSize(targetMaximumPoolSize);
            executor.setCorePoolSize(targetCorePoolSize);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("operation", "update");
        response.put("pool", snapshot(normalizedPoolName));
        return response;
    }

    /**
     * 删除线程池并移除观测。
     *
     * @param poolName 线程池名称
     * @return 删除结果
     */
    public Map<String, Object> delete(String poolName) {
        String normalizedPoolName = normalizePoolName(poolName);
        ThreadPoolExecutor executor = executors.remove(normalizedPoolName);
        if (executor == null) {
            throw new IllegalArgumentException("Thread pool does not exist: " + normalizedPoolName);
        }
        telemetry.remove(normalizedPoolName);
        executor.shutdownNow();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("operation", "delete");
        response.put("poolName", normalizedPoolName);
        response.put("remainingPools", executors.keySet());
        return response;
    }

    /**
     * 提交模拟任务，用于观察 active、queue 和 completed task 指标。
     *
     * @param poolName 线程池名称
     * @param request 任务请求
     * @return 提交结果
     */
    public Map<String, Object> submitTasks(String poolName, ThreadPoolTaskRequest request) {
        String normalizedPoolName = normalizePoolName(poolName);
        ThreadPoolExecutor executor = requireExecutor(normalizedPoolName);
        int taskCount = boundedTaskCount(request == null ? null : request.taskCount());
        long workMillis = boundedWorkMillis(request == null ? null : request.workMillis());
        String batchId = UUID.randomUUID().toString();

        for (int index = 0; index < taskCount; index++) {
            int taskIndex = index;
            executor.execute(() -> sleepQuietly(workMillis, batchId, taskIndex));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("operation", "submitTasks");
        response.put("poolName", normalizedPoolName);
        response.put("batchId", batchId);
        response.put("submittedTasks", taskCount);
        response.put("workMillis", workMillis);
        response.put("pool", snapshot(normalizedPoolName));
        return response;
    }

    /**
     * 查看所有受管线程池的指标快照。
     *
     * @return 指标快照
     */
    public Map<String, Object> metrics() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("operation", "metrics");
        response.put("pools", executors.keySet().stream().sorted().map(this::snapshot).toList());
        response.put("monitoredPoolNames", telemetry.monitoredPoolNames());
        return response;
    }

    @PreDestroy
    public void destroy() {
        executors.forEach(
                (poolName, executor) -> {
                    telemetry.remove(poolName);
                    executor.shutdownNow();
                });
        executors.clear();
    }

    private Map<String, Object> snapshot(String poolName) {
        String normalizedPoolName = normalizePoolName(poolName);
        requireExecutor(normalizedPoolName);
        StellfluxThreadPoolSnapshot snapshot = telemetry.snapshot(normalizedPoolName);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("poolName", snapshot.getPoolName());
        response.put("poolType", snapshot.getPoolType());
        response.put("activeCount", snapshot.getActiveCount());
        response.put("poolSize", snapshot.getPoolSize());
        response.put("corePoolSize", snapshot.getCorePoolSize());
        response.put("maximumPoolSize", snapshot.getMaximumPoolSize());
        response.put("largestPoolSize", snapshot.getLargestPoolSize());
        response.put("queueSize", snapshot.getQueueSize());
        response.put("queueRemainingCapacity", snapshot.getQueueRemainingCapacity());
        response.put("completedTaskCount", snapshot.getCompletedTaskCount());
        response.put("taskCount", snapshot.getTaskCount());
        response.put("shutdown", snapshot.isShutdown());
        response.put("terminated", snapshot.isTerminated());
        return response;
    }

    private ThreadPoolExecutor requireExecutor(String poolName) {
        ThreadPoolExecutor executor = executors.get(poolName);
        if (executor == null) {
            throw new IllegalArgumentException("Thread pool does not exist: " + poolName);
        }
        return executor;
    }

    private ThreadPoolCreateRequest defaultCreateRequest() {
        return new ThreadPoolCreateRequest(
                environment.getProperty("example.thread-pool.default-pool-name", "example-worker"),
                defaultCorePoolSize(),
                defaultMaximumPoolSize(),
                defaultQueueCapacity(),
                defaultKeepAliveSeconds());
    }

    private int defaultCorePoolSize() {
        return environment.getProperty("example.thread-pool.core-pool-size", Integer.class, 2);
    }

    private int defaultMaximumPoolSize() {
        return environment.getProperty("example.thread-pool.maximum-pool-size", Integer.class, 4);
    }

    private int defaultQueueCapacity() {
        return environment.getProperty("example.thread-pool.queue-capacity", Integer.class, 32);
    }

    private long defaultKeepAliveSeconds() {
        return environment.getProperty("example.thread-pool.keep-alive-seconds", Long.class, 30L);
    }

    private String normalizePoolName(String poolName) {
        if (poolName == null || poolName.isBlank()) {
            return "pool-" + UUID.randomUUID();
        }
        return poolName.trim();
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private long positiveOrDefault(Long value, long defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private int boundedTaskCount(Integer taskCount) {
        int value = positiveOrDefault(taskCount, 4);
        return Math.min(value, 128);
    }

    private long boundedWorkMillis(Long workMillis) {
        long value = positiveOrDefault(workMillis, 1_000L);
        return Math.min(value, 30_000L);
    }

    private void validatePoolSize(int corePoolSize, int maximumPoolSize) {
        if (corePoolSize > maximumPoolSize) {
            throw new IllegalArgumentException(
                    "corePoolSize must be less than or equal to maximumPoolSize");
        }
    }

    private void sleepQuietly(long workMillis, String batchId, int taskIndex) {
        try {
            Thread.sleep(workMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
