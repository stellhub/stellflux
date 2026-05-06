package io.github.stellflux.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

class StellfluxContextBusinessUsageTest {

    @Test
    void shouldStoreLocalContextOnlyInsideCurrentProcess() {
        StellfluxLocalContext baseContext = StellfluxLocalContext.current();
        StellfluxLocalContext nextContext =
                baseContext.with("tenant.id", "t1").with("internal.debug.flag", "true");

        assertFalse(baseContext.get("tenant.id").isPresent());
        assertEquals("t1", nextContext.get("tenant.id").orElseThrow());
        assertEquals("true", nextContext.get("internal.debug.flag").orElseThrow());
    }

    @Test
    void shouldMapOnlyWhitelistedFieldsIntoBaggage() {
        StellfluxLocalContext localContext =
                StellfluxLocalContext.current()
                        .with("tenant.id", "t1")
                        .with("locale", "zh-CN")
                        .with("internal.debug.flag", "true");
        StellfluxPropagatedFields propagatedFields =
                StellfluxPropagatedFields.builder().add("tenant.id").add("locale").build();
        StellfluxBaggageMapper mapper = StellfluxBaggageMapper.create();

        Baggage baggage = mapper.toBaggage(localContext, propagatedFields);

        assertEquals("t1", baggage.getEntryValue("tenant.id"));
        assertEquals("zh-CN", baggage.getEntryValue("locale"));
        assertEquals(null, baggage.getEntryValue("internal.debug.flag"));
    }

    @Test
    void shouldMergeBaggageBackIntoLocalContext() {
        StellfluxLocalContext sourceContext =
                StellfluxLocalContext.current().with("tenantId", "tenant-001").with("locale", "zh-CN");
        StellfluxPropagatedFields propagatedFields =
                StellfluxPropagatedFields.builder().add("tenantId", "tenant.id").add("locale").build();
        StellfluxBaggageMapper mapper = StellfluxBaggageMapper.create();

        Context outboundContext =
                mapper.storeToContext(Context.current(), sourceContext, propagatedFields);
        StellfluxLocalContext inboundContext =
                mapper.mergeFromContext(
                        StellfluxLocalContext.from(Context.root()), outboundContext, propagatedFields);

        assertEquals("tenant-001", inboundContext.get("tenantId").orElseThrow());
        assertEquals("zh-CN", inboundContext.get("locale").orElseThrow());
    }

    @Test
    void shouldMakeLocalContextCurrentInsideScope() {
        StellfluxLocalContext localContext = StellfluxLocalContext.current().with("tenant.id", "t1");

        try (Scope ignored = localContext.makeCurrent()) {
            assertEquals("t1", StellfluxLocalContext.current().get("tenant.id").orElseThrow());
        }

        assertFalse(StellfluxLocalContext.current().get("tenant.id").isPresent());
    }
}
