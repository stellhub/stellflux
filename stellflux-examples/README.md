# stellflux-examples

`stellflux-examples` 是 `stellflux` 的示例应用聚合模块，用于展示 HTTP、gRPC、OpenTelemetry、StellMap、Stellflow、Jedis、Caffeine、ThreadPool、DataSource 等能力的最小接入方式。

## 模块列表

| 模块 | 根包 | 默认端口 | 用途 |
| --- | --- | --- | --- |
| `stellflux-http-server-example` | `io.github.stellflux.examples.httpserver` | `18080` | 演示最小 HTTP Server 接入方式 |
| `stellflux-http-client-example` | `io.github.stellflux.examples.httpclient` | 无 | 演示最小 HTTP Client 接入方式 |
| `stellflux-grpc-client-example` | `io.github.stellflux.examples.grpcclient` | 无 | 演示最小 gRPC Client 接入方式 |
| `stellflux-grpc-server-example` | `io.github.stellflux.examples.grpcserver` | `19090` | 演示最小 gRPC Server 接入方式 |
| `stellflux-opentelemetry-example` | `io.github.stellflux.examples.opentelemetry` | `18083` | 演示 OpenTelemetry trace、log、metrics 的 HTTP 验证方式 |
| `stellflux-stellmap-example` | `io.github.stellflux.examples.stellmap` | `18081` | 演示最小 StellMap 集成方式 |
| `stellflux-stellflow-example` | `io.github.stellflux.examples.stellflow` | `18082` | 演示 Stellflow 生产和消费接入方式 |
| `stellflux-jedis-examples` | `io.github.stellflux.examples.jedis` | `18084` | 演示 Jedis CRUD 和 OpenTelemetry metrics 验证方式 |
| `stellflux-caffeine-examples` | `io.github.stellflux.examples.caffeine` | `18086` | 演示 Caffeine 本地缓存 CRUD 和 OpenTelemetry logs/traces/metrics 验证方式 |
| `stellflux-thread-pool-example` | `io.github.stellflux.examples.threadpool` | `18087` | 演示线程池 CRUD 和 OpenTelemetry metrics 验证方式 |
| `stellflux-datasource-example` | `io.github.stellflux.examples.datasource` | `18085` | 演示 MySQL DataSource 自动装配和一次性 SQL telemetry 验证方式 |

说明：

- “无端口”表示该模块默认以非 Web 模式启动
- gRPC 示例的 proto 生成代码位于各自模块的 `target/generated-sources/protobuf`
- 当前示例默认关闭了大部分 OpenTelemetry 导出，便于本地直接启动
- 所有示例的 Spring 配置文件统一使用 `application.yaml`
- `stellflux-examples` 的 `spring-boot:run` 默认会附带 `--log.stdout=true`，让日志优先输出到标准输出，更适合本地调试

## 构建方式

在仓库根目录执行：

```bash
mvn -pl "stellflux-examples/stellflux-http-server-example,stellflux-examples/stellflux-http-client-example,stellflux-examples/stellflux-grpc-client-example,stellflux-examples/stellflux-grpc-server-example,stellflux-examples/stellflux-opentelemetry-example,stellflux-examples/stellflux-stellmap-example,stellflux-examples/stellflux-stellflow-example,stellflux-examples/stellflux-jedis-examples,stellflux-examples/stellflux-caffeine-examples,stellflux-examples/stellflux-thread-pool-example,stellflux-examples/stellflux-datasource-example" -am compile
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
- 默认端口：`18083`
- 用途：演示 `stellflux-spring-boot-starter-opentelemetry` 和 `stellflux-spring-boot-starter-http` 共同装配后，通过 HTTP 接口触发 trace、log 和 metrics

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-opentelemetry-example -am install -DskipTests
mvn -f stellflux-examples/stellflux-opentelemetry-example/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run
```

默认行为：

- 启动后创建一次 `startup` 场景的综合观测事件
- 当前配置开启 traces、logs 和 metrics
- OTel 日志使用 console json 输出，便于本地直接在控制台查看
- OTel metrics 会记录到 SDK Meter，同时接口会返回本地指标快照，便于不接 Collector 时快速验证

示例接口：

- `GET http://127.0.0.1:18083/api/opentelemetry/status`
- `GET http://127.0.0.1:18083/api/opentelemetry/trace?operation=checkout`
- `POST http://127.0.0.1:18083/api/opentelemetry/logs`
- `POST http://127.0.0.1:18083/api/opentelemetry/metrics`
- `POST http://127.0.0.1:18083/api/opentelemetry/verify?scenario=checkout`

日志请求体示例：

```json
{
  "message": "order checkout log observation",
  "level": "INFO"
}
```

指标请求体示例：

