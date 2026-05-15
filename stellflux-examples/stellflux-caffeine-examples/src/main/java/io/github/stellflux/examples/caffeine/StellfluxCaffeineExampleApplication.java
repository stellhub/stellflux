package io.github.stellflux.examples.caffeine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Caffeine 示例应用。 */
@SpringBootApplication
public class StellfluxCaffeineExampleApplication {

    /**
     * 启动 Caffeine 示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // only for local mode
        System.setProperty("log.stdout", "true");
        SpringApplication.run(StellfluxCaffeineExampleApplication.class, args);
    }
}
