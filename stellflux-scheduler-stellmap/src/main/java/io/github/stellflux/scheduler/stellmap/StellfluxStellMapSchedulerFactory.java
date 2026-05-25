package io.github.stellflux.scheduler.stellmap;

import io.github.stellmap.ServiceDirectorySubscription;
import io.github.stellmap.StellMapClient;
import io.github.stellmap.model.RegistryWatchRequest;
import java.util.Objects;

/** StellMap 分布式定时任务判断器工厂。 */
public class StellfluxStellMapSchedulerFactory {

    private final StellMapClient stellMapClient;

    public StellfluxStellMapSchedulerFactory(StellMapClient stellMapClient) {
        this.stellMapClient = Objects.requireNonNull(stellMapClient, "stellMapClient must not be null");
    }

    /**
     * 创建 StellMap 分布式定时任务判断器。
     *
     * @param options 调度判断器配置
     * @return StellMap 分布式定时任务判断器
     */
    public StellfluxStellMapScheduler create(StellfluxStellMapSchedulerOptions options) {
        StellfluxStellMapSchedulerOptions safeOptions =
                Objects.requireNonNull(options, "options must not be null");
        RegistryWatchRequest watchRequest =
                RegistryWatchRequest.builder()
                        .namespace(safeOptions.getNamespace())
                        .service(safeOptions.getServiceId())
                        .includeSnapshot(safeOptions.isIncludeSnapshot())
                        .build();
        ServiceDirectorySubscription subscription = stellMapClient.watchDirectory(watchRequest);
        return new StellfluxStellMapScheduler(safeOptions, subscription);
    }
}
