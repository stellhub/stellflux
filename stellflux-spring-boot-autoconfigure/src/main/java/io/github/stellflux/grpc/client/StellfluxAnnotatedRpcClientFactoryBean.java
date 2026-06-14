package io.github.stellflux.grpc.client;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.stellmap.StellMapWatchingServiceInstanceSupplierFactory;
import io.grpc.ManagedChannel;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.StringUtils;

/** Factory bean for annotated Stellflux gRPC clients. */
public class StellfluxAnnotatedRpcClientFactoryBean
        implements FactoryBean<ManagedChannel>, ApplicationContextAware, DisposableBean {

    private StellfluxGrpcClientOptions options;
    private ApplicationContext applicationContext;
    private ManagedChannel managedChannel;

    public void setOptions(StellfluxGrpcClientOptions options) {
        this.options = options;
    }

    @Override
    public ManagedChannel getObject() {
        if (this.managedChannel == null) {
            StellfluxGrpcChannelFactory factory =
                    this.applicationContext.getBean(StellfluxGrpcChannelFactory.class);
            this.managedChannel = factory.create(resolveOptions());
        }
        return this.managedChannel;
    }

    @Override
    public Class<?> getObjectType() {
        return ManagedChannel.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void destroy() {
        if (this.managedChannel != null) {
            this.managedChannel.shutdownNow();
        }
    }

    private StellfluxGrpcClientOptions resolveOptions() {
        StellfluxGrpcClientProperties properties =
                this.applicationContext
                        .getBeanProvider(StellfluxGrpcClientProperties.class)
                        .getIfAvailable();
        StellfluxGrpcClientOptions resolved =
                properties == null
                        ? copyOptions(this.options)
                        : properties.mergeAnnotatedOptions(copyOptions(this.options));
        if (isDirectMode(resolved)) {
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
                throw new IllegalStateException("No StellfluxLoadBalancer bean available for RpcClient");
            }
            resolved.setServiceInstanceSupplier(
                    supplierFactory.grpcSupplier(resolved.getServiceId(), resolved.getNamespace()));
            resolved.setLoadBalancer(loadBalancer);
        }
        return resolved;
    }

    private boolean isDirectMode(StellfluxGrpcClientOptions options) {
        return StringUtils.hasText(options.getHost()) && options.getPort() > 0;
    }

    private StellfluxGrpcClientOptions copyOptions(StellfluxGrpcClientOptions source) {
        StellfluxGrpcClientOptions target = new StellfluxGrpcClientOptions();
        target.setHost(source.getHost());
        target.setPort(source.getPort());
        target.setServiceId(source.getServiceId());
        target.setNamespace(source.getNamespace());
        target.setProtocol(source.getProtocol());
        target.setEndpointName(source.getEndpointName());
        target.setLoadBalancerRequest(source.getLoadBalancerRequest());
        target.setServiceInstanceSupplier(source.getServiceInstanceSupplier());
        target.setLoadBalancer(source.getLoadBalancer());
        target.setPlaintext(source.isPlaintext());
        return target;
    }
}
