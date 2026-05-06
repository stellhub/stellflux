package io.github.stellflux.grpc.server;

import lombok.Getter;
import lombok.Setter;

/** gRPC server options. */
@Getter
@Setter
public class StellfluxGrpcServerOptions {

    /** Listening port. */
    private int port = 9090;
}
