package io.github.stellflux.scheduler.stellmap;

/** StellMap 调度执行权判断结果。 */
public final class StellfluxStellMapScheduleDecision {

    private final String taskName;
    private final String namespace;
    private final String serviceId;
    private final String currentInstanceId;
    private final String ownerInstanceId;
    private final boolean executable;
    private final long directoryRevision;
    private final String reason;

    public StellfluxStellMapScheduleDecision(
            String taskName,
            String namespace,
            String serviceId,
            String currentInstanceId,
            String ownerInstanceId,
            boolean executable,
            long directoryRevision,
            String reason) {
        this.taskName = requireText(taskName, "taskName");
        this.namespace = requireText(namespace, "namespace");
        this.serviceId = requireText(serviceId, "serviceId");
        this.currentInstanceId = requireText(currentInstanceId, "currentInstanceId");
        this.ownerInstanceId = normalizeText(ownerInstanceId);
        this.executable = executable;
        this.directoryRevision = directoryRevision;
        this.reason = requireText(reason, "reason");
    }

    /**
     * 获取任务名称。
     *
     * @return 任务名称
     */
    public String getTaskName() {
        return taskName;
    }

    /**
     * 获取命名空间。
     *
     * @return 命名空间
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * 获取服务标识。
     *
     * @return 服务标识
     */
    public String getServiceId() {
        return serviceId;
    }

    /**
     * 获取当前实例 ID。
     *
     * @return 当前实例 ID
     */
    public String getCurrentInstanceId() {
        return currentInstanceId;
    }

    /**
     * 获取任务 owner 实例 ID。
     *
     * @return 任务 owner 实例 ID
     */
    public String getOwnerInstanceId() {
        return ownerInstanceId;
    }

    /**
     * 判断当前实例是否允许执行任务。
     *
     * @return 允许执行返回 true
     */
    public boolean isExecutable() {
        return executable;
    }

    /**
     * 获取目录版本。
     *
     * @return 目录版本
     */
    public long getDirectoryRevision() {
        return directoryRevision;
    }

    /**
     * 获取判断原因。
     *
     * @return 判断原因
     */
    public String getReason() {
        return reason;
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
