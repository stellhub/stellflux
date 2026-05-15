package io.github.stellflux.examples.datasource;

import java.util.logging.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** DataSource 示例启动逻辑。 */
@Component
public class DataSourceExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER = Logger.getLogger(DataSourceExampleRunner.class.getName());

    private final DataSourceObservationService observationService;
    private final Environment environment;

    public DataSourceExampleRunner(
            DataSourceObservationService observationService, Environment environment) {
        this.observationService = observationService;
        this.environment = environment;
    }

    /**
     * 启动后输出 DataSource telemetry 状态，并按需执行一次 SQL。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info(() -> "Prepared DataSource example status=" + observationService.status());

        boolean invokeOnStartup =
                environment.getProperty("example.datasource.invoke-on-startup", Boolean.class, false);
        if (!invokeOnStartup) {
            return;
        }

        LOGGER.info(() -> "DataSource startup SQL result=" + observationService.executeOnce());
    }
}
