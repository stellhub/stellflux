package io.github.stellflux.grpc.client.config;

import io.github.stellflux.grpc.client.StellfluxGrpcClientOptions;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** gRPC client properties. */
@Getter
@Setter
@ConfigurationProperties(prefix = "stellflux.grpc.client")
public class StellfluxGrpcClientProperties {

    /** Remote host. */
    private String host = "localhost";

    /** Remote port. */
    private int port = 9090;

    /** Use plaintext connection. */
    private boolean plaintext = true;

    /**
     * 转换为纯能力模块配置对象。
     *
     * @return gRPC 客户端配置对象
     */
    public StellfluxGrpcClientOptions toOptions() {
        StellfluxGrpcClientOptions options = new StellfluxGrpcClientOptions();
        options.setHost(this.host);
        options.setPort(this.port);
        options.setPlaintext(this.plaintext);
        return options;
    }
}
