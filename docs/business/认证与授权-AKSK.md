# 认证与授权-AKSK

服务身份认证与授权：OAuth2 授权服务器（JWE Token、平台级 AKP / 用户级 AKU 双层凭证、应用授权投影），以及客户端与资源服务 Provider。管**服务身份**，与管人员身份的 [IAM](认证与授权-IAM.md) 独立部署、可协作。

从部署到接入的完整导引见 [AKSK 用户手册](../../sdk/auth/aksk/USER_MANUAL.md)。

## Server 3.x

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-aksk-core](../../sdk/auth/aksk/simple-aksk-core) | 3.0.2 | — | AKSK 核心库 | [README](../../sdk/auth/aksk/simple-aksk-core/README.md) |
| [simple-aksk-server-core](../../sdk/auth/aksk/server/simple-aksk-server-core) | 3.0.4 | — | Server 核心库 | [README](../../sdk/auth/aksk/server/simple-aksk-server-core/README.md) |
| [simple-aksk-server-starter](../../sdk/auth/aksk/server/simple-aksk-server-starter) | 3.2.2 | — | 认证服务器（OAuth2/JWE/Redis 必需/应用授权投影/精确 API permission+DATA/SQL 范式化） | [README](../../sdk/auth/aksk/server/simple-aksk-server-starter/README.md) |

**Server 3.x 版本对应**：

| server-starter | server-core | aksk-core | 说明 |
|----------------|-------------|-----------|------|
| 3.2.2 | 3.0.4 | 3.0.2 | SQL 范式化（schema 全量 + `V{from}__to__V{to}` 迁移命名）、降级响应头与候选版本策略 |
| 3.2.1 | 3.0.3 | 3.0.1 | OWNER_INHERITED 归属收口与凭证代办天花板（方案 A：投影是唯一事实源） |
| 3.1.1 | 3.0.2 | 3.0.0 | 过期 Token 定时清理（分布式锁互斥+分批删除），`access_token_expires_at` 索引补齐 |
| 3.1.0 | 3.0.1 | 3.0.0 | `/api` 鉴权移交公共资源层 1.1.1；cache 2.2.0 / limiter 2.1.0 对齐 route 1.2.2；MySQL 切 mysql-route 接管 |
| 3.0.1 | 3.0.1 | 3.0.0 | 内省时间整秒截断（修间歇 403）、Token 事件 `TokenEventCause` 契约 |
| 3.0.0 | 3.0.0 | 3.0.0 | 应用授权自闭环；Token 按已准入授权快照签发与内省 |

> Server 3.x 发布范围仅上表三项；3.x 线审计用独立 3.0.0 listener，`server-audit-listener:2.0.1` 属历史扩展。

## Client 3.x

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-aksk-client-core](../../sdk/auth/aksk/client/simple-aksk-client-core) | 3.0.0 | 1.0.0 | Client 核心库（`TokenManager`/`SecurityContextProvider` 契约） | [README](../../sdk/auth/aksk/client/simple-aksk-client-core/README.md) |
| [simple-aksk-redis-token-manager](../../sdk/auth/aksk/client/redis/simple-aksk-redis-token-manager) | 3.0.1 | — | Redis Token 管理器（L1+L2 二级缓存） | [README](../../sdk/auth/aksk/client/redis/simple-aksk-redis-token-manager/README.md) |
| [simple-aksk-feign-redis-client-starter](../../sdk/auth/aksk/client/redis/simple-aksk-feign-redis-client-starter) | 3.0.1 | 1.0.0 | Feign 客户端（Redis） | [README](../../sdk/auth/aksk/client/redis/simple-aksk-feign-redis-client-starter/README.md) |
| [simple-aksk-resttemplate-redis-client-starter](../../sdk/auth/aksk/client/redis/simple-aksk-resttemplate-redis-client-starter) | 3.0.1 | 1.0.0 | RestTemplate 客户端（Redis） | [README](../../sdk/auth/aksk/client/redis/simple-aksk-resttemplate-redis-client-starter/README.md) |

**Client 版本兼容**：

| feign / resttemplate | redis-token-manager | client-core | 说明 |
|----------------------|---------------------|-------------|------|
| 3.0.1 | 3.0.1 | 3.0.0 | smart-cache 2.2.0 对齐 route 1.2.2，lock 1.2.2 让位语义；token-manager 由 `api` 收紧为 `implementation` |
| 3.0.0 | 3.0.0 | 3.0.0 | Client 3.0：对接 Server 3.0 协议链路 |
| 2.0.1 | 2.0.1 | 2.0.0 | SHA-256 cacheKey 防多租户碰撞（历史 2.x） |
| 2.0.0 | 2.0.0 | 2.0.0 | Client 2.x 初始链路（历史 2.x） |

