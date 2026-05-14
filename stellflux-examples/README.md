# stellflux-examples

`stellflux-examples` 是 `stellflux` 的示例应用聚合模块，用于展示 HTTP、gRPC、OpenTelemetry、StellMap、Jedis 等能力的最小接入方式。

## 模块列表

| 模块 | 根包 | 默认端口 | 用途 |
| --- | --- | --- | --- |
| `stellflux-http-server-example` | `io.github.stellflux.examples.httpserver` | `18080` | 演示最小 HTTP Server 接入方式 |
| `stellflux-http-client-example` | `io.github.stellflux.examples.httpclient` | 无 | 演示最小 HTTP Client 接入方式 |
| `stellflux-grpc-client-example` | `io.github.stellflux.examples.grpcclient` | 无 | 演示最小 gRPC Client 接入方式 |
| `stellflux-grpc-server-example` | `io.github.stellflux.examples.grpcserver` | `19090` | 演示最小 gRPC Server 接入方式 |
| `stellflux-opentelemetry-example` | `io.github.stellflux.examples.opentelemetry` | 无 | 演示最小 OpenTelemetry 接入方式 |
| `stellflux-stellmap-example` | `io.github.stellflux.examples.stellmap` | `18081` | 演示最小 StellMap 集成方式 |
| `stellflux-jedis-examples` | `io.github.stellflux.examples.jedis` | 无 | 演示最小 Jedis 接入方式 |

说明：

- “无端口”表示该模块默认以非 Web 模式启动
- gRPC 示例的 proto 生成代码位于各自模块的 `target/generated-sources/protobuf`
- 当前示例默认关闭了大部分 OpenTelemetry 导出，便于本地直接启动
- 所有示例的 Spring 配置文件统一使用 `application.yaml`
- `stellflux-examples` 的 `spring-boot:run` 默认会附带 `--log.stdout=true`，让日志优先输出到标准输出，更适合本地调试

## 构建方式

在仓库根目录执行：

```bash
mvn -pl "stellflux-examples/stellflux-http-server-example,stellflux-examples/stellflux-http-client-example,stellflux-examples/stellflux-grpc-client-example,stellflux-examples/stellflux-grpc-server-example,stellflux-examples/stellflux-opentelemetry-example,stellflux-examples/stellflux-stellmap-example,stellflux-examples/stellflux-jedis-examples" -am compile
```

如果只想编译整个 examples 聚合模块对应的子模块，推荐仍然从根工程执行 reactor 构建，这样可以自动带上本仓库里的 starter 依赖模块。

## 本地日志参数

`stellflux-examples/pom.xml` 已为 `spring-boot:run` 配置了默认启动参数：

```text
--log.stdout=true
```

这样 examples 在本地运行时会优先走标准输出日志模式。它对应 `io.github.stellflux.log.springboot.StellfluxLogBootstrapModeResolver` 支持的 `log.stdout` 开关。

如果你想覆盖默认值，可以这样执行：

```bash
mvn -pl stellflux-examples/stellflux-http-server-example -am spring-boot:run -Dlog.stdout=false
```

## 启动方式

### 1. `stellflux-http-server-example`

- 根包：`io.github.stellflux.examples.httpserver`
- 启动类：`io.github.stellflux.examples.httpserver.StellfluxHttpServerExampleApplication`
- 默认端口：`18080`
- 用途：启动一个最小 HTTP 服务端，并暴露示例接口

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-http-server-example -am spring-boot:run
```

示例接口：

- `GET http://127.0.0.1:18080/api/example/hello`

### 2. `stellflux-http-client-example`

- 根包：`io.github.stellflux.examples.httpclient`
- 启动类：`io.github.stellflux.examples.httpclient.StellfluxHttpClientExampleApplication`
- 默认端口：无
- 用途：演示 `@OkHttpClient` 声明式客户端的最小接入方式

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-http-client-example -am spring-boot:run
```

默认行为：

- 启动时会准备一个目标为 `http://127.0.0.1:18080` 的 `StellfluxHttpClient`
- 默认只打印目标地址，不主动发请求

如需启动时自动调用 HTTP Server 示例：

```bash
mvn -pl stellflux-examples/stellflux-http-client-example -am spring-boot:run -Dspring-boot.run.arguments=--example.http.client.invoke-on-startup=true
```

### 3. `stellflux-grpc-client-example`

