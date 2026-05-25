package io.github.stellflux.scheduler.stellmap;

/** StellMap 分布式定时任务判断器配置。 */
public class StellfluxStellMapSchedulerOptions {

    private String namespace = "default";
    private String serviceId;
    private String currentInstanceId;
    private boolean includeSnapshot = true;

    /**
     * 获取 StellMap 命名空间。
     *
     * @return StellMap 命名空间
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * 设置 StellMap 命名空间。
     *
     * @param namespace StellMap 命名空间
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * 获取 StellMap 服务标识。
     *
     * @return StellMap 服务标识
     */
    public String getServiceId() {
        return serviceId;
    }

    /**
     * 设置 StellMap 服务标识。
     *
     * @param serviceId StellMap 服务标识
     */
    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
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
     * 设置当前实例 ID。
     *
     * @param currentInstanceId 当前实例 ID
     */
    public void setCurrentInstanceId(String currentInstanceId) {
        this.currentInstanceId = currentInstanceId;
    }

    /**
     * 是否在 watch 建立时包含初始快照。
     *
     * @return 包含初始快照返回 true
     */
    public boolean isIncludeSnapshot() {
        return includeSnapshot;
    }

    /**
     * 设置是否在 watch 建立时包含初始快照。
     *
     * @param includeSnapshot 包含初始快照
     */
    public void setIncludeSnapshot(boolean includeSnapshot) {
        this.includeSnapshot = includeSnapshot;
    }

    /**
     * 复制当前配置。
     *
     * @return 新配置对象
     */
    public StellfluxStellMapSchedulerOptions copy() {
        StellfluxStellMapSchedulerOptions copy = new StellfluxStellMapSchedulerOptions();
        copy.setNamespace(namespace);
        copy.setServiceId(serviceId);
        copy.setCurrentInstanceId(currentInstanceId);
        copy.setIncludeSnapshot(includeSnapshot);
        return copy;
    }
}
