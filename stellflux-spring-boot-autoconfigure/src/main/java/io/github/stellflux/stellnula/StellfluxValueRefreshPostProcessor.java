package io.github.stellflux.stellnula;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanExpressionException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

/** 支持 Spring 标准 @Value 运行期刷新。 */
public class StellfluxValueRefreshPostProcessor
        implements DestructionAwareBeanPostProcessor,
                BeanFactoryAware,
                ApplicationListener<StellfluxStellnulaConfigChangeEvent> {

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxValueRefreshPostProcessor.class.getName());

    private final Map<String, List<ValueTarget>> valueTargets = new ConcurrentHashMap<>();
    private ConfigurableBeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory) {
            this.beanFactory = configurableBeanFactory;
        }
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName)
            throws BeansException {
        if (this.beanFactory == null || isInfrastructureBean(bean)) {
            return bean;
        }
        List<ValueTarget> targets = findValueTargets(bean, beanName);
        if (!targets.isEmpty()) {
            this.valueTargets.put(beanName, targets);
        }
        return bean;
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        this.valueTargets.remove(beanName);
    }

    @Override
    public boolean requiresDestruction(Object bean) {
        return true;
    }

    @Override
    public void onApplicationEvent(StellfluxStellnulaConfigChangeEvent event) {
        if (this.beanFactory == null || this.valueTargets.isEmpty()) {
            return;
        }
        this.valueTargets.values().forEach(targets -> targets.forEach(ValueTarget::refresh));
    }

    private List<ValueTarget> findValueTargets(Object bean, String beanName) {
        List<ValueTarget> targets = new ArrayList<>();
        ReflectionUtils.doWithFields(
                bean.getClass(),
                field -> {
                    Value value = AnnotationUtils.findAnnotation(field, Value.class);
                    if (value != null && refreshable(field)) {
                        targets.add(new FieldValueTarget(beanName, bean, field, value.value()));
                    }
                });
        ReflectionUtils.doWithMethods(
                bean.getClass(),
                method -> {
                    Value value = AnnotationUtils.findAnnotation(method, Value.class);
                    if (value != null && refreshable(method)) {
                        targets.add(new MethodValueTarget(beanName, bean, method, value.value()));
                    }
                });
        return targets;
    }

    private boolean refreshable(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers);
    }

    private boolean refreshable(Method method) {
        int modifiers = method.getModifiers();
        return !Modifier.isStatic(modifiers)
                && method.getParameterCount() == 1
                && method.getReturnType() == Void.TYPE;
    }

    private boolean isInfrastructureBean(Object bean) {
        return bean instanceof StellfluxValueRefreshPostProcessor
                || bean instanceof StellfluxStellnulaPropertySourceRefresher;
    }

    private Object resolveValue(String expression) {
        String embedded = this.beanFactory.resolveEmbeddedValue(expression);
        Object value = embedded;
        BeanExpressionResolver resolver = this.beanFactory.getBeanExpressionResolver();
        if (resolver != null) {
            value = resolver.evaluate(embedded, new BeanExpressionContext(this.beanFactory, null));
        }
        return value;
    }

    private abstract class ValueTarget {

        private final String beanName;
        private final String expression;

        private ValueTarget(String beanName, String expression) {
            this.beanName = beanName;
            this.expression = expression;
        }

        private void refresh() {
            try {
                update(resolveValue(this.expression));
            } catch (BeanExpressionException | IllegalArgumentException | IllegalAccessException ex) {
                LOGGER.log(
                        Level.WARNING,
                        ex,
                        () -> "Failed to refresh @Value target for bean " + this.beanName);
            }
        }

        abstract void update(Object value) throws IllegalAccessException;
    }

    private final class FieldValueTarget extends ValueTarget {

        private final Object bean;
        private final Field field;

        private FieldValueTarget(String beanName, Object bean, Field field, String expression) {
            super(beanName, expression);
            this.bean = bean;
            this.field = field;
            ReflectionUtils.makeAccessible(field);
        }

        @Override
        void update(Object value) throws IllegalAccessException {
            TypeConverter typeConverter = beanFactory.getTypeConverter();
            Object converted = typeConverter.convertIfNecessary(value, this.field.getType(), this.field);
            this.field.set(this.bean, converted);
        }
    }

    private final class MethodValueTarget extends ValueTarget {

        private final Object bean;
        private final Method method;
        private final MethodParameter methodParameter;

        private MethodValueTarget(String beanName, Object bean, Method method, String expression) {
            super(beanName, expression);
            this.bean = bean;
            this.method = method;
            this.methodParameter = new MethodParameter(method, 0);
            ReflectionUtils.makeAccessible(method);
        }

        @Override
        void update(Object value) {
            TypeConverter typeConverter = beanFactory.getTypeConverter();
            Object converted =
                    typeConverter.convertIfNecessary(
                            value, this.method.getParameterTypes()[0], this.methodParameter);
            ReflectionUtils.invokeMethod(this.method, this.bean, converted);
        }
    }
}
