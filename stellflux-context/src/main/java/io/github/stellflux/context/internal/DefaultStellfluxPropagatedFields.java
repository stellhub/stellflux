package io.github.stellflux.context.internal;

import io.github.stellflux.context.StellfluxPropagatedFields;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Default immutable propagated field registry. */
public final class DefaultStellfluxPropagatedFields implements StellfluxPropagatedFields {

    private static final StellfluxPropagatedFields EMPTY =
            new DefaultStellfluxPropagatedFields(Collections.emptyMap());

    private final Map<String, String> mappings;

    private DefaultStellfluxPropagatedFields(Map<String, String> mappings) {
        this.mappings = Map.copyOf(mappings);
    }

    public static StellfluxPropagatedFields empty() {
        return EMPTY;
    }

    public static StellfluxPropagatedFields.Builder builder() {
        return new BuilderImpl();
    }

    @Override
    public Map<String, String> mappings() {
        return this.mappings;
    }

    @Override
    public boolean contains(String localKey) {
        Objects.requireNonNull(localKey, "localKey");
        return this.mappings.containsKey(localKey);
    }

    @Override
    public Optional<String> resolveBaggageKey(String localKey) {
        Objects.requireNonNull(localKey, "localKey");
        return Optional.ofNullable(this.mappings.get(localKey));
    }

    private static final class BuilderImpl implements StellfluxPropagatedFields.Builder {

        private final Map<String, String> mappings = new LinkedHashMap<>();

        @Override
        public StellfluxPropagatedFields.Builder add(String key) {
            return add(key, key);
        }

        @Override
        public StellfluxPropagatedFields.Builder add(String localKey, String baggageKey) {
            Objects.requireNonNull(localKey, "localKey");
            Objects.requireNonNull(baggageKey, "baggageKey");
            this.mappings.put(localKey, baggageKey);
            return this;
        }

        @Override
        public StellfluxPropagatedFields build() {
            return new DefaultStellfluxPropagatedFields(this.mappings);
        }
    }
}
