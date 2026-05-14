package io.github.stellflux.examples.stellflow;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Stellflow 示例启动逻辑。 */
@Component
public class StellflowExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER = Logger.getLogger(StellflowExampleRunner.class.getName());

    private final StellflowExampleService stellflowExampleService;
    private final Environment environment;

    public StellflowExampleRunner(
            StellflowExampleService stellflowExampleService, Environment environment) {
        this.stellflowExampleService = stellflowExampleService;
        this.environment = environment;
    }

    /**
     * 启动后输出 Stellflow 生产者和消费者信息，并按需执行端到端演示。
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
                                + ", consumerGroup="
                                + status.consumerGroup());

        boolean invokeOnStartup =
                environment.getProperty("example.stellflow.invoke-on-startup", Boolean.class, false);
        if (!invokeOnStartup) {
            return;
        }

        try {
            Duration pollTimeout =
                    environment.getProperty(
                            "example.stellflow.poll-timeout", Duration.class, Duration.ofSeconds(3));
            StellflowExampleService.WorkflowResult result =
                    stellflowExampleService.runOrderEventWorkflow(null, null, pollTimeout);
            LOGGER.info(
                    () ->
                            "Stellflow example workflow completed sentOrderId="
                                    + result.sent().orderId()
                                    + ", consumedRecords="
                                    + result.consumedRecords().size());
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Stellflow example workflow failed", ex);
        }
    }
}
