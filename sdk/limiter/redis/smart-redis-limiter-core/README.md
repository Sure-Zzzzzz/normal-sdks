# SmartRedisLimiter Core

[![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

`smart-redis-limiter-core` 是 `smart-redis-limiter-starter` 的核心契约层，定义限流事件、审计记录模型、扩展接口、错误码和统一常量。

## 版本定位

`2.0.0` 是 Redis Route 原生化配套的破坏性契约升级版本。starter 2.0.0 强制基于 `simple-redis-route-starter` 执行 Redis 限流，core 2.0.0 同步暴露 route / datasource / fallback 相关事件字段。

1.x 已封版，历史文档见 [README.1.x.md](README.1.x.md)。

## 核心组件

### 1. SmartRedisLimiterEvent

限流事件，在 Interceptor（拦截器模式）或 Aspect（注解模式）限流结果产生后发布。

2.0.0 使用 `SmartRedisLimiterEventPayload` 作为事件载荷：

```java
SmartRedisLimiterEvent event = new SmartRedisLimiterEvent(publisher,
        SmartRedisLimiterEventPayload.builder()
                .limitKey(limitKey)
                .routeKey(routeKey)
                .datasourceKey(datasourceKey)
                .redisMode(redisMode)
                .routeRequired(true)
                .routeResolved(true)
                .keyStrategy(keyStrategy)
                .algorithm(algorithm)
                .limitRules(limitRules)
                .passed(passed)
                .sourceType(SmartRedisLimiterConstant.SOURCE_INTERCEPTOR)
                .fallbackReason(fallbackReason)
                .build());
```

新增 route / fallback 字段：

| 字段 | 说明 |
|------|------|
| `routeKey` | 用于 redis-route 选择 datasource 的逻辑 key |
| `datasourceKey` | redis-route 解析得到的 datasource key |
| `redisMode` | Redis 模式：standalone / cluster / unknown |
| `routeRequired` | 是否要求通过 redis-route 执行 |
| `routeResolved` | 是否成功拿到可观测 datasource 信息 |
| `fallbackReason` | 降级原因 |

兼容说明：

- 旧 18 参构造器保留为 `@Deprecated` 兼容桥。
- 1.x 常用 getter 继续保留并委托到 payload。
- `getSource()` 继续返回限流来源字符串，兼容 1.x 语义。
- `getRawSource()` 返回原始事件发布者。

### 2. SmartRedisLimiterEventPayload

事件载荷模型，字段不可变，使用 builder 构建。

`attributes` 在构造时做防御性拷贝并包装为不可变 Map，避免异步监听器读取到后续修改。

### 3. SmartRedisLimiterRecord

审计记录 DTO，事件监听器可将 Event 转换为 Record 后交给 Handler 处理。

2.0.0 新增 route 字段：

- `routeKey`
- `datasourceKey`
- `redisMode`
- `routeRequired`
- `routeResolved`
- `fallbackReason`

### 4. Provider 接口

`SmartRedisLimiterUserProvider` 用于从当前请求上下文中提取用户身份：

```java
public interface SmartRedisLimiterUserProvider {
    String getClientId();
    String getClientType();
    String getUserId();
    String getUsername();
}
```

`SmartRedisLimiterTraceIdProvider` 用于提取链路追踪 ID：

```java
public interface SmartRedisLimiterTraceIdProvider {
    String getTraceId();
}
```

SDK 不提供默认实现，由调用方按需注册。

### 5. 错误码和错误消息

2.0.0 新增标准错误码和错误消息类：

| 类 | 说明 |
|----|------|
| `ErrorCode` | 标准错误码 |
| `ErrorMessage` | 标准错误消息 |

旧 `SmartRedisLimiterConstant.ERROR_CODE_*` / `MSG_*` 常量保留为 `@Deprecated` 兼容别名。

### 6. 常量与枚举

| 类 | 说明 |
|---|------|
| `SmartRedisLimiterConstant` | 统一常量类，包含配置前缀、来源标识、fallback reason、Redis mode、Redis Route 类名、HTTP 头、Key 模板等 |
| `SmartRedisLimiterRedisKeyConstant` | Redis Key 常量类，包含前缀、分隔符、时间单位后缀、滑动窗口标识等 |
| `SmartRedisLimiterKeyStrategy` | Key 生成策略枚举：method / path / path-pattern / ip / key-provider |
| `SmartRedisLimiterMode` | 限流模式枚举：annotation / interceptor / both |
| `SmartRedisLimiterFallbackStrategy` | 降级策略枚举：allow / deny |
| `SmartRedisLimiterContextAttribute` | 上下文属性枚举，包含 route / fallback / datasource 相关属性 |
| `SmartRedisLimiterHttpMethod` | HTTP 方法枚举：GET / POST / PUT / DELETE 等 |

## 依赖说明

本模块仅依赖 `spring-context`，不依赖 Redis Route，不包含 Spring Boot 自动配置。

## 使用方式

通常情况下，不需要直接依赖本模块，而是使用限流 starter：

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-starter:2.0.0'
```

如果需要自定义 Provider 或直接使用核心事件模型，可以直接依赖本模块：

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-core:2.0.0'
```

## 架构位置

```text
smart-redis-limiter-core               ← Event、Payload、Record、Provider、Exception、常量与枚举
        ↑
        └── smart-redis-limiter-starter                     (发布事件)
```

## 测试

core 2.0.0 覆盖以下单元测试：

- `SmartRedisLimiterEventPayloadTest`
- `SmartRedisLimiterEventTest`
- `SmartRedisLimiterCoreContractTest`

## 版本历史

### 2.0.0

- Redis Route 原生化配套的 core 契约升级。
- `SmartRedisLimiterEvent` 引入 payload 构造器。
- 新增 `SmartRedisLimiterEventPayload`。
- Event / Record / ContextAttribute 新增 route / fallback 字段。
- 新增 `ErrorCode` / `ErrorMessage`。
- `SmartRedisLimiterConstant` 新增 fallback reason、Redis mode、Redis Route 类名和 timeout executor 默认值常量。
- 保留旧 Event 构造器和旧错误码 / 错误消息常量作为兼容桥。

### 1.x

1.x 已封版，历史说明见 [README.1.x.md](README.1.x.md)。
