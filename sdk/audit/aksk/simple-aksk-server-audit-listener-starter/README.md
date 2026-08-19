# simple-aksk-server-audit-listener-starter

[![Version](https://img.shields.io/badge/version-3.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

AKSK Server 的可选 Token 生命周期审计扩展。它监听已提交的 Server 事件，转换为不含 Token 原文的审计记录并分发给业务处理器。

> **独立可选模块**：本模块不被 `simple-aksk-server-starter` 反向依赖。需要审计时由应用显式引入；不引入 IAM 运行时依赖，也不共享 IAM 数据库。
>
> **历史版本**：2.x 最终版本为 2.0.1，冻结快照见 [README.2.x.md](README.2.x.md)。

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-audit-listener-starter:3.0.0'
```

前提是项目已经引入 `simple-aksk-server-starter:3.0.0`，它负责发布 Token 生命周期事件。

## 快速接入

实现 `ServerTokenAuditHandler`；可同时存在多个 Handler。

```java
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MyServerTokenAuditHandler implements ServerTokenAuditHandler {

    @Override
    public void handle(ServerTokenAuditRecord record) {
        log.info("Token audit: type={}, cause={}, client={}",
                record.getEventType(), record.getCause(), record.getClientId());
    }
}
```

默认日志 Handler 已启用。应用自行落库、投递或对接审计系统后，可关闭默认 Handler：

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

## 事件与原因

| eventType | 触发场景 | cause |
|---|---|---|
| `ISSUED` | Token 签发 | `UNSPECIFIED` |
| `REVOKED` | OAuth2 撤销端点 | `OAUTH2_REVOKE` |
| `REVOKED` | 管理端单个或批量撤销 | `TOKEN_MANAGEMENT` |
| `REVOKED` | 完整替换应用授权 | `APPLICATION_AUTHORIZATION_REPLACED` |
| `REVOKED` | 撤销应用授权 | `APPLICATION_AUTHORIZATION_REVOKED` |
| `REVOKED` | 删除 Client | `CLIENT_DELETED` |
| `REVOKED` | 重置 Client Secret 且选择撤销 Token | `CLIENT_SECRET_RESET` |
| `REVOKED` | Client 禁用 | `CLIENT_DISABLED`（当前仅为 Core 契约预留，Server 3.0 禁用流程不发布该撤销事件） |
| `REMOVED` | Spring Authorization Server 内部删除 | `UNSPECIFIED` |
| `INTROSPECTED` | Token 自省 | `UNSPECIFIED` |

完整替换或撤销应用授权时，Server 会为该 Client 每个实际失效的活跃 Token 产生一条 `REVOKED` 事件；原因仅说明该次失效的业务来源，不携带授权内容、权限清单、操作者、IAM 主体、Secret 或 Token 原文。

## 提交后语义与可靠性边界

监听器使用 `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`：

- 业务操作处在事务中时，仅在事务成功提交后调用 Handler；回滚不会产生审计记录。
- 非事务事件仍可兼容消费。
- Handler 按顺序独立调用；某个 Handler 失败不会阻断已提交的业务操作或后续 Handler。
- 该监听器是提交后尽力消费，不提供可靠消息投递或重试承诺。需要持久化重试、Outbox 或 MQ 保障时，由具体 Handler 自行实现。

## 审计记录字段

| 字段 | 说明 |
|---|---|
| `eventType` | 生命周期事件类型：ISSUED / REVOKED / REMOVED / INTROSPECTED |
| `cause` | 事件业务来源；旧事件构造器为 `UNSPECIFIED` |
| `eventTime` | 事件发生时间 |
| `clientId` / `clientType` | Client 标识与类型 |
| `userId` / `username` | 用户级 Client 的归属信息（可空） |
| `scopes` | OAuth 授权范围 |
| `issuedAt` / `expiresAt` | Token 生命周期时间 |
| `active` | 自省结果，仅 INTROSPECTED 事件有值 |
| `tokenValue` | 历史兼容字段；3.0 listener 始终传递 `null`，Handler 不得依赖或回填 Token 原文 |

日志和 Handler 不得记录 Token、Token 前缀、Authorization/Basic 认证头、Client Secret、Cookie、完整 OAuth 响应或完整 introspection 响应。

## 版本历史

### 3.0.0

- 对齐 `simple-aksk-server-core:3.0.1` 与 `simple-aksk-server-starter:3.0.0` 的 Token 原因契约。
- 审计处理改为成功提交后同步投递，事务回滚不产生记录。
- 审计 record 新增 `cause`，不再向 Handler 下发 Token 原文。

### 2.0.1

依赖跟进 `simple-aksk-server-core:2.0.3`，审计接口和记录字段无变更。详见 [CHANGELOG.2.0.1.md](CHANGELOG.2.0.1.md)。