```json
{
  "name": "checkout",
  "value": 42.5
}
```

验证方式：

- 调用 `/trace` 后，响应会返回 `traceId` 和 `spanId`
- 调用 `/logs` 后，响应会返回同一次请求的 `traceId`，控制台会输出对应 OTel log
- 调用 `/metrics` 后，响应里的 `snapshot.totalMetrics` 会递增
- 调用 `/verify` 可一次性触发 trace、log、metrics，并在响应里看到同一次观测的 traceId 和指标快照

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

### 7. `stellflux-stellflow-example`

- 根包：`io.github.stellflux.examples.stellflow`
- 启动类：`io.github.stellflux.examples.stellflow.StellfluxStellflowExampleApplication`
- 默认端口：`18082`
- 用途：演示 `stellflux-spring-boot-starter-stellflow` 和 `stellflux-spring-boot-starter-http` 共同装配后，通过 HTTP 接口触发生产者和消费者

启动命令：

```bash
mvn -pl stellflux-spring-boot-starter-parent/stellflux-spring-boot-starter-stellflow,stellflux-examples/stellflux-stellflow-example -am install -DskipTests
mvn -pl stellflux-examples/stellflux-stellflow-example org.springframework.boot:spring-boot-maven-plugin:3.5.14:run
```

默认行为：

- 启动后准备 `StellflowProducer` 和 `StellflowConsumer`
- 默认连接地址为 `127.0.0.1:9092`
- 默认主题为 `orders.created`
- 默认不主动发送或消费消息，便于本地直接启动并通过接口手动触发

示例接口：

- `GET http://127.0.0.1:18082/api/stellflow/status`
- `POST http://127.0.0.1:18082/api/stellflow/producer/orders`
- `POST http://127.0.0.1:18082/api/stellflow/consumer/subscriptions`
- `GET http://127.0.0.1:18082/api/stellflow/consumer/records?timeout=3s`
- `POST http://127.0.0.1:18082/api/stellflow/consumer/offsets/commit`
- `POST http://127.0.0.1:18082/api/stellflow/workflows/orders`

发送订单事件请求体示例：

```json
{
  "orderId": "order-10001",
  "userId": "user-10001",
  "amount": 129.90,
  "currency": "CNY"
}
```

如需启动时模拟真实订单事件发送和消费：

```bash
mvn -pl stellflux-examples/stellflux-stellflow-example org.springframework.boot:spring-boot-maven-plugin:3.5.14:run -Dspring-boot.run.arguments=--example.stellflow.invoke-on-startup=true
```

如需指定 Stellflow broker 地址：

```bash
mvn -pl stellflux-examples/stellflux-stellflow-example org.springframework.boot:spring-boot-maven-plugin:3.5.14:run -Dspring-boot.run.arguments="--example.stellflow.invoke-on-startup=true --stellflux.stellflow.bootstrap-servers=127.0.0.1:9092"
```

### 8. `stellflux-jedis-examples`

- 根包：`io.github.stellflux.examples.jedis`
- 启动类：`io.github.stellflux.examples.jedis.StellfluxJedisExampleApplication`
- 默认端口：`18084`
- 用途：演示 `stellflux-spring-boot-starter-jedis` 和 `stellflux-spring-boot-starter-http` 共同装配后，通过 HTTP 接口执行 Redis CRUD 并观察 OpenTelemetry metrics

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-jedis-examples -am install -DskipTests
mvn -f stellflux-examples/stellflux-jedis-examples/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run
```

默认行为：

- 启动后输出自动装配的 `DefaultJedisClientConfig` telemetry 状态
- 默认不主动连接 Redis，便于本地直接启动并通过 HTTP 手动触发
- 当前配置开启 traces、logs 和 metrics
- OTel metrics 会记录 Redis 操作次数、错误次数和操作耗时，同时接口会返回本地指标快照，便于不接 Collector 时快速验证

如需启动时访问本地 Redis：

```bash
mvn -f stellflux-examples/stellflux-jedis-examples/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run -Dspring-boot.run.arguments=--example.jedis.invoke-on-startup=true
```

如需指定 Redis 地址：

```bash
mvn -f stellflux-examples/stellflux-jedis-examples/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run -Dspring-boot.run.arguments="--example.jedis.invoke-on-startup=true --example.jedis.host=127.0.0.1 --example.jedis.port=6379"
```

示例接口：

- `GET http://127.0.0.1:18084/api/jedis/status`
- `POST http://127.0.0.1:18084/api/jedis/keys`
- `GET http://127.0.0.1:18084/api/jedis/keys/stellflux:jedis:example:manual`
- `DELETE http://127.0.0.1:18084/api/jedis/keys/stellflux:jedis:example:manual`
- `POST http://127.0.0.1:18084/api/jedis/workflows/basic?scenario=checkout`

