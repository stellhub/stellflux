package io.github.stellflux.log.springboot;

import io.github.stellflux.opentelemetry.internal.EnvParsers;
import java.util.Map;
import java.util.Properties;

/** Spring Boot 日志模式决策器。 */
public final class StellfluxLogBootstrapModeResolver {

    private static final String[] STDOUT_KEYS = {
        "LOG_STDOUT", "log.stdout", "stellspec.log.stdout", "stellspec.stdout"
    };

    private StellfluxLogBootstrapModeResolver() {}

    /**
     * 根据环境、系统属性和命令行参数解析日志模式。
     *
     * @param env 环境变量
     * @param systemProperties 系统属性
     * @param args 启动参数
     * @param classLoader 类加载器
     * @return 路由模式
     */
    public static StellfluxLogBootstrapMode resolve(
            Map<String, String> env,
            Properties systemProperties,
            String[] args,
            ClassLoader classLoader) {
        if (isStdoutRequested(env, systemProperties, args)) {
            return StellfluxLogBootstrapMode.STDOUT;
        }
        if (StellfluxLogbackConfigurationDetector.hasLocalConfiguration(
                classLoader, systemProperties)) {
            return StellfluxLogBootstrapMode.LOCAL_LOGBACK;
        }
        return StellfluxLogBootstrapMode.OTEL;
    }

    /**
     * 判断是否显式要求输出到标准输出。
     *
     * @param env 环境变量
     * @param systemProperties 系统属性
     * @param args 启动参数
     * @return 是否显式要求标准输出
     */
    public static boolean isStdoutRequested(
            Map<String, String> env, Properties systemProperties, String[] args) {
        Map<String, String> parsedArgs = StellfluxLogCommandLine.parse(args);
        for (String key : STDOUT_KEYS) {
            if (EnvParsers.parseBoolean(env.get(key), false)
                    || EnvParsers.parseBoolean(systemProperties.getProperty(key), false)
                    || EnvParsers.parseBoolean(parsedArgs.get(key), false)) {
                return true;
            }
        }
        return false;
    }
}
