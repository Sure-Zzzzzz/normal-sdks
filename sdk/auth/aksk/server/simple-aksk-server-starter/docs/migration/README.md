# AKSK Server 升级

## 3.2.0 / 3.2.1

`3.2.0` 落地身份源所属人授权协作（OWNER_INHERITED AKU）与 AKU 自助生命周期。新部署直接执行上级 `schema.sql`（全量重建）；已运行 `3.1.1` 的数据库只执行一次 `V3.1.1__to__V3.2.0__owner_inherited_aku.sql`。`3.2.1` 为纯接口增量（候选目录降级响应头），**无表结构变化**，无需任何 SQL。

升级脚本只建新表（owner binding / 生命周期命令 / 授权投影五表），不创建绑定、不写凭据、不启用继承；存量 STATIC_LEGACY AKU 不迁移、行为不变。启用协作前需按协作手册完成 IAM 侧准备（reader 客户端、内部端点），AKSK 侧凭据经环境变量注入。

升级后应核对以下结果：

1. `aksk_client_owner_binding`、`aksk_client_lifecycle_command`、`aksk_owner_authorization_{cursor,inbox,owner_state,target_application_state,projection}` 七表存在，均无存量行（继承链路未启用时投影表为空是正常态）。
2. 存量 `oauth2_registered_client` / `oauth2_authorization` / `aksk_application_authorization` 结构与数据不变。
3. 服务启动日志无 `IAM owner 授权日志拉取失败`；未启用 `owner-authorization.enabled` 时相关后台任务静默。

数据库结构已变更时不支持手写反向 DDL。升级失败或验收不通过时，停止服务，从升级前备份恢复，并启动与该备份对应的历史版本。

## 3.1.1

`3.1.1` 为过期 Token 自动清理补 `oauth2_authorization.access_token_expires_at` 索引。新部署直接执行上级 `schema.sql`；已运行 `3.0.0` / `3.0.1` / `3.1.0` 的数据库只执行一次 `V3.0.0__to__V3.1.1__expired_token_index.sql`（不可重复执行）。

## 3.0.0

`3.0.0` 引入应用授权与数据权限管理（`aksk_application_authorization`）并移除匿名 introspect 配置。新部署执行 `migration/V3.0.0__baseline__full_schema.sql`（或直接用当前版 `schema.sql`）；已运行 `2.x` 的数据库执行 `V2.x__to__V3.0.0__application_authorization.sql`——该脚本要求保留 `oauth2_registered_client` 与 `oauth2_authorization` 两张历史表，执行前暂停写请求并完成逻辑备份。

## 1.x 基线

`migration/V1.0.0__baseline__legacy_full_schema.sql` 为 1.0 时代历史基线归档（含后续混入的 3.2 投影表段落，仅供考古，**不再用于部署**）；新装一律用上级 `schema.sql`。

## 通用纪律

- 每个迁移脚本只在目标库执行一次，不幂等；执行前后核对结果留存。
- 升级路径必须逐版串联（1.x → 3.0.0 → 3.1.1 → 3.2.0），不能跳版执行。
- 执行前暂停 AKSK Server 写请求并完成可恢复的逻辑备份；`CREATE DATABASE` 语句不在任何脚本内（建库语句：`CREATE DATABASE <库名> DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`）。
