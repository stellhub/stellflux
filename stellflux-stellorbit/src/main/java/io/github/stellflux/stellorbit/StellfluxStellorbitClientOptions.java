package io.github.stellflux.stellorbit;

import java.nio.file.Path;
import lombok.Getter;
import lombok.Setter;

/** StellOrbit 治理客户端配置。 */
@Getter
@Setter
public class StellfluxStellorbitClientOptions {

    /** 当前应用在治理规则中的服务名。 */
    private String targetService;

    /** 治理规则命名空间。 */
    private String ruleNamespace = "governance";

    /** 治理规则分组。 */
    private String ruleGroup = "service-governance";

    /** 是否监听规则变更。 */
    private boolean watchEnabled = true;

    /** 启动加载失败时是否快速失败。 */
    private boolean failFastOnBootstrap;

    /** 本地规则快照目录，预留给独立规则源客户端使用。 */
    private Path snapshotDirectory;
}
