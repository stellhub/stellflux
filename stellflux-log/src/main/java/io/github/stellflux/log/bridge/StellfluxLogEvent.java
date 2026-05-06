package io.github.stellflux.log.bridge;

import io.github.stellflux.log.model.StellfluxSeverity;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/** 框架无关的结构化日志事件。 */
@Getter
@Builder(toBuilder = true)
public class StellfluxLogEvent {

    @Builder.Default private final Instant timestamp = Instant.now();

    @Builder.Default private final Context context = Context.current();

    private final StellfluxSeverity severity;

    private final String loggerName;

    private final String threadName;

    private final String message;

    private final Throwable throwable;

    private final StellfluxErrorDescriptor error;

    @Singular("attribute")
    private final Map<String, Object> attributes;

    /**
     * 返回一份属性副本。
     *
     * @return 属性副本
     */
    public Map<String, Object> attributeSnapshot() {
        return attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }
}
