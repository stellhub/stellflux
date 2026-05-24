package io.github.stellflux.examples.elaticsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Elaticsearch 示例应用。 */
@SpringBootApplication
public class StellfluxElaticsearchExampleApplication {

    /**
     * 启动 Elaticsearch 示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // only for local mode
        System.setProperty("log.stdout", "true");
        SpringApplication.run(StellfluxElaticsearchExampleApplication.class, args);
    }
}
