package io.github.stellflux.examples.stellflow;

import java.util.logging.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Stellflow 示例启动逻辑。 */
@Component
public class StellflowExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER = Logger.getLogger(StellflowExampleRunner.class.getName());

    private final StellflowExampleService stellflowExampleService;

    public StellflowExampleRunner(StellflowExampleService stellflowExampleService) {
        this.stellflowExampleService = stellflowExampleService;
    }

    /**
     * 启动后输出 Stellflow 示例信息。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        StellflowExampleService.StatusResult status = stellflowExampleService.status();

        LOGGER.info(
                () ->
                        "Prepared Stellflow producer and consumer bootstrapServers="
                                + status.producerBootstrapServers()
                                + ", topic="
                                + status.topic()
                                + ", listenerGroup="
                                + status.listenerGroup());
    }
}
