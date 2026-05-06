# stellflux

基于 Spring Boot 3 的自研框架骨架工程，当前已提供以下基础模块：

- `stellflux-bom`
- `stellflux-http-client`
- `stellflux-loadbalancer`
- `stellflux-loadbalancer-stellmap`
- `stellflux-stellmap`
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

## 构建

```bash
mvn clean install
```

## 文档

- [客户端发现模型](./docs/stellflux-client-discovery-model.md)
- [Starter 模块说明](./docs/starter-modules.md)
- [客户端 Bean 关系图](./docs/stellflux-client-bean-relationship.svg)
- [动态服务发现流程图](./docs/stellflux-service-discovery-flow.svg)
