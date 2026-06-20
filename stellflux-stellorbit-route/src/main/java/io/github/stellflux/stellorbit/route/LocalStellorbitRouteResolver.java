package io.github.stellflux.stellorbit.route;

import io.github.stellflux.loadbalancer.StellfluxLoadBalancer;
import io.github.stellflux.loadbalancer.StellfluxLoadBalancerRequest;
import io.github.stellflux.loadbalancer.StellfluxServiceInstance;
import io.github.stellflux.loadbalancer.StellfluxServiceInstanceSupplier;
import io.github.stellflux.loadbalancer.stellmap.StellMapLoadBalancerAttributes;
import io.github.stellflux.loadbalancer.stellmap.StellMapWatchingServiceInstanceSupplierFactory;
import io.github.stellflux.stellorbit.observability.StellorbitTelemetry;
import io.github.stellorbit.client.model.RouteRuleQuery;
import io.github.stellorbit.client.provider.RouteRuleProvider;
import io.github.stellorbit.client.rule.GovernanceRule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** 基于 StellOrbit 规则和 StellMap 实例目录的本地路由解析器。 */
public class LocalStellorbitRouteResolver implements StellorbitRouteResolver {

    private final RouteRuleProvider ruleProvider;
    private final StellMapWatchingServiceInstanceSupplierFactory supplierFactory;
    private final StellfluxLoadBalancer<StellfluxServiceInstance> loadBalancer;
    private final StellorbitTelemetry telemetry;

