# stellflux Starter 模块说明

当前 `stellflux` 的 Starter 设计分成两类：

- 细粒度 starter：只负责某一个能力方向
- 聚合 starter：组合多个细粒度 starter，方便快速接入

## 统一约定

- HTTP / gRPC 服务端 starter 不再提供 `enabled` 配置开关
- 是否启用服务端自动装配，由是否引入对应 starter 决定
- 因此不要再配置 `stellflux.http.server.enabled` 或 `stellflux.grpc.server.enabled`

## HTTP

- `stellflux-spring-boot-starter-http-client`
  - HTTP 客户端能力
  - 包含 `stellflux-http-client` 与对应自动装配
- `stellflux-spring-boot-starter-http-server`
  - HTTP 服务端能力
  - 基于 Spring MVC / Servlet 自动装配
  - 不提供 `stellflux.http.server.enabled`
  - HTTP telemetry 配置指南见 [HTTP Server Telemetry 配置指南](./http-server-telemetry-guide.md)
- `stellflux-spring-boot-starter-http`
  - HTTP 聚合 starter
  - 同时引入 client 和 server

## gRPC

- `stellflux-spring-boot-starter-grpc-client`
  - gRPC 客户端能力
  - 包含 `stellflux-grpc-client` 与对应自动装配
- `stellflux-spring-boot-starter-grpc-server`
  - gRPC 服务端能力
  - 包含 `stellflux-grpc-server` 与对应自动装配
  - 不提供 `stellflux.grpc.server.enabled`
- `stellflux-spring-boot-starter-grpc`
  - gRPC 聚合 starter
  - 同时引入 client 和 server

## 其它 starter

- `stellflux-spring-boot-starter-opentelemetry`
- `stellflux-spring-boot-starter-metrics`
- `stellflux-spring-boot-starter-traces`
- `stellflux-spring-boot-starter-log`
- `stellflux-spring-boot-starter-stellmap`
- `stellflux-spring-boot-starter-caffeine`
  - Caffeine 本地缓存能力
  - 包含 `stellflux-caffeine` 与统一自动装配
  - 通过 `StellfluxCaffeineCacheFactory` 创建的缓存会自动发射 logs、traces 和 metrics
- `stellflux-spring-boot-starter-datasource`
  - MySQL DataSource 能力
  - 包含 `stellflux-datasource`、`spring-boot-starter-jdbc` 与统一自动装配
  - 配置 `stellflux.datasource.url` 后才会创建默认 `DataSource`
  - 默认创建 `DataSource` 不等于主动连接数据库，连接与 SQL 执行发生在业务代码调用 `getConnection()` 之后
- `stellflux-spring-boot-starter-elaticsearch`
  - Elaticsearch 8.x 客户端能力
  - 包含 `stellflux-elaticsearch` 与统一自动装配
  - 支持官方同步/异步客户端、Search API、EQL Search API，以及 Stellflux logs、traces 和 metrics
- `stellflux-spring-boot-starter-stellflow`
  - Stellflow 生产者与消费者能力
  - 包含 `stellflux-stellflow` 与统一自动装配

## 推荐用法

- 只做 HTTP 调用方：引入 `stellflux-spring-boot-starter-http-client`
- 只做 HTTP 服务提供方：引入 `stellflux-spring-boot-starter-http-server`
- 同时做 HTTP client + server：引入 `stellflux-spring-boot-starter-http`
- 只做 gRPC 调用方：引入 `stellflux-spring-boot-starter-grpc-client`
- 只做 gRPC 服务提供方：引入 `stellflux-spring-boot-starter-grpc-server`
- 同时做 gRPC client + server：引入 `stellflux-spring-boot-starter-grpc`
- 接入 Caffeine 本地缓存 telemetry：引入 `stellflux-spring-boot-starter-caffeine`
- 接入 MySQL DataSource telemetry：引入 `stellflux-spring-boot-starter-datasource`，并配置 `stellflux.datasource.url`
- 接入 Elaticsearch 8.x 客户端 telemetry：引入 `stellflux-spring-boot-starter-elaticsearch`，并配置 `stellflux.elaticsearch.endpoints`
- 发送或消费 Stellflow 消息：引入 `stellflux-spring-boot-starter-stellflow`

## DataSource 示例

`stellflux-examples/stellflux-datasource-example` 演示 datasource starter 的最小接入方式。示例默认配置 MySQL 目标地址，但不会主动获取连接或执行 SQL；只有显式开启 `example.datasource.invoke-on-startup=true` 时，才会在启动后执行一次 `SELECT 1`。

默认启动：

```bash
mvn -pl stellflux-examples/stellflux-datasource-example -am install -DskipTests
mvn -f stellflux-examples/stellflux-datasource-example/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run
```

启动时执行一次 SQL：

```bash
mvn -f stellflux-examples/stellflux-datasource-example/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run -Dspring-boot.run.arguments=--example.datasource.invoke-on-startup=true
```
