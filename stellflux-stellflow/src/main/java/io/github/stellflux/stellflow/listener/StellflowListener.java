package io.github.stellflux.stellflow.listener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明 Stellflow 消费监听方法。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StellflowListener {

    /**
     * 消费主题。
     *
     * @return 主题
     */
    String topic() default "";

    /**
     * 消费主题集合，配置后会订阅多个主题。
     *
     * @return 主题集合
     */
    String[] topics() default {};

    /**
     * 消费组，留空时使用全局 consumer group-id。
     *
     * @return 消费组
     */
    String groupId() default "";

    /**
     * 单次拉取超时时间。
     *
     * @return Duration 字符串，例如 1s
     */
    String pollTimeout() default "";

    /**
     * 是否自动提交 offset。
     *
     * @return 是否自动提交
     */
    boolean autoCommit() default true;
}