    public LocalStellorbitRouteResolver(
            RouteRuleProvider ruleProvider,
            StellMapWatchingServiceInstanceSupplierFactory supplierFactory,
            StellfluxLoadBalancer<StellfluxServiceInstance> loadBalancer,
            StellorbitTelemetry telemetry) {
        this.ruleProvider = Objects.requireNonNull(ruleProvider, "ruleProvider must not be null");
        this.supplierFactory =
                Objects.requireNonNull(supplierFactory, "supplierFactory must not be null");
        this.loadBalancer = Objects.requireNonNull(loadBalancer, "loadBalancer must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
    }

    /** 根据首条匹配路由规则选择目标服务和实例。 */
    @Override
    public RouteDecision route(StellorbitRouteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        StellorbitTelemetry.Observation observation =
                telemetry.start("route", "resolve", request.serviceName());
        GovernanceRule rule = null;
        RouteTarget target = RouteTarget.of(request.serviceName());
        try {
            rule = firstRule(request);
            target = rule == null ? target : chooseTarget(rule, request);
            StellfluxLoadBalancerRequest loadBalancerRequest = toLoadBalancerRequest(request, target);
            StellfluxServiceInstanceSupplier<StellfluxServiceInstance> supplier =
                    supplierFactory.getOrCreate(
                            target.serviceName(),
                            request.namespace(),
                            firstNonBlank(target.protocol(), request.protocol()),
                            firstNonBlank(target.endpointName(), request.endpointName()));
            Optional<StellfluxServiceInstance> instance =
                    loadBalancer.choose(supplier.getInstances(loadBalancerRequest), loadBalancerRequest);
            RouteDecision decision =
                    new RouteDecision(rule == null ? null : rule.ruleId(), target.serviceName(), instance);
            observation.success(
                    outcome(decision), observationAttributes(request, rule, target, decision));
            return decision;
        } catch (RuntimeException ex) {
            observation.error("error", observationAttributes(request, rule, target, null, ex), ex);
            throw ex;
        } finally {
            observation.close();
        }
    }

    private GovernanceRule firstRule(StellorbitRouteRequest request) {
        List<GovernanceRule> rules =
                ruleProvider.find(
                        new RouteRuleQuery(
                                request.serviceName(),
                                request.routeKey(),
                                request.attributes(),
                                request.context()));
        return rules.isEmpty() ? null : rules.getFirst();
    }

    @SuppressWarnings("unchecked")
    private RouteTarget chooseTarget(GovernanceRule rule, StellorbitRouteRequest request) {
        Object routesValue = rule.content().get("routes");
        if (!(routesValue instanceof List<?> routes) || routes.isEmpty()) {
            return RouteTarget.of(rule.targetService());
        }
        int totalWeight = 0;
        for (Object route : routes) {
            if (route instanceof Map<?, ?> routeMap) {
                totalWeight += Math.max(1, intValue((Map<String, Object>) routeMap, "weight", 1));
            }
        }
        int selected = selectWeightCursor(request.routeKey(), totalWeight);
        int cursor = 0;
        for (Object route : routes) {
            if (route instanceof Map<?, ?> routeMap) {
                Map<String, Object> values = (Map<String, Object>) routeMap;
                cursor += Math.max(1, intValue(values, "weight", 1));
                if (selected < cursor) {
                    return new RouteTarget(
                            textValue(values, "target", rule.targetService()),
                            textValue(values, "protocol", null),
                            textValue(values, "endpointName", null));
                }
            }
        }
        return RouteTarget.of(rule.targetService());
    }

    private int selectWeightCursor(String routeKey, int totalWeight) {
        int normalizedTotalWeight = Math.max(1, totalWeight);
        if (routeKey != null && !routeKey.isBlank()) {
            return Math.floorMod(routeKey.hashCode(), normalizedTotalWeight);
        }
        return ThreadLocalRandom.current().nextInt(normalizedTotalWeight);
    }

    private StellfluxLoadBalancerRequest toLoadBalancerRequest(
            StellorbitRouteRequest request, RouteTarget target) {
        Map<String, String> attributes = new java.util.LinkedHashMap<>(request.attributes());
        putIfText(attributes, StellMapLoadBalancerAttributes.NAMESPACE, request.namespace());
        putIfText(
                attributes,
                StellMapLoadBalancerAttributes.PROTOCOL,
                firstNonBlank(target.protocol(), request.protocol()));
        putIfText(
                attributes,
                StellMapLoadBalancerAttributes.ENDPOINT_NAME,
                firstNonBlank(target.endpointName(), request.endpointName()));
        return StellfluxLoadBalancerRequest.builder()
                .serviceId(target.serviceName())
                .hashKey(request.routeKey())
                .attributes(attributes)
                .build();
    }

    private void putIfText(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }

    private int intValue(Map<String, Object> values, String key, int defaultValue) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String textValue(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return defaultValue;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private String outcome(RouteDecision decision) {
        return decision.instance().isPresent() ? "routed" : "no_instance";
    }

    private Map<String, String> observationAttributes(
            StellorbitRouteRequest request,
            GovernanceRule rule,
            RouteTarget target,
            RouteDecision decision) {
        return observationAttributes(request, rule, target, decision, null);
    }

    private Map<String, String> observationAttributes(
            StellorbitRouteRequest request,
            GovernanceRule rule,
            RouteTarget target,
            RouteDecision decision,
            RuntimeException exception) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("stellorbit.route.source_service", request.serviceName());
        attributes.put("stellorbit.route.target_service", target.serviceName());
        putIfText(attributes, "stellorbit.route.key", request.routeKey());
        putIfText(attributes, "stellorbit.route.namespace", request.namespace());
        putIfText(
                attributes,
                "stellorbit.route.protocol",
                firstNonBlank(target.protocol(), request.protocol()));
        putIfText(
                attributes,
                "stellorbit.route.endpoint_name",
                firstNonBlank(target.endpointName(), request.endpointName()));
        if (rule != null) {
            attributes.put("stellorbit.rule_id", rule.ruleId());
            attributes.put("stellorbit.rule_revision", Long.toString(rule.revision()));
            putIfText(attributes, "stellorbit.rule_checksum", rule.checksum());
        }
        if (decision != null && decision.instance().isPresent()) {
            StellfluxServiceInstance instance = decision.instance().get();
            attributes.put("stellorbit.route.instance_service_id", instance.getServiceId());
            attributes.put("stellorbit.route.instance_host", instance.getHost());
            attributes.put("stellorbit.route.instance_port", Integer.toString(instance.getPort()));
        }
        if (exception != null) {
            attributes.put("error.type", exception.getClass().getName());
        }
        return attributes;
    }

    private record RouteTarget(String serviceName, String protocol, String endpointName) {

        private RouteTarget {
            if (serviceName == null || serviceName.isBlank()) {
                throw new IllegalArgumentException("serviceName must not be blank");
            }
            serviceName = serviceName.trim();
        }

        private static RouteTarget of(String serviceName) {
            return new RouteTarget(serviceName, null, null);
        }
    }
}
