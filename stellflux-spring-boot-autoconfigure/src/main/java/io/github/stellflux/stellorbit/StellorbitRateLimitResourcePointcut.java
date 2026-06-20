package io.github.stellflux.stellorbit;

import io.github.stellflux.stellorbit.ratelimit.annotation.StellorbitRateLimitResource;
import java.lang.reflect.Method;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.aop.support.AopUtils;

/** StellOrbit 注解式限流切点。 */
final class StellorbitRateLimitResourcePointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return findAnnotation(method, targetClass) != null;
    }

    /** 查找方法上的 StellOrbit 限流资源注解。 */
    @Nullable
    static StellorbitRateLimitResource findAnnotation(Method method, Class<?> targetClass) {
        Method specificMethod =
                targetClass == null ? method : AopUtils.getMostSpecificMethod(method, targetClass);
        StellorbitRateLimitResource annotation =
                AnnotatedElementUtils.findMergedAnnotation(
                        specificMethod, StellorbitRateLimitResource.class);
        if (annotation != null) {
            return annotation;
        }
        if (!specificMethod.equals(method)) {
            return AnnotatedElementUtils.findMergedAnnotation(
                    method, StellorbitRateLimitResource.class);
        }
        return null;
    }
}
