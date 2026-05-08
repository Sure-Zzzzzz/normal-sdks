# Changelog

## [1.0.3]

### ✨ 新增功能

#### 事件驱动审计能力

支持通过 Spring `ApplicationEvent` 机制发布限流事件，便于与外部审计系统集成。

**核心组件：**

| 组件 | 说明 |
|------|------|
| `SmartRedisLimiterEvent` | 限流事件，拦截器和注解模式共用 |
| `SmartRedisLimiterRecord` | 审计记录模型，包含用户、请求、限流结果等完整信息 |
| `SmartRedisLimiterUserProvider` | 用户信息提供者接口，从请求上下文提取用户身份 |
| `SmartRedisLimiterTraceIdProvider` | 链路追踪ID提供者接口，从请求上下文提取 traceId |

**事件发布时机：**

- 限流触发时（`passed=false`）：始终发布
- 限流通过时（`passed=true`）：仅当 `audit.logOnPass=true` 时发布

**使用示例：**

1. 注册 Provider 实现（可选）：
```java
@Component
public class MyUserProvider implements SmartRedisLimiterUserProvider {
    @Override
    public String getClientId() {
        // 从 SecurityContext 或请求头提取
        return getClientIdFromContext();
    }
    // ... 其他方法
}

@Component
public class MyTraceIdProvider implements SmartRedisLimiterTraceIdProvider {
    @Override
    public String getTraceId() {
        // 从 MDC 或请求头提取
        return MDC.get("traceId");
    }
}
```

2. 订阅限流事件：
```java
@Component
public class MyAuditListener {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @EventListener
    public void onLimitEvent(SmartRedisLimiterEvent event) {
        SmartRedisLimiterRecord record = SmartRedisLimiterRecord.builder()
                .clientId(userProvider.getClientId())
                .userId(userProvider.getUserId())
                .traceId(traceIdProvider.getTraceId())
                .limitKey(event.getLimitKey())
                .passed(event.isPassed())
                .source(event.getSource())
                // ... 其他字段
                .build();

        // 发送到 Kafka / Elasticsearch / 数据库等
        objectMapper.writeValueAsString(record);
    }
}
```

**配置项：**

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        limiter:
          redis:
            smart:
              audit:
                enabled: true       # 启用审计（事件始终发布，实际启用由外部 Listener 决定）
                log-on-pass: false  # 限流通过时也发布事件，默认 false
```

**事件字段说明：**

| 字段 | 说明 | 来源 |
|------|------|------|
| `source` | 触发来源 | `interceptor` / `aspect` |
| `passed` | 是否通过限流 | `true` / `false` |
| `limitKey` | 限流Key | 限流器生成 |
| `keyStrategy` | Key生成策略 | `method` / `path` / `path-pattern` / `ip` |
| `limitRules` | 命中的限流规则 | 触发限流时填入 |
| `requestUri` | 请求路径 | 仅 Interceptor 模式 |
| `httpMethod` | HTTP方法 | 仅 Interceptor 模式 |
| `clientIp` | 客户端IP | 仅 Interceptor 模式 |
| `matchedPathPattern` | 命中的路径模式 | 仅 Interceptor 模式 |
| `methodName` | 方法名 | 仅 Aspect 模式 |
| `methodQualifiedName` | 方法全限定名 | 仅 Aspect 模式 |
| `timestamp` | 事件时间戳 | 自动生成 |

### 🔧 改进

- 移除 embedded Redis 测试依赖，改为连接 localhost:6379
- 完善单元测试覆盖，端到端测试覆盖 Listener / Provider / Record 转换链

### 📝 兼容性

完全向后兼容 1.0.2 版本，`audit.enabled` 默认为 `false`，不影响现有行为。
