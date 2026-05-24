package io.github.stellflux.examples.elaticsearch;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Elaticsearch 示例启动逻辑。 */
@Component
public class ElaticsearchExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER =
            Logger.getLogger(ElaticsearchExampleRunner.class.getName());

    private final ElaticsearchObservationService observationService;
    private final Environment environment;

    public ElaticsearchExampleRunner(
            ElaticsearchObservationService observationService, Environment environment) {
        this.observationService = observationService;
        this.environment = environment;
    }

    /**
     * 启动后输出 Elaticsearch telemetry 状态，并按需执行一次 CRUD。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info(() -> "Prepared Elaticsearch example status=" + observationService.status());

        boolean invokeOnStartup =
                environment.getProperty(
                        "example.elaticsearch.invoke-on-startup", Boolean.class, false);
        if (!invokeOnStartup) {
            return;
        }

        try {
            LOGGER.info(
                    () -> "Elaticsearch startup workflow result=" + observationService.verify("startup"));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Elaticsearch example request failed", ex);
        }
    }
}
