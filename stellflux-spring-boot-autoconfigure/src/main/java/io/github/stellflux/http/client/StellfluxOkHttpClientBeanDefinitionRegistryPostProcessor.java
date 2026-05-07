package io.github.stellflux.http.client;

import io.github.stellflux.http.client.annotation.OkHttpClient;
import io.github.stellflux.support.AbstractAnnotatedClientBeanDefinitionRegistryPostProcessor;
import java.beans.Introspector;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.util.StringUtils;

/** Register Stellflux HTTP clients declared by annotation. */
public class StellfluxOkHttpClientBeanDefinitionRegistryPostProcessor
        extends AbstractAnnotatedClientBeanDefinitionRegistryPostProcessor<OkHttpClient> {

    @Override
    protected Class<OkHttpClient> getAnnotationType() {
        return OkHttpClient.class;
    }

    @Override
    protected String resolveBeanName(Class<?> annotatedClass, OkHttpClient annotation) {
        if (StringUtils.hasText(annotation.beanName())) {
            return annotation.beanName();
        }
        if (StringUtils.hasText(annotation.value())) {
            return annotation.value();
        }
        return Introspector.decapitalize(annotatedClass.getSimpleName());
    }

    @Override
    protected BeanDefinitionBuilder createBeanDefinition(OkHttpClient annotation) {
        BeanDefinitionBuilder builder =
                BeanDefinitionBuilder.genericBeanDefinition(StellfluxAnnotatedHttpClientFactoryBean.class);
        builder.addPropertyValue("options", toOptions(annotation));
        return builder;
    }

    private StellfluxHttpClientOptions toOptions(OkHttpClient annotation) {
        StellfluxHttpClientOptions options = new StellfluxHttpClientOptions();
        options.setServiceId(annotation.serviceId());
        options.setNamespace(annotation.namespace());
        options.setBaseUrl(annotation.baseUrl());
        options.setConnectTimeoutMillis(annotation.connectTimeoutMillis());
        options.setReadTimeoutMillis(annotation.readTimeoutMillis());
        options.setWriteTimeoutMillis(annotation.writeTimeoutMillis());
        options.setCallTimeoutMillis(annotation.callTimeoutMillis());
        options.setPingIntervalMillis(annotation.pingIntervalMillis());
        options.setRetryOnConnectionFailure(annotation.retryOnConnectionFailure());
        options.setFollowRedirects(annotation.followRedirects());
        options.setFollowSslRedirects(annotation.followSslRedirects());
        options.setLoadBalancer(
                io.github.stellflux.loadbalancer.StellfluxLoadBalancers.of(annotation.loadBalancer()));
        return options;
    }
}
