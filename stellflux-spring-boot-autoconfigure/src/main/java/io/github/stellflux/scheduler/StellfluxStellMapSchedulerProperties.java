package io.github.stellflux.scheduler;

import io.github.stellflux.scheduler.stellmap.StellfluxStellMapSchedulerOptions;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** StellMap 分布式定时任务配置属性。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.scheduler.stellmap")
public class StellfluxStellMapSchedulerProperties {

    /** Whether StellMap scheduler guard auto configuration is enabled. */
    private boolean enabled = true;

    /** StellMap namespace used to watch scheduler instances. */
    private String namespace;

    /** StellMap service id used to watch scheduler instances. */
    private String serviceId;

    /** Current instance id registered in StellMap. */
    private String currentInstanceId;

    /** Whether watch should include the initial service snapshot. */
    private boolean includeSnapshot = true;

    /**
     * 转换为核心调度判断器配置。
     *
     * @param defaultNamespace 默认命名空间
     * @return 核心调度判断器配置
     */
    public StellfluxStellMapSchedulerOptions toOptions(String defaultNamespace) {
        StellfluxStellMapSchedulerOptions options = new StellfluxStellMapSchedulerOptions();
        options.setNamespace(StringUtils.hasText(namespace) ? namespace.trim() : defaultNamespace);
        options.setServiceId(serviceId);
        options.setCurrentInstanceId(currentInstanceId);
        options.setIncludeSnapshot(includeSnapshot);
        return options;
    }
}
