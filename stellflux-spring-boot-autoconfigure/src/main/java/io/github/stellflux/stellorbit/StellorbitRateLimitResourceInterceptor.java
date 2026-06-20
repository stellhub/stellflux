package io.github.stellflux.stellorbit;

import io.github.stellflux.stellorbit.ratelimit.RateLimitAcquireOptions;
import io.github.stellflux.stellorbit.ratelimit.RateLimitDecision;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRejectedException;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimitRequest;
import io.github.stellflux.stellorbit.ratelimit.StellorbitRateLimiter;
import io.github.stellflux.stellorbit.ratelimit.annotation.RateLimitAcquireMode;
import io.github.stellflux.stellorbit.ratelimit.annotation.StellorbitRateLimitResource;
import io.github.stellorbit.client.model.RequestContext;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/** StellOrbit 注解式限流拦截器。 */
public class StellorbitRateLimitResourceInterceptor implements MethodInterceptor {

    private final StellorbitRateLimiter rateLimiter;
    private final StellfluxStellorbitProperties properties;
    private final Environment environment;
    private final ApplicationContext applicationContext;

    public StellorbitRateLimitResourceInterceptor(
            StellorbitRateLimiter rateLimiter,
            StellfluxStellorbitProperties properties,
            Environment environment,
            ApplicationContext applicationContext) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.applicationContext =
                Objects.requireNonNull(applicationContext, "applicationContext must not be null");
    }

    /** 对带有 StellOrbit 限流资源注解的方法执行配额申请。 */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Object target = invocation.getThis();
        Class<?> targetClass = target == null ? method.getDeclaringClass() : target.getClass();
        StellorbitRateLimitResource annotation =
                StellorbitRateLimitResourcePointcut.findAnnotation(method, targetClass);
        if (annotation == null) {
            return invocation.proceed();
        }

        String resourceKey = resourceKey(annotation);
        StellorbitRateLimitRequest request =
                new StellorbitRateLimitRequest(
                        serviceName(annotation),
                        resourceKey,
                        requestContext(annotation, resourceKey),
                        attributes(annotation, resourceKey, method));
        RateLimitDecision decision = rateLimiter.acquire(request, acquireOptions(annotation));
        if (decision.allowed()) {
            return invocation.proceed();
        }
        StellorbitRateLimitRejectedException exception =
                new StellorbitRateLimitRejectedException(request, decision);
        if (hasText(annotation.fallback())) {
            return invokeFallback(invocation, annotation, exception);
        }
        throw rejectedException(annotation.exceptionClass(), exception);
    }

    private Object invokeFallback(
            MethodInvocation invocation,
            StellorbitRateLimitResource annotation,
            StellorbitRateLimitRejectedException exception)
            throws Throwable {
        String fallbackName = annotation.fallback().trim();
        rejectSelfFallback(invocation.getMethod(), fallbackName);
        Object fallbackTarget = fallbackTarget(invocation, annotation.fallbackClass());
        Class<?> fallbackType =
                fallbackTarget instanceof Class<?> targetClass
                        ? targetClass
                        : ClassUtils.getUserClass(fallbackTarget);
        FallbackMethod fallbackMethod =
                findFallbackMethod(
                        fallbackType,
                        fallbackName,
                        invocation.getMethod(),
                        invocation.getArguments(),
                        exception);
        if (fallbackMethod == null) {
            throw new IllegalStateException(
                    "Cannot find rate limit fallback method '"
                            + annotation.fallback()
                            + "' for "
                            + invocation.getMethod());
        }
        Method method = fallbackMethod.method();
        Object target = Modifier.isStatic(method.getModifiers()) ? null : fallbackTarget;
        if (target instanceof Class<?>) {
            throw new IllegalStateException(
                    "Rate limit fallback method '" + method + "' must be static or a Spring bean");
        }
        if (target != null) {
            method = AopUtils.selectInvocableMethod(method, target.getClass());
        }
        try {
            ReflectionUtils.makeAccessible(method);
            return method.invoke(target, fallbackMethod.arguments());
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }

    private void rejectSelfFallback(Method sourceMethod, String fallbackName) {
        if (sourceMethod.getName().equals(fallbackName)) {
            throw new IllegalArgumentException(
                    "@StellorbitRateLimitResource fallback must not reference the protected method itself: "
                            + sourceMethod);
        }
    }

    private Object fallbackTarget(MethodInvocation invocation, Class<?> fallbackClass) {
        if (fallbackClass == Void.class) {
            Object target = invocation.getThis();
            return Objects.requireNonNull(target, "rate limit fallback target must not be null");
        }
        ObjectProvider<?> provider = applicationContext.getBeanProvider(fallbackClass);
        Object bean = provider.getIfAvailable();
        return bean == null ? fallbackClass : bean;
    }

    private FallbackMethod findFallbackMethod(
            Class<?> fallbackType,
            String fallbackName,
            Method sourceMethod,
            Object[] sourceArguments,
            StellorbitRateLimitRejectedException exception) {
        FallbackMethod best = null;
        for (Method candidate : ReflectionUtils.getAllDeclaredMethods(fallbackType)) {
            if (candidate.isBridge() || candidate.isSynthetic()) {
                continue;
            }
            FallbackMethod matched =
                    matchFallbackMethod(candidate, fallbackName, sourceMethod, sourceArguments, exception);
            if (matched != null && (best == null || matched.priority() < best.priority())) {
                best = matched;
            }
        }
        return best;
    }

    private FallbackMethod matchFallbackMethod(
            Method candidate,
            String fallbackName,
            Method sourceMethod,
            Object[] sourceArguments,
            StellorbitRateLimitRejectedException exception) {
        if (!candidate.getName().equals(fallbackName)
                || !returnTypeCompatible(sourceMethod.getReturnType(), candidate.getReturnType())) {
            return null;
        }
        Class<?>[] sourceParameterTypes = sourceMethod.getParameterTypes();
        Class<?>[] fallbackParameterTypes = candidate.getParameterTypes();
        if (methodParametersPrefix(fallbackParameterTypes, sourceParameterTypes)
                && exceptionParameter(fallbackParameterTypes, sourceParameterTypes.length)) {
            Object[] arguments = new Object[sourceArguments.length + 1];
            System.arraycopy(sourceArguments, 0, arguments, 0, sourceArguments.length);
            arguments[arguments.length - 1] = exception;
            return new FallbackMethod(candidate, arguments, 0);
        }
        if (sameMethodParameters(fallbackParameterTypes, sourceParameterTypes)) {
            return new FallbackMethod(candidate, sourceArguments, 1);
        }
        if (fallbackParameterTypes.length == 1
                && fallbackParameterTypes[0].isAssignableFrom(exception.getClass())) {
            return new FallbackMethod(candidate, new Object[] {exception}, 2);
        }
        if (fallbackParameterTypes.length == 0) {
            return new FallbackMethod(candidate, new Object[0], 3);
        }
        return null;
    }

    private boolean methodParametersPrefix(
            Class<?>[] fallbackParameterTypes, Class<?>[] sourceParameterTypes) {
        if (fallbackParameterTypes.length != sourceParameterTypes.length + 1) {
            return false;
        }
        for (int i = 0; i < sourceParameterTypes.length; i++) {
            if (!assignable(fallbackParameterTypes[i], sourceParameterTypes[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean sameMethodParameters(Class<?>[] fallbackParameterTypes, Class<?>[] sourceParameterTypes) {
        if (fallbackParameterTypes.length != sourceParameterTypes.length) {
            return false;
        }
        for (int i = 0; i < sourceParameterTypes.length; i++) {
            if (!assignable(fallbackParameterTypes[i], sourceParameterTypes[i])) {
                return false;
            }
        }
        return true;
    }

    private boolean exceptionParameter(Class<?>[] parameterTypes, int index) {
        return parameterTypes.length == index + 1
                && parameterTypes[index].isAssignableFrom(StellorbitRateLimitRejectedException.class);
    }

    private boolean returnTypeCompatible(Class<?> sourceReturnType, Class<?> fallbackReturnType) {
        if (sourceReturnType == Void.TYPE) {
            return fallbackReturnType == Void.TYPE;
        }
        if (fallbackReturnType == Void.TYPE) {
            return false;
        }
        return assignable(sourceReturnType, fallbackReturnType);
    }

    private boolean assignable(Class<?> targetType, Class<?> sourceType) {
        return ClassUtils.resolvePrimitiveIfNecessary(targetType)
                .isAssignableFrom(ClassUtils.resolvePrimitiveIfNecessary(sourceType));
    }

    private RuntimeException rejectedException(
            Class<? extends RuntimeException> exceptionClass,
            StellorbitRateLimitRejectedException fallbackException) {
        if (exceptionClass == StellorbitRateLimitRejectedException.class) {
            return fallbackException;
        }
        RuntimeException custom =
                instantiateException(
                        exceptionClass,
                        fallbackException.request(),
                        fallbackException.decision(),
                        fallbackException.getMessage());
        if (custom != null) {
            return custom;
        }
        throw new IllegalStateException(
                "Cannot instantiate rate limit exception class " + exceptionClass.getName());
    }

    private RuntimeException instantiateException(
            Class<? extends RuntimeException> exceptionClass,
            StellorbitRateLimitRequest request,
            RateLimitDecision decision,
            String message) {
        RuntimeException exception =
                instantiate(
                        exceptionClass,
                        new Class<?>[] {StellorbitRateLimitRequest.class, RateLimitDecision.class},
                        request,
                        decision);
        if (exception != null) {
            return exception;
        }
        exception =
                instantiate(
                        exceptionClass,
                        new Class<?>[] {
                            String.class, StellorbitRateLimitRequest.class, RateLimitDecision.class
                        },
                        message,
                        request,
                        decision);
        if (exception != null) {
            return exception;
        }
        exception = instantiate(exceptionClass, new Class<?>[] {String.class}, message);
        return exception == null ? instantiate(exceptionClass, new Class<?>[0]) : exception;
    }

    private RuntimeException instantiate(
            Class<? extends RuntimeException> exceptionClass, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Constructor<? extends RuntimeException> constructor =
                    exceptionClass.getDeclaredConstructor(parameterTypes);
            ReflectionUtils.makeAccessible(constructor);
            return constructor.newInstance(arguments);
        } catch (NoSuchMethodException ex) {
            return null;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "Failed to instantiate rate limit exception class " + exceptionClass.getName(), ex);
        }
    }

    private RateLimitAcquireOptions acquireOptions(StellorbitRateLimitResource annotation) {
        if (annotation.mode() == RateLimitAcquireMode.BLOCKING) {
            return RateLimitAcquireOptions.blocking(timeout(annotation.timeoutMillis()));
        }
        return RateLimitAcquireOptions.rejecting();
    }

    private Duration timeout(long timeoutMillis) {
        return timeoutMillis <= 0L ? null : Duration.ofMillis(timeoutMillis);
    }

    private RequestContext requestContext(
            StellorbitRateLimitResource annotation, String resourceKey) {
        return RequestContext.builder()
                .tenantId(resolve(annotation.tenantId()))
                .quotaKey(resourceKey)
                .authContextId(resolve(annotation.authContextId()))
                .trafficClass(resolve(annotation.trafficClass()))
                .trafficTag(resolve(annotation.trafficTag()))
                .build();
    }

    private Map<String, String> attributes(
            StellorbitRateLimitResource annotation, String resourceKey, Method method) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        putIfText(attributes, "resource", defaultText(resolve(annotation.resource()), resourceKey));
        putIfText(attributes, "method", defaultText(resolve(annotation.method()), method.getName()));
        putIfText(attributes, "tenantId", resolve(annotation.tenantId()));
        putIfText(attributes, "userId", resolve(annotation.userId()));
        putIfText(attributes, "trafficClass", resolve(annotation.trafficClass()));
        putIfText(attributes, "trafficTag", resolve(annotation.trafficTag()));
        if (annotation.cost() <= 0L) {
            throw new IllegalArgumentException("@StellorbitRateLimitResource cost must be positive");
        }
        attributes.put("cost", Long.toString(annotation.cost()));
        return Map.copyOf(attributes);
    }

    private String resourceKey(StellorbitRateLimitResource annotation) {
        String key = defaultText(resolve(annotation.key()), resolve(annotation.value()));
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "@StellorbitRateLimitResource value/key must not be blank");
        }
        return key.trim();
    }

    private String serviceName(StellorbitRateLimitResource annotation) {
        String springApplicationName =
                environment.getProperty("spring.application.name", "application");
        String defaultServiceName = defaultText(properties.getTargetService(), springApplicationName);
        return defaultText(resolve(annotation.serviceName()), defaultServiceName);
    }

    private String resolve(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return environment.resolvePlaceholders(value).trim();
    }

    private String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private void putIfText(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record FallbackMethod(Method method, Object[] arguments, int priority) {}
}
