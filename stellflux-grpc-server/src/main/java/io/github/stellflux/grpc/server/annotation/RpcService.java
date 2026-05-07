package io.github.stellflux.grpc.server.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

/**
 * 标记 gRPC 服务实现 Bean。
 *
 * <p>该注解同时具备 Spring {@link Service} 语义，业务实现类只需声明一个注解即可被容器发现。
 */
@Documented
@Inherited
@Service
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
    int order() default Ordered.LOWEST_PRECEDENCE;
}
