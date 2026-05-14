package io.github.stellflux.examples.jedis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Jedis 示例应用。 */
@SpringBootApplication
public class StellfluxJedisExampleApplication {

    /**
     * 启动 Jedis 示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // only for local mode
        System.setProperty("log.stdout", "true");
        SpringApplication.run(StellfluxJedisExampleApplication.class, args);
    }
}
