package io.github.stellflux.stellmap.registration;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** StellMap 服务注册配置。 */
@Getter
@Setter
public class StellfluxRegistrationProperties {

    /** 是否启用服务注册。 */
    private boolean enabled = true;

    /** 注册命名空间。 */
    private String namespace;

    /** 注册主机地址。 */
    private String host;

    /** 实例标识。 */
    private String instanceId;

    /** 组织标识。 */
    private String organization;

    /** 业务域。 */
    private String businessDomain;

    /** 能力域。 */
    private String capabilityDomain;

    /** 应用标识。 */
    private String application;

    /** 实例角色。 */
    private String role = "provider";

    /** 可用区。 */
    private String zone;

    /** 租约 TTL，单位秒。 */
    private long leaseTtlSeconds = 30L;

    /** 端点权重。 */
    private int weight = 100;

    /** 注册标签。 */
    private Map<String, String> labels = new LinkedHashMap<>();

    /** 注册元数据。 */
    private Map<String, String> metadata = new LinkedHashMap<>();
}
