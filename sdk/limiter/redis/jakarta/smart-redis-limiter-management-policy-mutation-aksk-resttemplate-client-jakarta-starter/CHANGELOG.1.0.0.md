# smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-jakarta-starter 1.0.0

## 首发

- 提供 Spring Boot 3.4 / Jakarta 线的限流策略变更 AKSK RestTemplate 传输装配。
- 复用 Jakarta AKSK RestTemplate 底座和 hc5 连接池，业务契约由 mutation client core 提供。
- 与 javax 线保持相同的策略变更、授权矩阵和失败关闭语义。

## 依赖与边界

- 传递依赖 `smart-redis-limiter-management-policy-mutation-client-core:1.0.0`。
- 不复制 Limiter Management 的 HTTP、认证或 Redis 实现；宿主负责提供 Spring Web 与 Jakarta AKSK 底座。

## 验证

- 发布前执行 Jakarta 线完整测试，并使用正式坐标消费已发布的 mutation client core。
