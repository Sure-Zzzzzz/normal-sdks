# simple-aksk-server-core

[![Version](https://img.shields.io/badge/version-3.0.1-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

AKSK Server 的共享契约模块，提供服务端配置、常量、Redis Key 规则和 Token 生命周期事件。业务通常通过 `simple-aksk-server-starter` 使用这些契约；只有需要直接订阅、转换或持久化 Token 生命周期事件的扩展模块，才需要直接依赖本模块。

> **历史版本**：2.x 最终发布版本为 2.0.3，详见 [README.2.x.md](README.2.x.md)；1.x 文档见 [README.1.x.md](README.1.x.md)。

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-core:3.0.1'
```

`simple-aksk-server-starter` 通过 `api` 传递引入本模块。仅使用 Server Starter 的应用通常不需要重复声明；独立审计、指标或集成模块可直接声明该坐标。

---

## 核心能力

### 服务端配置

`SimpleAkskServerProperties` 提供 JWT、Redis、Admin 和 OAuth2 限流配置模型，供 `simple-aksk-server-starter` 与扩展模块共同使用。

- JWT：密钥标识、有效期、密钥材料、JWE AES-256 加密密钥与 `security_context` 大小限制。
- Redis：Token 命名空间配置。
- Admin：本地管理页面开关、管理员账户及会话时长。
- 限流：OAuth2 Token、Introspect、Revoke 端点的开关、算法、降级策略、Key 策略和规则。

### 服务端常量

`SimpleAkskServerConstant` 定义服务端公共契约：

- JWT Claim、OAuth2 参数与响应字段、JWE 算法及默认配置。
- 管理 API 的应用编码、资源、动作、权限和数据权限维度。
- `JWT_CLAIM_APPLICATION_AUTHORIZATION` 复用 `simple-aksk-core` 的应用授权 Claim，保证签发、管理鉴权和内省消费同一契约。

### Redis Key 工具

`RedisKeyHelper` 统一构建 AKSK 授权记录、Token 与多实例命名空间的 Redis Key。

---

## Token 生命周期事件

Token 生命周期事件用于订阅签发、撤销、删除和自省等动作。事件采用两个正交维度：

- `TokenEventType` 表示**发生了什么动作**，是消费者进行第一层分发、统计和告警的稳定维度。
- `TokenEventCause` 表示**由什么业务动作触发**，用于需要进一步区分撤销来源的消费者。

不要为不同撤销来源扩展新的事件类型。先按 `eventType` 判断生命周期动作，再在需要时读取 `cause`，可保持已有消费者兼容。

### 事件类型

| 枚举值 | 稳定 code | 当前含义 |
|---|---|---|
| `ISSUED` | `issued` | OAuth2 Token 签发 |
| `REVOKED` | `revoked` | Token 被撤销或失效 |
| `REMOVED` | `removed` | Spring Authorization Server 内部删除授权记录 |
| `INTROSPECTED` | `introspected` | OAuth2 Token 自省；`TokenIntrospectedEvent#isActive()` 表示本次请求的即时有效性结果 |

`TokenEventType` 的四个取值是稳定公共契约。业务来源不通过新增事件类型表达。

### 事件原因与当前触发边界

`TokenEventCause` 的 `code` 可用于持久化、跨进程传输和审计检索；`description` 仅用于展示，不能作为程序分支依据。`fromCode` 忽略大小写，未知或空 code 返回 `null`，调用方应自行保留兜底处理。

| 枚举值 | 稳定 code | 展示说明 | 当前触发边界 |
|---|---|---|---|
| `UNSPECIFIED` | `unspecified` | 未指定 | 旧构造器、未明确来源的事件；`ISSUED`、`REMOVED`、`INTROSPECTED` 通常使用该值 |
| `OAUTH2_REVOKE` | `oauth2-revoke` | OAuth2 撤销 | OAuth2 `/oauth2/revoke` 撤销流程 |
| `TOKEN_MANAGEMENT` | `token-management` | Token 管理撤销 | 管理端单个或批量撤销 Token |
| `APPLICATION_AUTHORIZATION_REPLACED` | `application-authorization-replaced` | 应用授权完整替换 | 完整替换 Client 的应用授权后，失效原有活跃 Token |
| `APPLICATION_AUTHORIZATION_REVOKED` | `application-authorization-revoked` | 应用授权撤销 | 撤销 Client 的应用授权后，失效原有活跃 Token |
| `CLIENT_DISABLED` | `client-disabled` | Client 禁用 | 为后续语义预留；当前 Client 禁用流程不会发出 Token 撤销事件 |
| `CLIENT_DELETED` | `client-deleted` | Client 删除 | 删除 Client 时失效其活跃 Token |
| `CLIENT_SECRET_RESET` | `client-secret-reset` | Client Secret 重置 | 重置 Client Secret 且明确选择撤销 Token 时失效其活跃 Token |

应用授权完整替换或撤销会对**每个实际失效的活跃 Token**分别产生一条 `REVOKED` 事件。消费者应按事件逐条处理，不要假设一次应用授权操作只对应一条撤销事件。

### 最小消费示例

以下示例保持事件类型作为第一层分发，仅在撤销事件中细分业务原因；示例只处理非敏感元数据，不输出、持久化或转发 Token 与认证材料。

```java
import io.github.surezzzzzz.sdk.auth.aksk.server.event.AbstractTokenEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventCause;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventType;
import org.springframework.context.event.EventListener;

public class TokenLifecycleMetricsListener {

    @EventListener
    public void onTokenEvent(AbstractTokenEvent event) {
        if (event.getEventType() != TokenEventType.REVOKED) {
            return;
        }

        TokenEventCause cause = event.getCause();
        if (cause == TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED
                || cause == TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED) {
            recordApplicationAuthorizationRevocation(event.getClientId(), cause.getCode());
        }
    }

    private void recordApplicationAuthorizationRevocation(String clientId, String causeCode) {
        // 仅记录非敏感业务标识与稳定原因编码。
    }
}
```

### 兼容性与升级

- 3.0.1 是兼容性 patch release：`TokenEventType` 的四个稳定取值不变。
- `AbstractTokenEvent` 与 `TokenRevokedEvent` 的既有构造器保留，未传入原因时统一使用 `UNSPECIFIED`。
- 既有消费者如果只按 `eventType` 分发、统计或告警，无需修改逻辑。
- 需要区分撤销来源的消费者，应在 `eventType == REVOKED` 后读取 `cause`；面对 `UNSPECIFIED`、未知 code 或未来新增原因时必须保留兜底处理。
- 需要保存或传输原因时使用稳定 `code` 或枚举值，不要使用可展示的中文 `description`。

### 安全边界

Token 生命周期事件属于内部服务端事件契约，不是认证材料分发通道。事件消费者不得记录、输出、转发、持久化或重建任何原始凭据、密钥、请求认证信息、会话材料或完整认证响应。

为兼容既有生命周期事件实现，事件对象可能包含内部认证值；这不构成对消费者处理或传播该值的授权。独立审计实现应仅消费脱敏记录，并确保下游 Handler 永远不依赖原始认证值。

事件不会新增或携带应用授权内容、权限清单、操作者、IAM 主体或 Secret。IAM 集成仍应通过可选适配器或稳定的服务契约完成，本模块不引入 IAM 运行时依赖。

---

## 版本历史

### 3.0.1

新增 `TokenEventCause`，使撤销审计能够区分 OAuth 端点、Token 管理、应用授权变更和 Client 生命周期来源；保留既有事件类型与构造器兼容性。详见 [CHANGELOG.3.0.1.md](CHANGELOG.3.0.1.md)。

### 3.0.0

对齐 AKSK Server 3.0 的应用授权与数据权限管理契约，移除不再支持的匿名 introspect 配置。详见 [CHANGELOG.3.0.0.md](CHANGELOG.3.0.0.md)。

### 2.0.3 (2026-06-17)

删除 Redis 可选开关，新增 OAuth2 限流配置模型。详见 [CHANGELOG.2.0.3.md](CHANGELOG.2.0.3.md)。

### 2.0.0

新增 JWE Token 基础设施契约。详见 [CHANGELOG.2.0.0.md](CHANGELOG.2.0.0.md)。

---

## 许可证

Apache License 2.0
