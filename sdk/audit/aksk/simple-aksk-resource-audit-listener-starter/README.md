# simple-aksk-resource-audit-listener-starter

> **1.x 封版文档**：如果你使用的是 1.x 版本，请查看 [README.1.x.md](README.1.x.md)。

监听 AKSK Resource 端访问事件，生成审计记录并分发给业务处理器。

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-audit-listener-starter:2.0.0'
```

前提：项目中已引入 `simple-aksk-resource-server-starter`，它负责发布 `AkskAccessEvent` 事件（INTROSPECT 模式）。

---

## 快速接入

### 第一步：实现审计处理器

实现 `AkskAuditHandler` 接口，这是唯一必须实现的接口。有了它，监听器才会自动注册。

```java
@Component
public class MyAkskAuditHandler implements AkskAuditHandler {

    @Override
    public void handle(AkskAuditRecord record) {
        // 存数据库、发 MQ、写日志，随你
        log.info("AKSK audit: user={}, uri={}, method={}, source={}",
            record.getUsername(), record.getRequestUri(),
            record.getHttpMethod(), record.getSource());
    }
}
```

支持多个 Handler 同时工作，所有实现了 `AkskAuditHandler` 的 Bean 都会被调用。

### 第二步（可选）：启用默认日志 Handler

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          aksk:
            resource:
              listener:
                handler:
                  log:
                    enabled: true
```

启用后会自动打印审计日志：
```
INFO  AKSK_RESOURCE_AUDIT - AkskAuditRecord(clientId=xxx, userId=xxx, requestUri=/api/test, ...)
```

### 第三步（可选）：提供链路追踪 ID

```java
@Component
public class MyAkskAuditTraceIdProvider implements AkskAuditTraceIdProvider {

    @Override
    public String getTraceId() {
        HttpServletRequest request =
            ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return request.getHeader("X-Trace-Id");
    }
}
```

不实现此接口，审计记录中 `traceId` 字段为 null，其他字段正常。

---

## 审计记录字段（AkskAuditRecord）

| 字段 | 类型 | 说明 |
|------|------|------|
| `clientId` | `String` | 客户端 ID |
| `clientType` | `String` | 客户端类型：platform / user |
| `userId` | `String` | 用户 ID |
| `username` | `String` | 用户名 |
| `roles` | `String` | 角色 |
| `scope` | `String` | 权限范围 |
| `requestUri` | `String` | 请求 URI |
| `httpMethod` | `String` | HTTP 方法 |
| `remoteAddr` | `String` | 来源 IP |
| `userAgent` | `String` | User-Agent |
| `timestamp` | `Long` | 时间戳 |
| `source` | `String` | 来源类型：introspect |
| `traceId` | `String` | 链路追踪 ID |
| `context` | `Map<String, String>` | 完整上下文 |

---

## 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `io.github.surezzzzzz.sdk.audit.aksk.resource.listener.handler.log.enabled` | Boolean | false | 是否启用默认日志 Handler |

---

## 工作原理

1. 认证模块在请求通过后发布 `AkskAccessEvent` 事件
2. `AkskAuditEventListener` 监听事件，转换为 `AkskAuditRecord`
3. 使用 `@Async` 异步调用所有 `AkskAuditHandler` 实现
4. 单个 Handler 异常不影响其他 Handler 和主流程

---

## 版本历史

### 2.0.0

升级依赖至 resource-core 2.0.0、resource-server-starter 2.0.0；移除 security-context-starter 依赖；移除 Header 认证支持，仅支持 INTROSPECT 模式。

### 1.0.0
- 初始版本
- 支持监听 `AkskAccessEvent` 事件（Header 认证 / JWT 认证）
- 支持多 Handler 机制
- 提供默认日志 Handler（默认关闭）
- 支持自定义链路追踪 ID 提供者
- 异步处理，容错机制
