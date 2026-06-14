package io.github.stellflux.examples.stellflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Stellflow 示例应用。 */
@SpringBootApplication
public class StellfluxStellflowExampleApplication {

    /**
     * 启动 Stellflow 示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // only for local mode
        //        System.setProperty("log.stdout", "true");
        SpringApplication.run(StellfluxStellflowExampleApplication.class, args);
    }
}
