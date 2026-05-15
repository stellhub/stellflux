package io.github.stellflux.datasource;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 数据库客户端属性。 */
final class StellfluxDatabaseAttributes {

    private static final Pattern MYSQL_URL_PATTERN =
            Pattern.compile("^jdbc:mysql://([^/:?]+)(?::(\\d+))?(?:/([^?;]*))?.*$");

    private static final int MYSQL_DEFAULT_PORT = 3306;

    private final String system;

    private final String serverAddress;

    private final int serverPort;

    private final String database;

    private final String user;

    private StellfluxDatabaseAttributes(
            String system, String serverAddress, int serverPort, String database, String user) {
        this.system = system;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.database = database;
        this.user = user;
    }

    /**
     * 从配置创建数据库属性。
     *
     * @param options DataSource 配置
     * @return 数据库属性
     */
    static StellfluxDatabaseAttributes from(StellfluxDataSourceOptions options) {
        String url = options == null ? null : options.getUrl();
        String user = options == null ? null : options.getUsername();
        Matcher matcher = url == null ? null : MYSQL_URL_PATTERN.matcher(url);
        if (matcher != null && matcher.matches()) {
            return new StellfluxDatabaseAttributes(
                    "mysql",
                    matcher.group(1),
                    parsePort(matcher.group(2)),
                    emptyToUnknown(matcher.group(3)),
                    emptyToUnknown(user));
        }
        return new StellfluxDatabaseAttributes(
                "mysql", "unknown", MYSQL_DEFAULT_PORT, "unknown", emptyToUnknown(user));
    }

    Attributes connectionMetricAttributes(String errorType) {
        AttributesBuilder builder = baseMetricAttributes();
        if (errorType != null) {
            builder.put("error.type", errorType);
        }
        return builder.build();
    }

    Attributes sqlMetricAttributes(String operation, String errorType) {
        AttributesBuilder builder = baseMetricAttributes();
        builder.put("db.operation.name", operation);
        if (errorType != null) {
            builder.put("error.type", errorType);
        }
        return builder.build();
    }

    void populateConnectionSpan(io.opentelemetry.api.trace.Span span) {
        populateBaseSpan(span);
    }

    void populateSqlSpan(io.opentelemetry.api.trace.Span span, String sql, String operation) {
        populateBaseSpan(span);
        span.setAttribute("db.operation.name", operation);
        if (sql != null && !sql.isBlank()) {
            span.setAttribute("db.query.text", sql);
        }
    }

    void populateConnectionLog(io.opentelemetry.api.logs.LogRecordBuilder builder) {
        populateBaseLog(builder);
    }

    void populateSqlLog(
            io.opentelemetry.api.logs.LogRecordBuilder builder, String sql, String operation) {
        populateBaseLog(builder);
        builder.setAttribute(
                io.opentelemetry.api.common.AttributeKey.stringKey("db.operation.name"), operation);
        if (sql != null && !sql.isBlank()) {
            builder.setAttribute(
                    io.opentelemetry.api.common.AttributeKey.stringKey("db.query.text"), sql);
        }
    }

    static String resolveOperation(String sql) {
        if (sql == null || sql.isBlank()) {
            return "SQL";
        }
        String trimmed = sql.stripLeading();
        if (trimmed.startsWith("/*")) {
            int commentEnd = trimmed.indexOf("*/");
            if (commentEnd >= 0) {
                trimmed = trimmed.substring(commentEnd + 2).stripLeading();
            }
        }
        int separator = 0;
        while (separator < trimmed.length()
                && Character.isLetter(trimmed.charAt(separator))) {
            separator++;
        }
        if (separator == 0) {
            return "SQL";
        }
        return trimmed.substring(0, separator).toUpperCase(Locale.ROOT);
    }

    private AttributesBuilder baseMetricAttributes() {
        AttributesBuilder builder = Attributes.builder();
        builder.put("db.system.name", this.system);
        builder.put("db.namespace", this.database);
        builder.put("server.address", this.serverAddress);
        builder.put("server.port", (long) this.serverPort);
        return builder;
    }

    private void populateBaseSpan(io.opentelemetry.api.trace.Span span) {
        span.setAttribute("db.system.name", this.system);
        span.setAttribute("db.namespace", this.database);
        span.setAttribute("server.address", this.serverAddress);
        span.setAttribute("server.port", this.serverPort);
        span.setAttribute("db.user", this.user);
    }

    private void populateBaseLog(io.opentelemetry.api.logs.LogRecordBuilder builder) {
        builder.setAttribute(
                io.opentelemetry.api.common.AttributeKey.stringKey("db.system.name"), this.system);
        builder.setAttribute(
                io.opentelemetry.api.common.AttributeKey.stringKey("db.namespace"), this.database);
        builder.setAttribute(
                io.opentelemetry.api.common.AttributeKey.stringKey("server.address"),
                this.serverAddress);
        builder.setAttribute(
                io.opentelemetry.api.common.AttributeKey.longKey("server.port"),
                (long) this.serverPort);
        builder.setAttribute(io.opentelemetry.api.common.AttributeKey.stringKey("db.user"), this.user);
    }

    private static int parsePort(String port) {
        if (port == null || port.isBlank()) {
            return MYSQL_DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(port);
        } catch (NumberFormatException ignored) {
            return MYSQL_DEFAULT_PORT;
        }
    }

    private static String emptyToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
