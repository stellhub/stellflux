package io.github.stellflux.examples.datasource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** DataSource 示例应用。 */
@SpringBootApplication
public class StellfluxDataSourceExampleApplication {

    /**
     * 启动 DataSource 示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // only for local mode
        System.setProperty("log.stdout", "true");
        SpringApplication.run(StellfluxDataSourceExampleApplication.class, args);
    }
}
