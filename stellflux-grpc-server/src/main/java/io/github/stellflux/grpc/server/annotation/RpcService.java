package io.github.stellflux.grpc.server.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 gRPC 服务实现 Bean。
 *
 * <p>该注解仅描述 gRPC 服务暴露元数据，Spring 场景下的 Bean 注册由 starter 自动完成。
 */
@Documented
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcService {

    /**
     * 服务注册标识。
     *
     * <p>未显式配置时，默认回退到 gRPC Service Descriptor 名称。
     *
     * @return 服务注册标识
     */
    String serviceId() default "";

    /**
     * 是否启用当前服务暴露。
     *
     * @return 是否启用
     */
    boolean enabled() default true;

    /**
     * 服务暴露顺序。
     *
     * @return 顺序值
     */
    int order() default Integer.MAX_VALUE;
}
