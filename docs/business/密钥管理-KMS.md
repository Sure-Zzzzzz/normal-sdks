# 密钥管理-KMS

密钥全生命周期服务：逻辑密钥与版本、精确 allow-only 使用策略、延迟销毁与取消资格、ES256/AES-256 密码学操作。资源归属按稳定主体标识（`iam:` 人员 / `aksk:` 凭证），Server 注册为 IAM 可信应用，治理面走 PAGE/API/DATA 三权。

## 分层结构（core → server → contract → client → web）

| 层 | SDK | javax | jakarta | 说明 | 文档 |
|----|-----|-------|---------|------|------|
| 领域契约 | [smart-kms-core](../../sdk/kms/smart-kms-core) | 2.0.0 | — | 纯 Java 8 领域模型（密钥/版本状态、精确策略、幂等、销毁任务、审计安全边界、ES256 JOSE） | [README](../../sdk/kms/smart-kms-core/README.md) |
| Server | [smart-kms-server-starter](../../sdk/kms/smart-kms-server-starter) | 2.0.2 | — | KMS Server（本人公钥零策略读取、销毁明细、治理归属显示名与筛选、跨钥策略分页、延迟销毁 worker） | [README](../../sdk/kms/smart-kms-server-starter/README.md) |
| HTTP 契约 | [sdk/kms/server/contract](../../sdk/kms/server/contract) | 2.0.2 | — | OpenAPI 与端点契约（纯文档模块，随 Server 同版演进，不构建发布） | [README](../../sdk/kms/server/contract/README.md) |
| Client 契约层 | [simple-kms-client-core](../../sdk/kms/client/simple-kms-client-core) | 2.0.0 | — | 调用方公开接口、wire 模型、错误族与传输中立认证 SPI | [README](../../sdk/kms/client/simple-kms-client-core/README.md) |
| Client 装配 | [simple-kms-aksk-resttemplate-client-starter](../../sdk/kms/client/simple-kms-aksk-resttemplate-client-starter) | 2.0.0 | — | RestTemplate 装配件（AKSK 服务身份） | [README](../../sdk/kms/client/simple-kms-aksk-resttemplate-client-starter/README.md) |
| Client 装配 | [simple-kms-aksk-feign-client-starter](../../sdk/kms/client/simple-kms-aksk-feign-client-starter) | 2.0.0 | — | OpenFeign 装配件（AKSK 服务身份） | [README](../../sdk/kms/client/simple-kms-aksk-feign-client-starter/README.md) |
| Client 装配 | simple-kms-aksk-resttemplate-client-jakarta-starter | — | 1.0.0 | Jakarta RestTemplate 装配件 | [README](../../sdk/kms/client/jakarta/simple-kms-aksk-resttemplate-client-jakarta-starter/README.md) |
| Client 装配 | simple-kms-aksk-feign-client-jakarta-starter | — | 1.0.0 | Jakarta Feign 装配件 | [README](../../sdk/kms/client/jakarta/simple-kms-aksk-feign-client-jakarta-starter/README.md) |
| 管理端 Web | [smart-kms-admin-web](https://github.com/Sure-Zzzzzz/smart-kms-admin-web) | 1.0.0 | — | IAM 门户 qiankun 子应用（四页治理：我的密钥/密钥/策略/销毁任务） | [README](https://github.com/Sure-Zzzzzz/smart-kms-admin-web/blob/main/README.md) |

> 旧单体 `simple-kms-client-starter`（1.x）已由 client 分层组合替代并从仓库移除。

**jakarta 装配件内部配对**（共用 javax 线 client-core 2.0.0）：resttemplate-client-jakarta 1.0.0 → client-core 2.0.0 + aksk-resttemplate-redis-client-jakarta 1.0.0；feign-client-jakarta 1.0.0 → client-core 2.0.0 + aksk-feign-redis-client-jakarta 1.0.0。

## 依赖关系

- Server 依赖 [MySQL 路由](../middleware/MySQL.md)（1.1.1）与 [Redis 路由](../middleware/Redis.md)，身份接入走 IAM/AKSK 协作（见 [AKSK 篇](认证与授权-AKSK.md)）
- Server 本体固定 Spring Boot 2.7 + Java 8 测试基线；client jakarta 件支持矩阵见各模块 README（Boot 3.2–3.4 × JDK 17/21）；feign 件在 Boot 3.2/3.3 需配套 `jakartaCloudVersion=2023.0.4`（Spring Cloud 线随 Boot 版本切换）
- client 对 server 的兼容口径：client 消费的机器端点（`/keys`、`/crypto`、`/me/destruction-policy`）语义稳定；server 2.0.2 新增端点均为治理面增量（可选参数与可空字段），client 无需配套升级

## 版本映射（唯一事实源）

| server-starter | core | client-core | SB2 装配件 | jakarta 装配件 | 管理端 Web | 说明 |
|----------------|------|-------------|------------|----------------|------------|------|
| 2.0.2 | 2.0.0 | 2.0.0 | 2.0.0 | 1.0.0 | 1.0.0 | 本人公钥零策略、销毁明细、治理归属显示名/筛选、跨钥策略分页；无数据库迁移，Web 需先升 Server |
| 2.0.1 | 2.0.0 | 2.0.0 | 2.0.0 | 1.0.0 | - | 本人密钥生命周期（启停/轮换/安排/取消）与销毁窗口政策 |
| 2.0.0 | 2.0.0 | 2.0.0 | 2.0.0 | 1.0.0 | - | 2.0 基线：稳定主体归属、`/api/kms` 基路径、client 分层首发 |
