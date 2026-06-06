package io.github.stellflux.stellnula;

import io.github.stellnula.client.StellnulaClient;
import io.github.stellnula.client.StellnulaClientOptions;

/** Stellnula 配置中心客户端工厂。 */
public class StellfluxStellnulaClientFactory {

    /**
     * 根据配置创建 Stellnula 客户端。
     *
     * @param options Stellnula 客户端配置
     * @return Stellnula 客户端
     */
    public StellnulaClient create(StellfluxStellnulaClientOptions options) {
        StellnulaClientOptions clientOptions =
                StellnulaClientOptions.builder()
                        .endpoint(options.getEndpoint())
                        .grpcEndpoint(options.getGrpcEndpoint())
                        .grpcPlaintext(options.isGrpcPlaintext())
                        .apiToken(options.getApiToken())
                        .apiVersion(options.getApiVersion())
                        .sdkVersion(options.getSdkVersion())
                        .appId(options.getAppId())
                        .clientId(options.getClientId())
                        .env(options.getEnv())
                        .region(options.getRegion())
                        .zone(options.getZone())
                        .cluster(options.getCluster())
                        .namespace(options.getNamespace())
                        .group(options.getGroup())
                        .clientIp(options.getClientIp())
                        .hostName(options.getHostName())
                        .labels(options.getLabels())
                        .subscriptions(options.getSubscriptions())
                        .snapshotFile(options.getSnapshotFile())
                        .requestTimeout(options.getRequestTimeout())
                        .watchTimeout(options.getWatchTimeout())
                        .retryDelay(options.getRetryDelay())
                        .serverRefreshInterval(options.getServerRefreshInterval())
                        .serverFailureCooldown(options.getServerFailureCooldown())
                        .grpcShutdownTimeout(options.getGrpcShutdownTimeout())
                        .watchEnabled(options.isWatchEnabled())
                        .failFastOnBootstrap(options.isFailFastOnBootstrap())
                        .pageSize(options.getPageSize())
                        .maxPayloadBytes(options.getMaxPayloadBytes())
                        .acceptLargeFileReference(options.isAcceptLargeFileReference())
                        .tokenProvider(options.getTokenProvider())
                        .serverSelector(options.getServerSelector())
                        .openTelemetry(options.getOpenTelemetry())
                        .build();
        if (options.getHttpClient() != null
                && options.getWatchExecutor() != null
                && options.getListenerExecutor() != null) {
            return new StellnulaClient(
                    clientOptions,
                    options.getHttpClient(),
                    options.getWatchExecutor(),
                    options.getListenerExecutor());
        }
        if (options.getHttpClient() != null) {
            return new StellnulaClient(clientOptions, options.getHttpClient());
        }
        return new StellnulaClient(clientOptions);
    }
}
