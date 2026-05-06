# stellflux 客户端发现模型

当前 `stellflux` 的 HTTP / gRPC 客户端统一收敛为一套模型：

- 服务发现模式只围绕 `serviceId`
- 配置文件只保留 `clients[serviceId]`
- 注解显式配置优先级高于配置文件
- 直连模式仍然保留，但它是显式选择

## 配置规则

HTTP 客户端：

- 配置 `baseUrl` 表示直连
- 不配置 `baseUrl`，配置 `serviceId` 表示走 StellMap 服务发现和负载均衡

gRPC 客户端：

- 配置 `host + port` 表示直连
- 不配置 `host + port`，配置 `serviceId` 表示走 StellMap 服务发现和负载均衡

## 最小配置

HTTP：

```yaml
stellflux:
  http:
    client:
      clients:
        order-service:
          namespace: prod
```

gRPC：

```yaml
stellflux:
  grpc:
    client:
      clients:
        payment-service:
          namespace: prod
```

## 最小注解

HTTP：

```java
@OkHttpClient(serviceId = "order-service")
public class OrderHttpClientMarker {
}
```

gRPC：

```java
@RpcClient(serviceId = "payment-service")
public class PaymentRpcClientMarker {
}
```

## 默认行为

- HTTP discovery 固定选择 `http` endpoint
- gRPC discovery 固定选择 `grpc` endpoint
- `endpointName` 不再作为常规客户端配置面暴露
- 默认负载均衡算法来自 `stellflux.stellmap.discovery.load-balancer`

## 图示

- [客户端 Bean 关系图](./stellflux-client-bean-relationship.svg)
- [动态服务发现流程图](./stellflux-service-discovery-flow.svg)
