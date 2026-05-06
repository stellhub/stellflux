package io.github.stellflux.http.client.config;

import io.github.stellflux.http.client.StellfluxHttpClientOptions;
import io.github.stellflux.http.client.annotation.OkHttpClient;
import java.beans.Introspector;
import java.util.List;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
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

/** Register Stellflux HTTP clients declared by annotation. */
public class StellfluxOkHttpClientBeanDefinitionRegistryPostProcessor
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
                new ClassPathScanningCandidateComponentProvider(false, this.environment);
        provider.setResourceLoader(this.resourceLoader);
        provider.addIncludeFilter(new AnnotationTypeFilter(OkHttpClient.class));

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
        OkHttpClient annotation = annotatedClass.getAnnotation(OkHttpClient.class);
        if (annotation == null) {
            return;
        }

        String beanName = resolveBeanName(annotatedClass, annotation);
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }

        BeanDefinitionBuilder builder =
                BeanDefinitionBuilder.genericBeanDefinition(StellfluxAnnotatedHttpClientFactoryBean.class);
        builder.addPropertyValue("options", toOptions(annotation));
        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
    }

    private String resolveBeanName(Class<?> annotatedClass, OkHttpClient annotation) {
        if (StringUtils.hasText(annotation.beanName())) {
            return annotation.beanName();
        }
        if (StringUtils.hasText(annotation.value())) {
            return annotation.value();
        }
        return Introspector.decapitalize(annotatedClass.getSimpleName());
    }

    private StellfluxHttpClientOptions toOptions(OkHttpClient annotation) {
        StellfluxHttpClientOptions options = new StellfluxHttpClientOptions();
        options.setBaseUrl(annotation.baseUrl());
        options.setConnectTimeoutMillis(annotation.connectTimeoutMillis());
        options.setReadTimeoutMillis(annotation.readTimeoutMillis());
        options.setWriteTimeoutMillis(annotation.writeTimeoutMillis());
        options.setCallTimeoutMillis(annotation.callTimeoutMillis());
        options.setPingIntervalMillis(annotation.pingIntervalMillis());
        options.setRetryOnConnectionFailure(annotation.retryOnConnectionFailure());
        options.setFollowRedirects(annotation.followRedirects());
        options.setFollowSslRedirects(annotation.followSslRedirects());
        return options;
    }
}
