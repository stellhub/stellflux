package io.github.stellflux.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class StellfluxMetricNamingTest {

    @Test
    void shouldAcceptUnderscoreMetricName() {
        assertEquals(
                "stellflux_http_client_requests",
                StellfluxMetricNaming.requireValidMetricName("stellflux_http_client_requests"));
    }

    @Test
    void shouldRejectDotSeparatedMetricName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StellfluxMetricNaming.requireValidMetricName("stellflux.http.client.requests"));
    }

    @Test
    void shouldEnsureBuiltInMetricConstantsFollowConvention() throws IllegalAccessException {
        for (Field field : StellfluxMetricNames.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                String metricName = (String) field.get(null);
                assertEquals(metricName, StellfluxMetricNaming.requireValidMetricName(metricName));
            }
        }
    }
}
