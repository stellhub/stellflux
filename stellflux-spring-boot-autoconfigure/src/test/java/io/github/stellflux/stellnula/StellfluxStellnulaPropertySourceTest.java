package io.github.stellflux.stellnula;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellnula.config.StellnulaConfigEntry;
import io.github.stellnula.config.StellnulaSnapshot;
import java.util.List;
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

    @Test
    void shouldFlattenYamlConfigFile() {
        StellnulaSnapshot snapshot =
                new StellnulaSnapshot(
                        1,
                        "checksum",
                        List.of(
                                new StellnulaConfigEntry(
                                        "application-yaml",
                                        "application.yaml",
                                        "YAML",
                                        """
                                        example:
                                          stellnula:
                                            dynamic:
                                              string-value: beta
                                              int-value: 13
                                              boolean-value: false
                                        """,
                                        1,
                                        1,
                                        false,
                                        false,
                                        "BASE",
                                        null,
                                        null,
                                        null,
                                        "identity",
                                        "INLINE",
                                        0,
                                        "",
                                        null)));

        Map<String, String> properties = StellfluxStellnulaPropertyMapper.toProperties(snapshot);

        assertThat(properties)
                .containsEntry("example.stellnula.dynamic.string-value", "beta")
                .containsEntry("example.stellnula.dynamic.int-value", "13")
                .containsEntry("example.stellnula.dynamic.boolean-value", "false");
        assertThat(properties).doesNotContainKey("application.yaml");
    }
}
