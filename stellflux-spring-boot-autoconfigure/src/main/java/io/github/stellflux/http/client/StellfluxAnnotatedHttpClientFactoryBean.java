package io.github.stellflux.http.client;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.stellmap.StellMapWatchingServiceInstanceSupplierFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.StringUtils;

/** Factory bean for annotated Stellflux HTTP clients. */
public class StellfluxAnnotatedHttpClientFactoryBean
        implements FactoryBean<StellfluxHttpClient>, ApplicationContextAware {

    private StellfluxHttpClientOptions options;
    private ApplicationContext applicationContext;

    public void setOptions(StellfluxHttpClientOptions options) {
        this.options = options;
    }

    @Override
    public StellfluxHttpClient getObject() {
        StellfluxHttpClientFactory factory =
                this.applicationContext.getBean(StellfluxHttpClientFactory.class);
        return factory.create(resolveOptions());
    }

    @Override
    public Class<?> getObjectType() {
        return StellfluxHttpClient.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private StellfluxHttpClientOptions resolveOptions() {
        StellfluxHttpClientProperties properties =
                this.applicationContext.getBeanProvider(StellfluxHttpClientProperties.class).getIfAvailable();
        StellfluxHttpClientOptions resolved =
                properties == null
                        ? copyOptions(this.options)
                        : properties.mergeAnnotatedOptions(copyOptions(this.options));
        if (StringUtils.hasText(resolved.getBaseUrl())) {
            return resolved;
        }
        if (StringUtils.hasText(resolved.getServiceId())) {
            StellMapWatchingServiceInstanceSupplierFactory supplierFactory =
                    this.applicationContext.getBean(StellMapWatchingServiceInstanceSupplierFactory.class);
            @SuppressWarnings("unchecked")
            StellfluxLoadBalancer<StellfluxServiceInstance> loadBalancer = resolved.getLoadBalancer();
            if (loadBalancer == null) {
                loadBalancer =
                        (StellfluxLoadBalancer<StellfluxServiceInstance>)
                                this.applicationContext
                                        .getBeanProvider(StellfluxLoadBalancer.class)
                                        .getIfAvailable();
            }
            if (loadBalancer == null) {
                throw new IllegalStateException("No StellfluxLoadBalancer bean available for OkHttpClient");
            }
            resolved.setServiceInstanceSupplier(
                    supplierFactory.httpSupplier(resolved.getServiceId(), resolved.getNamespace()));
            resolved.setLoadBalancer(loadBalancer);
        }
        return resolved;
    }

    private StellfluxHttpClientOptions copyOptions(StellfluxHttpClientOptions source) {
        StellfluxHttpClientOptions target = new StellfluxHttpClientOptions();
        target.setBaseUrl(source.getBaseUrl());
        target.setServiceId(source.getServiceId());
        target.setNamespace(source.getNamespace());
        target.setProtocol(source.getProtocol());
        target.setEndpointName(source.getEndpointName());
        target.setLoadBalancerRequest(source.getLoadBalancerRequest());
        target.setServiceInstanceSupplier(source.getServiceInstanceSupplier());
        target.setLoadBalancer(source.getLoadBalancer());
        target.setConnectTimeoutMillis(source.getConnectTimeoutMillis());
        target.setReadTimeoutMillis(source.getReadTimeoutMillis());
        target.setWriteTimeoutMillis(source.getWriteTimeoutMillis());
        target.setCallTimeoutMillis(source.getCallTimeoutMillis());
        target.setPingIntervalMillis(source.getPingIntervalMillis());
        target.setRetryOnConnectionFailure(source.isRetryOnConnectionFailure());
        target.setFollowRedirects(source.isFollowRedirects());
        target.setFollowSslRedirects(source.isFollowSslRedirects());
        return target;
    }
}
