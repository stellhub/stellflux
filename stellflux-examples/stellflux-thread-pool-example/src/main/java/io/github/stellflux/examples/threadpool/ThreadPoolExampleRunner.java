package io.github.stellflux.examples.threadpool;

import java.util.logging.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 线程池指标观测示例启动逻辑。 */
@Component
public class ThreadPoolExampleRunner implements ApplicationRunner {

    private static final Logger LOGGER = Logger.getLogger(ThreadPoolExampleRunner.class.getName());

    private final ThreadPoolObservationService observationService;

    private final Environment environment;

    public ThreadPoolExampleRunner(
            ThreadPoolObservationService observationService, Environment environment) {
        this.observationService = observationService;
        this.environment = environment;
    }

    /**
     * 启动后输出线程池 telemetry 配置信息，并按需提交示例任务。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        LOGGER.info(() -> "Prepared thread pool example status=" + observationService.status());

        boolean invokeOnStartup =
                environment.getProperty("example.thread-pool.invoke-on-startup", Boolean.class, false);
        if (!invokeOnStartup) {
            return;
        }

        String poolName =
                environment.getProperty("example.thread-pool.default-pool-name", "example-worker");
        LOGGER.info(
                () ->
                        "Thread pool startup task result="
                                + observationService.submitTasks(poolName, new ThreadPoolTaskRequest(8, 1_000L)));
    }
}
