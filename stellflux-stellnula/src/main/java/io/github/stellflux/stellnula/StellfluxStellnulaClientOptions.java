package io.github.stellflux.stellnula;

import io.github.stellnula.auth.StellnulaTokenProvider;
import io.github.stellnula.config.StellnulaSubscription;
import io.github.stellnula.transport.StellnulaServerSelector;
import io.opentelemetry.api.OpenTelemetry;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.Getter;
import lombok.Setter;
import okhttp3.OkHttpClient;

/** Stellnula 配置中心客户端配置。 */
@Getter
@Setter
public class StellfluxStellnulaClientOptions {

    /** Stellnula HTTP 服务地址。 */
    private URI endpoint;

    /** Stellnula gRPC 服务地址，为空时使用服务端 bootstrap 返回值。 */
    private URI grpcEndpoint;

    /** gRPC 是否使用明文连接。 */
    private boolean grpcPlaintext = true;

    /** 固定 API 访问令牌。 */
    private String apiToken = "";

    /** API 版本。 */
    private String apiVersion = "v1";

    /** SDK 版本标识。 */
    private String sdkVersion = "stellflux-stellnula/1.0.1";

    /** 应用 ID。 */
    private String appId = "default-app";

    /** 客户端 ID。 */
    private String clientId = "default-client";

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
    private String hostName = "";

    /** 客户端标签。 */
    private Map<String, String> labels = new LinkedHashMap<>();

    /** 配置订阅列表。 */
    private List<StellnulaSubscription> subscriptions = List.of();

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

    /** 动态访问令牌提供器。 */
    private StellnulaTokenProvider tokenProvider;

    /** 服务端选择器。 */
    private StellnulaServerSelector serverSelector;

    /** OpenTelemetry 实例。 */
    private OpenTelemetry openTelemetry;

    /** 自定义 OkHttp 客户端。 */
    private OkHttpClient httpClient;

    /** watch 执行器。 */
    private ExecutorService watchExecutor;

    /** 配置监听执行器。 */
    private ExecutorService listenerExecutor;
}
