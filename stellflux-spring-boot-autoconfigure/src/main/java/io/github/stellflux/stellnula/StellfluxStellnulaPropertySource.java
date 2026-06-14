package io.github.stellflux.stellnula;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.EnumerablePropertySource;

/** Stellnula 配置中心 PropertySource。 */
public class StellfluxStellnulaPropertySource
        extends EnumerablePropertySource<Map<String, Object>> {

    private volatile Map<String, Object> properties;

    public StellfluxStellnulaPropertySource(String name, Map<String, String> properties) {
        super(name, new LinkedHashMap<>());
        replace(properties);
    }

    /**
     * 替换当前配置快照。
     *
     * @param properties 最新配置值
     */
    public void replace(Map<String, String> properties) {
        Map<String, Object> next = new LinkedHashMap<>();
        if (properties != null) {
            next.putAll(properties);
        }
        this.properties = Map.copyOf(next);
        getSource().clear();
        getSource().putAll(next);
    }

    @Override
    public String[] getPropertyNames() {
        return this.properties.keySet().toArray(String[]::new);
    }

    @Override
    public Object getProperty(String name) {
        return this.properties.get(name);
    }
}
