# 1.3.9

## 发布信息

- 版本：`1.3.9`
- 日期：`2026-11-27`
- 类型：Patch / 缓存依赖收敛
- 基线版本：`1.3.8`

## 变更

- `smart-cache-starter` 从 `2.1.0` 升级到 `2.2.1`，统一 SmartCache 版本线，消除宿主多版本共存时 `OAuth2Authorization` L2 载荷读回失败和类加载顺序不确定问题。
- `simple-redis-lock-starter` 从 `1.2.1` 升级到 `1.2.2`，对齐 SmartCache `2.2.x` 的 Redis Route/锁依赖线。

## 兼容性

- 无 HTTP 路径、请求参数、响应体、权限模型、SQL 或配置键变化。
- IAM 自身授权缓存读写语义不变；SmartCache `2.2.1` 可读取 `2.2.0` 缺省协议版本的旧 L2 JSON，并在回写时追加 `schemaVersion:1`。
- 使用方若在同一 BOOT JAR 显式引入 `smart-cache-starter:2.1.0` 或 `2.2.0`，必须移除旧固定坐标，统一到 `2.2.1`；同包多版本不受支持。

## 测试

- Starter 全量 `91` 个测试类分 `7` 批执行并全部通过；JUnit XML 无失败、无错误。
- `IamLoginRequirementsServiceTest` 在默认 REQUIRED 配置下单独复跑通过。