- 根包：`io.github.stellflux.examples.grpcclient`
- 启动类：`io.github.stellflux.examples.grpcclient.StellfluxGrpcClientExampleApplication`
- 默认端口：无
- 用途：演示 `@RpcClient` 声明式 gRPC 客户端和 proto 生成方式

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-grpc-client-example -am spring-boot:run
```

默认行为：

- 启动时会准备一个目标为 `127.0.0.1:19090` 的 `ManagedChannel`
- 默认只打印 channel 信息，不主动发起 RPC

如需启动时自动调用 gRPC Server 示例：

```bash
mvn -pl stellflux-examples/stellflux-grpc-client-example -am spring-boot:run -Dspring-boot.run.arguments=--example.grpc.client.invoke-on-startup=true
```

### 4. `stellflux-grpc-server-example`

- 根包：`io.github.stellflux.examples.grpcserver`
- 启动类：`io.github.stellflux.examples.grpcserver.StellfluxGrpcServerExampleApplication`
- 默认端口：`19090`
- 用途：演示最小 gRPC 服务端暴露方式

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-grpc-server-example -am spring-boot:run
```

默认行为：

- 暴露 `GreeterService`
- 服务实现类：`io.github.stellflux.examples.grpcserver.GreeterRpcService`
- 注册 `serviceId`：`example.greeter.rpc`

### 5. `stellflux-opentelemetry-example`

- 根包：`io.github.stellflux.examples.opentelemetry`
- 启动类：`io.github.stellflux.examples.opentelemetry.StellfluxOpenTelemetryExampleApplication`
- 默认端口：无
- 用途：演示最小 OpenTelemetry 运行时装配和 span 创建

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-opentelemetry-example -am spring-boot:run
```

默认行为：

- 启动后创建一个名为 `startup-demo-span` 的示例 span
- 当前配置只开启 traces，关闭 logs 和 metrics

### 6. `stellflux-stellmap-example`

- 根包：`io.github.stellflux.examples.stellmap`
- 启动类：`io.github.stellflux.examples.stellmap.StellfluxStellMapExampleApplication`
- 默认端口：`18081`
- 用途：演示最小 StellMap 集成方式，并保留 HTTP Server 场景便于观察注册行为

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-stellmap-example -am spring-boot:run
```

示例接口：

- `GET http://127.0.0.1:18081/api/stellmap/status`

默认行为：

- 未配置 `stellflux.stellmap.base-url` 时，不会初始化 `StellMapClient`
- 启动日志会提示如何开启 StellMap 集成

如需开启 StellMap：

```bash
mvn -pl stellflux-examples/stellflux-stellmap-example -am spring-boot:run -Dspring-boot.run.arguments=--stellflux.stellmap.base-url=http://127.0.0.1:8080
```

### 7. `stellflux-jedis-examples`

- 根包：`io.github.stellflux.examples.jedis`
- 启动类：`io.github.stellflux.examples.jedis.StellfluxJedisExampleApplication`
- 默认端口：无
- 用途：演示 `stellflux-spring-boot-starter-jedis` 自动装配带 OpenTelemetry 的 `DefaultJedisClientConfig`

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-jedis-examples -am spring-boot:run
```

默认行为：

- 启动后输出自动装配的 `DefaultJedisClientConfig` telemetry 状态
- 默认不主动连接 Redis，便于本地直接启动

如需启动时访问本地 Redis：

```bash
mvn -pl stellflux-examples/stellflux-jedis-examples -am spring-boot:run -Dspring-boot.run.arguments=--example.jedis.invoke-on-startup=true
```

如需指定 Redis 地址：

```bash
mvn -pl stellflux-examples/stellflux-jedis-examples -am spring-boot:run -Dspring-boot.run.arguments="--example.jedis.invoke-on-startup=true --example.jedis.host=127.0.0.1 --example.jedis.port=6379"
```

## 模块关系建议

- 想验证 HTTP client 到 HTTP server：先启动 `stellflux-http-server-example`，再启动 `stellflux-http-client-example`
- 想验证 gRPC client 到 gRPC server：先启动 `stellflux-grpc-server-example`，再启动 `stellflux-grpc-client-example`
- 想单独观察 TraceId / Span 创建：启动 `stellflux-opentelemetry-example`
- 想验证服务注册或后续接入服务发现：启动 `stellflux-stellmap-example`
- 想验证 Jedis telemetry 配置装配：启动 `stellflux-jedis-examples`
