package io.github.stellflux.stellnula;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

class StellfluxValueRefreshPostProcessorTest {

    @Test
    void shouldRefreshValueAnnotatedFieldWhenStellnulaConfigChanges() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("demo.name", "alpha");
        GenericApplicationContext context = new GenericApplicationContext();
        context
                .getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("stellnula-test", values));
        context.registerBean(StellfluxValueRefreshPostProcessor.class);
        context.registerBean(ValueHolder.class);
        context.refresh();

        ValueHolder holder = context.getBean(ValueHolder.class);
        values.put("demo.name", "beta");
        context.publishEvent(new StellfluxStellnulaConfigChangeEvent(this, Set.of("demo.name"), 2));

        assertThat(holder.getName()).isEqualTo("beta");
        context.close();
    }

    @Test
    void shouldRefreshValueAnnotatedFieldAfterPropertySourceIsRegisteredLate() {
        Map<String, Object> values = new LinkedHashMap<>();
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(StellfluxValueRefreshPostProcessor.class);
        context.registerBean(ValueHolder.class);
        context.refresh();

        ValueHolder holder = context.getBean(ValueHolder.class);
        assertThat(holder.getName()).isEqualTo("fallback");

        values.put("demo.name", "alpha");
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource("stellnula-test", values));
        context.getBean(StellfluxValueRefreshPostProcessor.class).afterSingletonsInstantiated();

        assertThat(holder.getName()).isEqualTo("alpha");
        context.close();
    }

    static class ValueHolder {

        @Value("${demo.name:fallback}")
        private String name;

        String getName() {
            return this.name;
        }
    }
}
