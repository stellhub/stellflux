package io.github.stellflux.stellmap;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import lombok.Getter;
import lombok.Setter;

/** StellMap 客户端配置。 */
@Getter
@Setter
public class StellfluxStellMapClientOptions {

    /** StellMap 服务地址。 */
    private String baseUrl = "";

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

    /** watch 回调线程池。 */
    private ExecutorService watchCallbackExecutor;

    /** 心跳调度线程池。 */
    private ScheduledExecutorService heartbeatExecutor;

    /** watch IO 线程数，0 表示使用 SDK 默认实现。 */
    private int watchThreads;

    /** watch IO 执行器。 */
    private ExecutorService watchExecutor;

    /** watch 重连调度器。 */
    private ScheduledExecutorService watchReconnectScheduler;

    /** watch 自定义线程工厂。 */
    private ThreadFactory watchThreadFactory;

    /** 默认附加请求头。 */
    private Map<String, String> defaultHeaders = new LinkedHashMap<>();
}
