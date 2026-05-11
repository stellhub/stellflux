# HTTP Server Telemetry 配置指南

本文档用于说明 `stellflux.http.server.telemetry.*` 这一组配置项的意义、默认行为与生产环境建议。

## 1. 这组配置是做什么的

`http-server telemetry` 不是用来决定 HTTP 服务能不能对外提供接口，而是用来决定：

- HTTP 请求是否进入可观测链路
- HTTP 请求如何采集 trace / metrics / access log
- 哪些请求应该采集，哪些请求应该排除
- 是否把 `traceparent` 回写到响应头

当前实现基于 HTTP 入站 filter：

- [StellfluxHttpServerAutoConfiguration.java](/E:/PersonalCode/JavaProject/stellflux/stellflux-spring-boot-autoconfigure/src/main/java/io/github/stellflux/http/server/StellfluxHttpServerAutoConfiguration.java)
- [StellfluxHttpServerTelemetryFilter.java](/E:/PersonalCode/JavaProject/stellflux/stellflux-spring-boot-autoconfigure/src/main/java/io/github/stellflux/http/server/StellfluxHttpServerTelemetryFilter.java)
- [StellfluxHttpServerProperties.java](/E:/PersonalCode/JavaProject/stellflux/stellflux-spring-boot-autoconfigure/src/main/java/io/github/stellflux/http/server/StellfluxHttpServerProperties.java)

它主要负责：

1. 从请求头提取上游 `traceparent`
2. 为当前 HTTP 请求创建 server span
3. 记录 HTTP server 请求计数与耗时指标
4. 记录 access telemetry 事件
5. 按配置决定是否回写 `traceparent` 到响应头

## 2. 不配置会怎么样

如果应用已经引入了 `stellflux-spring-boot-starter-http-server`，并且容器里存在 `OpenTelemetry` Bean，那么即使不写任何 `stellflux.http.server.telemetry.*` 配置，这套能力也会按默认值生效。

当前默认值等价于：

```yaml
stellflux:
  http:
    server:
      telemetry:
        enabled: true
        filter-order: -2147483638
        url-patterns:
          - /*
        excluded-paths: []
        response-trace-header-enabled: true
```

如果显式关闭 telemetry，或者根本没有 `OpenTelemetry` Bean，那么 HTTP 请求仍然可以正常处理，但会失去下面这些能力：

- HTTP server span
- HTTP server 请求耗时与请求次数指标
- access telemetry 事件
- 与上游 / 下游链路的 trace 串联能力
- 响应头中的 `traceparent`

也就是说，没有 telemetry 不会让业务接口不可用，但会让排查、监控和链路追踪能力明显下降。

## 3. 配置项说明

### 3.1 `enabled`

配置项：

```yaml
stellflux.http.server.telemetry.enabled
```

默认值：

```yaml
true
```

作用：

- 控制 HTTP telemetry filter 是否创建和注册
- 关闭后不再采集 HTTP server trace / metrics / access telemetry

适合关闭的场景：

- 已接入其它 HTTP server 自动埋点，担心重复采集
- 极高 QPS 场景下临时需要压缩观测开销

生产建议：

- 大多数服务保持 `true`
- 除非明确存在重复埋点或性能压力，否则不建议关闭

### 3.2 `filter-order`

配置项：

```yaml
stellflux.http.server.telemetry.filter-order
```

默认值：

```yaml
Ordered.HIGHEST_PRECEDENCE + 10
```

作用：

- 控制 telemetry filter 在整个 Servlet Filter 链中的执行顺序
- 顺序越靠前，越容易覆盖完整请求生命周期

生产建议：

- 没有明确依赖关系时，保持默认值
- 如果你依赖某些前置 filter 先改写 `scheme`、`host`、`path` 或建立租户上下文，可以把 telemetry 放到这些 filter 之后
- 如果你希望尽量早开始计时和 trace 提取，应保持它尽量靠前

### 3.3 `url-patterns`

配置项：

```yaml
stellflux.http.server.telemetry.url-patterns
```

默认值：

```yaml
- /*
```

作用：

- 控制 telemetry filter 挂载到哪些 URL Pattern 上
- 只会对命中的请求进入 telemetry 处理

推荐默认值：

- API 服务优先使用 `/*`

