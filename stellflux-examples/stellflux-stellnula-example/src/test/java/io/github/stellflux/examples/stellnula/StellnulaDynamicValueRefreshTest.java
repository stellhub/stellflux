package io.github.stellflux.examples.stellnula;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stellflux.stellnula.StellfluxStellnulaConfigChangeEvent;
import io.github.stellflux.stellnula.StellfluxValueRefreshPostProcessor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

class StellnulaDynamicValueRefreshTest {

    @Test
    void shouldRefreshStringAndPrimitiveValueFields() {
        Map<String, Object> values = initialValues();
        ApplicationContextRunner contextRunner =
                new ApplicationContextRunner()
                        .withInitializer(
                                context ->
                                        context
                                                .getEnvironment()
                                                .getPropertySources()
                                                .addFirst(new MapPropertySource("stellnula-test", values)))
                        .withBean(StellfluxValueRefreshPostProcessor.class)
                        .withBean(StellnulaDynamicConfigValues.class);

        contextRunner.run(
                context -> {
                    StellnulaDynamicConfigValues configValues =
                            context.getBean(StellnulaDynamicConfigValues.class);
                    assertThat(configValues.snapshot().stringValue()).isEqualTo("alpha");
                    assertThat(configValues.snapshot().intValue()).isEqualTo(3);

                    values.put("example.stellnula.dynamic.string-value", "beta");
                    values.put("example.stellnula.dynamic.byte-value", "11");
                    values.put("example.stellnula.dynamic.short-value", "12");
                    values.put("example.stellnula.dynamic.int-value", "13");
                    values.put("example.stellnula.dynamic.long-value", "14");
                    values.put("example.stellnula.dynamic.float-value", "15.5");
                    values.put("example.stellnula.dynamic.double-value", "16.5");
                    values.put("example.stellnula.dynamic.boolean-value", "false");
                    values.put("example.stellnula.dynamic.char-value", "B");
                    context.publishEvent(
                            new StellfluxStellnulaConfigChangeEvent(this, Set.copyOf(values.keySet()), 2));

                    StellnulaDynamicConfigValues.DynamicConfigSnapshot snapshot = configValues.snapshot();
                    assertThat(snapshot.stringValue()).isEqualTo("beta");
                    assertThat(snapshot.byteValue()).isEqualTo((byte) 11);
                    assertThat(snapshot.shortValue()).isEqualTo((short) 12);
                    assertThat(snapshot.intValue()).isEqualTo(13);
                    assertThat(snapshot.longValue()).isEqualTo(14L);
                    assertThat(snapshot.floatValue()).isEqualTo(15.5F);
                    assertThat(snapshot.doubleValue()).isEqualTo(16.5D);
                    assertThat(snapshot.booleanValue()).isFalse();
                    assertThat(snapshot.charValue()).isEqualTo('B');
                });
    }

    private Map<String, Object> initialValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("example.stellnula.dynamic.string-value", "alpha");
        values.put("example.stellnula.dynamic.byte-value", "1");
        values.put("example.stellnula.dynamic.short-value", "2");
        values.put("example.stellnula.dynamic.int-value", "3");
        values.put("example.stellnula.dynamic.long-value", "4");
        values.put("example.stellnula.dynamic.float-value", "5.5");
        values.put("example.stellnula.dynamic.double-value", "6.5");
        values.put("example.stellnula.dynamic.boolean-value", "true");
        values.put("example.stellnula.dynamic.char-value", "A");
        return values;
    }
}
