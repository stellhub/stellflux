package io.github.stellflux.log;

import io.github.stellflux.log.bridge.StellfluxAppender;
import io.github.stellflux.log.bridge.StellfluxLogger;
import io.github.stellflux.log.sdk.OtelLogAppender;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.github.stellflux.opentelemetry.scope.StellfluxTelemetryScopeFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.Logger;

/** Stellflux 日志对象工厂。 */
public class StellfluxLoggerFactory {

    /**
     * 创建日志追加器。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName instrumentation scope 名称
     * @param config OpenTelemetry 配置
     * @return 日志追加器
     */
    public StellfluxAppender createAppender(
            OpenTelemetry openTelemetry,
            String instrumentationScopeName,
            StellfluxOpenTelemetryConfig config) {
        Logger logger = openTelemetry.getLogsBridge().loggerBuilder(instrumentationScopeName).build();
        return new OtelLogAppender(logger, config);
    }

    /**
     * 创建带模块版本的日志追加器。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName instrumentation scope 名称
     * @param artifactId Maven artifactId
     * @param anchorClass 模块锚点类型
     * @param config OpenTelemetry 配置
     * @return 日志追加器
     */
    public StellfluxAppender createAppender(
            OpenTelemetry openTelemetry,
            String instrumentationScopeName,
            String artifactId,
            Class<?> anchorClass,
            StellfluxOpenTelemetryConfig config) {
        Logger logger =
                StellfluxTelemetryScopeFactory.createLogger(
                        openTelemetry, instrumentationScopeName, artifactId, anchorClass);
        return new OtelLogAppender(logger, config);
    }

    /**
     * 创建轻量日志门面。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName instrumentation scope 名称
     * @param config OpenTelemetry 配置
     * @return 轻量日志门面
     */
    public StellfluxLogger createLogger(
            OpenTelemetry openTelemetry,
            String instrumentationScopeName,
            StellfluxOpenTelemetryConfig config) {
        return new StellfluxLogger(
                instrumentationScopeName, createAppender(openTelemetry, instrumentationScopeName, config));
    }

    /**
     * 创建带模块版本的轻量日志门面。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName instrumentation scope 名称
     * @param artifactId Maven artifactId
     * @param anchorClass 模块锚点类型
     * @param config OpenTelemetry 配置
     * @return 轻量日志门面
     */
    public StellfluxLogger createLogger(
            OpenTelemetry openTelemetry,
            String instrumentationScopeName,
            String artifactId,
            Class<?> anchorClass,
            StellfluxOpenTelemetryConfig config) {
        return new StellfluxLogger(
                instrumentationScopeName,
                createAppender(openTelemetry, instrumentationScopeName, artifactId, anchorClass, config));
    }
}
