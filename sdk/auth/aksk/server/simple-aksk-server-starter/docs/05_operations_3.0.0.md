# Simple AKSK Server Starter 3.0.0 运维手册

## 运行边界

- MySQL 保存 Client、Token 与应用授权投影；Redis 保存 Token 缓存、缓存失效广播和 OAuth2 限流状态。
- 所有实例必须使用相同的 `redis.token.me`、limiter `me` 与 cache `me`，并使用同一套可访问的 JWE 密钥材料。
- Redis 不可用不是支持的降级模式。Token 生命周期与多实例一致性必须按部署监控与处置。
- `/oauth2/introspect` 仅允许已认证 Client 调用；不要记录 Token、认证头、Client Secret、Cookie 或完整响应。

## 应用授权运维

- `scope` 只决定 OAuth 请求范围，`security_context` 只是调用方业务上下文；两者都不是 API 或 DATA 授权来源。
- `aksk_authorization` 是服务端维护的可信快照。管理更新必须通过 Admin 或携带精确 permission 与 `DataAccessPlan` 的机器 REST。
- 替换或撤销应用授权会撤销该 Client 的活跃 Token。操作后应以状态计数和 active/inactive 结果确认，不记录 Token 值。
- 需要逐 Token 审计时，显式引入可选的 `simple-aksk-server-audit-listener-starter:3.0.0`。每个实际失效 Token 产生一条 `REVOKED` 记录，`cause` 区分 `APPLICATION_AUTHORIZATION_REPLACED` 与 `APPLICATION_AUTHORIZATION_REVOKED`；监听器仅在事务成功提交后调用 Handler，审计记录不包含 Token 原文。
- 并发替换返回 HTTP 409 时，读取当前投影后基于新版本重新提交完整请求；不要重放旧请求体覆盖他人成功变更。

## 常见处置

| 现象 | 检查与处置 |
|---|---|
| Token 不能签发 | 检查 Client 是否启用，以及应用授权是否存在、启用、已准入且未撤销；不要通过扩大 scope 绕过。 |
| 内省 inactive | 检查 Token 是否已撤销或过期，再检查当前投影与 Client 状态。完整替换/撤销后的历史 Token 应保持 inactive。 |
| 管理 REST 返回 403 | 同时检查精确 API permission 和目标资源的 `DataAccessPlan`；应用授权替换/撤销还需要 Token update 范围。 |
| 管理 REST 返回 409 | 发生乐观锁竞争；重新读取当前投影，人工确认后提交新的完整替换。 |
| 多实例撤销感知延迟 | 检查 Redis 连通性、Pub/Sub 与三个 `me` 标识的一致性。 |

## IAM 可选协作

AKSK Server 不依赖 IAM 才能签发或内省 Token。需要 IAM 人员身份与 AKSK 服务身份访问同一业务 API 时，在资源服务组合两个独立认证适配器：认证源只选择一个，权限与 DATA 文档不合并、不回退；IAM 不读取或写入 AKSK 数据库。真实跨进程夹具验收由部署侧提供受保护的临时清单与凭据，不能从源码或本机配置中收集。

## 安全日志

日志可以包含状态、数量、非敏感 Client 标识和操作结果。禁止写入 Access Token、Token 前缀、Authorization/Basic 认证头、Client Secret、管理员口令、会话 Cookie、JWE 私钥/加密密钥或完整 Token/授权响应。
