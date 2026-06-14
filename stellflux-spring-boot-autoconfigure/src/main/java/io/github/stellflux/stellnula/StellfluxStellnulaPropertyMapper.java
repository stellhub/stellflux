package io.github.stellflux.stellnula;

import io.github.stellnula.config.StellnulaConfigEntry;
import io.github.stellnula.config.StellnulaSnapshot;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.StringUtils;

/** Stellnula 配置项到 Spring PropertySource 属性的映射器。 */
final class StellfluxStellnulaPropertyMapper {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellnulaPropertyMapper.class.getName());

    private StellfluxStellnulaPropertyMapper() {}

    /**
     * 将 Stellnula 快照转换为 Spring 可解析的扁平属性。
     *
     * @param snapshot Stellnula 配置快照
     * @return Spring 扁平配置属性
     */
    static Map<String, String> toProperties(StellnulaSnapshot snapshot) {
        Map<String, String> properties = new LinkedHashMap<>();
        if (snapshot == null) {
            return properties;
        }
        for (StellnulaConfigEntry entry : snapshot.entries()) {
            if (entry.deleted()) {
                continue;
            }
            properties.putAll(toProperties(entry));
        }
        return properties;
    }

    private static Map<String, String> toProperties(StellnulaConfigEntry entry) {
        if (isYaml(entry)) {
            return yamlToProperties(entry);
        }
        if (isProperties(entry)) {
            return propertiesToProperties(entry);
        }
        return Map.of(entry.configKey(), entry.configValue());
    }

    private static Map<String, String> yamlToProperties(StellnulaConfigEntry entry) {
        YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
        factoryBean.setResources(
                new ByteArrayResource(
                        entry.configValue().getBytes(StandardCharsets.UTF_8), entry.configKey()));
        Properties loaded = factoryBean.getObject();
        return loaded == null ? Map.of() : toStringMap(loaded);
    }

    private static Map<String, String> propertiesToProperties(StellnulaConfigEntry entry) {
        Properties loaded = new Properties();
        try {
            loaded.load(new StringReader(entry.configValue()));
            return toStringMap(loaded);
        } catch (IOException ex) {
            LOGGER.log(
                    Level.WARNING,
                    ex,
                    () -> "Failed to parse Stellnula properties config: " + entry.configKey());
            return Map.of(entry.configKey(), entry.configValue());
        }
    }

    private static Map<String, String> toStringMap(Properties properties) {
        Map<String, String> result = new LinkedHashMap<>();
        properties.forEach((key, val) -> result.put(String.valueOf(key), String.valueOf(val)));
        return result;
    }

    private static boolean isYaml(StellnulaConfigEntry entry) {
        return hasContentType(entry, "YAML")
                || hasExtension(entry.configKey(), ".yaml")
                || hasExtension(entry.configKey(), ".yml");
    }

    private static boolean isProperties(StellnulaConfigEntry entry) {
        return hasContentType(entry, "PROPERTIES") || hasExtension(entry.configKey(), ".properties");
    }

    private static boolean hasContentType(StellnulaConfigEntry entry, String token) {
        return StringUtils.hasText(entry.contentType())
                && entry.contentType().toUpperCase().contains(token);
    }

    private static boolean hasExtension(String configKey, String extension) {
        return StringUtils.hasText(configKey) && configKey.toLowerCase().endsWith(extension);
    }
}
