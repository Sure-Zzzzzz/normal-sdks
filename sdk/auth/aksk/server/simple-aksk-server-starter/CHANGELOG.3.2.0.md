# CHANGELOG - simple-aksk-server-starter 3.2.0

## 发布信息

- 版本：`3.2.0`
- 类型：Minor / IAM 授权投影协作、AKU 自助与三权管理（含创建入口收敛的行为变化）
- 基线版本：`3.1.1`

## 版本定位

AKSK 协作的执行端落地：OWNER_INHERITED AKU 的自助生命周期（创建/改名/轮换/终止）、IAM 所属人授权本地投影与增量同步、四纪元失败关闭校验，以及个人凭证资源的 owner 天花板。静态 AKP 与存量 STATIC_LEGACY AKU 行为不变。

## 主要变更

- AKSK 三权按应用管理权、AKU 管理权和所属人使用权收口；管理员可管理授权范围，人员仅能查看和使用自己名下的 AKU。
- **AKU 创建入口收敛为门户自助唯一路径**：`/api/me/aksk-clients` 以已验证 IAM HUMAN 建立不可变 binding（幂等键、If-Match 生命周期版本、tombstone 语义）；管理 REST `POST /api/client` 对 `type=user` 一律 409，内嵌管理台的创建用户级表单停用并展示自助指引。
- 服务端支持从 IAM 增量拉取所属人授权投影；同步失败保留最近有效快照并按配置退避重试，不将 IAM 短时不可用放大为全量拒绝。
- AKU 归属链路补齐 `ownerUsername`：resolve/变更流携带 IAM 用户名，创建时落库，管理端列表与详情展示"用户名（subjectId）"。
- 适配 IAM 1.3 `subjectId`：AKU binding、人员授权投影及个人凭证 owner 约束统一使用稳定主体字符串；兼容字段 `ownerUserId` 不再承载 IAM 自增主键或可变 `username`。
- 人员 Token 校验与 AKSK Token 授权投影统一解析，令牌声明明确区分服务主体与人员主体；四类授权纪元不一致即失败关闭。
- 个人凭证天花板收位（2026-09-26 老大拍板方案A：本人现场按投影执行，凭证代办压到本人）：天花板只作用于 OWNER_INHERITED AKU 令牌路径（签发与内省，含 `all=true` 亦收敛到所属人），管理台 PKCE 的 HUMAN 令牌按身份源投影原样执行——平台管理员（投影全量授予）可见并治理全部 AKP 与所有用户的 AKU，普通用户无 `akskClient` API 权限则管理面直接 403；此前对 IAM HUMAN 认证结果的一律收敛（admin 只能看自己名下 AKU）已随包装适配器删除。自助 `/api/me/aksk-clients` 继续按不可变 binding 隔离，AKSK 不感知任何 IAM 角色语义。
- AKU 生命周期事件（创建/改名/轮换/终止）按 AFTER_COMMIT 发布脱敏审计；审计监听 starter 落地存储。
- 应用授权变化触发缓存失效、审计事件和运行日志；缓存键不再暴露原始敏感标识（详见 `simple-aksk-server-core` 3.0.3 的升级影响说明）。
- 变更流载荷键与绑定来源等协议字段常量化；绑定授权模式枚举按 SDK 规范补齐 code/description 与解析方法。
- 新装 SQL 更新为 `3.2.0`，并提供 `3.1.1 -> 3.2.0` 升级脚本。
- 聚散收口联动：协作适配器（simple-iam-aksk-collaboration-starter，随本批首发）对 IAM 内部协作端点切换中性路径 `/iam/internal/owner-authorization/**`、scope `iam:internal:owner-authorization:read|stream` 与 reader 客户端 `owner-authorization-reader`；resolve 消费统一走中立协议模型（ownerUsername 必填纠名、authorization 为 claim 形态）。
- 修复跨资源权限评估的应用编码裂缝：`CrossResourceDataPlanHelper` 原以 `aksk-server` 比对应用编码，方案A 后管理台 HUMAN 令牌的 IAM 投影（applicationCode=aksk）在删客户端等跨资源端点恒被误拒 403；API 评估改按授权上下文自身声明的应用编码进行（SERVICE 与 HUMAN 双形态均按各自投影判权，归属校验仍由资源链保证）。

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
| --- | --- | --- |
| `simple-aksk-core` | `3.0.0` | `3.0.2` |
| `simple-aksk-server-core` | `3.0.2` | `3.0.4` |

## 新增或扩展测试

- 新增自助生命周期集成测试（乐观锁版本、tombstone、跨 owner 拒绝）、IAM reader 客户端解析契约测试（含 `ownerUsername` 透传与缺失失败关闭）、JWE 资源适配器与 AKU 令牌路径天花板测试（管理台 HUMAN 收敛适配器测试随适配器删除）。
- 管理面测试补齐"创建用户级表单拒绝并指向自助"用例（库行数不变断言）。
- 全量 41 个测试类（基线 35，本版新增 6：自助生命周期集成、所属人授权投影、内省响应处理、个人凭证天花板、JWE 资源适配器、跨资源应用编码回归 `CrossResourceDataPlanHelperTest`）。

## 向后兼容性

- 既有 AK/SK 签发、验证、撤销和 OAuth2 管理端点保持可用；存量 STATIC_LEGACY AKU 不迁移、行为不变。
- **行为变化**：管理 REST 与内嵌表单不再支持创建用户级客户端（`type=user` 返回 409），新 AKU 只能由本人经统一应用门户自助创建；管理端查询、启停、改名、Scope、归属维护与令牌操作不受影响。
- 新增 IAM 协作配置默认不启用；启用后应配置 IAM 资源校验客户端与内部所属人授权读取身份。

## 升级指南

1. 升级依赖至 `io.github.sure-zzzzzz:simple-aksk-server-starter:3.2.0`（同时需要 `simple-aksk-core:3.0.2`、`simple-aksk-server-core:3.0.4`）。
2. 存量 `3.1.1` 数据库执行 `docs/04_upgrade_3.1.1_to_3.2.0.sql`；新安装使用 `docs/01_schema_3.2.0.sql`。升级脚本不创建 binding、不写凭据、不启用继承。
3. 按 IAM 可信应用接入手册分别登记浏览器 PKCE 客户端和资源校验客户端，再启用 IAM 授权投影同步；内部读取凭据以环境变量注入。
4. 上线灰度顺序：先以继承关闭状态完成 reader/stream 健康验证，再按目标应用逐个开启 OWNER_INHERITED。
