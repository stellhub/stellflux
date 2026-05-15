package io.github.stellflux.threadpool;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thread pool properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.thread-pool")
public class StellfluxThreadPoolProperties {

    /** Whether thread pool telemetry is enabled. */
    private boolean enabled = true;

    /** Whether Spring executor beans should be registered automatically. */
    private boolean autoRegisterExecutorBeans = true;
}
