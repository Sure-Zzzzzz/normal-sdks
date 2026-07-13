# smart-redis-limiter-core 2.0.0 Changelog

## 发布信息

- 版本：`2.0.0`
- 类型：Breaking Change / 契约升级

## 版本定位

`2.0.0` 是 Redis Route 原生化配套的 core 契约升级版本，用于支撑 `smart-redis-limiter-starter 2.0.0` 强制基于 redis-route 执行 Redis 限流。

`1.x` 已封版，最后版本为 `1.1.7`。后续不再规划 `1.1.8` 或 `1.2.0` 的 Redis Route 可选接入。

## 主要变更

### 1. 事件契约升级

`SmartRedisLimiterEvent` 新增 payload 构造器，事件字段统一由 `SmartRedisLimiterEventPayload` 承载。

新增 route / fallback 字段：

- `routeKey`
- `datasourceKey`
- `redisMode`
- `routeRequired`
- `routeResolved`
- `fallbackReason`

保留 1.x 旧 18 参构造器作为 `@Deprecated` 兼容桥，但新代码必须使用 payload 构造器。

### 2. 新增事件 Payload 模型

新增 `SmartRedisLimiterEventPayload`：

- 使用不可变字段模型。
- 使用 builder 构建。
- `attributes` 构造时做防御性拷贝并包装为不可变 Map，避免异步监听器读到后续修改。

### 3. 审计记录模型补齐 route 字段

`SmartRedisLimiterRecord` 新增：

- `routeKey`
- `datasourceKey`
- `redisMode`
- `routeRequired`
- `routeResolved`
- `fallbackReason`

### 4. 上下文属性补齐 route 字段

`SmartRedisLimiterContextAttribute` 新增：

- `ROUTE_KEY`
- `DATASOURCE_KEY`
- `REDIS_MODE`
- `ROUTE_REQUIRED`
- `ROUTE_RESOLVED`
- `FALLBACK_REASON`

### 5. 常量规范化

`SmartRedisLimiterConstant` 新增：

- fallback reason 常量。
- Redis mode 常量。
- Redis Route 类名常量。
- timeout executor 默认值常量。

旧错误码和错误消息常量保留为 `@Deprecated` 兼容别名，并指向新的 `ErrorCode` / `ErrorMessage`。

### 6. 错误码和错误消息拆分

新增：

- `ErrorCode`
- `ErrorMessage`

`SmartRedisLimitExceededException` 改为引用标准错误码和错误消息。

## 兼容性说明

这是破坏性版本：

- `SmartRedisLimiterEvent` 内部字段结构调整为 payload 模型。
- `SmartRedisLimiterRecord` 新增字段。
- `SmartRedisLimiterContextAttribute` 新增枚举项。

保留兼容：

- 旧 18 参 `SmartRedisLimiterEvent` 构造器保留为 `@Deprecated` 兼容桥。
- 1.x 常用 getter 继续保留并委托到 payload。
- `getSource()` 继续返回 1.x 语义中的限流来源字符串。
- 新增 `getRawSource()` 获取原始事件发布者。
- 旧错误码 / 错误消息常量作为兼容别名保留。

## 测试

新增并通过以下单元测试：

- `SmartRedisLimiterEventPayloadTest`
- `SmartRedisLimiterEventTest`
- `SmartRedisLimiterCoreContractTest`

验证内容包括：

- payload builder 与 attributes 防御性拷贝。
- Event getter 全量委托。
- `getSource()` / `getRawSource()` 兼容语义。
- 旧构造器兼容桥。
- route 上下文属性。
- fallback reason / Redis mode / route 类名常量。
- Record route 字段。
- 标准错误码和错误消息。

## 升级提示

调用方如直接构造 `SmartRedisLimiterEvent`，建议迁移到 payload 构造器：

```java
new SmartRedisLimiterEvent(publisher, SmartRedisLimiterEventPayload.builder()
        .limitKey(limitKey)
        .routeKey(routeKey)
        .datasourceKey(datasourceKey)
        .redisMode(redisMode)
        .routeRequired(true)
        .routeResolved(true)
        .build());
```

仍使用旧构造器可以编译通过，但无法表达 route / fallback 字段。
