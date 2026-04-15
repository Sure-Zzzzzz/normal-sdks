# simple-aksk-server-audit-listener-starter

监听 AKSK Server 端 Token 生命周期事件，生成审计记录并分发给业务处理器。

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-audit-listener-starter:1.0.0'
```

前提：项目中已引入 `simple-aksk-server-starter`，它负责发布 Token 生命周期事件。

---

## 快速接入

### 第一步：实现审计处理器

实现 `ServerTokenAuditHandler` 接口，这是唯一必须实现的接口。有了它，监听器才会自动注册。

```java
@Component
public class MyServerTokenAuditHandler implements ServerTokenAuditHandler {

    @Override
    public void handle(ServerTokenAuditRecord record) {
        // 存数据库、发 MQ、写日志，随你
        log.info("Token audit: type={}, client={}, user={}",
            record.getEventType(), record.getClientId(), record.getUserId());
    }
}
```

支持多个 Handler 同时工作，所有实现了 `ServerTokenAuditHandler` 的 Bean 都会被调用。

### 第二步（可选）：关闭默认日志 Handler

默认日志 Handler 是开启的，生产环境实现了自己的 Handler 后可以关闭：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        audit:
          aksk:
            server:
              listener:
                handler:
                  log:
                    enabled: false
```

---

## 监听的事件

| 事件类 | 触发时机 | eventType |
|--------|---------|-----------|
| `TokenIssuedEvent` | `/oauth2/token` 颁发成功 | `ISSUED` |
| `TokenRevokedEvent` | `/oauth2/revoke` 主动撤销 | `REVOKED` |
| `TokenRemovedEvent` | Spring AS 内部删除（过期清理等） | `REMOVED` |
| `TokenIntrospectedEvent` | `/oauth2/introspect` 自省 | `INTROSPECTED` |

---

## 审计记录字段（ServerTokenAuditRecord）

| 字段 | 类型 | 说明 |
|------|------|------|
| `eventType` | `TokenEventType` | 事件类型：ISSUED / REVOKED / REMOVED / INTROSPECTED |
| `eventTime` | `Instant` | 事件发生时间 |
| `clientId` | `String` | 客户端 ID |
| `clientType` | `String` | 客户端类型：platform / user |
| `userId` | `String` | 用户 ID（用户级才有） |
| `username` | `String` | 用户名（用户级才有） |
| `scopes` | `Set<String>` | 授权范围 |
| `tokenValue` | `String` | Token 值 |
| `issuedAt` | `Instant` | Token 颁发时间 |
| `expiresAt` | `Instant` | Token 过期时间 |
| `active` | `Boolean` | token 是否有效（仅 INTROSPECTED 有值，其余为 null） |

---

## 配置项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `io.github.surezzzzzz.sdk.audit.aksk.server.listener.handler.log.enabled` | Boolean | true | 是否启用默认日志 Handler |

---

## 工作原理

1. `simple-aksk-server-starter` 在 Token 生命周期各阶段发布 `AbstractTokenEvent` 子类事件
2. `ServerTokenAuditEventListener` 监听所有 `AbstractTokenEvent`，转换为 `ServerTokenAuditRecord`
3. 使用 `@Async` 异步调用所有 `ServerTokenAuditHandler` 实现
4. 单个 Handler 异常不影响其他 Handler 和主流程

---

## 版本历史

### 1.0.0
- 初始版本
- 支持监听 Token 颁发、撤销、删除、自省四类事件
- 支持多 Handler 机制
- 提供默认日志 Handler（默认开启）
- 异步处理，容错机制
