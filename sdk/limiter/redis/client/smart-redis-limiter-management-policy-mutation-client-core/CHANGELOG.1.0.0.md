# smart-redis-limiter-management-policy-mutation-client-core 1.0.0

## 首发

- 提供限流策略变更客户端的 Java 8 中立契约、策略键、限额模型和变更结果模型。
- 提供幂等 upsert、精确回读和基于 `rowVersion` 的 CAS 变更编排。
- 保持零 Spring、零 HTTP、零认证传输依赖；javax/Jakarta 传输由独立 starter 装配。

## 授权与边界

- 变更调用需要 `smartLimiterPolicy:write` 和 `limiter-policy` DATA write。
- 回读调用需要 `smartLimiterPolicy:read` 和 `limiter-policy` DATA read。
- 本模块只定义公开客户端契约，不复制 Management 的领域实体、Repository 或数据库结构。

## 验证

- 发布前执行本模块完整测试，并在两个传输 starter 使用正式 Maven 坐标重新验证。
