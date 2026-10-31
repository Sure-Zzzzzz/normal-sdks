# Normal SDKs

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-8%20%7C%2017%20%7C%2021-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.9%20%7C%203.4.2-brightgreen.svg)](https://spring.io/projects/spring-boot)

> 企业级通用 Spring Boot Starter 集合：中间件、身份与业务领域、通用工具分层供给。javax 与 jakarta 两条生态线并行，各模块独立演进。

## 坐标与版本口径

所有正式发布的 Maven artifact 使用 `io.github.sure-zzzzzz` group：

```gradle
implementation 'io.github.sure-zzzzzz:artifact-name:version'
```

- 表中的 `javax / jakarta` 是同一能力在两条生态线的正式版本；`—` 表示该变体没有发布。
- 版本以模块 `version.properties`、正式发布记录和 Maven Central 坐标为准；模块 README、CHANGELOG 里的历史兼容矩阵仍用于说明依赖组合。
- 当前验证基线是 javax 线 Spring Boot 2.7.9 / Java 8 与 jakarta 线 Spring Boot 3.4.2 / Java 17、21；精确矩阵以模块 README 为准。
- 下表列正式发布坐标；已发布但已停用的旧 artifact 会标为历史坐标。

## 中间件

### Redis、限流与缓存

| Artifact | javax | jakarta | 说明 |
|---|---:|---:|---|
| `simple-redis-route-starter` | 1.2.2 | — | javax Redis 路由与 Cluster 接入 |
| `simple-redis-route-jakarta-starter` | — | 1.0.0 | Jakarta Redis 路由 |
| `smart-cache-starter` | 2.2.0 | — | L1/L2 缓存 |
| `smart-cache-jakarta-starter` | — | 1.0.0 | Jakarta 缓存装配 |
| `simple-redis-lock-starter` | 1.2.2 | — | 分布式锁 |
| `simple-redis-lock-jakarta-starter` | — | 1.0.0 | Jakarta 分布式锁 |
| `simple-redis-limiter-starter` | 1.0.1 | — | 简单限流 |
| `smart-redis-limiter-core` | 2.3.0 | — | 纯 Java 限流核心 |
| `smart-redis-limiter-starter` | 2.2.1 | — | javax runtime 限流装配 |
| `smart-redis-limiter-jakarta-starter` | — | 1.1.0 | Jakarta runtime 限流装配 |
| `smart-redis-limiter-management-starter` | 2.2.0 | — | 独立的 Limiter Management 服务 |
| `smart-redis-limiter-management-iam-directory-starter` | 1.1.0 | — | Management IAM 目录接入 |
| `smart-redis-limiter-management-client-core` | 1.0.0 | — | 限流快照读取契约 |
| `smart-redis-limiter-management-aksk-resttemplate-client-starter` | 1.0.0 | — | javax 快照读取 AKSK 传输 |
| `smart-redis-limiter-management-aksk-resttemplate-client-jakarta-starter` | — | 1.0.0 | Jakarta 快照读取 AKSK 传输 |
| `smart-redis-limiter-management-policy-mutation-client-core` | 1.0.0 | — | 策略变更契约 |
| `smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-starter` | 1.0.0 | — | javax 策略变更 AKSK 传输 |
| `smart-redis-limiter-management-policy-mutation-aksk-resttemplate-client-jakarta-starter` | — | 1.0.0 | Jakarta 策略变更 AKSK 传输 |
| `smart-redis-limiter-metrics-starter` | 1.0.0 | — | 限流指标 |
| `smart-redis-limiter-audit-listener-starter` | 2.1.0 | — | 限流执行审计 |
| `smart-redis-limiter-audit-listener-jakarta-starter` | — | 1.0.0 | Jakarta 限流执行审计 |
| `smart-redis-limiter-management-audit-listener-starter` | 1.0.0 | — | 策略变更审计 |
| `redis-retry-starter` / `smart-redis-retry-starter` | 1.1.0 / 1.1.0 | — | Redis 操作重试 |

文档：[Redis 工具链](docs/middleware/Redis.md)。Limiter Management 是服务端，runtime 通过 snapshot client 读取策略；License 只通过 mutation client/adaptor 写入策略，三者不共用数据库。

### Elasticsearch

| Artifact | javax | jakarta | 说明 |
|---|---:|---:|---|
| `simple-elasticsearch-route-starter` | 1.2.1 | — | javax ES 路由 |
| `simple-elasticsearch-route-jakarta-starter` | — | 1.0.0 | Jakarta ES 路由 |
| `simple-elasticsearch-search-core` | 1.0.12 | — | 搜索领域核心 |
| `simple-elasticsearch-search-starter` | 1.7.2 | — | javax 搜索与聚合 |
| `simple-elasticsearch-search-jakarta-starter` | — | 1.0.0 | Jakarta 搜索与聚合 |
| `simple-elasticsearch-search-metrics-starter` | 1.0.2 | — | 搜索指标 |
| `simple-elasticsearch-search-audit-listener-starter` | 1.0.4 | — | javax 搜索审计 |
| `simple-elasticsearch-search-audit-listener-jakarta-starter` | — | 1.0.0 | Jakarta 搜索审计 |
| `simple-elasticsearch-persistence-core` | 1.0.3 | — | 写入领域核心 |
| `simple-elasticsearch-persistence-starter` | 1.1.1 | — | javax 写入、bulk 与 byQuery |
| `simple-elasticsearch-persistence-jakarta-starter` | — | 1.0.0 | Jakarta 写入装配 |
| `simple-elasticsearch-persistence-audit-listener-starter` | 1.0.0 | — | javax 写入审计 |
| `simple-elasticsearch-persistence-audit-listener-jakarta-starter` | — | 1.0.0 | Jakarta 写入审计 |

文档：[Elasticsearch 工具链](docs/middleware/Elasticsearch.md)。

### Kafka、MySQL、S3 与 Prometheus

| Artifact | javax | jakarta | 说明 |
|---|---:|---:|---|
| `simple-kafka-route-starter` | 1.0.5 | — | javax Kafka 路由 |
| `simple-kafka-route-jakarta-starter` | — | 1.0.0 | Jakarta Kafka 路由 |
| `simple-kafka-publisher-starter` | 1.1.1 | — | javax 消息发布 |
| `simple-kafka-publisher-jakarta-starter` | — | 1.0.1 | Jakarta 消息发布 |
| `simple-kafka-consumer-starter` | 1.0.1 | — | javax 消息消费 |
| `simple-kafka-consumer-jakarta-starter` | — | 1.0.0 | Jakarta 消息消费 |
| `simple-kafka-outbox-core` | 1.0.0 | — | Outbox 领域模型 |
| `simple-kafka-outbox-starter` | 1.0.1 | — | javax 事务 Outbox 投递 |
| `simple-kafka-outbox-jakarta-starter` | — | 1.0.0 | Jakarta 事务 Outbox 投递 |
| `simple-kafka-outbox-management-starter` | 1.0.0 | — | Outbox 管理面 |
| `simple-mysql-route-starter` | 1.1.1 | — | javax MySQL 路由 |
| `simple-mysql-route-jakarta-starter` | — | 1.0.0 | Jakarta MySQL 路由 |
| `simple-s3-route-starter` | 1.0.0 | — | javax S3/MinIO/OSS 路由 |
| `simple-s3-route-jakarta-starter` | — | 1.0.0 | Jakarta S3 路由 |
| `simple-s3-client-starter` | 1.0.0 | — | 当前 javax S3 客户端 |
| `simple-s3-client-jakarta-starter` | — | 1.0.0 | Jakarta S3 客户端 |
| `s3` | 1.0.0 | — | 历史 S3 事件模型，仍已发布 |
| `s3-client-starter` | 2.1.0 | — | 历史 S3 客户端坐标，Feedback/Ticket 现有部署契约仍使用此线，新接入使用 `simple-*` 线 |
| `simple-prometheus-route-starter` | 1.0.0 | — | javax Prometheus 路由 |
| `simple-prometheus-route-jakarta-starter` | — | 1.0.0 | Jakarta Prometheus 路由 |
| `prometheus-core` | 2.0.1 | — | Prometheus 核心 |
| `simple-prometheus-client-starter` | 1.0.0 | — | 当前 Prometheus 客户端 |

文档：[Kafka](docs/middleware/Kafka.md)、[MySQL](docs/middleware/MySQL.md)、[S3](docs/middleware/S3.md)、[Prometheus](docs/middleware/Prometheus.md)。

## 业务领域

### IAM（人员身份）

| Artifact | javax | jakarta | 说明 |
|---|---:|---:|---|
| `simple-iam-core` | 1.1.0 | — | IAM 协议核心 |
| `simple-iam-server-core` / `simple-iam-server-starter` | 1.3.6 / 1.3.8 | — | OAuth 2.1、PKCE、RBAC、门户与开放 API |
| `simple-iam-client-core` | 1.0.4 | — | IAM 开放 API 契约 |
| `simple-iam-aksk-feign-client-starter` / `simple-iam-aksk-resttemplate-client-starter` | 1.0.3 / 1.0.3 | — | SB2 认证传输 |
| `simple-iam-aksk-feign-client-jakarta-starter` / `simple-iam-aksk-resttemplate-client-jakarta-starter` | — | 1.0.3 / 1.0.3 | Jakarta 认证传输 |
| `simple-iam-resource-core` / `simple-iam-resource-server-starter` | 1.0.0 / 1.0.0 | — | IAM Resource Provider |
| `simple-iam-resource-server-jakarta-starter` | — | 1.0.0 | Jakarta Resource Provider |
| `simple-iam-server-audit-listener-starter` | 1.0.0 | — | Server 审计 |
| `simple-iam-resource-audit-listener-starter` | 1.0.0 | — | javax Resource 审计 |
| `simple-iam-resource-audit-listener-jakarta-starter` | — | 1.0.1 | Jakarta Resource 审计 |
| `simple-iam-ldap-adapter-starter` / `simple-iam-oidc-adapter-starter` | 1.0.0 / 1.0.0 | — | LDAP/OIDC 登录适配器 |
| `simple-iam-captcha-adapter-starter` / `simple-iam-sms-b2m-adapter-starter` | 1.0.0 / 1.0.0 | — | 验证码/短信适配器 |
| `simple-iam-aksk-collaboration-starter` | 1.0.0 | — | IAM 与 AKSK 协作适配器 |
| `simple-iam-portal-gateway-adaptor-core` / `simple-iam-portal-gateway-adaptor-starter` | 1.0.0 / 1.0.0 | — | Portal Gateway 适配器 |

文档：[认证与授权-IAM](docs/business/认证与授权-IAM.md)。

### AKSK（服务身份与资源 Provider）

| Artifact | javax | jakarta | 说明 |
|---|---:|---:|---|
| `simple-aksk-core` | 3.0.2 | — | AKSK 协议核心 |
| `simple-aksk-server-core` / `simple-aksk-server-starter` | 3.0.4 / 3.2.2 | — | OAuth2、JWE、AKP/AKU 与应用授权 |
| `simple-aksk-client-core` | 3.0.0 | — | javax Client 核心 |
| `simple-aksk-redis-token-manager` / `simple-aksk-feign-redis-client-starter` / `simple-aksk-resttemplate-redis-client-starter` | 3.0.2 / 3.0.2 / 3.0.2 | — | Redis 客户端三件套 |
| `simple-aksk-client-core-jakarta` / `simple-aksk-redis-token-manager-jakarta-starter` / `simple-aksk-feign-redis-client-jakarta-starter` / `simple-aksk-resttemplate-redis-client-jakarta-starter` | — | 1.0.0 / 1.0.0 / 1.0.0 / 1.0.0 | Jakarta Redis 客户端四件套 |
| `simple-aksk-openapi-client-core` | 1.0.1 | — | 管理 OpenAPI 契约 |
| `simple-aksk-openapi-feign-client-starter` / `simple-aksk-openapi-resttemplate-client-starter` | 1.0.0 / 1.0.0 | — | SB2 OpenAPI 传输 |
| `simple-aksk-openapi-feign-client-jakarta-starter` / `simple-aksk-openapi-resttemplate-client-jakarta-starter` | — | 1.0.0 / 1.0.0 | Jakarta OpenAPI 传输 |
| `simple-aksk-resource-core` / `simple-aksk-resource-server-starter` | 3.0.0 / 3.1.0 | — | javax AKSK Resource Provider |
| `simple-aksk-resource-server-jakarta-starter` | — | 1.0.0 | Jakarta Resource Provider |
| `simple-aksk-resource-server-metrics-starter` / `simple-aksk-server-metrics-starter` | 1.0.0 / 1.0.0 | — | Resource/Server 指标 |
| `simple-aksk-security-context-starter` | 1.0.3 | — | 历史 Security Context 扩展 |
| `simple-aksk-resource-audit-listener-starter` | 3.0.0 | — | Resource 审计 |
| `simple-aksk-resource-audit-listener-jakarta-starter` | — | 1.0.0 | Jakarta Resource 审计 |
| `simple-aksk-server-audit-listener-starter` | 3.1.0 | — | Server 审计 |
| `simple-resource-server-core` / `simple-resource-server-starter` | 1.1.1 / 1.1.1 | — | 公共 Resource 层 |
| `simple-resource-server-jakarta-starter` | — | 1.0.0 | Jakarta 公共 Resource 层 |
| `simple-data-permission-core` / `simple-data-permission-spring-mvc-starter` | 1.2.0 / 1.1.0 | — | DATA 权限计划 |
| `simple-data-permission-spring-mvc-jakarta-starter` | — | 1.1.0 | Jakarta DATA 权限 |
| `simple-application-authorization-core` / `simple-owner-authorization-collaboration-core` | 1.0.1 / 1.0.0 | — | 应用授权与归属协作 |
| `simple-aksk-httpsession-token-manager` / `simple-aksk-feign-httpsession-client-starter` / `simple-aksk-resttemplate-httpsession-client-starter` | 1.0.1 / 1.0.1 / 1.0.1 | — | 历史 HttpSession 客户端 |
| `simple-captcha-starter` | 1.0.0 | — | 通用验证码组件 |

文档：[认证与授权-AKSK](docs/business/认证与授权-AKSK.md)。

### KMS、License、CRM、Feedback、Ticket

| 领域 | Artifact | javax | jakarta | 说明 |
|---|---|---:|---:|---|
| KMS | `smart-kms-core` / `smart-kms-server-starter` | 2.0.0 / 2.0.2 | — | 密钥领域核心与 Server |
| KMS | `simple-kms-client-core` / `simple-kms-aksk-feign-client-starter` / `simple-kms-aksk-resttemplate-client-starter` | 2.0.0 / 2.0.0 / 2.0.0 | — | KMS 调用契约与 SB2 传输 |
| KMS | `simple-kms-aksk-feign-client-jakarta-starter` / `simple-kms-aksk-resttemplate-client-jakarta-starter` | — | 1.0.0 / 1.0.0 | Jakarta KMS 传输 |
| License | `smart-license-core` / `smart-license-server-starter` | 2.0.0 / 2.0.0 | — | License 契约与 Server |
| License | `smart-license-limiter-policy-adaptor-starter` / `simple-license-audit-listener-starter` | 2.0.0 / 2.0.0 | — | 可选限流策略投影与审计 |
| License | `simple-license-client-core` / `simple-license-verifier-starter` | 2.0.1 / 2.0.1 | — | 远程客户端、在线/离线验证 |
| License | `simple-license-aksk-feign-client-starter` / `simple-license-aksk-resttemplate-client-starter` | 2.0.0 / 2.0.0 | — | SB2 AKSK 传输 |
| License | `simple-license-aksk-feign-client-jakarta-starter` / `simple-license-aksk-resttemplate-client-jakarta-starter` | — | 2.0.0 / 2.0.0 | Jakarta AKSK 传输 |
| CRM | `simple-crm-server-core` / `simple-crm-server-starter` | 2.1.0 / 1.1.0 | — | CRM 领域核心与 Server |
| CRM | `simple-crm-client-core` / `simple-crm-aksk-feign-client-starter` / `simple-crm-aksk-resttemplate-client-starter` | 1.1.0 / 1.1.0 / 1.1.0 | — | SB2 CRM 客户端 |
| CRM | `simple-crm-aksk-feign-client-jakarta-starter` / `simple-crm-aksk-resttemplate-client-jakarta-starter` | — | 1.1.0 / 1.1.0 | Jakarta 客户端 |
| CRM | `simple-crm-audit-listener-starter` | 1.0.0 | — | CRM 审计 |
| Feedback | `simple-feedback-server-core` / `simple-feedback-server-starter` / `simple-feedback-iam-adaptor-starter` | 1.0.1 / 1.0.1 / 1.0.1 | — | Feedback 服务与 IAM 协作 |
| Feedback | `simple-feedback-client-core` / `simple-feedback-aksk-feign-client-starter` / `simple-feedback-aksk-resttemplate-client-starter` | 1.0.1 / 1.0.1 / 1.0.1 | — | SB2 Feedback 客户端 |
| Feedback | `simple-feedback-aksk-feign-client-jakarta-starter` / `simple-feedback-aksk-resttemplate-client-jakarta-starter` | — | 1.0.1 / 1.0.1 | Jakarta Feedback 客户端 |
| Ticket | `simple-ticket-server-core` / `simple-ticket-server-starter` / `simple-ticket-iam-adaptor-starter` | 1.0.0 / 1.0.1 / 1.0.0 | — | 工单领域、Server 与 IAM 适配 |

文档：[KMS](docs/business/密钥管理-KMS.md)、[License Server](sdk/license/server/smart-license-server-starter/README.md)、[CRM](docs/business/客户管理-CRM.md)、[Feedback](docs/business/产品反馈-Feedback.md)、[Ticket](docs/business/业务工单-Ticket.md)。KMS contract 是 OpenAPI 文档模块，不构建 Maven artifact；License runtime 是部署宿主，不是 SDK 发布物；Ticket Web 是外部应用。

## 通用工具

| Artifact | javax | jakarta | 说明 |
|---|---:|---:|---|
| `simple-xff-capture-core` | 1.1.1 | — | X-Forwarded-For 事件核心 |
| `simple-xff-capture-starter` | 1.1.2 | — | javax Servlet 事实捕获 |
| `simple-xff-capture-jakarta-starter` | — | 1.0.0 | Jakarta 捕获装配 |
| `simple-xff-capture-audit-core` / `simple-xff-capture-audit-listener-starter` / `simple-xff-capture-audit-es-persistence-provider-starter` | 1.1.1 / 1.1.1 / 1.1.1 | — | XFF 审计核心、监听与 ES Provider |
| `simple-xff-capture-audit-listener-jakarta-starter` / `simple-xff-capture-audit-es-persistence-provider-jakarta-starter` | — | 1.0.0 / 1.0.0 | Jakarta XFF 审计挂件 |
| `condition-expression-parser-starter` | 1.0.5 | — | 条件表达式解析 |
| `condition-expression-parser-jakarta-starter` | — | 1.0.0 | Jakarta 表达式解析 |
| `smart-keyword-sensitive-starter` | 1.0.5 | — | 关键词脱敏 |
| `simple-ip-sensitive-starter` | 1.0.0 | — | IP/IPv6/CIDR 脱敏 |
| `natural-language-parser-starter` | 1.1.4 | — | 自然语言查询解析 |
| `mail-client-starter` | 2.0.0 | — | 邮件收发 |
| `b2m-sms-client-starter` | 2.0.1 | — | B2M 短信客户端 |
| `simple-doc-template-starter` | 1.3.0 | — | 文档模板渲染 |
| `log-truncate-starter` | 1.1.0 | — | UTF-8 日志截断 |
| `task-retry-starter` | 2.0.0 | — | 本地任务重试 |
| `task-retry-jakarta-starter` | — | 1.0.0 | Jakarta 任务重试 |
| `smart-middleware-ops-server-starter` | 1.1.1 | — | 中间件运维观测 |

文档：[HTTP](docs/tool/HTTP.md)、[表达式解析](docs/tool/表达式解析.md)、[任务重试](docs/tool/任务重试.md)、[关键词脱敏](docs/tool/关键词脱敏.md)、[IP 脱敏](docs/tool/IP脱敏.md)、[自然语言解析](docs/tool/自然语言解析.md)、[mail](docs/tool/mail.md)、[sms](docs/tool/sms.md)、[文档模板](docs/tool/文档模板.md)、[日志](docs/tool/日志.md)、[中间件运维](docs/tool/中间件运维.md)。

## 版本组合边界

- 表格展示的是各 artifact 自身已经发布的版本，不承诺把所有“最新版本”直接拼成一个可运行组合；请以对应模块 README 的版本映射和 POM 依赖为准。
- `smart-redis-limiter-starter:2.2.1` 的正式 POM 仍固定 `smart-redis-limiter-core:2.2.0`；`smart-redis-limiter-core:2.3.0` 是独立发布坐标，不能据表强行替换 runtime 依赖。
- `simple-kafka-outbox-starter:1.0.1` 的内部 publisher 兼容固定值仍可能是 `1.1.0`，publisher 的最新独立坐标是 `1.1.1`；升级以 outbox POM 为准。
- Server、client、resource、audit 的版本线可以独立发布；跨模块联调必须使用各领域文档给出的兼容行。
- `javax` 与 `jakarta` artifact 不混用 Spring API；纯 Java core 是否跨线复用，以该模块 README 的依赖声明为准。

## 许可证与链接

Apache License 2.0

- [GitHub Issues](https://github.com/Sure-Zzzzzz/normal-sdks/issues)
- [Maven Central](https://central.sonatype.com/search?q=io.github.sure-zzzzzz)
