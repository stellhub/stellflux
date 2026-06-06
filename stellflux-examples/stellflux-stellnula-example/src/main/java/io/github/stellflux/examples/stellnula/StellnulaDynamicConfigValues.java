package io.github.stellflux.examples.stellnula;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stellnula 动态配置值。 */
@Component
public class StellnulaDynamicConfigValues {

    @Value("${example.stellnula.dynamic.string-value:alpha}")
    private String stringValue;

    @Value("${example.stellnula.dynamic.byte-value:1}")
    private byte byteValue;

    @Value("${example.stellnula.dynamic.short-value:2}")
    private short shortValue;

    @Value("${example.stellnula.dynamic.int-value:3}")
    private int intValue;

    @Value("${example.stellnula.dynamic.long-value:4}")
    private long longValue;

    @Value("${example.stellnula.dynamic.float-value:5.5}")
    private float floatValue;

    @Value("${example.stellnula.dynamic.double-value:6.5}")
    private double doubleValue;

    @Value("${example.stellnula.dynamic.boolean-value:true}")
    private boolean booleanValue;

    @Value("${example.stellnula.dynamic.char-value:A}")
    private char charValue;

    /**
     * 返回当前配置快照。
     *
     * @return 当前动态配置快照
     */
    public DynamicConfigSnapshot snapshot() {
        return new DynamicConfigSnapshot(
                this.stringValue,
                this.byteValue,
                this.shortValue,
                this.intValue,
                this.longValue,
                this.floatValue,
                this.doubleValue,
                this.booleanValue,
                this.charValue);
    }

    /** 动态配置快照。 */
    public record DynamicConfigSnapshot(
            String stringValue,
            byte byteValue,
            short shortValue,
            int intValue,
            long longValue,
            float floatValue,
            double doubleValue,
            boolean booleanValue,
            char charValue) {}
}
