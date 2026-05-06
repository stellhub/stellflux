package io.github.stellflux.opentelemetry.exporter;

import io.github.stellflux.opentelemetry.RetryConfig;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/** 简单指数退避重试日志导出器。 */
public class RetryingLogRecordExporter implements LogRecordExporter {

    private final LogRecordExporter delegate;

    private final RetryConfig retryConfig;

    public RetryingLogRecordExporter(LogRecordExporter delegate, RetryConfig retryConfig) {
        this.delegate = delegate;
        this.retryConfig = retryConfig;
    }

    @Override
    public CompletableResultCode export(Collection<LogRecordData> logs) {
        if (retryConfig == null || !retryConfig.isEnabled()) {
            return delegate.export(logs);
        }
        Instant deadline = Instant.now().plus(retryConfig.getMaxElapsedTime());
        Duration nextDelay = retryConfig.getInitialInterval();
        while (true) {
            CompletableResultCode result = delegate.export(logs);
            if (result.join(10, TimeUnit.SECONDS).isSuccess()) {
                return CompletableResultCode.ofSuccess();
            }
            if (Instant.now().plus(nextDelay).isAfter(deadline)) {
                return CompletableResultCode.ofFailure();
            }
            sleep(nextDelay);
            Duration doubled = nextDelay.multipliedBy(2);
            nextDelay =
                    doubled.compareTo(retryConfig.getMaxInterval()) > 0
                            ? retryConfig.getMaxInterval()
                            : doubled;
        }
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
