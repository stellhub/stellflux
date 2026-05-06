package io.github.stellflux.grpc.server;

import io.github.stellflux.grpc.server.StellfluxGrpcServerOptions;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** gRPC server properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.grpc.server")
public class StellfluxGrpcServerProperties {

    /** Enable gRPC server support. */
    private boolean enabled = true;

    /** Listening port. */
    private int port = 9090;

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
