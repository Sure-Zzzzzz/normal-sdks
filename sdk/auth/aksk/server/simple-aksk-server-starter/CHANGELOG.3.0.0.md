# CHANGELOG - simple-aksk-server-starter 3.0.0

## 版本类型

Major Release - 应用授权自闭环与 AKSK / IAM 可选协作边界

## 变更概述

3.0.0 将 AKSK Server 的应用授权从 OAuth Scope 和运行时上下文中分离出来。Server 自主维护可信的应用授权投影，并在 Token 签发和内省时按当前有效投影生成 `aksk_authorization` 快照。IAM 可在未来通过可选 adapter 或受认证 REST contract 集成，不共享数据库，也不成为 AKSK Token 签发的运行时依赖。

## 新增

### 应用授权投影与生命周期

- 新增 `aksk_application_authorization` 持久化投影，按 Client 保存应用编码、角色、页面权限、精确 API permission、`DataGrantDocument`、准入状态、撤销状态和版本信息。
- 新 Client 只完成 OAuth Client 注册，不自动授予应用授权。
- 首次配置默认未准入；仅已启用、已准入且未撤销的投影可以签发有效 Token。
- 支持创建、查询、完整替换、撤销和重新配置；替换或撤销递增对外 `authorization_version`。
- 新增独立 `lock_version` JPA 乐观锁字段，避免并发替换导致授权版本静默丢失。

### Admin 与机器管理入口

- Admin 新增应用授权管理页面，支持角色、页面权限、精确 API permission 和 DATA 授权文档的完整配置、显式准入及撤销。
- 新增 `/api/application-authorization` REST 资源，用于部署自动化和后续可选 IAM 集成。
- 应用授权管理资源使用独立 permission：
  - `akskApplicationAuthorization:create`
  - `akskApplicationAuthorization:read`
  - `akskApplicationAuthorization:update`
  - `akskApplicationAuthorization:revoke`
- 列表查询按固定页扫描投影、每页批量读取关联 Client，再逐项 fail-close 执行 `DataAccessPlan` 过滤、统计与分页；关联 Client 缺失的投影不可见。
- 替换或撤销应用授权前，额外要求 `akskToken:update` 与受影响活跃 Token 的 DATA 范围预检；预检失败时不修改授权投影。

### Token 与内省边界

- Access Token 签发时写入服务端生成的 `aksk_authorization` 授权快照。
- Token 内省按当前有效授权投影重建授权；投影缺失、未准入、禁用或撤销时返回 inactive。
- 完整替换或撤销应用授权时，在同一事务中撤销该 Client 的活跃 Token；替换前或撤销前签发的历史 Token 不会因当前投影变化重新获得授权。
- OAuth `scope` 仅保留 OAuth 请求范围语义，不再推导 API permission 或 DATA grant。
- `security_context` 仅作为调用方传入的运行时业务上下文，不得作为 API 或 DATA 授权来源。

### Admin Secret 一次性交付

- Admin 重置 Client Secret 后，敏感值仅通过认证会话交付一次。
- 成功页使用禁止缓存响应头，并在页面读取后清除会话中的一次性 Secret。
- Admin 重置接口的普通 JSON 响应不再包含 Secret，浏览器跳转 URL 不再承载 Secret。

## 数据库与升级

- 新部署使用 `docs/01_schema_3.0.0.sql` 建立完整 3.0.0 表结构。
- 从 2.x 升级使用 `docs/02_upgrade_3.0.0.sql`：保留历史 Client 与 Token 表，仅新建应用授权投影表和查询索引。
- 2.x 升级不从旧 Scope 自动推导新权限，也不自动准入任何 Client。
- 每个存量 Client 在首次 3.0 准入前必须先处理全部 2.x 活跃 Token，再人工配置完整授权并显式准入；进入 3.0 后的替换和撤销由 Server 事务性失效活跃 Token。

## 兼容性说明

- Server 3.0.0 使用 `simple-aksk-core:3.0.0`、`simple-aksk-server-core:3.0.1`、`simple-application-authorization-core:1.0.0` 与 `simple-data-permission-core:1.1.0`。
- Redis 仍是运行时必需基础设施，用于 Token 缓存、撤销同步、多实例缓存失效广播和 OAuth2 端点限流。
- 2.x 的 Client 与授权表结构可保留，但业务授权语义安全收紧：未配置投影的 Client 不可继续签发或内省有效 Token。
- `simple-aksk-server-audit-listener-starter:3.0.0` 是可选独立扩展，不反向加入 Server Starter 生产依赖；它按提交后语义消费 Token 事件，并且不向 Handler 传递 Token 原文。

## 测试覆盖

- 应用授权首次创建、准入、完整替换、撤销、重新配置与版本递增。
- 未配置或未准入 Client 的 Token 签发拒绝，撤销后的内省失效。
- 精确 API permission、`DataAccessPlan` 多维目标过滤及过滤后分页统计。
- Token 范围预检失败时的授权投影不变性，以及本地与机器管理路径在 Token 撤销失败时的事务回滚。
- 两个持久化事务并发替换时恰有一个提交、最终投影不混合；机器 REST 将乐观锁失败映射为无响应体 HTTP 409。
- IAM 与 AKSK 资源协作的独立认证、API/DATA 权限执行和拒绝路径，不引入 AKSK 对 IAM 的运行时依赖。
- Admin CSRF、应用授权页面生命周期、Secret 一次性交付和缓存禁止响应头。
