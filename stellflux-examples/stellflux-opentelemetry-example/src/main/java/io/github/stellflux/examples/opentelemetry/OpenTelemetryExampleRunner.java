package io.github.stellflux.examples.opentelemetry;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** OpenTelemetry 示例启动逻辑。 */
@Component
public class OpenTelemetryExampleRunner implements ApplicationRunner {

    private final OpenTelemetryObservationService observationService;

    public OpenTelemetryExampleRunner(OpenTelemetryObservationService observationService) {
        this.observationService = observationService;
    }

    /**
     * 启动后创建一次演示观测事件。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        observationService.verify("startup");
    }
}
