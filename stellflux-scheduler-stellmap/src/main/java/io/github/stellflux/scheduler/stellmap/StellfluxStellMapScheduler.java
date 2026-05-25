package io.github.stellflux.scheduler.stellmap;

import io.github.stellmap.ServiceDirectory;
import io.github.stellmap.ServiceDirectorySubscription;
import io.github.stellmap.model.RegistryInstance;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 基于 StellMap 实例目录的分布式定时任务执行权判断器。 */
public final class StellfluxStellMapScheduler implements AutoCloseable {

    public static final String REASON_OWNER_MATCHED = "OWNER_MATCHED";
    public static final String REASON_NOT_OWNER = "NOT_OWNER";
    public static final String REASON_NO_AVAILABLE_INSTANCE = "NO_AVAILABLE_INSTANCE";

    private static final long FNV64_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV64_PRIME = 0x100000001b3L;

    private final StellfluxStellMapSchedulerOptions options;
    private final ServiceDirectorySubscription subscription;

    public StellfluxStellMapScheduler(
            StellfluxStellMapSchedulerOptions options, ServiceDirectorySubscription subscription) {
        this.options = requireOptions(options).copy();
        this.subscription = Objects.requireNonNull(subscription, "subscription must not be null");
    }

    /**
     * 判断当前实例是否允许执行任务。
     *
     * @param taskName 任务名称
     * @return 允许执行返回 true
     */
    public boolean canExecute(String taskName) {
        return evaluate(taskName).isExecutable();
    }

    /**
     * 计算任务执行权归属。
     *
     * @param taskName 任务名称
     * @return 调度执行权判断结果
     */
    public StellfluxStellMapScheduleDecision evaluate(String taskName) {
        String normalizedTaskName = requireText(taskName, "taskName");
        ServiceDirectory serviceDirectory = subscription.getServiceDirectory();
        List<String> instanceIds =
                serviceDirectory.listInstances(options.getNamespace(), options.getServiceId()).stream()
                        .map(RegistryInstance::getInstanceId)
                        .map(StellfluxStellMapScheduler::normalizeText)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted(Comparator.naturalOrder())
                        .toList();
        if (instanceIds.isEmpty()) {
            return decision(
                    normalizedTaskName,
                    null,
                    false,
                    serviceDirectory.getDirectoryRevision(),
                    REASON_NO_AVAILABLE_INSTANCE);
        }

        String ownerInstanceId = selectOwner(normalizedTaskName, instanceIds);
        boolean executable = ownerInstanceId.equals(options.getCurrentInstanceId());
        return decision(
                normalizedTaskName,
                ownerInstanceId,
                executable,
                serviceDirectory.getDirectoryRevision(),
                executable ? REASON_OWNER_MATCHED : REASON_NOT_OWNER);
    }

    @Override
    public void close() {
        subscription.close();
    }

    private StellfluxStellMapScheduleDecision decision(
            String taskName,
            String ownerInstanceId,
            boolean executable,
            long directoryRevision,
            String reason) {
        return new StellfluxStellMapScheduleDecision(
                taskName,
                options.getNamespace(),
                options.getServiceId(),
                options.getCurrentInstanceId(),
                ownerInstanceId,
                executable,
                directoryRevision,
                reason);
    }

    private String selectOwner(String taskName, List<String> instanceIds) {
        int index = (int) Math.floorMod(stableHash(taskName), instanceIds.size());
        return instanceIds.get(index);
    }

    private long stableHash(String value) {
        long hash = FNV64_OFFSET_BASIS;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xffL;
            hash *= FNV64_PRIME;
        }
        return hash;
    }

    private static StellfluxStellMapSchedulerOptions requireOptions(
            StellfluxStellMapSchedulerOptions options) {
        StellfluxStellMapSchedulerOptions safeOptions =
                Objects.requireNonNull(options, "options must not be null");
        requireText(safeOptions.getNamespace(), "namespace");
        requireText(safeOptions.getServiceId(), "serviceId");
        requireText(safeOptions.getCurrentInstanceId(), "currentInstanceId");
        return safeOptions;
    }

    private static String requireText(String value, String name) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
