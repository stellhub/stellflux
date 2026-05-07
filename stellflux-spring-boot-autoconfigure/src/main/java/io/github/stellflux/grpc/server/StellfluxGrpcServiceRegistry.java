package io.github.stellflux.grpc.server;

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.github.stellflux.grpc.server.annotation.RpcService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import lombok.Getter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/** gRPC 服务注册表。 */
@Getter
public class StellfluxGrpcServiceRegistry {

    private final List<StellfluxGrpcServiceRegistration> registrations;

    private final List<String> skippedBeanNames;

    private StellfluxGrpcServiceRegistry(
            List<StellfluxGrpcServiceRegistration> registrations, List<String> skippedBeanNames) {
        this.registrations = List.copyOf(registrations);
        this.skippedBeanNames = List.copyOf(skippedBeanNames);
    }

    /**
     * 从 Spring Bean 映射中构建服务注册表。
     *
     * @param bindableServices BindableService Bean 映射
     * @return 服务注册表
     */
    static StellfluxGrpcServiceRegistry from(Map<String, BindableService> bindableServices) {
        List<StellfluxGrpcServiceRegistration> registrations = new ArrayList<>();
        List<String> skippedBeanNames = new ArrayList<>();
        bindableServices.forEach(
                (beanName, bindableService) -> {
                    Class<?> beanClass = ClassUtils.getUserClass(bindableService);
                    RpcService annotation =
                            AnnotatedElementUtils.findMergedAnnotation(beanClass, RpcService.class);
                    if (annotation != null && !annotation.enabled()) {
                        skippedBeanNames.add(beanName);
                        return;
                    }
                    ServerServiceDefinition serviceDefinition = bindableService.bindService();
                    String grpcServiceName = serviceDefinition.getServiceDescriptor().getName();
                    String serviceId =
                            annotation != null && StringUtils.hasText(annotation.serviceId())
                                    ? annotation.serviceId().trim()
                                    : grpcServiceName;
                    int order = annotation != null ? annotation.order() : Integer.MAX_VALUE;
                    registrations.add(
                            new StellfluxGrpcServiceRegistration(
                                    beanName,
                                    grpcServiceName,
                                    serviceId,
                                    order,
                                    bindableService,
                                    serviceDefinition));
                });
        registrations.sort(
                Comparator.comparingInt(StellfluxGrpcServiceRegistration::order)
                        .thenComparing(StellfluxGrpcServiceRegistration::grpcServiceName)
                        .thenComparing(StellfluxGrpcServiceRegistration::beanName));
        return new StellfluxGrpcServiceRegistry(registrations, skippedBeanNames);
    }

    /**
     * 是否存在可暴露的 gRPC 服务。
     *
     * @return 是否存在可暴露服务
     */
    boolean hasRegistrations() {
        return !this.registrations.isEmpty();
    }

    /**
     * 汇总已暴露服务。
     *
     * @return 汇总字符串
     */
    String summarizeRegistrations() {
        if (this.registrations.isEmpty()) {
            return "[]";
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        this.registrations.forEach(
                registration ->
                        joiner.add(
                                "{beanName=" + registration.beanName()
                                        + ", grpcService=" + registration.grpcServiceName()
                                        + ", registrationServiceId=" + registration.serviceId()
                                        + "}"));
        return joiner.toString();
    }

    /**
     * 汇总跳过的服务 Bean。
     *
     * @return 汇总字符串
     */
    String summarizeSkippedBeans() {
        if (this.skippedBeanNames.isEmpty()) {
            return "[]";
        }
        return this.skippedBeanNames.toString();
    }
}
