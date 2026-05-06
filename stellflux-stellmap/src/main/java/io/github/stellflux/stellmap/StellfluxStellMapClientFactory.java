package io.github.stellflux.stellmap;

import io.github.stellmap.HttpOptions;
import io.github.stellmap.StellMapClient;
import io.github.stellmap.StellMapClientOptions;
import io.opentelemetry.api.OpenTelemetry;
import java.util.LinkedHashMap;

/** StellMap 客户端工厂。 */
public class StellfluxStellMapClientFactory {

    private final OpenTelemetry openTelemetry;

    public StellfluxStellMapClientFactory() {
        this(null);
    }

    public StellfluxStellMapClientFactory(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    /**
     * 根据配置创建 StellMap 客户端。
     *
     * @param options StellMap 客户端配置
     * @return StellMap 客户端
     */
    public StellMapClient create(StellfluxStellMapClientOptions options) {
        HttpOptions httpOptions = buildHttpOptions(options);
        StellMapClientOptions clientOptions =
                StellMapClientOptions.builder()
                        .baseUrl(options.getBaseUrl())
                        .requestTimeout(options.getRequestTimeout())
                        .followLeaderRedirect(options.isFollowLeaderRedirect())
                        .maxLeaderRedirects(options.getMaxLeaderRedirects())
                        .autoDeregisterOnClose(options.isAutoDeregisterOnClose())
                        .watchAutoReconnect(options.isWatchAutoReconnect())
                        .watchReconnectInitialDelay(options.getWatchReconnectInitialDelay())
                        .watchReconnectMaxDelay(options.getWatchReconnectMaxDelay())
                        .watchReconnectMaxAttempts(options.getWatchReconnectMaxAttempts())
                        .watchCallbackExecutor(options.getWatchCallbackExecutor())
                        .heartbeatExecutor(options.getHeartbeatExecutor())
                        .httpOptions(httpOptions)
                        .defaultHeaders(new LinkedHashMap<>(options.getDefaultHeaders()))
                        .build();
        return new StellMapClient(clientOptions, this.openTelemetry);
    }

    private HttpOptions buildHttpOptions(StellfluxStellMapClientOptions options) {
        boolean hasDedicatedRuntime =
                options.getWatchThreads() > 0
                        || options.getWatchExecutor() != null
                        || options.getWatchReconnectScheduler() != null
                        || options.getWatchThreadFactory() != null;
        if (!hasDedicatedRuntime) {
            return null;
        }
        return HttpOptions.builder()
                .threads(options.getWatchThreads())
                .executor(options.getWatchExecutor())
                .scheduler(options.getWatchReconnectScheduler())
                .threadFactory(options.getWatchThreadFactory())
                .build();
    }
}
