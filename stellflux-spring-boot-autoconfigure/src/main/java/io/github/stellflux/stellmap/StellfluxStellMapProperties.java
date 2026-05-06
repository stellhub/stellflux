package io.github.stellflux.stellmap;

import io.github.stellflux.stellmap.StellfluxStellMapClientOptions;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** StellMap 自动装配配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.stellmap")
public class StellfluxStellMapProperties {

    /** 是否启用 StellMap 集成。 */
    private boolean enabled = true;

    /** StellMap 服务地址。 */
    private String baseUrl;

    /** 单次请求超时时间。 */
    private Duration requestTimeout = Duration.ofSeconds(5);

    /** 是否自动跟随 leader 跳转。 */
    private boolean followLeaderRedirect = true;

    /** 最大 leader 跳转次数。 */
    private int maxLeaderRedirects = 1;

    /** 关闭客户端时是否自动注销已注册实例。 */
    private boolean autoDeregisterOnClose;

    /** watch 断开后是否自动重连。 */
    private boolean watchAutoReconnect = true;

    /** watch 首次重连延迟。 */
    private Duration watchReconnectInitialDelay = Duration.ofSeconds(1);

    /** watch 最大重连延迟。 */
    private Duration watchReconnectMaxDelay = Duration.ofSeconds(10);

    /** watch 最大重连次数，-1 表示不限。 */
    private int watchReconnectMaxAttempts = -1;

    /** 默认附加请求头。 */
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();

    /** 运行时资源配置。 */
    private final RuntimeProperties runtime = new RuntimeProperties();

    /**
     * 转换为框架内部的 StellMap 客户端配置。
     *
     * @return StellMap 客户端配置
     */
    public StellfluxStellMapClientOptions toOptions() {
        StellfluxStellMapClientOptions options = new StellfluxStellMapClientOptions();
        options.setBaseUrl(this.baseUrl);
        options.setRequestTimeout(this.requestTimeout);
        options.setFollowLeaderRedirect(this.followLeaderRedirect);
        options.setMaxLeaderRedirects(this.maxLeaderRedirects);
        options.setAutoDeregisterOnClose(this.autoDeregisterOnClose);
        options.setWatchAutoReconnect(this.watchAutoReconnect);
        options.setWatchReconnectInitialDelay(this.watchReconnectInitialDelay);
        options.setWatchReconnectMaxDelay(this.watchReconnectMaxDelay);
        options.setWatchReconnectMaxAttempts(this.watchReconnectMaxAttempts);
        options.setDefaultHeaders(new LinkedHashMap<>(this.defaultHeaders));
        options.setWatchThreads(this.runtime.getHttpOptions().getThreads());
        return options;
    }

    /** StellMap 运行时资源配置。 */
    @Getter
    @Setter
    public static class RuntimeProperties {

        /** watch 回调线程池 bean 名称。 */
        private String watchCallbackExecutorBeanName;

        /** 心跳调度线程池 bean 名称。 */
        private String heartbeatExecutorBeanName;

        /** watch 运行时高级配置。 */
        private final HttpOptionsProperties httpOptions = new HttpOptionsProperties();
    }

    /** StellMap watch 高级运行时配置。 */
    @Getter
    @Setter
    public static class HttpOptionsProperties {

        /** watch IO 线程数。 */
        private int threads;

        /** watch IO 执行器 bean 名称。 */
        private String executorBeanName;

        /** watch 重连调度器 bean 名称。 */
        private String schedulerBeanName;

        /** watch 线程工厂 bean 名称。 */
        private String threadFactoryBeanName;
    }
}
