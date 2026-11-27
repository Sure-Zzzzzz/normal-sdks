# 3.1.1

## 发布信息

- 日期：`2026-11-27`
- 类型：Dependency Bugfix / 向后兼容
- 基线版本：`3.1.0`

## 变更

- 运行时依赖 `smart-cache-starter` 由 `2.2.0` 收敛到 `2.2.1`。
- 避免宿主同时引入 AKSK Server/IAM Server `2.2.1` 与本模块传递 `2.2.0` 造成同进程多版本。

## 兼容性

- Profile 缓存隔离、锁隔离、预刷新和 Token 调用契约不变。
- `2.2.1` L2 JSON 载荷可读旧版缺 `schemaVersion` 的缓存；未知协议仍拒绝。

## 回归验证

- Spring Boot 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE 四轮 `cleanTest test --rerun-tasks` 全部通过；每轮核对 JUnit XML：`60` tests、`0` skipped、`0` failures、`0` errors。
- 真实 Redis 与 AKSK Server 的端到端链路参与全轮。
