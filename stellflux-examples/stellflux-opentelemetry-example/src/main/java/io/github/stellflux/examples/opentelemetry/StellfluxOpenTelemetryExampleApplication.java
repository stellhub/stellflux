package io.github.stellflux.examples.opentelemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** OpenTelemetry 示例应用。 */
@SpringBootApplication
public class StellfluxOpenTelemetryExampleApplication {

    /**
     * 启动 OpenTelemetry 示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(StellfluxOpenTelemetryExampleApplication.class, args);
    }
}
