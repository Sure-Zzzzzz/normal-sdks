# CHANGELOG - simple-iam-server-starter 1.2.0

## 发布信息

- 版本：`1.2.0`
- 类型：Minor / 可信应用生命周期与 AKSK 协作能力
- 基线版本：`1.1.1`

## 版本定位

AKSK 协作的权威端落地：可信应用安全生命周期（停用/恢复、异步删除清理）与 AKSK 所属人授权内部读取（resolve、候选目录、变更日志流）。内部读取只接受固定 SERVICE 短时 Bearer，不属于浏览器、资源验证或普通 OAuth 客户端的任何一条链。

## 主要变更

- 可信应用补齐启停、撤销与授权范围管理；停用后其用户信息端点与 PKCE 授权即行拒绝，Portal 仅按服务端持久化顺序展示可访问应用。
- 新增可信应用删除的异步清理：入队、租约分批执行、失败重试与管理端重新入队，删除过程阻断该应用的其他变更。
- 新增 AKSK 所属人授权投影的内部读取接口：resolve 与候选目录供 AKSK 建立绑定，变更日志流支持增量游标拉取，变更与业务状态同事务落库。
- 人员、角色、部门和应用授权变化会使相关会话与投影失效，保证 AKSK 可获得最新三权结论。
- 内部读取客户端启动时校验固定身份；凭据变更时撤销旧授权记录后重建，避免旧密钥继续有效。
- 站内信 SSE 连接建立时未读数查询与首帧推送移至后台线程：修复 SSE 长连接在 OSIV 下终身占用数据库连接导致的连接池耗尽。
- 授权变更流类型与清理操作状态枚举按 SDK 规范补齐 code/description、`fromCode()`、`isValid()`、`getAllCodes()`；code 与常量名同形，持久化与变更流值保持稳定。
- 全量建表和 `1.1.1 -> 1.2.0` 升级脚本补齐可信应用生命周期字段与授权数据。

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
| --- | --- | --- |
| `simple-iam-server-core` | `1.1.2` | `1.1.3` |

## 新增或扩展测试

- 新增 `IamOwnerAuthorizationInternalApiTest`：内部 reader 的认证边界（401/scope 隔离/密钥轮换）与 resolve 携带 `ownerUsername` 的生产端断言。
- 新增 `IamAuthorizationStateEnumTest`：变更流类型与清理状态枚举的 `fromCode`、code 与常量名同形稳定性断言。
- 扩展可信应用生命周期、授权投影与站内信 SSE 相关集成测试；全量测试基线 64 个测试类。

## 向后兼容性

- 既有 OAuth2/OIDC、Portal、RBAC 与管理 API 保持可用；新增字段和端点不改变既有请求语义。
- 可信应用停用为新增的即行拒绝语义：停用后该应用的登录与用户信息链路按失效处理，恢复后重新放行。
- 升级存量数据库前必须执行对应迁移脚本；新安装使用当前 `docs/schema.sql`。

## 升级指南

1. 升级依赖至 `io.github.sure-zzzzzz:simple-iam-server-starter:1.2.0`（同时需要 `simple-iam-server-core:1.1.3`）。
2. 从 `1.1.1` 升级时执行 `docs/migration/V1.1.1__to__V1.2.0__trusted_application_lifecycle.sql`，并按照 `docs/migration/README.md` 完成前检与后检。
3. 对接 AKSK 时登记应用登录客户端和资源校验客户端；内部所属人授权读取使用独立服务身份，不可使用浏览器客户端替代。
4. 启用内部 reader 时必须以环境变量注入部署密钥；缺失时启动校验主动拒绝，避免空密钥运行。