适合收窄的场景：

- 一个应用里既有业务 API，又有静态资源、文档页、管理后台
- 只希望采集 `/api/*`、`/internal/*` 这类核心入口

示例：

```yaml
stellflux:
  http:
    server:
      telemetry:
        url-patterns:
          - /api/*
          - /internal/*
```

### 3.4 `excluded-paths`

配置项：

```yaml
stellflux.http.server.telemetry.excluded-paths
```

默认值：

```yaml
[]
```

作用：

- 基于 Ant 风格路径匹配，跳过某些请求的 telemetry 采集
- 这通常是生产环境里最值得优先配置的一项

推荐优先排除：

- `/favicon.ico`
- `/actuator/health`
- `/actuator/health/**`

按需排除：

- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/error`
- `/webjars/**`
- `/static/**`

生产建议：

- 优先排除健康检查、浏览器噪音和文档页流量
- 不建议一开始直接排除整个 `/actuator/**`，除非你们明确不关心任何运维接口访问

示例：

```yaml
stellflux:
  http:
    server:
      telemetry:
        excluded-paths:
          - /favicon.ico
          - /actuator/health
          - /actuator/health/**
          - /swagger-ui/**
          - /v3/api-docs/**
```

### 3.5 `response-trace-header-enabled`

配置项：

```yaml
stellflux.http.server.telemetry.response-trace-header-enabled
```

默认值：

```yaml
true
```

作用：

- 控制是否把当前请求对应的 `traceparent` 回写到 HTTP 响应头

适合开启的场景：

- 微服务内网调用
- 联调环境
- 希望调用方或前端能快速拿到 trace 标识定位问题

适合关闭的场景：

- 对公网暴露的 API
- 对响应头暴露面要求较严格的场景

生产建议：

- 内网服务通常保持 `true`
- 公网接口按团队安全策略决定是否关闭

## 4. 推荐默认值

如果你希望在不牺牲观测完整性的前提下尽量减少噪音，推荐从下面这组配置起步：

```yaml
stellflux:
  http:
    server:
      telemetry:
        enabled: true
        filter-order: -2147483638
        url-patterns:
          - /*
        excluded-paths:
          - /actuator/health
          - /favicon.ico
        response-trace-header-enabled: true
```

这组配置的特点是：

- 默认保留全部业务请求的 trace / metrics
- 只排除最典型的低价值噪音请求
- 便于联调和链路排查

## 5. 生产环境建议

### 5.1 通用内网微服务

```yaml
stellflux:
  http:
    server:
      telemetry:
        enabled: true
        url-patterns:
          - /*
        excluded-paths:
          - /favicon.ico
          - /actuator/health
          - /actuator/health/**
          - /swagger-ui/**
          - /v3/api-docs/**
        response-trace-header-enabled: true
```

适合：

- 典型 Spring Boot 微服务
- 以接口调用为主
- 需要完整分布式链路追踪

### 5.2 对公网暴露的 API 服务

```yaml
stellflux:
  http:
    server:
      telemetry:
        enabled: true
        url-patterns:
          - /api/*
        excluded-paths:
          - /favicon.ico
          - /actuator/health
          - /actuator/health/**
        response-trace-header-enabled: false
```

适合：

- 对外开放 API
- 希望控制响应头暴露范围
- 不希望静态或非业务路径进入主链路观测

### 5.3 重点关注的问题

在生产环境里，这组配置主要帮助解决下面几类问题：

- 降低 `/health`、`/favicon.ico`、文档页等低价值请求带来的观测噪音
- 避免无意义 span 和指标堆积
- 控制 telemetry 对高流量服务带来的额外开销
- 让真正重要的业务接口更容易被定位和分析
- 在联调和故障排查时快速拿到 `traceparent`

## 6. 与 HTTP 服务端 starter 的关系

需要注意：

- `stellflux.http.server.enabled` 不存在，也不应该配置
- HTTP 服务端是否启用，取决于是否引入 `stellflux-spring-boot-starter-http-server`
- `telemetry.enabled` 控制的是“是否采集 HTTP 入站可观测数据”，不是“HTTP 服务是否启动”

相关说明可参考：

- [starter-modules.md](./starter-modules.md)
- [opentelemetry-design.md](./opentelemetry-design.md)
