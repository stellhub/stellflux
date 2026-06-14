package io.github.stellflux.grpc.server;

import io.github.stellflux.grpc.server.annotation.RpcService;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

/**
 * 扫描并注册 {@link RpcService} Bean。
 *
 * <p>starter 负责在 Spring Boot 自动配置包下发现并注册 {@link RpcService}，从而让 {@code stellflux-grpc-server} 模块保持
 * Spring 无关。
 */
public class StellfluxGrpcRpcServiceBeanRegistrar
        implements ImportBeanDefinitionRegistrar,
                BeanFactoryAware,
                ResourceLoaderAware,
                EnvironmentAware {

    private BeanFactory beanFactory;

    private ResourceLoader resourceLoader;

    private Environment environment;

    /**
     * 注册 {@link RpcService} BeanDefinition。
     *
     * @param importingClassMetadata 导入方元数据
     * @param registry BeanDefinition 注册表
     */
    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(
                        false, this.environment != null ? this.environment : new StandardEnvironment());
        if (this.resourceLoader != null) {
            scanner.setResourceLoader(this.resourceLoader);
        }
        scanner.addIncludeFilter(new AnnotationTypeFilter(RpcService.class));

        Set<String> registeredClassNames = collectRegisteredClassNames(registry);
        for (String basePackage : resolveBasePackages(registry)) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                String beanClassName = candidate.getBeanClassName();
                if (beanClassName == null || registeredClassNames.contains(beanClassName)) {
                    continue;
                }
                String beanName =
                        AnnotationBeanNameGenerator.INSTANCE.generateBeanName(candidate, registry);
                if (registry.containsBeanDefinition(beanName)) {
                    continue;
                }
                registry.registerBeanDefinition(beanName, candidate);
                registeredClassNames.add(beanClassName);
            }
        }
    }

    private Set<String> resolveBasePackages(BeanDefinitionRegistry registry) {
        if (this.beanFactory != null && AutoConfigurationPackages.has(this.beanFactory)) {
            return new LinkedHashSet<>(AutoConfigurationPackages.get(this.beanFactory));
        }
        Set<String> basePackages = new LinkedHashSet<>();
        for (String beanName : registry.getBeanDefinitionNames()) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
            if (!(beanDefinition instanceof AnnotatedBeanDefinition annotatedBeanDefinition)) {
                continue;
            }
            AnnotationMetadata metadata = annotatedBeanDefinition.getMetadata();
            if (!metadata.hasAnnotation(SpringBootConfiguration.class.getName())
                    && !metadata.hasMetaAnnotation(SpringBootConfiguration.class.getName())) {
                continue;
            }
            String beanClassName = beanDefinition.getBeanClassName();
            if (beanClassName != null) {
                basePackages.add(ClassUtils.getPackageName(beanClassName));
            }
        }
        return basePackages;
    }

    private Set<String> collectRegisteredClassNames(BeanDefinitionRegistry registry) {
        Set<String> registeredClassNames = new HashSet<>();
        for (String beanName : registry.getBeanDefinitionNames()) {
            String beanClassName = registry.getBeanDefinition(beanName).getBeanClassName();
            if (beanClassName != null) {
                registeredClassNames.add(beanClassName);
            }
        }
        return registeredClassNames;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
