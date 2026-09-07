# CHANGELOG - simple-aksk-server-core 3.0.2

## 版本类型

Patch Release - 过期 Token 定时清理配置契约。

3.0.2 为 `simple-aksk-server-starter` 3.1.1 的过期 Token 定时清理任务提供配置常量与配置类支持，不改变既有授权数据模型、Token 事件契约或 IAM 集成边界。

## 新增

- `SimpleAkskServerConstant` 新增过期 Token 清理相关常量：`DEFAULT_CLEANUP_ENABLE`（默认 `true`）、`DEFAULT_CLEANUP_CRON`（默认 `0 0 2 * * ?`，每天凌晨 2 点）、`DEFAULT_CLEANUP_BATCH_SIZE`（默认 `2000`）、`DEFAULT_CLEANUP_LOCK_LEASE_SECONDS`（默认 `600`）、`CLEANUP_LOCK_KEY`（清理任务分布式锁 key）。
- `SimpleAkskServerProperties` 新增 `cleanup` 配置分组（`CleanupConfig`：`enable`/`cron`/`batchSize`/`lockLeaseSeconds`，默认值均取上述常量），完整配置键前缀 `io.github.surezzzzzz.sdk.auth.aksk.server.cleanup.*`。

## 兼容性

- 本版本仅新增常量与配置类字段，不修改任何既有公开 API 的签名或行为。
- 新增配置项均有默认值，不影响未升级到 starter 3.1.1 的既有使用方。
- 不引入新的运行时依赖。

## 升级说明

1. 将直接依赖坐标升级为：

   ```gradle
   implementation 'io.github.sure-zzzzzz:simple-aksk-server-core:3.0.2'
   ```

2. 单独使用 core 且不关心过期 Token 清理的现有代码无需改动，重新编译后即可继续使用。
3. 本版本的配置契约由 `simple-aksk-server-starter` 3.1.1 消费；单独升级 core 不会产生任何行为变化。
