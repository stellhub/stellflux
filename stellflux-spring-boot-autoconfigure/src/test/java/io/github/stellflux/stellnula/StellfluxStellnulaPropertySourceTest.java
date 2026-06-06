package io.github.stellflux.stellnula;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StellfluxStellnulaPropertySourceTest {

    @Test
    void shouldReplaceSnapshotProperties() {
        StellfluxStellnulaPropertySource propertySource =
                new StellfluxStellnulaPropertySource(
                        "stellnula", Map.of("demo.name", "alpha", "demo.enabled", "true"));

        propertySource.replace(Map.of("demo.name", "beta"));

        assertThat(propertySource.getProperty("demo.name")).isEqualTo("beta");
        assertThat(propertySource.getProperty("demo.enabled")).isNull();
        assertThat(propertySource.getPropertyNames()).containsExactly("demo.name");
    }
}
