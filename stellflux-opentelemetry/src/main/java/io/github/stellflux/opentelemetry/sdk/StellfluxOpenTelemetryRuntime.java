package io.github.stellflux.opentelemetry.sdk;

import io.github.stellflux.opentelemetry.StellfluxOpenTelemetryConfig;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.concurrent.TimeUnit;
import lombok.Getter;

/** Stellflux OpenTelemetry 运行时。 */
@Getter
public class StellfluxOpenTelemetryRuntime implements AutoCloseable {

    private final StellfluxOpenTelemetryConfig config;

    private final OpenTelemetrySdk openTelemetrySdk;

    private final Resource resource;

    private final SdkLoggerProvider loggerProvider;

    private final SdkMeterProvider meterProvider;

    private final SdkTracerProvider tracerProvider;

    public StellfluxOpenTelemetryRuntime(
            StellfluxOpenTelemetryConfig config,
            OpenTelemetrySdk openTelemetrySdk,
            Resource resource,
            SdkLoggerProvider loggerProvider,
            SdkMeterProvider meterProvider,
            SdkTracerProvider tracerProvider) {
        this.config = config;
        this.openTelemetrySdk = openTelemetrySdk;
        this.resource = resource;
        this.loggerProvider = loggerProvider;
        this.meterProvider = meterProvider;
        this.tracerProvider = tracerProvider;
    }

    /**
     * 返回统一 OpenTelemetry API。
     *
     * @return OpenTelemetry API
     */
    public OpenTelemetry getOpenTelemetry() {
        return openTelemetrySdk;
    }

    /** 强制刷新所有已启用 signal。 */
    public void flush() {
        join(forceFlush(loggerProvider));
        join(forceFlush(meterProvider));
        join(forceFlush(tracerProvider));
    }

    /** 优雅关闭运行时。 */
    @Override
    public void close() {
        join(openTelemetrySdk.shutdown());
    }

    private CompletableResultCode forceFlush(SdkLoggerProvider provider) {
        return provider == null ? CompletableResultCode.ofSuccess() : provider.forceFlush();
    }

    private CompletableResultCode forceFlush(SdkMeterProvider provider) {
        return provider == null ? CompletableResultCode.ofSuccess() : provider.forceFlush();
    }

    private CompletableResultCode forceFlush(SdkTracerProvider provider) {
        return provider == null ? CompletableResultCode.ofSuccess() : provider.forceFlush();
    }

    private void join(CompletableResultCode resultCode) {
        if (resultCode != null) {
            resultCode.join(10, TimeUnit.SECONDS);
        }
    }
}
