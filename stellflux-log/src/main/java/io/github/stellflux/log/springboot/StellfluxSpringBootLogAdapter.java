package io.github.stellflux.log.springboot;

import io.github.stellflux.log.bridge.logback.StellfluxLogbackBridgeInstaller;
import io.github.stellflux.opentelemetry.config.StellfluxOpenTelemetryConfig;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import lombok.Getter;

/** 适用于 Spring Boot 自动装配场景的日志适配器。 */
@Getter
public class StellfluxSpringBootLogAdapter implements AutoCloseable {

    private final StellfluxLogBootstrapResult bootstrapResult;

    private StellfluxSpringBootLogAdapter(StellfluxLogBootstrapResult bootstrapResult) {
        this.bootstrapResult = bootstrapResult;
    }

    /**
     * 使用当前进程上下文完成初始化。
     *
     * @param args 启动参数
     * @param openTelemetry 全局 OpenTelemetry
     * @param config OpenTelemetry 配置
     * @return 日志适配器
     */
    public static StellfluxSpringBootLogAdapter initialize(
            String[] args, OpenTelemetry openTelemetry, StellfluxOpenTelemetryConfig config) {
        return initialize(
                System.getenv(),
                System.getProperties(),
                args,
                Thread.currentThread().getContextClassLoader(),
                "spring-boot",
                openTelemetry,
                config);
    }

    /**
     * 使用指定上下文完成初始化。
     *
     * @param env 环境变量
     * @param systemProperties 系统属性
     * @param args 启动参数
     * @param classLoader 类加载器
     * @param instrumentationScopeName instrumentation scope 名称
     * @param openTelemetry 全局 OpenTelemetry
     * @param config OpenTelemetry 配置
     * @return 日志适配器
     */
    public static StellfluxSpringBootLogAdapter initialize(
            Map<String, String> env,
            Properties systemProperties,
            String[] args,
            ClassLoader classLoader,
            String instrumentationScopeName,
            OpenTelemetry openTelemetry,
            StellfluxOpenTelemetryConfig config) {
        Objects.requireNonNull(env, "env must not be null");
        Objects.requireNonNull(systemProperties, "systemProperties must not be null");
        Objects.requireNonNull(classLoader, "classLoader must not be null");
        Objects.requireNonNull(openTelemetry, "openTelemetry must not be null");
        Objects.requireNonNull(config, "config must not be null");

        StellfluxLogBootstrapMode mode =
                StellfluxLogBootstrapModeResolver.resolve(env, systemProperties, args, classLoader);
        return new StellfluxSpringBootLogAdapter(
                StellfluxLogBootstrapResult.builder()
                        .mode(mode)
                        .installedLogbackBridge(
                                mode == StellfluxLogBootstrapMode.OTEL
                                        && StellfluxLogbackBridgeInstaller.install(
                                                openTelemetry, instrumentationScopeName, config, true))
                        .config(config)
                        .openTelemetry(openTelemetry)
                        .build());
    }

    /**
     * 是否启用了 OTel 日志桥接。
     *
     * @return 是否启用 OTel 日志桥接
     */
    public boolean isOtelEnabled() {
        return bootstrapResult.getMode() == StellfluxLogBootstrapMode.OTEL;
    }

    /** 优雅关闭运行时。 */
    @Override
    public void close() {}
}
