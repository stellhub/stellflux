package io.github.stellflux.caffeine;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.github.stellflux.opentelemetry.sdk.StellfluxOpenTelemetryRuntime;
import io.opentelemetry.api.OpenTelemetry;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Caffeine auto configuration. */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({Caffeine.class, StellfluxCaffeineCacheFactory.class, OpenTelemetry.class})
@ConditionalOnBean(OpenTelemetry.class)
public class StellfluxCaffeineAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxCaffeineAutoConfiguration.class.getName());

    /**
     * 注册带 OpenTelemetry 的 Caffeine 缓存工厂。
     *
     * @param openTelemetry OpenTelemetry 实例
     * @param runtimeProvider OpenTelemetry 运行时
     * @return Caffeine 缓存工厂
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public StellfluxCaffeineCacheFactory stellfluxCaffeineCacheFactory(
            OpenTelemetry openTelemetry,
            ObjectProvider<StellfluxOpenTelemetryRuntime> runtimeProvider) {
        StellfluxOpenTelemetryRuntime runtime = runtimeProvider.getIfAvailable();
        StellfluxOpenTelemetryConfig config =
                runtime == null ? StellfluxOpenTelemetryConfig.builder().build() : runtime.getConfig();
        return new StellfluxCaffeineCacheFactory(openTelemetry, config);
    }

    /**
     * 记录 Caffeine starter 启动日志。
     *
     * @param moduleInfoMeterProvider 模块指标注册器
     * @return 启动日志探针
     */
    @Bean("stellfluxCaffeineStarterStartupLogger")
    public SmartInitializingSingleton stellfluxCaffeineStarterStartupLogger(
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule(
                        "stellflux-caffeine", StellfluxCaffeineCacheFactory.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-caffeine started successfully"
                                    + ", telemetry=true");
        };
    }
}
