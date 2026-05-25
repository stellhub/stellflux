# stellflux

基于 Spring Boot 3 的自研框架骨架工程，当前已提供以下基础模块：

- `stellflux-bom`
- `stellflux-http-client`
- `stellflux-loadbalancer`
- `stellflux-loadbalancer-stellmap`
- `stellflux-stellmap`
- `stellflux-scheduler-stellmap`
- `stellflux-stellflow`
- `stellflux-caffeine`
- `stellflux-datasource`
- `stellflux-elaticsearch`
- `stellflux-lock-jedis`
- `stellflux-grpc-server`
- `stellflux-grpc-client`
- `stellflux-spring-boot-autoconfigure`
- `stellhub-spring-boot-starter-parent`
- `stellflux-spring-boot-starter-http-server`
- `stellflux-spring-boot-starter-http-client`
- `stellflux-spring-boot-starter-http`
- `stellflux-spring-boot-starter-grpc-client`
- `stellflux-spring-boot-starter-grpc-server`
- `stellflux-spring-boot-starter-grpc`
- `stellflux-spring-boot-starter-stellmap`
- `stellflux-spring-boot-starter-scheduler-stellmap`
- `stellflux-spring-boot-starter-caffeine`
- `stellflux-spring-boot-starter-datasource`
- `stellflux-spring-boot-starter-elaticsearch`
- `stellflux-spring-boot-starter-lock-jedis`
- `stellflux-spring-boot-starter-stellflow`

## 示例

示例应用统一放在 `stellflux-examples` 聚合模块下。DataSource 示例位于 `stellflux-examples/stellflux-datasource-example`，默认只创建带 OpenTelemetry 的 MySQL `DataSource` 状态页，不主动连接 MySQL；Elaticsearch 示例位于 `stellflux-examples/stellflux-elaticsearch-examples`，默认只初始化客户端并提供 HTTP CRUD 触发入口。

```bash
mvn -pl stellflux-examples/stellflux-datasource-example -am install -DskipTests
mvn -f stellflux-examples/stellflux-datasource-example/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run
```

如需启动时执行一次 SQL：

```bash
mvn -f stellflux-examples/stellflux-datasource-example/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run -Dspring-boot.run.arguments=--example.datasource.invoke-on-startup=true
```

## 构建

```bash
mvn clean install
```

## 文档

服务端模块说明：

- `stellflux-spring-boot-starter-http-server` 和 `stellflux-spring-boot-starter-grpc-server` 不再提供 `enabled` 配置开关
- 是否启用服务端自动装配，由是否引入对应 starter 决定

- [客户端发现模型](./docs/stellflux-client-discovery-model.md)
- [Starter 模块说明](./docs/starter-modules.md)
- [gRPC RpcService 说明](./docs/rpc-service.md)
- [客户端 Bean 关系图](./docs/stellflux-client-bean-relationship.svg)
- [动态服务发现流程图](./docs/stellflux-service-discovery-flow.svg)
