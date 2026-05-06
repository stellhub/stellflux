package io.github.stellflux.log.bridge.logback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import io.github.stellflux.log.StellfluxLoggerFactory;
import io.github.stellflux.opentelemetry.config.StellfluxOpenTelemetryConfig;
import io.opentelemetry.api.OpenTelemetry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/** Logback 桥接安装器。 */
public final class StellfluxLogbackBridgeInstaller {

    private static final String APPENDER_NAME_PREFIX = "stellflux-logback-";

    private StellfluxLogbackBridgeInstaller() {}

    /**
     * 将 stellflux appender 安装到 Logback Root Logger。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName scope 名称
     * @param config OpenTelemetry 配置
     * @return 是否安装成功
     */
    public static boolean install(
            OpenTelemetry openTelemetry,
            String instrumentationScopeName,
            StellfluxOpenTelemetryConfig config) {
        return install(openTelemetry, instrumentationScopeName, config, false);
    }

    /**
     * 将 stellflux appender 安装到 Logback Root Logger。
     *
     * @param openTelemetry 全局 OpenTelemetry
     * @param instrumentationScopeName scope 名称
     * @param config OpenTelemetry 配置
     * @param replaceExistingRootAppenders 是否替换现有 root appenders
     * @return 是否安装成功
     */
    public static boolean install(
            OpenTelemetry openTelemetry,
            String instrumentationScopeName,
            StellfluxOpenTelemetryConfig config,
            boolean replaceExistingRootAppenders) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!(factory instanceof LoggerContext context)) {
            return false;
        }

        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        String appenderName = APPENDER_NAME_PREFIX + instrumentationScopeName;
        if (root.getAppender(appenderName) != null) {
            return true;
        }
        if (replaceExistingRootAppenders) {
            detachAllAppenders(root);
        }

        StellfluxLogbackAppender appender =
                new StellfluxLogbackAppender(
                        new StellfluxLoggerFactory()
                                .createAppender(openTelemetry, instrumentationScopeName, config));
        appender.setContext(context);
        appender.setName(appenderName);
        appender.start();
        root.addAppender(appender);
        return true;
    }

    private static void detachAllAppenders(Logger root) {
        List<Appender<ch.qos.logback.classic.spi.ILoggingEvent>> appenders = new ArrayList<>();
        Iterator<Appender<ch.qos.logback.classic.spi.ILoggingEvent>> iterator =
                root.iteratorForAppenders();
        while (iterator.hasNext()) {
            appenders.add(iterator.next());
        }
        for (Appender<ch.qos.logback.classic.spi.ILoggingEvent> appender : appenders) {
            root.detachAppender(appender);
            appender.stop();
        }
    }
}
