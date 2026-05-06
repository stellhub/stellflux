package io.github.stellflux.http.client;

import lombok.Getter;
import lombok.Setter;

/** HTTP client options. */
@Getter
@Setter
public class StellfluxHttpClientOptions {

    /** Base URL. */
    private String baseUrl = "";

    /** Connect timeout in milliseconds. */
    private long connectTimeoutMillis = 3_000L;

    /** Read timeout in milliseconds. */
    private long readTimeoutMillis = 5_000L;

    /** Write timeout in milliseconds. */
    private long writeTimeoutMillis = 5_000L;

    /** Call timeout in milliseconds. */
    private long callTimeoutMillis;

    /** Ping interval in milliseconds. */
    private long pingIntervalMillis;

    /** Retry on connection failure. */
    private boolean retryOnConnectionFailure = true;

    /** Follow redirects. */
    private boolean followRedirects = true;

    /** Follow SSL redirects. */
    private boolean followSslRedirects = true;
}
