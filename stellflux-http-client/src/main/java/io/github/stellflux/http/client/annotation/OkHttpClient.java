package io.github.stellflux.http.client.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declare a named OkHttp-based HTTP client. */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OkHttpClient {

    /**
     * Bean name alias.
     *
     * @return bean name
     */
    String value() default "";

    /**
     * Explicit bean name.
     *
     * @return bean name
     */
    String beanName() default "";

    /**
     * Base URL.
     *
     * @return base URL
     */
    String baseUrl() default "";

    /**
     * Connect timeout in milliseconds.
     *
     * @return connect timeout
     */
    long connectTimeoutMillis() default 3_000L;

    /**
     * Read timeout in milliseconds.
     *
     * @return read timeout
     */
    long readTimeoutMillis() default 5_000L;

    /**
     * Write timeout in milliseconds.
     *
     * @return write timeout
     */
    long writeTimeoutMillis() default 5_000L;

    /**
     * Call timeout in milliseconds.
     *
     * @return call timeout
     */
    long callTimeoutMillis() default 0L;

    /**
     * Ping interval in milliseconds.
     *
     * @return ping interval
     */
    long pingIntervalMillis() default 0L;

    /**
     * Retry on connection failure.
     *
     * @return whether retry is enabled
     */
    boolean retryOnConnectionFailure() default true;

    /**
     * Follow redirects.
     *
     * @return whether redirects are followed
     */
    boolean followRedirects() default true;

    /**
     * Follow SSL redirects.
     *
     * @return whether SSL redirects are followed
     */
    boolean followSslRedirects() default true;
}
