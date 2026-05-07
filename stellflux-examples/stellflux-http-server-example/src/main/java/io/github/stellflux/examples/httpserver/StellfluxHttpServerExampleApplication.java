package io.github.stellflux.examples.httpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** HTTP 服务端示例应用。 */
@SpringBootApplication
public class StellfluxHttpServerExampleApplication {

    /**
     * 启动 HTTP 服务端示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // only for local mode
        System.setProperty("log.stdout","true");
        SpringApplication.run(StellfluxHttpServerExampleApplication.class, args);
    }
}
