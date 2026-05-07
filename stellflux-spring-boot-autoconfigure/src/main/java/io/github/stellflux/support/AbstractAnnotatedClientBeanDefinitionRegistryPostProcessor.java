package io.github.stellflux.support;

import java.lang.annotation.Annotation;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * 通用的注解客户端 Bean 注册器基类。
 *
 * @param <A> 客户端注解类型
 */
public abstract class AbstractAnnotatedClientBeanDefinitionRegistryPostProcessor<A extends Annotation>
        implements BeanDefinitionRegistryPostProcessor,
                PriorityOrdered,
                BeanClassLoaderAware,
                EnvironmentAware,
                ResourceLoaderAware {

    private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

    private Environment environment;

    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry)
            throws BeansException {
        if (!(registry instanceof ConfigurableListableBeanFactory beanFactory)) {
            return;
        }
        if (!AutoConfigurationPackages.has(beanFactory)) {
            return;
        }

        List<String> packages = AutoConfigurationPackages.get(beanFactory);
        ClassPathScanningCandidateComponentProvider provider =
                new ClassPathScanningCandidateComponentProvider(false, this.environment) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isIndependent()
                                && (beanDefinition.getMetadata().isConcrete()
                                        || beanDefinition.getMetadata().isInterface());
                    }
                };
        provider.setResourceLoader(this.resourceLoader);
        provider.addIncludeFilter(new AnnotationTypeFilter(getAnnotationType()));

        for (String basePackage : packages) {
            for (BeanDefinition beanDefinition : provider.findCandidateComponents(basePackage)) {
                registerAnnotatedClient(registry, beanDefinition);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // No-op
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    private void registerAnnotatedClient(
            BeanDefinitionRegistry registry, BeanDefinition beanDefinition) {
        String className = beanDefinition.getBeanClassName();
        if (!StringUtils.hasText(className)) {
            return;
        }

        Class<?> annotatedClass = ClassUtils.resolveClassName(className, this.beanClassLoader);
        A annotation = annotatedClass.getAnnotation(getAnnotationType());
        if (annotation == null) {
            return;
        }

        String beanName = resolveBeanName(annotatedClass, annotation);
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }

        registry.registerBeanDefinition(beanName, createBeanDefinition(annotation).getBeanDefinition());
    }

    protected abstract Class<A> getAnnotationType();

    protected abstract String resolveBeanName(Class<?> annotatedClass, A annotation);

    protected abstract BeanDefinitionBuilder createBeanDefinition(A annotation);
}
