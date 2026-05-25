package io.github.stellflux.lock.jedis;

import io.github.stellflux.metrics.StellfluxModuleInfoMeter;
import java.util.logging.Logger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;

/** Jedis 分布式锁自动装配。 */
@AutoConfiguration(afterName = "io.github.stellflux.jedis.StellfluxJedisAutoConfiguration")
@ConditionalOnClass({StellfluxJedisLock.class, Jedis.class, JedisClientConfig.class})
@ConditionalOnBean(JedisClientConfig.class)
@ConditionalOnProperty(
        prefix = "stellflux.lock.jedis",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(StellfluxJedisLockProperties.class)
public class StellfluxJedisLockAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxJedisLockAutoConfiguration.class.getName());

    /**
     * 注册 Jedis 分布式锁工厂。
     *
     * @param jedisClientConfig Jedis 客户端配置
     * @return Jedis 分布式锁工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxJedisLockFactory stellfluxJedisLockFactory(JedisClientConfig jedisClientConfig) {
        return new StellfluxJedisLockFactory(jedisClientConfig);
    }

    /**
     * 注册 Jedis 分布式锁。
     *
     * @param factory Jedis 分布式锁工厂
     * @param properties Jedis 分布式锁配置属性
     * @return Jedis 分布式锁
     */
    @Bean
    @ConditionalOnMissingBean
    public StellfluxJedisLock stellfluxJedisLock(
            StellfluxJedisLockFactory factory, StellfluxJedisLockProperties properties) {
        return factory.create(properties.toOptions());
    }

    /**
     * 记录 Jedis 分布式锁 starter 启动日志。
     *
     * @param properties Jedis 分布式锁配置属性
     * @param moduleInfoMeterProvider 模块指标注册器
     * @return 启动日志探针
     */
    @Bean("stellfluxJedisLockStarterStartupLogger")
    @ConditionalOnBean(StellfluxJedisLock.class)
    public SmartInitializingSingleton stellfluxJedisLockStarterStartupLogger(
            StellfluxJedisLockProperties properties,
            ObjectProvider<StellfluxModuleInfoMeter> moduleInfoMeterProvider) {
        return () -> {
            StellfluxModuleInfoMeter moduleInfoMeter = moduleInfoMeterProvider.getIfAvailable();
            if (moduleInfoMeter != null) {
                moduleInfoMeter.registerModule("stellflux-lock-jedis", StellfluxJedisLock.class);
            }
            LOGGER.info(
                    () ->
                            "Starter stellflux-spring-boot-starter-lock-jedis started successfully"
                                    + ", host="
                                    + properties.getHost()
                                    + ", port="
                                    + properties.getPort()
                                    + ", keyPrefix="
                                    + properties.getKeyPrefix()
                                    + ", defaultTtl="
                                    + properties.getDefaultTtl());
        };
    }
}
