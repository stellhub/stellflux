package io.github.stellflux.examples.jedis;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;

/** Jedis 示例启动逻辑。 */
@Component
public class JedisExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER = Logger.getLogger(JedisExampleRunner.class.getName());

    private final DefaultJedisClientConfig jedisClientConfig;
    private final Environment environment;

    public JedisExampleRunner(DefaultJedisClientConfig jedisClientConfig, Environment environment) {
        this.jedisClientConfig = jedisClientConfig;
        this.environment = environment;
    }

    /**
     * 启动后输出 Jedis telemetry 配置信息，并按需访问 Redis。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info(
                () ->
                        "Prepared DefaultJedisClientConfig telemetryEnabled="
                                + jedisClientConfig.getTelemetryConfig().isEnabled()
                                + ", openTelemetry="
                                + jedisClientConfig.getTelemetryConfig().getOpenTelemetry().getClass().getName());

        boolean invokeOnStartup =
                environment.getProperty("example.jedis.invoke-on-startup", Boolean.class, false);
        if (!invokeOnStartup) {
            return;
        }

        String host = environment.getProperty("example.jedis.host", "127.0.0.1");
        int port = environment.getProperty("example.jedis.port", Integer.class, 6379);
        String key = "stellflux:jedis:example:" + UUID.randomUUID();
        String value = "hello-stellflux-jedis";

        try (Jedis jedis = new Jedis(host, port, jedisClientConfig)) {
            jedis.set(key, value);
            String actual = jedis.get(key);
            jedis.del(key);
            LOGGER.info(() -> "Jedis example request completed key=" + key + ", value=" + actual);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Jedis example request failed", ex);
        }
    }
}
