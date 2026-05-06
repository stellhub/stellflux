package io.github.stellflux.grpc.client;

import io.github.stellflux.grpc.client.annotation.RpcClient;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancers;
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

/** 注册使用 RpcClient 注解声明的 gRPC 客户端。 */
public class StellfluxRpcClientBeanDefinitionRegistryPostProcessor
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
        provider.addIncludeFilter(new AnnotationTypeFilter(RpcClient.class));

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
        RpcClient annotation = annotatedClass.getAnnotation(RpcClient.class);
        if (annotation == null) {
            return;
        }

        String beanName = resolveBeanName(annotatedClass, annotation);
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }

        BeanDefinitionBuilder builder =
                BeanDefinitionBuilder.genericBeanDefinition(StellfluxAnnotatedRpcClientFactoryBean.class);
        builder.addPropertyValue("options", toOptions(annotation));
        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
    }

    private String resolveBeanName(Class<?> annotatedClass, RpcClient annotation) {
        if (StringUtils.hasText(annotation.beanName())) {
            return annotation.beanName();
        }
        if (StringUtils.hasText(annotation.value())) {
            return annotation.value();
        }
        return Introspector.decapitalize(annotatedClass.getSimpleName());
    }

    private StellfluxGrpcClientOptions toOptions(RpcClient annotation) {
        StellfluxGrpcClientOptions options = new StellfluxGrpcClientOptions();
        options.setServiceId(annotation.serviceId());
        options.setNamespace(annotation.namespace());
        options.setHost(annotation.host());
        if (annotation.port() > 0) {
            options.setPort(annotation.port());
        }
        options.setPlaintext(annotation.plaintext());
        options.setLoadBalancer(StellfluxLoadBalancers.of(annotation.loadBalancer()));
        return options;
    }
}
