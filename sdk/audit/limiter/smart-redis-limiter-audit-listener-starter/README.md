# smart-redis-limiter-audit-listener-starter

监听 `smart-redis-limiter-starter` 发布的限流事件，生成审计记录并分发给业务处理器。

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-audit-listener-starter:1.0.0'
```

前提：项目中已引入 `smart-redis-limiter-starter`，它负责发布限流事件。

---

## 快速接入

### 第一步：引入依赖

```gradle
implementation 'io.github.surezzzzzz:smart-redis-limiter-starter:1.1.3'
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-audit-listener-starter:1.0.0'
```

### 第二步（可选）：实现审计处理器

默认已提供日志 Handler，开箱即用。如需持久化到数据库、ES 等，实现 `SmartRedisLimiterAuditHandler` 接口：

```java
@Component
public class MyAuditHandler implements SmartRedisLimiterAuditHandler {

    @Override
    public void handle(SmartRedisLimiterRecord record) {
        // 存数据库、发 MQ、写 ES，随你
        log.info("Limiter audit: passed={}, source={}, algorithm={}, key={}",
            record.isPassed(), record.getSource(), record.getAlgorithm(), record.getLimitKey());
    }
}
```

支持多个 Handler 同时工作，所有实现了 `SmartRedisLimiterAuditHandler` 的 Bean 都会被调用。

### 第三步（可选）：注入用户信息和 TraceId

实现 `SmartRedisLimiterUserProvider` 和 `SmartRedisLimiterTraceIdProvider`，审计记录中会自动填充用户信息和链路 ID：

```java
@Component
public class MyUserProvider implements SmartRedisLimiterUserProvider {

    @Override
    public String getClientId() {
        return SecurityContext.getClientId();
    }

    @Override
    public String getClientType() {
        return SecurityContext.getClientType();
    }

    @Override
    public String getUserId() {
        return SecurityContext.getUserId();
    }

    @Override
    public String getUsername() {
        return SecurityContext.getUsername();
    }
}

@Component
public class MyTraceIdProvider implements SmartRedisLimiterTraceIdProvider {

    @Override
    public String getTraceId() {
        return MDC.get("traceId");
    }
}
```

---

## 审计记录字段（SmartRedisLimiterRecord）

### 用户信息（来自 Provider）

| 字段 | 类型 | 说明 |
|------|------|------|
| `clientId` | String | 客户端 ID |
| `clientType` | String | 客户端类型 |
| `userId` | String | 用户 ID |
| `username` | String | 用户名 |

### 限流上下文

| 字段 | 类型 | 说明 |
|------|------|------|
| `limitKey` | String | 限流 Key |
| `keyStrategy` | String | Key 生成策略：method / path / ip / path-pattern |
| `algorithm` | String | 限流算法：fixed / sliding |
| `limitRules` | String | 限流规则（序列化字符串） |
| `passed` | boolean | 限流结果：true=通过，false=触发限流 |

### 来源信息

| 字段 | 类型 | 说明 |
|------|------|------|
| `source` | String | 来源：INTERCEPTOR / ASPECT |

### 请求信息（仅拦截器模式）

| 字段 | 类型 | 说明 |
|------|------|------|
| `requestUri` | String | 请求 URI |
| `httpMethod` | String | HTTP 方法 |
| `clientIp` | String | 客户端 IP |
| `matchedPathPattern` | String | 匹配到的路径模式 |

### 方法信息（仅注解模式）

| 字段 | 类型 | 说明 |
|------|------|------|
| `methodName` | String | 方法名 |
| `methodQualifiedName` | String | 方法全限定名 |

### 限流详情

| 字段 | 类型 | 说明 |
|------|------|------|
| `limit` | long | 限流阈值 |
| `remaining` | long | 剩余配额 |
| `resetAt` | long | 窗口重置时间（Unix 秒） |
| `durationNanos` | long | 限流检查耗时（纳秒） |

### 元数据

| 字段 | 类型 | 说明 |
|------|------|------|
| `timestamp` | Long | 事件时间戳 |
| `traceId` | String | 链路追踪 ID |
| `extra` | Map<String, String> | 扩展字段（来自 Context attributes，如 fallback 信息） |

---

## 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `io.github.surezzzzzz.sdk.audit.limiter.listener.handler.log.enabled` | Boolean | true | 是否启用默认日志 Handler |

关闭默认日志 Handler：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          limiter:
            listener:
              handler:
                log:
                  enabled: false
```

---

## 工作原理

1. `smart-redis-limiter-starter` 在限流检查后发布 `SmartRedisLimiterEvent`
2. `SmartRedisLimiterAuditEventListener` 监听事件，转换为 `SmartRedisLimiterRecord`
3. 使用 `@Async` 异步调用所有 `SmartRedisLimiterAuditHandler` 实现
4. 单个 Handler 异常不影响其他 Handler 和主流程
5. 默认日志 Handler 将审计记录输出到日志（通过时 INFO，限流时 WARN）

---

## 版本历史

### 1.0.0
- 初始版本
- 监听 `SmartRedisLimiterEvent`，转换为 `SmartRedisLimiterRecord`
- 支持多 Handler 机制
- 提供默认日志 Handler（默认开启，可通过配置关闭）
- 支持 `SmartRedisLimiterUserProvider` 注入用户信息
- 支持 `SmartRedisLimiterTraceIdProvider` 注入链路 ID
- Event → Record 完整字段映射（含 algorithm/limit/remaining/resetAt/durationNanos/extra）
- 异步处理，容错机制
