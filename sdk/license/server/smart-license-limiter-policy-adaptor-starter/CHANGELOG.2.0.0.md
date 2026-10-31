# smart-license-limiter-policy-adaptor-starter 2.0.0

## 首发

- 提供 License 到 Limiter Management 的可选履约适配件，实现 `LimiterPolicyPort`。
- 将客户 OpenAPI 限频授权投影为 `CUSTOMER + EXACT + objectId=customerId` 的类型化策略。
- 复用 License Core 与 Limiter policy-mutation client core，不复制 HTTP、AKSK 或 Redis 实现。

## 依赖与边界

- License Server 不直接依赖本适配件；宿主显式组装本适配件和对应的 AKSK RestTemplate transport。
- 远端 namespace、ruleId、rowVersion 通过正式 mutation client 回读并用于后续 CAS 操作。
- 适配器未装配远端 client Bean 时启动失败，不回落到本地假实现。

## 测试覆盖

- 覆盖自动配置、策略 upsert、CAS 回读、启停与远端异常失败关闭路径。
