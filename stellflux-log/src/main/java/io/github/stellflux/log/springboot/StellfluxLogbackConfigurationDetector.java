package io.github.stellflux.log.springboot;

import java.net.URL;
import java.util.Properties;

/** Logback 配置探测器。 */
public final class StellfluxLogbackConfigurationDetector {

    private static final String[] CANDIDATES = {
        "logback.xml", "logback-spring.xml", "logback-test.xml"
    };

    private StellfluxLogbackConfigurationDetector() {}

    /**
     * 判断当前应用是否存在本地 Logback 配置。
     *
     * @param classLoader 类加载器
     * @param systemProperties 系统属性
     * @return 是否存在本地 Logback 配置
     */
    public static boolean hasLocalConfiguration(
            ClassLoader classLoader, Properties systemProperties) {
        String explicitLocation = systemProperties.getProperty("logback.configurationFile");
        if (explicitLocation != null && !explicitLocation.isBlank()) {
            return true;
        }
        for (String candidate : CANDIDATES) {
            URL resource = classLoader.getResource(candidate);
            if (resource != null) {
                return true;
            }
        }
        return false;
    }
}
