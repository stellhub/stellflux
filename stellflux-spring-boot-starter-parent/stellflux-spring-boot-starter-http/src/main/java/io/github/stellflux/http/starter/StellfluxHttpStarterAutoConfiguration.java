package io.github.stellflux.http.starter;

import java.util.logging.Logger;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Aggregate HTTP starter auto configuration. */
@AutoConfiguration
public class StellfluxHttpStarterAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxHttpStarterAutoConfiguration.class.getName());

    /**
     * 记录 HTTP 聚合 starter 启动日志。
     *
     * @return 启动日志探针
     */
    @Bean("stellfluxHttpStarterStartupLogger")
    public SmartInitializingSingleton stellfluxHttpStarterStartupLogger() {
        return () ->
                LOGGER.info(
                        () ->
                                "Starter stellflux-spring-boot-starter-http started successfully"
                                        + ", aggregates=[stellflux-spring-boot-starter-http-client,"
                                        + " stellflux-spring-boot-starter-http-server]");
    }
}
