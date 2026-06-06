package io.github.stellflux.examples.stellnula;

import java.util.logging.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Stellnula 示例启动日志。 */
@Component
public class StellnulaDynamicConfigExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER =
            Logger.getLogger(StellnulaDynamicConfigExampleRunner.class.getName());

    private final StellnulaDynamicConfigValues configValues;

    public StellnulaDynamicConfigExampleRunner(StellnulaDynamicConfigValues configValues) {
        this.configValues = configValues;
    }

    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info(() -> "Stellnula dynamic config example started, " + this.configValues.snapshot());
    }
}
