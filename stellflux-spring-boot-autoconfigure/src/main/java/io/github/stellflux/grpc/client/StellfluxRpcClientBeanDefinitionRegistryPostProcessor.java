package io.github.stellflux.grpc.client;

import io.github.stellflux.grpc.client.annotation.RpcClient;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancers;
import io.github.stellflux.support.AbstractAnnotatedClientBeanDefinitionRegistryPostProcessor;
import java.beans.Introspector;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.util.StringUtils;

/** 注册使用 RpcClient 注解声明的 gRPC 客户端。 */
public class StellfluxRpcClientBeanDefinitionRegistryPostProcessor
        extends AbstractAnnotatedClientBeanDefinitionRegistryPostProcessor<RpcClient> {

    @Override
    protected Class<RpcClient> getAnnotationType() {
        return RpcClient.class;
    }

    @Override
    protected String resolveBeanName(Class<?> annotatedClass, RpcClient annotation) {
        if (StringUtils.hasText(annotation.beanName())) {
            return annotation.beanName();
        }
        if (StringUtils.hasText(annotation.value())) {
            return annotation.value();
        }
        return Introspector.decapitalize(annotatedClass.getSimpleName());
    }

    @Override
    protected BeanDefinitionBuilder createBeanDefinition(RpcClient annotation) {
        BeanDefinitionBuilder builder =
                BeanDefinitionBuilder.genericBeanDefinition(StellfluxAnnotatedRpcClientFactoryBean.class);
        builder.addPropertyValue("options", toOptions(annotation));
        return builder;
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
