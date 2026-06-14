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

    @Test
    void shouldFlattenTomlConfigFile() {
        StellnulaSnapshot snapshot =
                new StellnulaSnapshot(
                        1,
                        "checksum",
                        List.of(
                                entry(
                                        "application.toml",
                                        "TOML",
                                        """
                                        [example.stellnula.dynamic]
                                        string-value = "beta"
                                        int-value = 13
                                        boolean-value = false
                                        tags = ["alpha", "beta"]

                                        [example.stellnula.dynamic.nested]
                                        long-value = 100
                                        """)));

        Map<String, String> properties = StellfluxStellnulaPropertyMapper.toProperties(snapshot);

        assertThat(properties)
                .containsEntry("example.stellnula.dynamic.string-value", "beta")
                .containsEntry("example.stellnula.dynamic.int-value", "13")
                .containsEntry("example.stellnula.dynamic.boolean-value", "false")
                .containsEntry("example.stellnula.dynamic.tags", "alpha,beta")
                .containsEntry("example.stellnula.dynamic.nested.long-value", "100");
        assertThat(properties).doesNotContainKey("application.toml");
    }

    @Test
    void shouldFlattenJavaPropertiesXmlConfigFile() {
        StellnulaSnapshot snapshot =
                new StellnulaSnapshot(
                        1,
                        "checksum",
                        List.of(
                                entry(
                                        "application.xml",
                                        "XML",
                                        """
                                        <?xml version="1.0" encoding="UTF-8"?>
                                        <!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
                                        <properties>
                                          <entry key="example.stellnula.dynamic.string-value">beta</entry>
                                          <entry key="example.stellnula.dynamic.int-value">13</entry>
                                          <entry key="example.stellnula.dynamic.boolean-value">false</entry>
                                        </properties>
                                        """)));

        Map<String, String> properties = StellfluxStellnulaPropertyMapper.toProperties(snapshot);

        assertThat(properties)
                .containsEntry("example.stellnula.dynamic.string-value", "beta")
                .containsEntry("example.stellnula.dynamic.int-value", "13")
                .containsEntry("example.stellnula.dynamic.boolean-value", "false");
        assertThat(properties).doesNotContainKey("application.xml");
    }

    @Test
    void shouldFlattenHierarchicalXmlConfigFile() {
        StellnulaSnapshot snapshot =
                new StellnulaSnapshot(
                        1,
                        "checksum",
                        List.of(
                                entry(
                                        "application.xml",
                                        "XML",
                                        """
                                        <example>
                                          <stellnula>
                                            <dynamic>
                                              <string-value>beta</string-value>
                                              <int-value>13</int-value>
                                              <boolean-value>false</boolean-value>
                                            </dynamic>
                                          </stellnula>
                                        </example>
                                        """)));

        Map<String, String> properties = StellfluxStellnulaPropertyMapper.toProperties(snapshot);

        assertThat(properties)
                .containsEntry("example.stellnula.dynamic.string-value", "beta")
                .containsEntry("example.stellnula.dynamic.int-value", "13")
                .containsEntry("example.stellnula.dynamic.boolean-value", "false");
        assertThat(properties).doesNotContainKey("application.xml");
    }

    private static StellnulaConfigEntry entry(String configKey, String contentType, String value) {
        return new StellnulaConfigEntry(
                "application",
                configKey,
                contentType,
                value,
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
                null);
    }
}