## 公共资源层 + Provider（3.x）

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-resource-server-core](../../sdk/auth/resource/simple-resource-server-core) | 1.1.1 | 1.0.0 | 公共资源层核心（认证契约/`BearerResourceCredential`/`ResourceAccessEvent`，`api` 传递授权 core） | [README](../../sdk/auth/resource/simple-resource-server-core/README.md) |
| [simple-resource-server-starter](../../sdk/auth/resource/simple-resource-server-starter) | 1.1.1 | 1.0.0 | 公共资源层（唯一 Bearer 入口/`kid` 来源路由/统一 401 403/精确 API permission/公共访问事件） | [README](../../sdk/auth/resource/simple-resource-server-starter/README.md) |
| [simple-aksk-resource-core](../../sdk/auth/aksk/resource/simple-aksk-resource-core) | 3.0.0 | — | AKSK Resource 协议核心（内省 claim 契约） | [README](../../sdk/auth/aksk/resource/simple-aksk-resource-core/README.md) |
| [simple-aksk-resource-server-starter](../../sdk/auth/aksk/resource/simple-aksk-resource-server-starter) | 3.0.1 | 1.0.0 | AKSK Provider（只注册 Adapter 不建私有安全链；内省+本地缓存与 stale fallback 边界） | [README](../../sdk/auth/aksk/resource/simple-aksk-resource-server-starter/README.md) |
| [simple-aksk-resource-audit-listener-starter](../../sdk/audit/aksk/simple-aksk-resource-audit-listener-starter) | 3.0.0 | 1.0.0 | 公共事件审计（AKSK 来源过滤/`@Async` Handler 分发） | [README](../../sdk/audit/aksk/simple-aksk-resource-audit-listener-starter/README.md) |
| [simple-data-permission-spring-mvc-starter](../../sdk/auth/data-permission/simple-data-permission-spring-mvc-starter) | 1.0.1 | 1.0.0 | DATA 访问计划评估（`@DataPermissionOperation`/`@CurrentDataAccessPlan`，通用模块） | [README](../../sdk/auth/data-permission/simple-data-permission-spring-mvc-starter/README.md) |

**Resource 版本兼容**：

| resource-server（公共） | aksk-resource-server | audit-listener | data-permission | 说明 |
|------------------------|----------------------|----------------|-----------------|------|
| 1.1.1 | 3.0.1 | 3.0.0 | 1.0.1 | 公共 `/api` 鉴权链固定 STATELESS（零 Set-Cookie，修载体歧义间歇 401） |
| 1.1.0 | 3.0.1 | 3.0.0 | 1.0.1 | `ResourceAccessEvent` 迁入 core；传递授权 core 时钟容差 |
| 1.0.2 | 3.0.0 | - | 1.0.0 | AKSK 3.0 资源链路初始版 |

**资源服务协作**：IAM 与 AKSK 可独立运行；同一业务 API 需同时接受人员和服务身份时，在资源服务组合公共资源层及两个 Provider。接入边界与四步操作序见 [IAM 与 AKSK 协作接入](../../sdk/auth/README.IAM-AKSK协作.md)。

## 已封版：2.x 与 1.x

> 2.x / 1.x 均已封版，不再接受功能、缺陷修复或常规维护发布；最终坐标与接入方式固化在 [USER_MANUAL.2.x.md](../../sdk/auth/aksk/USER_MANUAL.2.x.md) 与各模块 `README.2.x.md` 冻结快照。

**2.x 终版坐标**：core 2.0.0 / server-core 2.0.3 / server-starter 2.0.3 / server-audit 2.0.1；client 全家 2.0.1（core 2.0.0）；resource-core 2.0.0 / resource-server 2.0.1 / resource-audit 2.0.0。Redis 必需化 + OAuth2 端点限流 + Client 两级缓存为 2.x 后期形态。

**1.x 终版坐标**：server-core 1.0.4 / server-starter 1.1.3 / client 全家 1.1.0（core 1.0.1）/ httpsession 系 1.0.1 / resource-core 1.0.3 / resource-server 1.0.6 / security-context 1.0.3。

> Security Context、HttpSession Client、metrics starter 与 Client Demo 仅保留历史或测试用途，不生成 3.x 发布物。

## 核心特性

- OAuth2 标准协议（Authorization Server）；双层级凭证（平台级 AKP / 用户级 AKU）
- JWE Token（A256GCMKW + A256GCM，scope 不裸奔）
- Redis 必需基础设施：Token 缓存、撤销同步、多实例 L1 失效广播
- OAuth2 端点限流（`/oauth2/token`、`/introspect`、`/revoke`，按 clientId provider 计数）
- 公共资源层：统一 Bearer 入口与 `kid` 路由，认证失败不回退；AKSK Provider 内省校验；精确 API permission 与 DATA 访问计划
