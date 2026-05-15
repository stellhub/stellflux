package io.github.stellflux.examples.threadpool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 线程池指标观测示例应用。 */
@SpringBootApplication
public class StellfluxThreadPoolExampleApplication {

    /**
     * 启动线程池指标观测示例。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // only for local mode
        System.setProperty("log.stdout", "true");
        SpringApplication.run(StellfluxThreadPoolExampleApplication.class, args);
    }
}
