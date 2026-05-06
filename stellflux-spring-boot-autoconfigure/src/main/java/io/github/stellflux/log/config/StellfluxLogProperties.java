package io.github.stellflux.log.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stellflux.log")
@Getter
@Setter
public class StellfluxLogProperties {

    private boolean enabled = true;
    private String instrumentationScopeName = "spring-boot";
}
