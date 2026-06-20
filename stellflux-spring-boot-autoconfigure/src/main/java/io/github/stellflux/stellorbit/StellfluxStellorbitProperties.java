package io.github.stellflux.stellorbit;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** StellOrbit 服务治理自动装配配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.stellorbit")
public class StellfluxStellorbitProperties {

    /** 当前应用在治理规则中的服务名，默认使用 spring.application.name。 */
    private String targetService;

    /** 治理规则命名空间。 */
    private String ruleNamespace = "governance";

    /** 治理规则分组。 */
    private String ruleGroup = "service-governance";

    /** 是否监听治理规则变更。 */
    private boolean watchEnabled = true;

    /** 启动加载失败时是否快速失败。 */
    private boolean failFastOnBootstrap;

    /** 本地规则快照目录，预留给独立规则源客户端使用。 */
    private Path snapshotDirectory;

    /** 鉴权配置。 */
    private AuthProperties auth = new AuthProperties();

    /** 限流配置。 */
    private RateLimitProperties rateLimit = new RateLimitProperties();

    /** 转换为客户端配置。 */
    public StellfluxStellorbitClientOptions toOptions(String defaultTargetService) {
        StellfluxStellorbitClientOptions options = new StellfluxStellorbitClientOptions();
        options.setTargetService(defaultText(this.targetService, defaultTargetService));
        options.setRuleNamespace(this.ruleNamespace);
        options.setRuleGroup(this.ruleGroup);
        options.setWatchEnabled(this.watchEnabled);
        options.setFailFastOnBootstrap(this.failFastOnBootstrap);
        options.setSnapshotDirectory(this.snapshotDirectory);
        return options;
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    /** 鉴权配置。 */
    @Getter
    @Setter
    public static class AuthProperties {

        /** JWT 配置。 */
        private JwtProperties jwt = new JwtProperties();
    }

    /** JWT 配置。 */
    @Getter
    @Setter
    public static class JwtProperties {

        /** JWT issuer 地址。 */
        private String issuerUri;

        /** JWT JWK Set 地址。 */
        private String jwkSetUri;
    }

    /** 限流配置。 */
    @Getter
    @Setter
    public static class RateLimitProperties {

        /** 限流实现模式：auto、local 或 distributed。 */
        private String mode = "auto";

        /** 分布式限流配置。 */
        private DistributedProperties distributed = new DistributedProperties();
    }

    /** StellPulsar 分布式限流配置。 */
    @Getter
    @Setter
    public static class DistributedProperties {

        /** 当前应用编码，默认使用 StellOrbit target-service 或 spring.application.name。 */
        private String applicationCode;

        /** 当前 JVM 客户端 ID。 */
        private String clientId = "stellflux-stellpulsar-" + UUID.randomUUID();

        /** StellPulsar 服务发现命名空间。 */
        private String namespace = "default";

        /** StellPulsar 服务名。 */
        private String serviceName = "stellpulsar-service";

        /** StellPulsar discovery gRPC host。 */
        private String discoveryHost = "127.0.0.1";

        /** StellPulsar discovery gRPC port。 */
        private int discoveryPort = 9090;

        /** 是否使用明文 gRPC。 */
        private boolean grpcPlaintext = true;

        /** gRPC metadata token。 */
        private String apiToken;

        /** 单次 gRPC 调用 deadline。 */
        private Duration grpcDeadline = Duration.ofSeconds(3);

        /** 单条规则最大配额申请次数。 */
        private int maxAcquireAttempts = 3;

        /** 服务端未返回 retry-after 时的默认等待时间。 */
        private Duration retryDelay = Duration.ofMillis(50);

        /** 默认失败降级策略，支持 fail-open 或 fail-closed。 */
        private String defaultFailPolicy = "fail-open";

        /** 传给 StellPulsar discovery 的客户端标签。 */
        private Map<String, String> labels = new LinkedHashMap<>();
    }
}
