# CHANGELOG - simple-aksk-server-core 3.0.1

## 版本类型

Patch Release - Token 生命周期审计原因契约。

3.0.1 在不改变既有 Token 事件类型的前提下，为事件增加独立的业务来源维度。该版本是面向事件消费者的兼容性补充，不改变 AKSK Server 的授权数据模型、IAM 集成边界或 Token 生命周期事件的可靠投递语义。

## 新增

- 新增 `TokenEventCause`，通过稳定 `code` 标识 Token 生命周期事件的业务来源。
- `AbstractTokenEvent` 新增只读 `cause` 字段。
- `TokenRevokedEvent` 新增接收明确 `TokenEventCause` 的构造器。
- 保留既有事件构造器；未显式提供原因时，统一使用 `TokenEventCause.UNSPECIFIED`。

`TokenEventType` 继续表示发生的生命周期动作，`TokenEventCause` 仅补充触发来源。撤销来源不会扩展为新的事件类型。

## 原因值与触发边界

| 枚举值 | 稳定 code | 当前触发边界 |
|---|---|---|
| `UNSPECIFIED` | `unspecified` | 旧构造器和未明确来源的事件；`ISSUED`、`REMOVED`、`INTROSPECTED` 通常使用该值 |
| `OAUTH2_REVOKE` | `oauth2-revoke` | OAuth2 `/oauth2/revoke` 撤销流程 |
| `TOKEN_MANAGEMENT` | `token-management` | 管理端单个或批量撤销 Token |
| `APPLICATION_AUTHORIZATION_REPLACED` | `application-authorization-replaced` | 完整替换 Client 应用授权后失效原有活跃 Token |
| `APPLICATION_AUTHORIZATION_REVOKED` | `application-authorization-revoked` | 撤销 Client 应用授权后失效原有活跃 Token |
| `CLIENT_DISABLED` | `client-disabled` | 预留的未来语义；当前 Client 禁用流程不会发出 Token 撤销事件 |
| `CLIENT_DELETED` | `client-deleted` | 删除 Client 时失效其活跃 Token |
| `CLIENT_SECRET_RESET` | `client-secret-reset` | 重置 Client Secret 且明确选择撤销 Token 时失效其活跃 Token |

应用授权完整替换或撤销时，每个实际失效的活跃 Token 都会产生一条 `REVOKED` 事件。下游消费者必须按事件逐条处理，不应将一次授权变更假设为单条撤销事件。

## 兼容性

- `TokenEventType` 的稳定取值仍为 `ISSUED`、`REVOKED`、`REMOVED`、`INTROSPECTED`，既有按事件类型分发、统计或告警的消费者无需修改。
- `AbstractTokenEvent` 和 `TokenRevokedEvent` 的既有构造器保持可用，默认原因是 `UNSPECIFIED`。
- 原因值是附加维度，既有消费者不会因未读取 `cause` 而改变行为。
- `TokenEventCause#code` 是持久化、跨进程传输和审计检索的稳定标识；`description` 仅面向展示，不能作为程序分支依据。
- `TokenEventCause#fromCode` 忽略大小写，未知或空 code 返回 `null`；消费者应保留未知来源的兜底处理，避免未来新增原因导致失败。

## 升级说明

1. 将直接依赖坐标升级为：

   ```gradle
   implementation 'io.github.sure-zzzzzz:simple-aksk-server-core:3.0.1'
   ```

2. 仅按 `TokenEventType` 处理事件的现有代码无需改动，重新编译后即可继续使用。
3. 需要区分撤销来源时，在 `eventType == REVOKED` 后读取 `event.getCause()`；对于 `UNSPECIFIED`、未知 code 和未来原因保留兜底分支。
4. 需要存储或传输原因时使用 `cause.getCode()`，不要依赖中文展示文案。
5. 应用授权替换或撤销的审计、指标和下游处理需按实际失效 Token 的事件数量设计容量与幂等性。

## 安全与边界

- 本版本不新增应用授权内容、权限清单、操作者、IAM 主体、Secret 或认证材料到事件契约。
- 事件对象可能因既有内部实现携带原始认证值；消费者不得记录、输出、转发、持久化或重建任何原始凭据、密钥、请求认证信息、会话材料或完整认证响应。
- 独立审计实现应只向下游处理器提供脱敏数据；下游处理器不得依赖原始认证值。
- 本版本不引入 IAM 运行时依赖、不共享 IAM 数据库，也不改变 IAM 可选适配边界。
- 本版本不改变事件发布的事务、可靠投递或重试语义；需要可靠持久化与重试的消费者应由其自身实现相应能力。
