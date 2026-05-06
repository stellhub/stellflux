package io.github.stellflux.http.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** HTTP server properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.http.server")
public class StellfluxHttpServerProperties {

    /** Enable the HTTP server support. */
    private boolean enabled = true;
}
