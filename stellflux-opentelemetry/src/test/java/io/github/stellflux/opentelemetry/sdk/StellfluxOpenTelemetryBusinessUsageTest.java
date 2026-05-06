package io.github.stellflux.opentelemetry.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.stellflux.opentelemetry.config.StellfluxOpenTelemetryConfig;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.Test;

class StellfluxOpenTelemetryBusinessUsageTest {

    @Test
    void shouldUseResourceAsServiceLevelMetadataSource() {
        StellfluxOpenTelemetryConfig config =
                StellfluxOpenTelemetryConfig.builder()
                        .serviceName("payment-service")
                        .serviceNamespace("stellar.payment")
                        .logsEnabled(false)
                        .metricsEnabled(false)
                        .tracesEnabled(false)
                        .build();

        try (StellfluxOpenTelemetryRuntime runtime = StellfluxOpenTelemetrySdk.create(config)) {
            Resource resource = runtime.getResource();
            assertEquals(
                    "payment-service",
                    resource.getAttribute(
                            io.opentelemetry.api.common.AttributeKey.stringKey("service.name")));
            assertEquals(
                    "stellar.payment",
                    resource.getAttribute(
                            io.opentelemetry.api.common.AttributeKey.stringKey("service.namespace")));
        }
    }

    @Test
    void shouldUseBaggageAndSpanThroughStandardOtelApi() {
        StellfluxOpenTelemetryConfig config =
                StellfluxOpenTelemetryConfig.builder()
                        .serviceName("order-service")
                        .logsEnabled(false)
                        .metricsEnabled(false)
                        .tracesEnabled(true)
                        .build();

        try (StellfluxOpenTelemetryRuntime runtime = StellfluxOpenTelemetrySdk.create(config)) {
            Tracer tracer = runtime.getOpenTelemetry().getTracer("biz.test");
            BaggageBuilder baggageBuilder = Baggage.builder();
            baggageBuilder.put("tenant.id", "tenant-001", BaggageEntryMetadata.empty());
            Context baggageContext = baggageBuilder.build().storeInContext(Context.current());

            Span span = tracer.spanBuilder("create-order").setParent(baggageContext).startSpan();
            span.setAttribute("order.id", "o-1001");
            try (Scope ignored = span.storeInContext(baggageContext).makeCurrent()) {
                assertEquals("tenant-001", Baggage.current().getEntryValue("tenant.id"));
                assertFalse(Span.current().getSpanContext().getTraceId().isBlank());
            } finally {
                span.end();
            }
        }
    }

    @Test
    void shouldUseMeterThroughStandardOtelApi() {
        StellfluxOpenTelemetryConfig config =
                StellfluxOpenTelemetryConfig.builder()
                        .serviceName("inventory-service")
                        .logsEnabled(false)
                        .metricsEnabled(true)
                        .tracesEnabled(false)
                        .build();

        try (StellfluxOpenTelemetryRuntime runtime = StellfluxOpenTelemetrySdk.create(config)) {
            Meter meter = runtime.getOpenTelemetry().getMeter("biz.test");
            LongCounter counter = meter.counterBuilder("inventory_request_total").build();
            counter.add(1);
            assertNotNull(counter);
        }
    }
}
