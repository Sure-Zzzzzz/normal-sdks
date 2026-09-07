# CHANGELOG - simple-aksk-server-starter 3.1.1

## 发布日期

2026-09-07

## 版本类型

Patch Release - 过期 Token 定时清理（分布式锁多实例互斥 + 分批删除）+ `oauth2_authorization.access_token_expires_at` 索引补齐；core 同步升级 3.0.2

## 变更概述

本版本为 `oauth2_authorization` 表补上自动化清理能力：新增默认每天凌晨 2 点执行的定时清理任务，多实例部署通过 `simple-redis-lock-starter` 分布式锁互斥（抢到锁的实例执行，抢不到直接跳过本次调度），清理改为分批删除避免大事务长锁表，并补齐清理语句依赖的 `access_token_expires_at` 索引。

## 变更详情

### 新增：过期 Token 定时清理任务

- 新增 `ExpiredTokenCleanupScheduler`（`scheduler` 包），默认 cron `0 0 2 * * ?`（每天凌晨 2 点），经 `io.github.surezzzzzz.sdk.auth.aksk.server.cleanup.cron` 覆盖。
- 多实例互斥：清理前经 `simple-redis-lock-starter`（1.2.2，与 route 同版本线）`tryLockWithLease` 抢锁，抢不到直接跳过本次调度；无续租 watchdog——单次失败不做补偿，等下次调度重试。
- 配置分组 `io.github.surezzzzzz.sdk.auth.aksk.server.cleanup.*`：`enable`（默认 true，false 时任务不装配）、`cron`、`batch-size`（默认 2000）、`lock-lease-seconds`（默认 600）。常量与默认值落在 server-core 3.0.2 的 `SimpleAkskServerConstant` / `SimpleAkskServerProperties.CleanupConfig`。
- 自动配置 `SimpleAkskServerAutoConfiguration` 补 `@EnableScheduling`（模块内首次引入 Spring 调度；对宿主已开启调度的应用无副作用）。

### 调整：清理改为分批删除

- `OAuth2AuthorizationEntityRepository` 新增原生 `deleteExpiredBatch`（`DELETE ... WHERE access_token_expires_at < :now LIMIT :limit`，每批独立事务）；原单条 JPQL 全量 `deleteByAccessTokenExpiresAtBefore` 删除（零调用方死代码）。
- `OAuth2AuthorizationRepository.deleteExpired()` 改为循环分批删除直到不足一批；Admin 页面手动清理按钮（`DELETE /admin/token/expired`）与定时任务共用该实现。`/api/token/expired`（DataAccessPlan 过滤路径）行为不变。

### 变更：`access_token_expires_at` 索引

- `01_schema_3.0.0.sql` 的 `oauth2_authorization` 表新增 `idx_oauth2_authorization_access_token_expires_at`（新装环境直接带索引）。
- 新增 `03_upgrade_3.1.1.sql`：存量 3.0.0 / 3.0.1 / 3.1.0 环境执行 `ALTER TABLE ... ADD KEY` 补齐索引。

## 兼容性

- 对外 API、配置键、introspection / token / admin 行为零变化（除下述清理行为项）。
- 新增配置键 `io.github.surezzzzzz.sdk.auth.aksk.server.cleanup.*` 全部有默认值，存量部署零配置升级即获得自动清理；不希望自动清理的部署显式配 `cleanup.enable: false`。
- 新增依赖 `simple-redis-lock-starter:1.2.2`（`implementation`，实现细节不泄漏进编译类路径）；其锁通道复用既有 Redis 连接（容器内已有 `StringRedisTemplate` 时自动让位复用）。
- 存量数据库需执行 `03_upgrade_3.1.1.sql` 补索引，否则清理语句全表扫描（功能可用但大表性能差）。
- server-core 依赖 3.0.1 → 3.0.2。
- 本模块运行时仅支持 Spring Boot 2.7.x（既定硬性要求，无变化）。

## 测试

- 2.7.9 单档全量（server 硬性 SB 2.7 基线）：212 个用例全部通过（0 skipped / 0 failures / 0 errors，37 个测试类），测试配置固定 mysql-route / redis-route 接管形态；`simple-aksk-server-core` 坐标为发布后的 Maven Central 制品 3.0.2（非 project 引用）。
- 新增覆盖：分批删除循环（`ExpiredTokenCleanupRepositoryIntegrationTest`，真实 MySQL，种子数据跨批并断言总删除数，`countQuery = "SELECT 1"` 未触发 count 派生 NPE）、调度器锁分支（`ExpiredTokenCleanupSchedulerTest`，mock 验证拿到锁执行并释放 / 抢不到锁跳过不触仓库 / 清理失败仍释放锁 / `lockLeaseSeconds` 生效）、装配开关（`ExpiredTokenCleanupAssemblyTest`，`cleanup.enable=false` 不装配、默认装配）。
- 既有 201 个用例全部回归通过，含 OAuth2 签发 / introspect / 撤销、Admin 鉴权链、资源层接管、mysql-route / redis-route 接管等既有覆盖面。
- 测试环境：MySQL 127.0.0.1:3306（`sure_auth_aksk`）+ Redis 127.0.0.1:6379；Gradle 8.5 + Zulu 11。
