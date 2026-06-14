package io.github.stellflux.opentelemetry;

import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** Stellflux 服务名解析器。 */
public final class StellfluxServiceNameResolver {

    private static final String RESOURCE_SERVICE_NAME =
            "stellflux.opentelemetry.resource.service-name";

    private static final String SPRING_APPLICATION_NAME = "spring.application.name";

    private static final String DEFAULT_PROPERTIES_SOURCE_NAME = "defaultProperties";

    private static final String CONFIGURATION_PROPERTIES_SOURCE_NAME = "configurationProperties";

    private static final ConversionService CONVERSION_SERVICE =
            ApplicationConversionService.getSharedInstance();

    private StellfluxServiceNameResolver() {}

    /**
     * 按 OpenTelemetry 同一优先级链路解析服务名。
     *
     * @param environment Spring 环境
     * @return 最终服务名
     */
    public static String resolve(Environment environment) {
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            String serviceName =
                    firstNonBlank(
                            findString(
                                    configurableEnvironment,
                                    PropertySourceLayer.CONFIGURATION,
                                    RESOURCE_SERVICE_NAME),
                            findString(
                                    configurableEnvironment,
                                    PropertySourceLayer.CONFIGURATION,
                                    SPRING_APPLICATION_NAME),
                            findString(
                                    configurableEnvironment,
                                    PropertySourceLayer.COMMAND_LINE,
                                    RESOURCE_SERVICE_NAME,
                                    SPRING_APPLICATION_NAME,
                                    "stellflux_OTEL_SERVICE_NAME",
                                    "OTEL_SERVICE_NAME",
                                    "STELLAR_APP_NAME"),
                            findString(
                                    configurableEnvironment,
                                    PropertySourceLayer.ENVIRONMENT,
                                    RESOURCE_SERVICE_NAME,
                                    SPRING_APPLICATION_NAME,
                                    "stellflux_OTEL_SERVICE_NAME",
                                    "OTEL_SERVICE_NAME",
                                    "STELLAR_APP_NAME"),
                            findString(
                                    configurableEnvironment, PropertySourceLayer.DEFAULT, RESOURCE_SERVICE_NAME));
            return firstNonBlank(serviceName, "unknown-service");
        }
        return firstNonBlank(
                environment.getProperty(RESOURCE_SERVICE_NAME),
                environment.getProperty(SPRING_APPLICATION_NAME),
                "unknown-service");
    }

    private static String findString(
            ConfigurableEnvironment environment, PropertySourceLayer layer, String... keys) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (classify(propertySource) != layer) {
                continue;
            }
            for (String key : keys) {
                String value = convertToString(propertySource.getProperty(key));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String convertToString(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = CONVERSION_SERVICE.convert(rawValue, String.class);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static PropertySourceLayer classify(PropertySource<?> propertySource) {
        String name = propertySource.getName();
        if (CONFIGURATION_PROPERTIES_SOURCE_NAME.equals(name)) {
            return PropertySourceLayer.IGNORED;
        }
        if (propertySource instanceof CommandLinePropertySource<?>
                || "commandLineArgs".equals(name)
                || StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME.equals(name)) {
            return PropertySourceLayer.COMMAND_LINE;
        }
        if (propertySource instanceof SystemEnvironmentPropertySource
                || StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME.equals(name)) {
            return PropertySourceLayer.ENVIRONMENT;
        }
        if (DEFAULT_PROPERTIES_SOURCE_NAME.equals(name)) {
            return PropertySourceLayer.DEFAULT;
        }
        return PropertySourceLayer.CONFIGURATION;
    }

    private enum PropertySourceLayer {
        CONFIGURATION,
        COMMAND_LINE,
        ENVIRONMENT,
        DEFAULT,
        IGNORED
    }
}
