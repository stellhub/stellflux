package io.github.stellflux.context.internal;

import io.github.stellflux.context.StellfluxBaggageMapper;
import io.github.stellflux.context.StellfluxLocalContext;
import io.github.stellflux.context.StellfluxPropagatedFields;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.baggage.BaggageEntryMetadata;
import io.opentelemetry.context.Context;
import java.util.Map;
import java.util.Objects;

/** Default mapper between local context and OpenTelemetry Baggage. */
public enum DefaultStellfluxBaggageMapper implements StellfluxBaggageMapper {
    INSTANCE;

    @Override
    public Baggage toBaggage(
            StellfluxLocalContext localContext, StellfluxPropagatedFields propagatedFields) {
        Objects.requireNonNull(localContext, "localContext");
        Objects.requireNonNull(propagatedFields, "propagatedFields");

        Map<String, String> snapshot = localContext.snapshot();
        BaggageBuilder builder = Baggage.builder();
        propagatedFields
                .mappings()
                .forEach(
                        (localKey, baggageKey) -> {
                            String value = snapshot.get(localKey);
                            if (value != null) {
                                builder.put(baggageKey, value, BaggageEntryMetadata.empty());
                            }
                        });
        return builder.build();
    }

    @Override
    public Context storeToContext(
            Context context,
            StellfluxLocalContext localContext,
            StellfluxPropagatedFields propagatedFields) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(localContext, "localContext");
        Objects.requireNonNull(propagatedFields, "propagatedFields");

        Baggage currentBaggage = Baggage.fromContext(context);
        BaggageBuilder builder = Baggage.builder();
        currentBaggage
                .asMap()
                .forEach(
                        (key, entry) -> {
                            if (!propagatedFields.mappings().containsValue(key)) {
                                builder.put(key, entry.getValue(), entry.getMetadata());
                            }
                        });
        toBaggage(localContext, propagatedFields)
                .asMap()
                .forEach((key, entry) -> builder.put(key, entry.getValue(), entry.getMetadata()));
        return builder.build().storeInContext(context);
    }

    @Override
    public StellfluxLocalContext mergeFromBaggage(
            StellfluxLocalContext localContext,
            Baggage baggage,
            StellfluxPropagatedFields propagatedFields) {
        Objects.requireNonNull(localContext, "localContext");
        Objects.requireNonNull(baggage, "baggage");
        Objects.requireNonNull(propagatedFields, "propagatedFields");

        StellfluxLocalContext mergedContext = localContext;
        for (Map.Entry<String, String> mapping : propagatedFields.mappings().entrySet()) {
            BaggageEntry baggageEntry = baggage.getEntry(mapping.getValue());
            if (baggageEntry != null) {
                mergedContext = mergedContext.with(mapping.getKey(), baggageEntry.getValue());
            }
        }
        return mergedContext;
    }

    @Override
    public StellfluxLocalContext mergeFromContext(
            StellfluxLocalContext localContext,
            Context context,
            StellfluxPropagatedFields propagatedFields) {
        Objects.requireNonNull(context, "context");
        return mergeFromBaggage(localContext, Baggage.fromContext(context), propagatedFields);
    }
}
