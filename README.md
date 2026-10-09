# Normal SDKs

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-8%20%7C%2017%20%7C%2021-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.2%20%7C%202.3%20%7C%202.4%20%7C%202.7%20%7C%203.2%20%7C%203.3%20%7C%203.4-brightgreen.svg)](https://spring.io/projects/spring-boot)

> 企业级通用 Spring Boot Starter 集合：中间件接入、身份与密钥业务体系、通用工具，三类能力分层供给。javax 与 jakarta 双生态线并行，各模块独立演进。

## 快速上手

所有组件发布于 Maven Central，坐标统一为 `io.github.sure-zzzzzz:{模块名}:{版本}`：

```gradle
implementation 'io.github.sure-zzzzzzz.sdk:示例:版本'   // 见各文档篇的版本映射表
```

- **javax 线**（Spring Boot 2.2–2.7）与 **jakarta 线**（Spring Boot 3.x）同名平行组件，jakarta 坐标带 `-jakarta-` 段
- 兼容矩阵：javax 线 Spring Boot 2.2/2.3/2.4/2.7 × Java 8（测试基线）；jakarta 线 Spring Boot 3.2/3.3/3.4 × Java 17/21
- jakarta 线各模块的支持矩阵以各模块 README 兼容矩阵小节为准（Boot 3.2–3.4 / JDK 17/21，逐模块组合见各篇文档）
- 每篇文档自带版本映射表，是版本的唯一事实源

## 中间件（middleware）

按中间件组织，各篇从 route（连接路由）到 client 再到能力层，自底向上。版本列：javax / jakarta。

| 模块 | javax | jakarta | 一句话 | 文档 |
|------|-------|---------|--------|------|
| simple-redis-route-starter | 1.2.2 | 1.0.0 | Redis 连接路由（多实例/Cluster） | [Redis](docs/middleware/Redis.md) |
| smart-cache-starter | 2.1.0 | 1.0.0 | Redis 缓存（L1 本地 + L2 二级） | [Redis](docs/middleware/Redis.md) |
| simple-redis-lock-starter | 1.2.1 | 1.0.0 | Redis 分布式锁 | [Redis](docs/middleware/Redis.md) |
| smart-redis-limiter-core / starter | 2.1.0 / 2.0.0 | 1.1.0 | 智能限流（客户/服务/IP 维度） | [Redis](docs/middleware/Redis.md) |
| smart-redis-limiter-management-starter（+策略变更审计 1.0.0） | 2.0.0 | — | 限流策略管理面（Portal 形态，多维类型化规则） | [Redis](docs/middleware/Redis.md) |
| redis-retry / smart-redis-retry-starter | 1.1.0 | — | Redis 操作重试 | [Redis](docs/middleware/Redis.md) |
| simple-elasticsearch-route-starter | 1.2.1 | 1.0.0 | ES 连接路由（日期分片+代理） | [Elasticsearch](docs/middleware/Elasticsearch.md) |
| simple-elasticsearch-search-starter | 1.7.2 | 1.0.0 | 结构化/表达式/自然语言查询 | [Elasticsearch](docs/middleware/Elasticsearch.md) |
| simple-elasticsearch-persistence-starter | 1.1.1 | 1.0.0 | 写侧框架（index/CRUD/bulk） | [Elasticsearch](docs/middleware/Elasticsearch.md) |
| ES search/persistence-audit-listener | 1.0.4 / 1.0.0 | 1.0.0 / 1.0.0 | 搜索/写入审计事件 | [Elasticsearch](docs/middleware/Elasticsearch.md) |
| simple-kafka-route-starter | 1.0.5 | 1.0.0 | Kafka 连接路由 | [Kafka](docs/middleware/Kafka.md) |
| simple-kafka-publisher-starter | 1.1.0 | 1.0.1 | 事务安全发布 | [Kafka](docs/middleware/Kafka.md) |
| simple-kafka-outbox-starter | 1.0.1 | — | Outbox 投递（core/management 配套） | [Kafka](docs/middleware/Kafka.md) |
| simple-mysql-route-starter | 1.1.1 | 1.0.0 | MySQL 连接路由（读写/多库接管） | [MySQL](docs/middleware/MySQL.md) |
| s3-route / s3-client-starter | 1.0.0 / 2.1.0 | 1.0.0 / 1.0.0 | S3 协议连接路由 / 客户端 | [S3](docs/middleware/S3.md) |
| prometheus-route / client-starter | 1.0.0 | 1.0.0 / — | Prometheus 查询客户端 | [Prometheus](docs/middleware/Prometheus.md) |

## 业务体系（business）

按产品组织，各篇从 core 到 server 到 client 再到 Web，自底向上。

| 模块 | javax | jakarta | 一句话 | 文档 |
|------|-------|---------|--------|------|
| simple-iam-server-starter（+core/adapter/resource/audit 共10件） | 1.3.6 | 资源侧 1.0.0 / 审计 1.0.1 | 统一身份：OAuth 2.1+PKCE / RBAC 投影 / 门户 | [认证与授权-IAM](docs/business/认证与授权-IAM.md) |
| IAM 开放 API client（client-core + feign/rt × SB2/jakarta 四件） | 1.0.1 | 1.0.1 | IAM 开放 API 契约与传输件（受委托角色/页面准入/subjectId） | [认证与授权-IAM](docs/business/认证与授权-IAM.md) |
| simple-aksk-server-starter（+client/resource 共12件） | 3.2.2 | 资源侧 1.0.0 | 服务身份：OAuth2 / JWE / 应用授权投影 | [认证与授权-AKSK](docs/business/认证与授权-AKSK.md) |
| AKSK 管理 OpenAPI client（openapi-client-core + feign/rt × SB2/jakarta 四件） | core 1.0.1 + 1.0.0 | 1.0.0 | AKSK 管理 OpenAPI 契约与传输件（20 端点，listClients 分页/批量双形态） | [认证与授权-AKSK](docs/business/认证与授权-AKSK.md) |
| 公共资源层 resource-server + data-permission | 1.1.1 / 1.0.1 | 1.0.0 / 1.0.0 | 人+机双身份资源服务协作底座 | [认证与授权-AKSK](docs/business/认证与授权-AKSK.md) |
| smart-kms-server-starter（+core/contract 共3件） | 2.0.2 | — | 密钥全生命周期 | [密钥管理-KMS](docs/business/密钥管理-KMS.md) |
| KMS client（core + SB2 双件 + jakarta 双件） | 2.0.0 | 1.0.0 | 服务端调用 KMS 的契约与装配件 | [密钥管理-KMS](docs/business/密钥管理-KMS.md) |
| simple-crm-server-core | 1.0.2 | — | 报价签发与确认（幂等可重放） | [客户管理-CRM](docs/business/客户管理-CRM.md) |

> AKSK 2.x / 1.x 已封版，快照见 AKSK 篇内封版小节。IAM/AKSK 的 Server 本体固定 Spring Boot 2.7，仅资源侧与客户端走 jakarta 线。

## 工具（tool）

| 模块 | javax | jakarta | 一句话 | 文档 |
|------|-------|---------|--------|------|
| simple-xff-capture（capture + audit 链） | 1.1.x | 1.0.0 | HTTP 请求事实捕获与审计 | [HTTP](docs/tool/HTTP.md) |
| smart-keyword-sensitive-starter | 1.0.5 | — | 关键词脱敏（NLR+规则三级降级） | [关键词脱敏](docs/tool/关键词脱敏.md) |
| simple-ip-sensitive-starter | 1.0.0 | — | IP 脱敏（IPv4/IPv6/CIDR） | [IP脱敏](docs/tool/IP脱敏.md) |
| natural-language-parser-starter | 1.1.4 | — | 自然语言解析为结构化条件 | [自然语言解析](docs/tool/自然语言解析.md) |
| condition-expression-parser-starter | 1.0.5 | 1.0.0 | 条件表达式解析 | [表达式解析](docs/tool/表达式解析.md) |
| mail-client-starter | 2.0.0 | — | 邮件客户端 | [mail](docs/tool/mail.md) |
| b2m-sms-client-starter | 1.0.0 | — | 短信客户端 | [sms](docs/tool/sms.md) |
| simple-doc-template-starter | 1.3.0 | — | 文档模板（已封版） | [文档模板](docs/tool/文档模板.md) |
| log-truncate-starter | 1.1.0 | — | 日志截断 | [日志](docs/tool/日志.md) |
| task-retry-starter | 2.0.0 | — | 任务重试 | [任务重试](docs/tool/任务重试.md) |
| smart-middleware-ops-server-starter | 1.1.1 | — | 中间件运维观测服务 | [中间件运维](docs/tool/中间件运维.md) |

## 许可证

Apache License 2.0

## 相关链接

- [GitHub Issues](https://github.com/Sure-Zzzzzz/normal-sdks/issues)
- [Maven Central](https://central.sonatype.com/search?q=io.github.sure-zzzzzz)
