package io.github.stellflux.stellflow.starter;

import java.util.logging.Logger;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Stellflow starter auto configuration. */
@AutoConfiguration
public class StellfluxStellflowStarterAutoConfiguration {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellflowStarterAutoConfiguration.class.getName());

    /**
     * 记录 Stellflow 聚合 starter 启动日志。
     *
     * @return 启动日志探针
     */
    @Bean("stellfluxStellflowStarterStartupLogger")
    public SmartInitializingSingleton stellfluxStellflowStarterStartupLogger() {
        return () ->
                LOGGER.info(() -> "Starter stellflux-spring-boot-starter-stellflow started successfully");
    }
}
