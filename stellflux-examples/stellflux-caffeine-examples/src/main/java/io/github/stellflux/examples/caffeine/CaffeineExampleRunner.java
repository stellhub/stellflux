package io.github.stellflux.examples.caffeine;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Caffeine 示例启动逻辑。 */
@Component
public class CaffeineExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER = Logger.getLogger(CaffeineExampleRunner.class.getName());

    private final CaffeineObservationService observationService;

    private final Environment environment;

    public CaffeineExampleRunner(
            CaffeineObservationService observationService, Environment environment) {
        this.observationService = observationService;
        this.environment = environment;
    }

    /**
     * 启动后输出 Caffeine telemetry 配置信息，并按需执行本地缓存 CRUD。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info(() -> "Prepared Caffeine example status=" + observationService.status());

        boolean invokeOnStartup =
                environment.getProperty("example.caffeine.invoke-on-startup", Boolean.class, false);
        if (!invokeOnStartup) {
            return;
        }

        try {
            LOGGER.info(() -> "Caffeine startup workflow result=" + observationService.verify("startup"));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Caffeine example request failed", ex);
        }
    }
}
