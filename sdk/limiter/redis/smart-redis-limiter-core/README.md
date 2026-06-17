# SmartRedisLimiter Core

[![Version](https://img.shields.io/badge/version-1.1.7-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

`smart-redis-limiter-core` 是 `smart-redis-limiter-starter` 的核心抽象层，定义了限流事件、审计记录模型、扩展接口和统一常量，供限流主模块和审计模块使用。

## 概述

本模块不含任何实现，仅定义核心契约：

- **事件与记录**：限流结果的载体
- **Provider 接口**：用户上下文扩展点
- **异常体系**：统一的异常定义
- **常量与枚举**：所有枚举和 Key 常量统一归口

## 核心组件

### 1. SmartRedisLimiterEvent

限流事件，在 Interceptor（拦截器模式）或 Aspect（注解模式）限流结果产生后发布。

```java
@Getter
public class SmartRedisLimiterEvent extends ApplicationEvent {

    private final String limitKey;
    private final String keyStrategy;    // Key生成策略：method/path/ip/path-pattern
    private final String algorithm;      // 限流算法：fixed/sliding
    private final String limitRules;
    private final boolean passed;        // true=通过，false=触发限流
    private final String source;        // INTERCEPTOR / ASPECT

    // Interceptor 专有字段
    private final String requestUri;
    private final String httpMethod;
    private final String clientIp;
    private final String matchedPathPattern;

    // Aspect 专有字段
    private final String methodName;
    private final String methodQualifiedName;

    private final Map<String, Object> attributes;

    // 限流详情（1.1.4新增）
    private final long limit;           // 限流阈值
    private final long remaining;       // 剩余配额
    private final long resetAt;         // 窗口重置时间（Unix 秒）
    private final long durationNanos;   // 限流检查耗时（纳秒）
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
    private String algorithm;           // 限流算法：fixed/sliding
    private String limitRules;
    private boolean passed;

    // 来源信息
    private String source;              // INTERCEPTOR / ASPECT

    // 请求信息（仅 Interceptor）
    private String requestUri;
    private String httpMethod;
    private String clientIp;
    private String matchedPathPattern;

    // 方法信息（仅 Aspect）
    private String methodName;
    private String methodQualifiedName;

    // 限流详情（1.1.4新增）
    private long limit;           // 限流阈值
    private long remaining;       // 剩余配额
    private long resetAt;         // 窗口重置时间（Unix 秒）
    private long durationNanos;   // 限流检查耗时（纳秒）

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

### 5. 异常体系

```
SmartRedisLimiterException                    ← 基础异常类
└── SmartRedisLimitExceededException         ← 限流超限异常
```

所有业务异常继承 `SmartRedisLimiterException`，提供统一的 `errorCode` 字段。

### 6. 常量与枚举

| 类 | 说明 |
|---|------|
| `SmartRedisLimiterConstant` | 统一常量类，包含配置前缀、默认值、来源标识、HTTP相关、Key模板、限流算法、错误码、异常消息等 |
| `SmartRedisLimiterRedisKeyConstant` | Redis Key 常量类，包含前缀、分隔符、时间单位后缀、滑动窗口标识等 |
| `SmartRedisLimiterKeyStrategy` | Key生成策略枚举：method / path / path-pattern / ip |
| `SmartRedisLimiterMode` | 限流模式枚举：annotation / interceptor / both |
| `SmartRedisLimiterFallbackStrategy` | 降级策略枚举：allow / deny |
| `SmartRedisLimiterContextAttribute` | 上下文属性枚举：requestPath / requestMethod / clientIp / matchedPathPattern / durationNanos / fallback / fallbackStrategy / precomputedKeyPart |
| `SmartRedisLimiterHttpMethod` | HTTP方法枚举：GET / POST / PUT / DELETE 等 |

> 所有枚举均提供 `fromCode()`、`isValid()`、`getAllCodes()`、`toString()` 标准方法。

## 依赖说明

本模块仅依赖 `spring-context`，无任何传递依赖。

## 使用方式

通常情况下，不需要直接依赖本模块，而是使用限流 starter：

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-starter:1.1.0'
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-audit-listener-starter:1.1.0'
```

如果需要自定义 Provider 或直接使用核心组件，可以直接依赖本模块：

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-core:1.1.7'
```

自定义 Provider 示例：

```java
@Component
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart",
        name = "enable",
        havingValue = "true"
)
public class MyLimiterUserProvider implements SmartRedisLimiterUserProvider {
    @Override
    public String getUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
    // ... 其他方法
}
```

## 架构位置

```
smart-redis-limiter-core               ← Event、Record、Provider、Exception、常量与枚举
        ↑
        ├── smart-redis-limiter-starter                     (发布事件)
        └── smart-redis-limiter-audit-listener-starter      (订阅事件)
```

## 版本历史

### 1.1.7

- `SmartRedisLimiterConstant` 新增 `EVENT_KEY_STRATEGY_CUSTOM_PREFIX = "custom:"` 常量，用于在限流事件 `keyStrategy` 字段中标识自定义 KeyProvider 来源（实际值为 `"custom:" + keyProviderName`）
- `SmartRedisLimiterContextAttribute` 新增 `PRECOMPUTED_KEY_PART` 枚举值（code = `precomputedKeyPart`），承载自定义 KeyProvider 预先计算好的 key 片段，避免算法层和事件构建器重复执行 KeyGenerator
- 配套 starter 1.1.4 KeyProvider 扩展点使用，仅契约扩展，无破坏性变更

### 1.1.6

- `SmartRedisLimiterFallbackStrategy` 恢复 `ALLOW_CODE` / `DENY_CODE` 公共常量，避免使用方注解中硬编码字符串

### 1.1.5

- 常量类由 `interface` 改为 `final class`（符合SDK开发规范）
- 枚举类补齐 `isValid()`、`getAllCodes()`、`toString()` 标准方法
- `SmartRedisLimiterHttpMethod` 字段 `method` → `code`，方法 `fromMethod()` → `fromCode()`
- `SmartRedisLimiterContextAttribute` 字段 `key` → `code`，新增 `fromCode()`、`isValid()`、`getAllCodes()`
- `SmartRedisLimiterException` 新增 `@Getter` 注解
- `SmartRedisLimiterConstant` 新增大量配置默认值常量、Key模板常量
- `SmartRedisLimiterFallbackStrategy` 移除冗余 `ALLOW_CODE`/`DENY_CODE` 常量

### 1.1.4

- Event 和 Record 新增限流详情字段：`limit`、`remaining`、`resetAt`、`durationNanos`
- ContextAttribute 新增 `DURATION_NANOS`、`FALLBACK`、`FALLBACK_STRATEGY` 常量

### 1.1.0

- 新增 `SmartRedisLimiterException` 基础异常类和 `SmartRedisLimitExceededException` 限流超限异常
- 新增 `SmartRedisLimiterEvent` 和 `SmartRedisLimiterRecord` 的 `algorithm` 字段
- 新增限流算法常量：`ALGORITHM_FIXED`、`ALGORITHM_SLIDING`
- 新增滑动窗口相关常量：`SUFFIX_SLIDING_WINDOW`
- 新增错误码常量：`ERROR_CODE_RATE_LIMIT_EXCEEDED`
- 将枚举和常量类统一移入 core：Key生成策略、限流模式、降级策略、上下文属性、HTTP方法、Redis Key常量

### 1.0.1

- 新增 `SmartRedisLimiterConstant` 统一常量定义

### 1.0.0

- 定义 `SmartRedisLimiterEvent` 限流事件
- 定义 `SmartRedisLimiterRecord` 审计记录
- 定义 `SmartRedisLimiterUserProvider` 用户信息接口
- 定义 `SmartRedisLimiterTraceIdProvider` 链路追踪 ID 接口

## 相关模块

- **smart-redis-limiter-starter** - 智能限流主模块，发布限流事件
- **smart-redis-limiter-audit-listener-starter** - 限流审计监听器，订阅事件并处理

## 许可证

Apache License 2.0
