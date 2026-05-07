package io.github.stellflux.grpc.server;

import io.github.stellflux.stellmap.registration.StellfluxRegistrationProperties;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** gRPC server properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.grpc.server")
public class StellfluxGrpcServerProperties {

    /** Listening port. */
    private int port = 9090;

    /** Graceful shutdown timeout. */
    private Duration shutdownTimeout = Duration.ofSeconds(30);

    /** gRPC 服务注册配置。 */
    private final StellfluxRegistrationProperties registration = new StellfluxRegistrationProperties();

    /**
     * 转换为纯能力模块配置对象。
     *
     * @return gRPC 服务端配置对象
     */
    public StellfluxGrpcServerOptions toOptions() {
        StellfluxGrpcServerOptions options = new StellfluxGrpcServerOptions();
        options.setPort(this.port);
        return options;
    }
}
