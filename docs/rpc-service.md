# stellflux `@RpcService` 说明

`@RpcService` 用于标记一个需要对外暴露的 gRPC 服务实现类。

它具备两层协作语义：

- `stellflux-grpc-server` 模块中的纯 gRPC 服务暴露元数据
- `stellflux-spring-boot-starter-grpc-server` 提供的 Spring Boot 自动扫描注册能力

这意味着业务实现类通常仍然只需要一个注解即可完成 Bean 注册与服务暴露，但 Spring 依赖只存在于 starter / autoconfigure 模块中。

## 最小用法

```java
@RpcService(serviceId = "trade.order.rpc")
public class OrderRpcService extends OrderServiceGrpc.OrderServiceImplBase {
}
```

推荐业务实现类继承 protobuf 生成的 `XxxServiceGrpc.XxxServiceImplBase`，而不是直接手写实现 `io.grpc.BindableService`。

## 配置示例

gRPC 服务注册到 StellMap 的最小配置：

```yaml
stellflux:
  stellmap:
    base-url: http://127.0.0.1:8080
  grpc:
    server:
      port: 9090
```

这里不需要也不应该再写 `stellflux.grpc.server.enabled=true`。

HTTP 服务注册到 StellMap 的最小配置：

```yaml
stellflux:
  stellmap:
    base-url: http://127.0.0.1:8080
  opentelemetry:
    resource:
      service-name: edge.gateway.http
```

这里不需要也不应该再写 `stellflux.http.server.enabled=true`。

## 默认行为

- 在 Spring Boot 自动配置包下自动扫描 `@RpcService`
- 自动发现 Spring 容器中的 `BindableService` Bean
- 自动收集 `@RpcService` 元数据
- 自动 `addService(...)`
- 自动创建并启动 `io.grpc.Server`
- 自动优雅停机
- 自动挂载框架级 interceptor
- 启动时打印监听端口、暴露服务列表和 `serviceId`
- 若启用了 StellMap，则应用启动后会自动把该 gRPC 服务注册到 StellMap
- gRPC / HTTP 服务端是否启用，取决于是否引入对应 starter，而不是 `enabled` 配置

## 注解参数

- `serviceId`
  - 服务注册标识
  - 未配置时默认回退到 gRPC service descriptor 名称
- `enabled`
  - 是否启用当前服务暴露
- `order`
  - 多个服务加入 Server 的顺序

## 当前日志

Server 启动时会输出类似日志：

```text
Started StellfluxGrpcServer listeningPort=8574, configuredPort=0, exposedServices=1,
services=[{beanName=orderRpcService, grpcService=demo.OrderService, registrationServiceId=trade.order.rpc}],
skippedServices=[]
```

这条日志可以直接帮助排查：

- 服务是否真正暴露
- 实际监听端口是什么
- 当前服务使用的 `serviceId` 是什么
