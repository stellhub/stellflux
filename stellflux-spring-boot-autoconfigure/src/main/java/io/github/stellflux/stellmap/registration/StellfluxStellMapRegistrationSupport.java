package io.github.stellflux.stellmap.registration;

import io.github.stellflux.grpc.server.StellfluxGrpcServiceRegistration;
import io.github.stellmap.model.DeregisterRequest;
import io.github.stellmap.model.Endpoint;
import io.github.stellmap.model.RegisterRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/** StellMap 注册请求构造支持类。 */
public final class StellfluxStellMapRegistrationSupport {

    private StellfluxStellMapRegistrationSupport() {}

    /**
     * 构建 HTTP 服务注册请求。
     *
     * @param serviceId 服务标识
     * @param port 监听端口
     * @param contextPath 上下文路径
     * @param registration 注册配置
     * @param defaultNamespace 默认命名空间
     * @param environment Spring 环境
     * @return 注册请求
     */
    public static RegisterRequest buildHttpRegisterRequest(
            String serviceId,
            int port,
            String contextPath,
            StellfluxRegistrationProperties registration,
            String defaultNamespace,
            Environment environment) {
        String namespace = resolveNamespace(registration, defaultNamespace);
        String host = resolveHost(registration, environment);
        String application = resolveApplication(serviceId, registration, environment);
        String instanceId = resolveInstanceId(serviceId, host, port, registration);
        Map<String, String> labels = new LinkedHashMap<>(registration.getLabels());
        labels.putIfAbsent("transport", "http");
        Map<String, String> metadata = new LinkedHashMap<>(registration.getMetadata());
        metadata.putIfAbsent("framework", "stellflux");
        Endpoint endpoint =
                Endpoint.builder()
                        .name("http")
                        .protocol("http")
                        .host(host)
                        .port(port)
                        .path(normalizeHttpPath(contextPath))
                        .weight(registration.getWeight())
                        .build();
        return RegisterRequest.builder()
                .namespace(namespace)
                .service(serviceId)
                .application(application)
                .role(resolveRole(registration))
                .instanceId(instanceId)
                .organization(trimToNull(registration.getOrganization()))
                .businessDomain(trimToNull(registration.getBusinessDomain()))
                .capabilityDomain(trimToNull(registration.getCapabilityDomain()))
                .zone(trimToNull(registration.getZone()))
                .labels(labels)
                .metadata(metadata)
                .endpoints(List.of(endpoint))
                .leaseTtlSeconds(registration.getLeaseTtlSeconds())
                .build();
    }

    /**
     * 构建 gRPC 服务注册请求。
     *
     * @param serviceId 服务标识
     * @param registrations 已暴露的 gRPC 服务
     * @param port 监听端口
     * @param registration 注册配置
     * @param defaultNamespace 默认命名空间
     * @param environment Spring 环境
     * @return 注册请求
     */
    public static RegisterRequest buildGrpcRegisterRequest(
            String serviceId,
            List<StellfluxGrpcServiceRegistration> registrations,
            int port,
            StellfluxRegistrationProperties registration,
            String defaultNamespace,
            Environment environment) {
        String namespace = resolveNamespace(registration, defaultNamespace);
        String host = resolveHost(registration, environment);
        String application = resolveApplication(serviceId, registration, environment);
        String instanceId = resolveInstanceId(serviceId, host, port, registration);
        Map<String, String> labels = new LinkedHashMap<>(registration.getLabels());
        labels.putIfAbsent("transport", "grpc");
        Map<String, String> metadata = new LinkedHashMap<>(registration.getMetadata());
        metadata.putIfAbsent("framework", "stellflux");
        metadata.putIfAbsent(
                "grpcServices",
                registrations.stream()
                        .map(StellfluxGrpcServiceRegistration::grpcServiceName)
                        .distinct()
                        .sorted()
                        .reduce((left, right) -> left + "," + right)
                        .orElse(""));
        Endpoint endpoint =
                Endpoint.builder()
                        .name("grpc")
                        .protocol("grpc")
                        .host(host)
                        .port(port)
                        .path("")
                        .weight(registration.getWeight())
                        .build();
        return RegisterRequest.builder()
                .namespace(namespace)
                .service(serviceId)
                .application(application)
                .role(resolveRole(registration))
                .instanceId(instanceId)
                .organization(trimToNull(registration.getOrganization()))
                .businessDomain(trimToNull(registration.getBusinessDomain()))
                .capabilityDomain(trimToNull(registration.getCapabilityDomain()))
                .zone(trimToNull(registration.getZone()))
                .labels(labels)
                .metadata(metadata)
                .endpoints(List.of(endpoint))
                .leaseTtlSeconds(registration.getLeaseTtlSeconds())
                .build();
    }

    /**
     * 根据注册请求生成反注册请求。
     *
     * @param registerRequest 注册请求
     * @return 反注册请求
     */
    public static DeregisterRequest toDeregisterRequest(RegisterRequest registerRequest) {
        return DeregisterRequest.builder()
                .namespace(registerRequest.getNamespace())
                .service(registerRequest.getService())
                .organization(registerRequest.getOrganization())
                .businessDomain(registerRequest.getBusinessDomain())
                .capabilityDomain(registerRequest.getCapabilityDomain())
                .application(registerRequest.getApplication())
                .role(registerRequest.getRole())
                .instanceId(registerRequest.getInstanceId())
                .build();
    }

    /**
     * 汇总 gRPC 服务名称。
     *
     * @param registrations 服务注册
     * @return 汇总文本
     */
    public static String summarizeGrpcServices(List<StellfluxGrpcServiceRegistration> registrations) {
        List<String> names = new ArrayList<>();
        registrations.stream()
                .map(StellfluxGrpcServiceRegistration::grpcServiceName)
                .distinct()
                .sorted()
                .forEach(names::add);
        return names.toString();
    }

    private static String resolveNamespace(
            StellfluxRegistrationProperties registration, String defaultNamespace) {
        return StringUtils.hasText(registration.getNamespace())
                ? registration.getNamespace().trim()
                : defaultNamespace;
    }

    private static String resolveHost(
            StellfluxRegistrationProperties registration, Environment environment) {
        if (StringUtils.hasText(registration.getHost())) {
            return registration.getHost().trim();
        }
        String configuredAddress = environment.getProperty("server.address");
        if (StringUtils.hasText(configuredAddress)
                && !"0.0.0.0".equals(configuredAddress)
                && !"::".equals(configuredAddress)
                && !"[::]".equals(configuredAddress)) {
            return configuredAddress.trim();
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException exception) {
            return "127.0.0.1";
        }
    }

    private static String resolveApplication(
            String serviceId, StellfluxRegistrationProperties registration, Environment environment) {
        if (StringUtils.hasText(registration.getApplication())) {
            return registration.getApplication().trim();
        }
        String applicationName = environment.getProperty("spring.application.name");
        return StringUtils.hasText(applicationName) ? applicationName.trim() : serviceId;
    }

    private static String resolveInstanceId(
            String serviceId, String host, int port, StellfluxRegistrationProperties registration) {
        if (StringUtils.hasText(registration.getInstanceId())) {
            return registration.getInstanceId().trim();
        }
        return serviceId + "@" + host + ":" + port;
    }

    private static String resolveRole(StellfluxRegistrationProperties registration) {
        return StringUtils.hasText(registration.getRole()) ? registration.getRole().trim() : "provider";
    }

    private static String normalizeHttpPath(String contextPath) {
        if (!StringUtils.hasText(contextPath) || "/".equals(contextPath.trim())) {
            return "/";
        }
        String path = contextPath.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
