package io.github.stellflux.log.springboot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class StellfluxLogBootstrapModeResolverTest {

    @Test
    void shouldPreferStdoutWhenEnvironmentEnabled() {
        StellfluxLogBootstrapMode mode =
                StellfluxLogBootstrapModeResolver.resolve(
                        Map.of("LOG_STDOUT", "true"),
                        new Properties(),
                        new String[0],
                        getClass().getClassLoader());
        assertEquals(StellfluxLogBootstrapMode.STDOUT, mode);
    }

    @Test
    void shouldPreferStdoutWhenCommandLineEnabled() {
        StellfluxLogBootstrapMode mode =
                StellfluxLogBootstrapModeResolver.resolve(
                        Map.of(),
                        new Properties(),
                        new String[] {"--LOG_STDOUT=true"},
                        getClass().getClassLoader());
        assertEquals(StellfluxLogBootstrapMode.STDOUT, mode);
    }

    @Test
    void shouldPreferLocalLogbackWhenConfigurationExists() throws Exception {
        Path tempDirectory = Files.createTempDirectory("stellflux-logback-test");
        Files.writeString(tempDirectory.resolve("logback.xml"), "<configuration />");
        try (URLClassLoader classLoader =
                new URLClassLoader(new URL[] {tempDirectory.toUri().toURL()}, null)) {
            StellfluxLogBootstrapMode mode =
                    StellfluxLogBootstrapModeResolver.resolve(
                            Map.of(), new Properties(), new String[0], classLoader);
            assertEquals(StellfluxLogBootstrapMode.LOCAL_LOGBACK, mode);
        }
    }

    @Test
    void shouldFallbackToOtelWhenNoLocalRoutingRuleExists() {
        StellfluxLogBootstrapMode mode =
                StellfluxLogBootstrapModeResolver.resolve(
                        Map.of(), new Properties(), new String[0], ClassLoader.getSystemClassLoader());
        assertEquals(StellfluxLogBootstrapMode.OTEL, mode);
    }
}