写入请求体示例：

```json
{
  "key": "stellflux:jedis:example:manual",
  "value": "hello-stellflux-jedis",
  "ttlSeconds": 60
}
```

验证方式：

- 调用 `/keys` 后，响应会返回当前 Redis 操作的 `traceId`、`spanId` 和 `metrics.totalOperations`
- 调用 `/workflows/basic` 可一次性执行 set/get/delete，并在响应里看到三次操作各自的 traceId
- Redis 可用时，`metrics.totalErrors` 保持不变，`metrics.totalMetricRecords` 会随操作递增
- Redis 不可用时，接口会返回 `success=false` 和错误信息，同时 `metrics.totalErrors` 会递增

### 9. `stellflux-caffeine-examples`

- 根包：`io.github.stellflux.examples.caffeine`
- 启动类：`io.github.stellflux.examples.caffeine.StellfluxCaffeineExampleApplication`
- 默认端口：`18086`
- 用途：演示 `stellflux-spring-boot-starter-caffeine` 和 `stellflux-spring-boot-starter-http` 共同装配后，通过 HTTP 接口执行本地缓存 CRUD 并观察 OpenTelemetry logs、traces 和 metrics

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-caffeine-examples -am install -DskipTests
mvn -f stellflux-examples/stellflux-caffeine-examples/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run
```

默认行为：

- 启动后创建一个 `stellflux-caffeine-example` 本地缓存
- 默认缓存最大容量为 `1000`，写入后 `10m` 过期
- 默认不主动执行 CRUD，便于本地直接启动并通过 HTTP 手动触发
- 当前配置开启 traces、logs 和 metrics
- OTel metrics 会记录缓存操作次数、错误次数、命中次数、未命中次数、操作耗时和缓存 size gauge

如需启动时执行一次本地缓存 CRUD：

```bash
mvn -f stellflux-examples/stellflux-caffeine-examples/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run -Dspring-boot.run.arguments=--example.caffeine.invoke-on-startup=true
```

示例接口：

- `GET http://127.0.0.1:18086/api/caffeine/status`
- `POST http://127.0.0.1:18086/api/caffeine/keys`
- `GET http://127.0.0.1:18086/api/caffeine/keys/stellflux:caffeine:example:manual`
- `DELETE http://127.0.0.1:18086/api/caffeine/keys/stellflux:caffeine:example:manual`
- `POST http://127.0.0.1:18086/api/caffeine/workflows/basic?scenario=checkout`

写入请求体示例：

```json
{
  "key": "stellflux:caffeine:example:manual",
  "value": "hello-stellflux-caffeine"
}
```

验证方式：

- 调用 `/keys` 后，响应会返回本地缓存 size 和 telemetry 快照
- 调用 `/keys/{key}` 后，响应里的 `hit` 表示本次读取是否命中
- 调用 `/workflows/basic` 可一次性执行 put/get/delete，并在响应里看到操作后的 Caffeine stats
- 控制台会输出对应的 OTel structured log，trace 和 metrics 由 `stellflux-caffeine` 核心封装统一发射

### 10. `stellflux-thread-pool-example`

- 根包：`io.github.stellflux.examples.threadpool`
- 启动类：`io.github.stellflux.examples.threadpool.StellfluxThreadPoolExampleApplication`
- 默认端口：`18087`
- 用途：演示 `stellflux-spring-boot-starter-thread-pool` 和 `stellflux-spring-boot-starter-http` 共同装配后，通过 HTTP 接口管理线程池并观察 OpenTelemetry metrics

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-thread-pool-example -am install -DskipTests
mvn -f stellflux-examples/stellflux-thread-pool-example/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run
```

默认行为：

- 启动后创建一个 `example-worker` 线程池
- 默认线程池配置为 `corePoolSize=2`、`maximumPoolSize=4`、`queueCapacity=32`
- 默认不主动提交任务，便于本地直接启动并通过 HTTP 手动触发
- 当前配置开启 metrics，关闭 logs 和 traces
- OTel metrics 会记录 active threads、pool size、core/max threads、queue size、remaining capacity、task count 和 completed task count

如需启动时提交一批示例任务：

```bash
mvn -f stellflux-examples/stellflux-thread-pool-example/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run -Dspring-boot.run.arguments=--example.thread-pool.invoke-on-startup=true
```

示例接口：

- `GET http://127.0.0.1:18087/api/thread-pool/status`
- `GET http://127.0.0.1:18087/api/thread-pool/metrics`
- `POST http://127.0.0.1:18087/api/thread-pool/pools`
- `GET http://127.0.0.1:18087/api/thread-pool/pools/example-worker`
- `PUT http://127.0.0.1:18087/api/thread-pool/pools/example-worker`
- `DELETE http://127.0.0.1:18087/api/thread-pool/pools/example-worker`
- `POST http://127.0.0.1:18087/api/thread-pool/pools/example-worker/tasks`

