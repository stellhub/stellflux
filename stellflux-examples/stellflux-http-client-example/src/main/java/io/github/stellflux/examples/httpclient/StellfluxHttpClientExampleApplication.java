package io.github.stellflux.examples.httpclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** HTTP 客户端示例应用。 */
@SpringBootApplication
public class StellfluxHttpClientExampleApplication {

    /**
     * 启动 HTTP 客户端示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // only for local mode
        System.setProperty("log.stdout", "true");
        SpringApplication.run(StellfluxHttpClientExampleApplication.class, args);
    }
}
