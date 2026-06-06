package io.github.stellflux.stellnula;

import io.github.stellnula.client.StellnulaClient;
import java.io.IOException;
import java.util.logging.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.util.StringUtils;

/** Stellnula 配置中心启动期接入。 */
public class StellfluxStellnulaBootstrapPostProcessor implements BeanFactoryPostProcessor, Ordered {

    public static final String CLIENT_BEAN_NAME = "stellnulaClient";
    public static final String CLIENT_OPTIONS_BEAN_NAME = "stellfluxStellnulaClientOptions";
    public static final String CLIENT_FACTORY_BEAN_NAME = "stellfluxStellnulaClientFactory";
    public static final String PROPERTY_SOURCE_BEAN_NAME = "stellfluxStellnulaPropertySource";

    private static final Logger LOGGER =
            Logger.getLogger(StellfluxStellnulaBootstrapPostProcessor.class.getName());

    private final Environment environment;

    public StellfluxStellnulaBootstrapPostProcessor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        StellfluxStellnulaProperties properties =
                Binder.get(this.environment)
                        .bind("stellflux.stellnula", Bindable.of(StellfluxStellnulaProperties.class))
                        .orElseGet(StellfluxStellnulaProperties::new);
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getEndpoint())) {
            return;
        }
        if (!(this.environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            throw new BeanCreationException(
                    CLIENT_BEAN_NAME, "Stellnula requires a ConfigurableEnvironment");
        }
        if (beanFactory.containsBean(CLIENT_BEAN_NAME)) {
            return;
        }
        StellfluxStellnulaClientOptions options = properties.toOptions(this.environment);
        StellfluxStellnulaClientFactory factory = new StellfluxStellnulaClientFactory();
        StellnulaClient client = factory.create(options);
        startClient(client);
        StellfluxStellnulaPropertySource propertySource =
                new StellfluxStellnulaPropertySource(properties.getPropertySourceName(), client.asMap());
        addPropertySource(configurableEnvironment.getPropertySources(), propertySource);
        registerSingleton(beanFactory, CLIENT_FACTORY_BEAN_NAME, factory);
        registerSingleton(beanFactory, CLIENT_OPTIONS_BEAN_NAME, options);
        registerSingleton(beanFactory, CLIENT_BEAN_NAME, client);
        registerSingleton(beanFactory, PROPERTY_SOURCE_BEAN_NAME, propertySource);
        if (beanFactory instanceof DefaultSingletonBeanRegistry registry) {
            registry.registerDisposableBean(CLIENT_BEAN_NAME, client::close);
        }
    }

    private void startClient(StellnulaClient client) {
        try {
            client.start();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BeanCreationException(CLIENT_BEAN_NAME, "Stellnula bootstrap was interrupted", ex);
        } catch (IOException | RuntimeException ex) {
            throw new BeanCreationException(CLIENT_BEAN_NAME, "Failed to bootstrap Stellnula", ex);
        }
    }

    private void addPropertySource(
            MutablePropertySources propertySources, StellfluxStellnulaPropertySource propertySource) {
        if (propertySources.contains(propertySource.getName())) {
            propertySources.replace(propertySource.getName(), propertySource);
            return;
        }
        propertySources.addFirst(propertySource);
    }

    private void registerSingleton(
            ConfigurableListableBeanFactory beanFactory, String beanName, Object singleton) {
        if (beanFactory.containsBean(beanName)) {
            LOGGER.fine(() -> "Skip registering existing bean: " + beanName);
            return;
        }
        beanFactory.registerSingleton(beanName, singleton);
    }
}
