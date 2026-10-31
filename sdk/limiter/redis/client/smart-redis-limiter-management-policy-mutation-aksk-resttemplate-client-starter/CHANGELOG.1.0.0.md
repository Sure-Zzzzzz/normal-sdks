# smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-starter 1.0.0

## 首发

- 提供 Spring Boot 2.7 / javax 线的限流策略变更 AKSK RestTemplate 传输装配。
- 复用 `akskClientRestTemplate` 完成认证、连接池和超时处理，业务契约由 mutation client core 提供。
- 保留 403 失败关闭、409 冲突回读重试和响应字节上限校验。

## 依赖与边界

- 传递依赖 `smart-redis-limiter-management-policy-mutation-client-core:1.0.0`。
- 不复制 Limiter Management 的 HTTP、认证或 Redis 实现；宿主负责提供 Spring Web 与 AKSK 底座。
- 授权矩阵以 core README 和 Management OpenAPI 为准。

## 验证

- 发布前执行本模块完整测试，并使用正式坐标消费已发布的 mutation client core。
