package io.github.stellflux.examples.stellmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** StellMap 示例应用。 */
@SpringBootApplication
public class StellfluxStellMapExampleApplication {

    /**
     * 启动 StellMap 示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // only for local mode
        System.setProperty("log.stdout","true");
        SpringApplication.run(StellfluxStellMapExampleApplication.class, args);
    }
}
