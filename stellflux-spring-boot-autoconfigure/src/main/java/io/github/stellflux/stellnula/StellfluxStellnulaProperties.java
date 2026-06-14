package io.github.stellflux.stellnula;

import io.github.stellnula.config.StellnulaSubscription;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Stellnula 配置中心自动装配配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.stellnula")
public class StellfluxStellnulaProperties {

    /** Stellnula HTTP 服务地址。 */
    private String endpoint;

    /** Stellnula gRPC 服务地址，为空时使用服务端 bootstrap 返回值。 */
    private String grpcEndpoint;

    /** gRPC 是否使用明文连接。 */
    private boolean grpcPlaintext = true;

    /** 固定 API 访问令牌。 */
    private String apiToken = "";

    /** API 版本。 */
    private String apiVersion = "v1";

    /** SDK 版本标识，默认从当前包版本推导。 */
    private String sdkVersion;

    /** 应用 ID，默认为 spring.application.name。 */
    private String appId;

    /** 客户端 ID，默认为 appId + 主机名。 */
    private String clientId;

    /** 环境标识。 */
    private String env = "dev";

    /** 区域标识。 */
    private String region = "default";

    /** 可用区标识。 */
    private String zone = "default";

    /** 集群标识。 */
    private String cluster = "default";

    /** 配置命名空间。 */
    private String namespace = "default";

    /** 配置分组。 */
    private String group = "default";

    /** 客户端 IP。 */
    private String clientIp = "";

    /** 客户端主机名。 */
    private String hostName;

    /** 客户端标签。 */
    private Map<String, String> labels = new LinkedHashMap<>();

    /** 配置订阅列表。 */
    private List<SubscriptionProperties> subscriptions = List.of();

    /** 本地快照文件。 */
    private Path snapshotFile;

    /** HTTP 请求超时时间。 */
    private Duration requestTimeout = Duration.ofSeconds(10);

    /** watch 长轮询超时时间。 */
    private Duration watchTimeout = Duration.ofSeconds(30);

    /** watch 失败重试延迟。 */
    private Duration retryDelay = Duration.ofSeconds(3);

    /** 服务端节点刷新间隔。 */
    private Duration serverRefreshInterval = Duration.ofMinutes(1);

    /** 服务端节点失败冷却时间。 */
    private Duration serverFailureCooldown = Duration.ofSeconds(30);

    /** gRPC 关闭超时时间。 */
    private Duration grpcShutdownTimeout = Duration.ofSeconds(3);

    /** 是否启用 watch。 */
    private boolean watchEnabled = true;

    /** bootstrap 失败时是否立即失败。 */
    private boolean failFastOnBootstrap;

    /** 分页大小，0 表示使用 SDK 默认值。 */
    private int pageSize;

    /** 最大载荷字节数，0 表示使用 SDK 默认值。 */
    private int maxPayloadBytes;

    /** 是否接受大文件引用。 */
    private boolean acceptLargeFileReference;

    /** 动态 @Value 刷新是否启用。 */
    private boolean dynamicValueRefresh = true;

    /** 注入 Spring Environment 的 PropertySource 名称。 */
    private String propertySourceName = "stellnula";

    /** 动态访问令牌提供器 Bean 名称。 */
    private String tokenProviderBeanName;

    /** 服务端选择器 Bean 名称。 */
    private String serverSelectorBeanName;

    /** OpenTelemetry Bean 名称。 */
    private String openTelemetryBeanName;

    /** 自定义 OkHttpClient Bean 名称。 */
    private String httpClientBeanName;

    /** watch 执行器 Bean 名称。 */
    private String watchExecutorBeanName;

    /** 配置监听执行器 Bean 名称。 */
    private String listenerExecutorBeanName;

    /** Stellnula 配置订阅配置。 */
    @Getter
    @Setter
    public static class SubscriptionProperties {

        /** 订阅分组。 */
        private String group = "default";

        /** 订阅类型。 */
        private String subscriptionType = "ALL";

        /** 订阅键。 */
        private String subscriptionKey = "*";

        /** 当前 revision。 */
        private Long currentRevision;

        /** 当前 checksum。 */
        private String currentChecksum = "";

        /** 订阅传输协议。 */
        private String transport = "GRPC";

        /** 订阅状态。 */
        private String status = "ACTIVE";

        /**
         * 转换为 SDK 订阅配置。
         *
         * @return SDK 订阅配置
         */
        public StellnulaSubscription toSubscription() {
            return new StellnulaSubscription(
                    this.group,
                    this.subscriptionType,
                    this.subscriptionKey,
                    this.currentRevision,
                    this.currentChecksum,
                    this.transport,
                    this.status);
        }
    }
}
