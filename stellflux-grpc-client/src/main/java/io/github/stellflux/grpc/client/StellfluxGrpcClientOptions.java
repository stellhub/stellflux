package io.github.stellflux.grpc.client;

import lombok.Getter;
import lombok.Setter;

/** gRPC client options. */
@Getter
@Setter
public class StellfluxGrpcClientOptions {

    /** Remote host. */
    private String host = "localhost";

    /** Remote port. */
    private int port = 9090;

    /** Use plaintext connection. */
    private boolean plaintext = true;
}
