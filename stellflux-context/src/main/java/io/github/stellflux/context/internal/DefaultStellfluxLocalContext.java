package io.github.stellflux.context.internal;

import io.github.stellflux.context.StellfluxLocalContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Default implementation backed by OpenTelemetry Context. */
public final class DefaultStellfluxLocalContext implements StellfluxLocalContext {

    private static final ContextKey<Map<String, String>> LOCAL_CONTEXT_KEY =
            ContextKey.named("stellflux.local-context");

    private final Context context;

    private DefaultStellfluxLocalContext(Context context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    public static StellfluxLocalContext from(Context context) {
        return new DefaultStellfluxLocalContext(context);
    }

    @Override
    public Optional<String> get(String key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(snapshot().get(key));
    }

    @Override
    public Map<String, String> snapshot() {
        Map<String, String> state = this.context.get(LOCAL_CONTEXT_KEY);
        if (state == null || state.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(state));
    }

    @Override
    public StellfluxLocalContext with(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Map<String, String> nextState = new LinkedHashMap<>(snapshot());
        nextState.put(key, value);
        return new DefaultStellfluxLocalContext(this.context.with(LOCAL_CONTEXT_KEY, nextState));
    }

    @Override
    public StellfluxLocalContext withAll(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        Map<String, String> nextState = new LinkedHashMap<>(snapshot());
        values.forEach(
                (key, v) -> {
                    Objects.requireNonNull(key, "values contains null key");
                    Objects.requireNonNull(v, "values contains null value");
                    nextState.put(key, v);
                });
        return new DefaultStellfluxLocalContext(this.context.with(LOCAL_CONTEXT_KEY, nextState));
    }

    @Override
    public StellfluxLocalContext without(String key) {
        Objects.requireNonNull(key, "key");
        Map<String, String> nextState = new LinkedHashMap<>(snapshot());
        nextState.remove(key);
        return new DefaultStellfluxLocalContext(this.context.with(LOCAL_CONTEXT_KEY, nextState));
    }

    @Override
    public Context storeInContext(Context context) {
        Objects.requireNonNull(context, "context");
        return context.with(LOCAL_CONTEXT_KEY, new LinkedHashMap<>(snapshot()));
    }

    @Override
    public Context toOpenTelemetryContext() {
        return this.context;
    }

    @Override
    public Scope makeCurrent() {
        return this.context.makeCurrent();
    }
}
