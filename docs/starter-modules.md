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
- `stellflux-spring-boot-starter-stellflow`
  - Stellflow 聚合 starter
  - 同时引入 producer 和 consumer
- `stellflux-spring-boot-starter-stellflow-producer`
  - Stellflow 生产者能力
  - 包含 `stellflux-stellflow-producer` 与对应自动装配
- `stellflux-spring-boot-starter-stellflow-consumer`
  - Stellflow 消费者能力
  - 包含 `stellflux-stellflow-consumer` 与对应自动装配

## 推荐用法

- 只做 HTTP 调用方：引入 `stellflux-spring-boot-starter-http-client`
- 只做 HTTP 服务提供方：引入 `stellflux-spring-boot-starter-http-server`
- 同时做 HTTP client + server：引入 `stellflux-spring-boot-starter-http`
- 只做 gRPC 调用方：引入 `stellflux-spring-boot-starter-grpc-client`
- 只做 gRPC 服务提供方：引入 `stellflux-spring-boot-starter-grpc-server`
- 同时做 gRPC client + server：引入 `stellflux-spring-boot-starter-grpc`
- 只发送 Stellflow 消息：引入 `stellflux-spring-boot-starter-stellflow-producer`
- 只消费 Stellflow 消息：引入 `stellflux-spring-boot-starter-stellflow-consumer`
- 同时发送和消费 Stellflow 消息：引入 `stellflux-spring-boot-starter-stellflow`
