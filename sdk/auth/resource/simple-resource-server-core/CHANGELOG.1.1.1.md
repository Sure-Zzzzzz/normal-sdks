# simple-resource-server-core 1.1.1 变更记录

## 依赖升级：simple-application-authorization-core 1.0.1

- `simple-application-authorization-core` `1.0.0` → `1.0.1`（`api`）：该版本为授权时效判定引入默认 2 秒时钟容差（仅放宽 `issuedAt` 下界，`expiresAt` 上界零容差），消除上游签发时间亚秒取整导致的签发后立即访问间歇拒绝。
- 本模块无源码变更；事件模型、契约类与 `api` 传递关系不变，下游经本模块传递获得 1.0.1。
