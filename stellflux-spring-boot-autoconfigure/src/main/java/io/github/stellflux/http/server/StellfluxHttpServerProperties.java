package io.github.stellflux.http.server;

import io.github.stellflux.stellmap.registration.StellfluxRegistrationProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** HTTP server properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.http.server")
public class StellfluxHttpServerProperties {

    /** HTTP 服务注册标识。 */
    private String serviceId;

    /** HTTP 服务注册配置。 */
    private final StellfluxRegistrationProperties registration = new StellfluxRegistrationProperties();
}