创建线程池请求体示例：

```json
{
  "poolName": "order-worker",
  "corePoolSize": 2,
  "maximumPoolSize": 6,
  "queueCapacity": 64,
  "keepAliveSeconds": 30
}
```

更新线程池请求体示例：

```json
{
  "corePoolSize": 3,
  "maximumPoolSize": 8
}
```

提交任务请求体示例：

```json
{
  "taskCount": 12,
  "workMillis": 3000
}
```

验证方式：

- 调用 `/pools` 创建线程池后，响应会返回该线程池的当前指标快照
- 调用 `/tasks` 后，立刻调用 `/metrics` 可以看到 `activeCount`、`queueSize`、`taskCount` 变化
- 任务执行完成后，再调用 `/metrics` 可以看到 `completedTaskCount` 增长
- 不接 Collector 时，接口里的本地快照也可以验证指标采集对象已经被 `StellfluxThreadPoolTelemetry` 监控

### 11. `stellflux-datasource-example`

- 根包：`io.github.stellflux.examples.datasource`
- 启动类：`io.github.stellflux.examples.datasource.StellfluxDataSourceExampleApplication`
- 默认端口：`18085`
- 用途：演示 `stellflux-spring-boot-starter-datasource` 和 `stellflux-spring-boot-starter-http` 共同装配后，如何准备带 OpenTelemetry 的 MySQL `DataSource`

启动命令：

```bash
mvn -pl stellflux-examples/stellflux-datasource-example -am install -DskipTests
mvn -f stellflux-examples/stellflux-datasource-example/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run
```

默认行为：

- 启动后输出自动装配的 `DataSource` 状态
- 默认目标地址为 `jdbc:mysql://127.0.0.1:3306/stellflux_example`
- 默认不主动获取连接，也不执行 SQL，便于本地没有 MySQL 时直接启动
- 当前配置开启 traces、logs 和 metrics
- 只有显式开启 `example.datasource.invoke-on-startup=true` 时，才会在启动后执行一次 `SELECT 1`

如需启动时执行一次 SQL：

```bash
mvn -f stellflux-examples/stellflux-datasource-example/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run -Dspring-boot.run.arguments=--example.datasource.invoke-on-startup=true
```

如需指定 MySQL 地址和验证 SQL：

```bash
mvn -f stellflux-examples/stellflux-datasource-example/pom.xml org.springframework.boot:spring-boot-maven-plugin:3.5.14:run -Dspring-boot.run.arguments="--example.datasource.invoke-on-startup=true --stellflux.datasource.url=jdbc:mysql://127.0.0.1:3306/stellflux_example --stellflux.datasource.username=root --stellflux.datasource.password=secret --example.datasource.verification-sql=SELECT 1"
```

示例接口：

- `GET http://127.0.0.1:18085/api/datasource/status`

验证方式：

- 默认启动后调用 `/status`，响应会返回 `dataSourcePresent=true`，但不会触发 MySQL 连接
- 加上 `--example.datasource.invoke-on-startup=true` 后，启动日志会输出一次 SQL 执行结果
- MySQL 可用时，SQL 执行返回 `success=true`
- MySQL 不可用时，SQL 执行返回 `success=false` 和错误信息，应用仍然可以用于观察失败 telemetry

## 模块关系建议

- 想验证 HTTP client 到 HTTP server：先启动 `stellflux-http-server-example`，再启动 `stellflux-http-client-example`
- 想验证 gRPC client 到 gRPC server：先启动 `stellflux-grpc-server-example`，再启动 `stellflux-grpc-client-example`
- 想单独观察 TraceId / Log / Metrics：启动 `stellflux-opentelemetry-example`
- 想验证服务注册或后续接入服务发现：启动 `stellflux-stellmap-example`
- 想验证 Stellflow 生产和消费自动装配：启动 `stellflux-stellflow-example`
- 想验证 Jedis CRUD 与 OpenTelemetry metrics：启动 `stellflux-jedis-examples`
- 想验证 Caffeine 本地缓存 CRUD 与 OpenTelemetry logs/traces/metrics：启动 `stellflux-caffeine-examples`
- 想验证线程池 CRUD 与 OpenTelemetry metrics：启动 `stellflux-thread-pool-example`
- 想验证 MySQL DataSource 连接与 SQL telemetry：启动 `stellflux-datasource-example`，并显式打开 `example.datasource.invoke-on-startup`
