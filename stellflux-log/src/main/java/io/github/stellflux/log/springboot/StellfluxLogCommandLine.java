package io.github.stellflux.log.springboot;

import java.util.LinkedHashMap;
import java.util.Map;

/** 命令行参数解析器。 */
public final class StellfluxLogCommandLine {

    private StellfluxLogCommandLine() {}

    /**
     * 将命令行参数解析为键值对。
     *
     * @param args 原始命令行参数
     * @return 参数键值对
     */
    public static Map<String, String> parse(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        if (args == null) {
            return values;
        }
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            String normalized = normalizePrefix(arg.trim());
            int separator = normalized.indexOf('=');
            if (separator <= 0) {
                values.put(normalized, "true");
                continue;
            }
            values.put(normalized.substring(0, separator), normalized.substring(separator + 1));
        }
        return values;
    }

    private static String normalizePrefix(String arg) {
        if (arg.startsWith("--")) {
            return arg.substring(2);
        }
        if (arg.startsWith("-D")) {
            return arg.substring(2);
        }
        return arg;
    }
}
