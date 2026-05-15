package io.github.stellflux.examples.jedis;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Jedis 示例启动逻辑。 */
@Component
public class JedisExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER = Logger.getLogger(JedisExampleRunner.class.getName());

    private final JedisObservationService observationService;
    private final Environment environment;

    public JedisExampleRunner(JedisObservationService observationService, Environment environment) {
        this.observationService = observationService;
        this.environment = environment;
    }

    /**
     * 启动后输出 Jedis telemetry 配置信息，并按需访问 Redis。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info(() -> "Prepared Jedis example status=" + observationService.status());

        boolean invokeOnStartup =
                environment.getProperty("example.jedis.invoke-on-startup", Boolean.class, false);
        if (!invokeOnStartup) {
            return;
        }

        try {
            LOGGER.info(() -> "Jedis startup workflow result=" + observationService.verify("startup"));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Jedis example request failed", ex);
        }
    }
}
