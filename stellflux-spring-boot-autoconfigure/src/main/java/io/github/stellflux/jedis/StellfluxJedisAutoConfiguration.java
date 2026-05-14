package io.github.stellflux.jedis;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryAutoConfiguration;
import io.opentelemetry.api.OpenTelemetry;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;

/** Jedis auto configuration. */
@AutoConfiguration(after = StellfluxOpenTelemetryAutoConfiguration.class)
@ConditionalOnClass({DefaultJedisClientConfig.class, JedisClientConfig.class, OpenTelemetry.class})
@ConditionalOnBean(OpenTelemetry.class)
public class StellfluxJedisAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxJedisAutoConfiguration.class.getName());

    /**
     * 注册 Jedis 客户端配置工厂。
     *
     * @param openTelemetry OpenTelemetry 实例
     * @return Jedis 客户端配置工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxJedisClientConfigFactory stellfluxJedisClientConfigFactory(
            OpenTelemetry openTelemetry) {
        return new StellfluxJedisClientConfigFactory(openTelemetry);
    }

    /**
     * 注册带 OpenTelemetry 的默认 Jedis 客户端配置。
     *
     * @param factory Jedis 客户端配置工厂
     * @return 默认 Jedis 客户端配置
     */
    @Bean
    @ConditionalOnMissingBean(JedisClientConfig.class)
    public DefaultJedisClientConfig defaultJedisClientConfig(
            StellfluxJedisClientConfigFactory factory) {
        return factory.createDefaultJedisClientConfig();
    }

    /**
     * 记录 Jedis starter 启动日志。
     *
     * @param moduleInfoMeterProvider 模块指标注册器
     * @return 启动日志探针
     */
    @Bean("stellfluxJedisStarterStartupLogger")
    public SmartInitializingSingleton stellfluxJedisStarterStartupLogger(
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule(
                        "stellflux-jedis", StellfluxJedisClientConfigFactory.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-jedis started successfully"
                                    + ", telemetry=true");
        };
    }
}
