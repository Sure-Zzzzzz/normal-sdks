# License Limiter Policy Adaptor

License 与 limiter Management 的可选履约适配件。它实现 License Core 的
`LimiterPolicyPort`，把客户 OpenAPI 限频授权投影到 limiter 的类型化规则接口。

## 边界

- 只依赖 License Core 与 limiter policy-mutation client core，不复制 HTTP、AKSK 或 Redis 逻辑。
- License Server 不直接依赖本适配件；宿主显式同时引入本适配件和对应的 limiter AKSK RestTemplate starter。
- 每条授权固定映射为 `CUSTOMER + EXACT + objectId=customerId`。
- `namespace` 由宿主固定配置；远端回读的 namespace 会写入 License 台账并用于后续 CAS 操作。
- limiter 幂等命中返回 `revision=-1`，License 以 `PolicyResult.NO_REVISION` 保留“没有新修订”的事实。

## 依赖

正式宿主引入本模块后还需要同一 Spring Boot 线的：

1. limiter policy-mutation AKSK RestTemplate starter；
2. AKSK client RestTemplate 底座；
3. `io.github.surezzzzzz.sdk.limiter.management.policy-mutation-client.enable=true`、`base-url`；
4. `io.github.surezzzzzz.sdk.license.limiter.customer-namespace`。

未装配 limiter client Bean 时，适配器在启动构造阶段响亮失败；不会回落到本地假实现。
