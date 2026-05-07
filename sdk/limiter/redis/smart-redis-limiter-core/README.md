# SmartRedisLimiter Core

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

`smart-redis-limiter-core` 是 `smart-redis-limiter-starter` 的核心抽象层，定义了限流事件、审计记录模型和扩展接口，供限流审计模块使用。

## 概述

`smart-redis-limiter-starter` 在限流结果产生后发布事件，由审计模块订阅处理。本模块不含任何实现，仅定义事件契约。

## 核心组件

### 1. SmartRedisLimiterEvent

限流事件，在 Interceptor（拦截器模式）或 Aspect（注解模式）限流结果产生后发布。

```java

@Getter
public class SmartRedisLimiterEvent extends ApplicationEvent {

    private final String limitKey;
    private final String keyStrategy;
    private final String limitRules;
    private final boolean passed;          // true=通过，false=触发限流
    private final String source;           // INTERCEPTOR / ASPECT

    // Interceptor 专有字段
    private final String requestUri;
    private final String httpMethod;
    private final String clientIp;
    private final String matchedPathPattern;

    // Aspect 专有字段
    private final String methodName;
    private final String methodQualifiedName;

    private final Map<String, Object> attributes;
}
```

> Interceptor 和 Aspect 两种来源共享同一个 Event 类，各自对应字段可为空。

### 2. SmartRedisLimiterRecord

审计记录 DTO，事件监听器将 Event 转换为 Record 后交给 Handler 处理。

```java
@Data
@Builder
public class SmartRedisLimiterRecord {
    // 用户信息（来自 Provider）
    private String clientId;
    private String clientType;
    private String userId;
    private String username;

    // 限流上下文（来自事件）
    private String limitKey;
    private String keyStrategy;
    private String limitRules;
    private boolean passed;

    // 来源信息
    private String source;   // INTERCEPTOR / ASPECT

    // 请求信息（仅 Interceptor）
    private String requestUri;
    private String httpMethod;
    private String clientIp;
    private String matchedPathPattern;

    // 方法信息（仅 Aspect）
    private String methodName;
    private String methodQualifiedName;

    // 元数据
    private Long timestamp;
    private String traceId;
    private Map<String, String> extra;
}
```

### 3. SmartRedisLimiterUserProvider

用户信息 Provider 接口，用于从当前请求上下文中提取用户身份。

```java
public interface SmartRedisLimiterUserProvider {
    String getClientId();
    String getClientType();
    String getUserId();
    String getUsername();
}
```

SDK 不提供默认实现，由业务方按需注册。

### 4. SmartRedisLimiterTraceIdProvider

链路追踪 ID Provider 接口。

```java
public interface SmartRedisLimiterTraceIdProvider {
    String getTraceId();
}
```

## 依赖说明

本模块仅依赖 `spring-context`，无任何传递依赖。

## 使用方式

通常情况下，不需要直接依赖本模块，而是使用限流 starter：

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-starter'
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-audit-listener-starter'
```

如果需要自定义 Provider，可以直接依赖本模块：

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-core'
```

自定义 Provider 示例：

```java
@Component
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart.audit",
        name = "enabled",
        havingValue = "true"
)
public class MyLimiterUserProvider implements SmartRedisLimiterUserProvider {
    @Override
    public String getUserId() {
        // 从 SecurityContext 或其他上下文中获取
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
    // ... 其他方法
}
```

## 架构位置

```
smart-redis-limiter-core           ← Event、Record、Provider 接口
        ↑
        ├── smart-redis-limiter-starter                     (发布事件)
        └── smart-redis-limiter-audit-listener-starter      (订阅事件)
```

## 版本历史

### 1.0.0

初始版本：
- 定义 SmartRedisLimiterEvent 限流事件
- 定义 SmartRedisLimiterRecord 审计记录
- 定义 SmartRedisLimiterUserProvider 用户信息接口
- 定义 SmartRedisLimiterTraceIdProvider 链路追踪 ID 接口

## 相关模块

- **smart-redis-limiter-starter** - 智能限流主模块，发布限流事件
- **smart-redis-limiter-audit-listener-starter** - 限流审计监听器，订阅事件并处理

## 许可证

Apache License 2.0
