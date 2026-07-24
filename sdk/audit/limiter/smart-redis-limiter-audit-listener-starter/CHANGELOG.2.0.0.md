# CHANGELOG 2.0.0

发布日期：2026-07-24
类型：Breaking Change / Security Enhancement

## 依赖映射

| 组件 | 版本 |
|------|------|
| smart-redis-limiter-starter | 2.0.0 |
| smart-redis-limiter-core | 2.1.0 |
| simple-redis-route-starter | 1.1.0 |

## 变更内容

### 1.x 封版

- `smart-redis-limiter-audit-listener-starter:1.0.0` 固定为历史 1.x 的唯一已发布审计 artifact，不再维护。
- 新增 `README.1.x.md` 保留历史使用边界。

### 完整执行快照

- 完整映射 Route：`routeKey`、`datasourceKey`、`redisMode`、`routeRequired`、`routeResolved`。
- 完整映射 `fallbackReason` 及 `resourceCode`、`policySource`、`policyRevision`。
- 审计监听器独立生成 `timestamp`，表示生成执行快照的时间。
- 映射后校验 local/remote 策略上下文，再交给 Handler。

### 审计安全边界

- `SmartRedisLimiterRecord.extra` 固定为 `null`，不再写入任意 event attributes。
- 默认日志不输出 key、用户标识、IP、TraceId、原始 URI 或扩展属性。
- 默认日志只保留结果、算法、策略来源、Route/fallback 诊断和限额结果；自定义 Handler 仍可读取 Record 的显式请求、身份与关联字段，并自行承担数据治理责任。

### 异步模型

- 在事件发布线程读取 User Provider 与 Trace Provider 并生成审计快照。
- Handler 仍异步执行；单个 Provider 或 Handler 失败不影响限流主流程和其他 Handler。

## 升级指南

```gradle
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-starter:2.0.0'
implementation 'io.github.sure-zzzzzz:smart-redis-limiter-audit-listener-starter:2.0.0'
```

- `SmartRedisLimiterAuditHandler` 接口无需修改。
- 原先依赖 `record.extra` 的 Handler 必须移除该依赖。
- Management 策略 CRUD 不属于本模块的执行审计范围。
