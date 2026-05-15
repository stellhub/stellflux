package io.github.stellflux.threadpool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.opentelemetry.api.OpenTelemetry;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StellfluxThreadPoolTelemetryTest {

    @Test
    void shouldMonitorThreadPoolSnapshot() {
        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(1, 2, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        StellfluxThreadPoolTelemetry telemetry = new StellfluxThreadPoolTelemetry(OpenTelemetry.noop());

        try {
            telemetry.monitor("worker", executor);

            StellfluxThreadPoolSnapshot snapshot = telemetry.snapshot("worker");

            assertThat(telemetry.monitoredPoolNames()).isEqualTo(Set.of("worker"));
            assertThat(snapshot.getPoolName()).isEqualTo("worker");
            assertThat(snapshot.getCorePoolSize()).isEqualTo(1);
            assertThat(snapshot.getMaximumPoolSize()).isEqualTo(2);
            assertThat(snapshot.getQueueSize()).isZero();
            assertThat(snapshot.isShutdown()).isFalse();
        } finally {
            telemetry.close();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectBlankPoolName() {
        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        StellfluxThreadPoolTelemetry telemetry = new StellfluxThreadPoolTelemetry(OpenTelemetry.noop());

        try {
            assertThatIllegalArgumentException().isThrownBy(() -> telemetry.monitor(" ", executor));
        } finally {
            telemetry.close();
            executor.shutdownNow();
        }
    }
}
