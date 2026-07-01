# Normal SDKs

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3%20%7C%202.4%20%7C%202.7-brightgreen.svg)](https://spring.io/projects/spring-boot)

> 企业级通用 Spring Boot Starter 集合，提供开箱即用的基础设施组件，助力快速开发。

## 📦 SDK 目录

### 🔍 Elasticsearch

#### 路由

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-elasticsearch-route-starter](sdk/route/elasticsearch/simple-elasticsearch-route-starter) | 1.1.0 | 多数据源路由（日期分片 + 异步写 + 可配置代理） | [README](sdk/route/elasticsearch/simple-elasticsearch-route-starter/README.md) |

#### 搜索

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-elasticsearch-search-core](sdk/search/elasticsearch/simple-elasticsearch-search-core) | 1.0.12 | 搜索核心库（事件发布） | - |
| [simple-elasticsearch-search-starter](sdk/search/elasticsearch/simple-elasticsearch-search-starter) | 1.6.7 | 查询框架（API / NL / 表达式 / countOnly），内置集成 route-starter | [README](sdk/search/elasticsearch/simple-elasticsearch-search-starter/README.md) |
| [simple-elasticsearch-search-metrics-starter](sdk/metrics/elasticsearch/simple-elasticsearch-search-metrics-starter) | 1.0.2 | 指标采集 | [README](sdk/metrics/elasticsearch/simple-elasticsearch-search-metrics-starter/README.md) |
| [simple-elasticsearch-search-audit-listener-starter](sdk/audit/search/elasticsearch/simple-elasticsearch-search-audit-listener-starter) | 1.0.4 | 审计事件 | [README](sdk/audit/search/elasticsearch/simple-elasticsearch-search-audit-listener-starter/README.md) |

**版本兼容**：

| search-starter | search-core | route-starter | metrics-starter | audit-listener-starter |
|----------------|-------------|---------------|-----------------|----------------------|
| 1.6.7 | 1.0.12 | 1.0.10        | 1.0.2 | 1.0.4 |
| 1.6.6 | 1.0.11 | 1.0.10        | 1.0.1 | 1.0.3 |
| 1.6.5 | 1.0.10 | 1.0.10        | 1.0.0 | 1.0.2 |
| 1.6.4 | 1.0.10 | 1.0.10        | - | 1.0.2 |
| 1.6.3 | 1.0.10 | 1.0.10        | - | 1.0.2 |
| 1.6.2 | 1.0.10 | 1.0.10        | - | 1.0.2 |
| 1.6.1 | 1.0.8 | 1.0.10        | - | 1.0.1 |
| 1.6.0 | 1.0.8 | 1.0.10        | - | 1.0.1 |
| 1.5.8 | 1.0.8 | 1.0.8         | - | 1.0.1 |
| 1.5.7 | 1.0.7 | 1.0.8         | - | 1.0.0 |
| 1.5.6 | 1.0.6 | 1.0.8         | - | 1.0.0 |
| 1.5.5 | 1.0.5 | 1.0.8         | - | 1.0.0 |
| 1.5.4 | 1.0.5 | 1.0.8         | - | 1.0.0 |
| 1.5.3 | 1.0.5 | 1.0.7         | - | 1.0.0 |
| 1.5.2 | 1.0.5 | 1.0.7         | - | 1.0.0 |
| 1.5.1 | 1.0.4 | 1.0.7         | - | 1.0.0 |
| 1.5.0 | 1.0.4 | 1.0.7         | - | 1.0.0 |
| 1.4.0 | 1.0.4 | 1.0.7         | - | 1.0.0 |
| 1.3.1 | 1.0.3 | 1.0.7         | - | 1.0.0 |
| 1.3.0 | 1.0.3 | 1.0.7         | - | 1.0.0 |
| 1.2.1 | 1.0.1 | 1.0.5         | - | 1.0.0 |
| 1.2.0 | 1.0.1 | 1.0.5         | - | 1.0.0 |
| ≤ 1.1.x | - | 1.0.5         | - | - |

**核心特性**：
- 支持 ES 6.x 和 7.x+
- 支持 Spring Boot 2.3 / 2.4 / 2.7
- 零代码配置驱动的查询和聚合
- RESTful API 自动生成
- 查询/聚合执行后自动发布事件，支持审计和监控扩展

---

### 💾 缓存

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [smart-cache-starter](sdk/cache/smart-cache-starter) | 1.1.2 | 两级缓存（L1 本地 Caffeine + L2 分布式 Redis） | [README](sdk/cache/smart-cache-starter/README.md) |

**核心特性**：
- L1 本地缓存 + L2 Redis 分布式缓存，Pub/Sub 多实例 L1 失效广播
- 防护缓存穿透、缓存击穿、缓存雪崩
- 注解式 API：`@SmartCache`、`@SmartCacheEvict`、`@SmartCachePut`
- 自定义 L2 TTL，支持 `CachePreloadHandler` 预加载即将过期的缓存

---

### 🔒 Redis

#### 锁

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-redis-lock-starter](sdk/lock/redis/simple-redis-lock-starter) | 1.0.1 | 分布式锁（SETNX + 过期时间，Lua 原子解锁） | [README](sdk/lock/redis/simple-redis-lock-starter/README.md) |

#### 限流

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-redis-limiter-starter](sdk/limiter/redis/simple-redis-limiter-starter) | 1.0.1 | 令牌桶 + Set 去重，定时重置，适合配额制和幂等控制（每日 API 配额、支付去重、消息去重、定时任务保护） | [README](sdk/limiter/redis/simple-redis-limiter-starter/README.md) |
| [smart-redis-limiter-core](sdk/limiter/redis/smart-redis-limiter-core) | 1.1.6 | 滑动窗口 / 固定窗口限流核心库 | - |
| [smart-redis-limiter-starter](sdk/limiter/redis/smart-redis-limiter-starter) | 1.1.3 | 滑动窗口 / 固定窗口限流（Lua 脚本），适合精度限流（防短信突刺、支付保护、严格 API 限速） | [README](sdk/limiter/redis/smart-redis-limiter-starter/README.md) |
| [smart-redis-limiter-metrics-starter](sdk/metrics/limiter/smart-redis-limiter-metrics-starter) | 1.0.0 | 指标采集 | [README](sdk/metrics/limiter/smart-redis-limiter-metrics-starter/README.md) |
| [smart-redis-limiter-audit-listener-starter](sdk/audit/limiter/smart-redis-limiter-audit-listener-starter) | 1.0.0 | 审计事件 | [README](sdk/audit/limiter/smart-redis-limiter-audit-listener-starter/README.md) |

**核心特性**：
- 注解驱动和拦截器模式
- 滑动窗口限流，Lua 脚本保证原子性
- 基于 Spring 事件机制，零侵入接入审计，内置异步线程池（4核/20最大/2000队列）

---

### 🔄 重试

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [task-retry-starter](sdk/retry/task-retry-starter) | 1.0.1 | 本地任务重试框架 | [README](sdk/retry/task-retry-starter/README.md) |
| [redis-retry-starter](sdk/retry/redis-retry-starter) | 1.0.0 | Redis 持久化重试，支持跨实例 | [README](sdk/retry/redis-retry-starter/README.md) |

**核心特性**：
- 灵活的重试策略（指数退避、固定延迟）
- 支持持久化和跨实例重试

---

### 🔐 认证与授权（AKSK 2.x）

#### Server

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-aksk-core](sdk/auth/aksk/simple-aksk-core) | 2.0.0 | AKSK 核心库 | [README](sdk/auth/aksk/simple-aksk-core/README.md) |
| [simple-aksk-server-core](sdk/auth/aksk/server/simple-aksk-server-core) | 2.0.3 | Server 核心库 | [README](sdk/auth/aksk/server/simple-aksk-server-core/README.md) |
| [simple-aksk-server-starter](sdk/auth/aksk/server/simple-aksk-server-starter) | 2.0.3 | 认证服务器（OAuth2 / JWE / Redis 必需 / OAuth2 端点限流） | [README](sdk/auth/aksk/server/simple-aksk-server-starter/README.md) |
| [simple-aksk-server-audit-listener-starter](sdk/audit/aksk/simple-aksk-server-audit-listener-starter) | 2.0.1 | Server Token 审计事件 | [README](sdk/audit/aksk/simple-aksk-server-audit-listener-starter/README.md) |

**Server 版本兼容**：

| server-starter | server-core | server-audit-listener-starter | 说明 |
|----------------|-------------|-------------------------------|------|
| 2.0.3 | 2.0.3 | 2.0.1 | Redis 必需化，新增 OAuth2 Security Filter 端点限流，复用 smart-limiter |
| 2.0.2 | 2.0.2 | 2.0.0 | Client Entity 两级缓存 |
| 2.0.0 | 2.0.1 | 2.0.0 | AKSK Server 2.x 初始链路，JWE Token |

**核心特性**：
- OAuth2 标准协议（Authorization Server）
- 双层级 AKSK 管理（平台级 AKP / 用户级 AKU）
- JWE Token（A256GCMKW + A256GCM 加密，scope 等敏感信息不裸奔，HTTPS 被截获也无法解读）
- Redis 必需基础设施，支持 Token 缓存、撤销同步、多实例 L1 缓存失效广播
- OAuth2 端点限流（`/oauth2/token`、`/oauth2/introspect`、`/oauth2/revoke`），默认按 clientId provider 维度计数

#### Client

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-aksk-client-core](sdk/auth/aksk/client/simple-aksk-client-core) | 2.0.0 | Client 核心库 | [README](sdk/auth/aksk/client/simple-aksk-client-core/README.md) |
| [simple-aksk-redis-token-manager](sdk/auth/aksk/client/redis/simple-aksk-redis-token-manager) | 2.0.1 | Redis Token 管理器（L1 本地 + L2 Redis 二级缓存，SHA-256 cacheKey 防多租户碰撞串号） | [README](sdk/auth/aksk/client/redis/simple-aksk-redis-token-manager/README.md) |
| [simple-aksk-feign-redis-client-starter](sdk/auth/aksk/client/redis/simple-aksk-feign-redis-client-starter) | 2.0.1 | Feign 客户端（Redis） | [README](sdk/auth/aksk/client/redis/simple-aksk-feign-redis-client-starter/README.md) |
| [simple-aksk-resttemplate-redis-client-starter](sdk/auth/aksk/client/redis/simple-aksk-resttemplate-redis-client-starter) | 2.0.1 | RestTemplate 客户端（Redis） | [README](sdk/auth/aksk/client/redis/simple-aksk-resttemplate-redis-client-starter/README.md) |

**Client 版本兼容**：

| feign-redis-client-starter | resttemplate-redis-client-starter | redis-token-manager | client-core | 说明 |
|----------------------------|-----------------------------------|---------------------|-------------|------|
| 2.0.1 | 2.0.1 | 2.0.1 | 2.0.0 | SHA-256 cacheKey 防多租户碰撞串号 |
| 2.0.0 | 2.0.0 | 2.0.0 | 2.0.0 | AKSK Client 2.x 初始链路 |

**核心特性**：
- Feign / RestTemplate 客户端各自独立注册，开箱即用
- L1 本地缓存 + L2 Redis 二级缓存

#### Resource

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-aksk-resource-core](sdk/auth/aksk/resource/simple-aksk-resource-core) | 2.0.0 | Resource 核心库 | [README](sdk/auth/aksk/resource/simple-aksk-resource-core/README.md) |
| [simple-aksk-resource-server-starter](sdk/auth/aksk/resource/simple-aksk-resource-server-starter) | 2.0.1 | 资源服务器（Introspect 远程校验 / context-path-aware 路径归一化） | [README](sdk/auth/aksk/resource/simple-aksk-resource-server-starter/README.md) |
| [simple-aksk-resource-audit-listener-starter](sdk/audit/aksk/simple-aksk-resource-audit-listener-starter) | 2.0.0 | Resource 访问审计事件 | [README](sdk/audit/aksk/simple-aksk-resource-audit-listener-starter/README.md) |

**Resource 版本兼容**：

| resource-server-starter | resource-core | resource-audit-listener-starter | 说明 |
|-------------------------|---------------|---------------------------------|------|
| 2.0.1 | 2.0.0 | 2.0.0 | 支持 server.servlet.context-path 场景下路径归一化 |
| 2.0.0 | 2.0.0 | 2.0.0 | AKSK Resource 2.x 初始链路，Introspect 远程校验 |

**核心特性**：
- Introspect 远程校验（本地缓存 + 兜底降级）
- Scope 权限控制，精确匹配 + 通配符防护
- `@RequireExpression` 等权限注解，支持 SpEL 表达式

---

### 🔐 认证与授权（AKSK 1.x）

#### Server

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-aksk-server-core](sdk/auth/aksk/server/simple-aksk-server-core) | 1.0.4 | Server 核心库 | [README](sdk/auth/aksk/server/simple-aksk-server-core/README.md) |
| [simple-aksk-server-starter](sdk/auth/aksk/server/simple-aksk-server-starter) | 1.1.3 | 认证服务器 | [USER_MANUAL](sdk/auth/aksk/USER_MANUAL.md) |

**核心特性**：
- OAuth2 认证（Authorization Server）
- 双层级 AKSK 管理（平台级 AKP / 用户级 AKU）
- JWS Token（明文 payload，scope 等敏感信息在 HTTPS 被截获时存在泄露风险，建议升级到 2.x）

#### Client

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-aksk-client-core](sdk/auth/aksk/client/simple-aksk-client-core) | 1.0.1 | Client 核心库 | [README](sdk/auth/aksk/client/simple-aksk-client-core/README.md) |
| [simple-aksk-redis-token-manager](sdk/auth/aksk/client/redis/simple-aksk-redis-token-manager) | 1.1.0 | Redis Token 管理器（L1+L2 缓存） | [README](sdk/auth/aksk/client/redis/simple-aksk-redis-token-manager/README.md) |
| [simple-aksk-feign-redis-client-starter](sdk/auth/aksk/client/redis/simple-aksk-feign-redis-client-starter) | 1.1.0 | Feign 客户端（Redis） | [README](sdk/auth/aksk/client/redis/simple-aksk-feign-redis-client-starter/README.md) |
| [simple-aksk-resttemplate-redis-client-starter](sdk/auth/aksk/client/redis/simple-aksk-resttemplate-redis-client-starter) | 1.1.0 | RestTemplate 客户端（Redis） | [README](sdk/auth/aksk/client/redis/simple-aksk-resttemplate-redis-client-starter/README.md) |
| [simple-aksk-httpsession-token-manager](sdk/auth/aksk/client/http-session/simple-aksk-httpsession-token-manager) | 1.0.1 | HttpSession Token 管理器 | [README](sdk/auth/aksk/client/http-session/simple-aksk-httpsession-token-manager/README.md) |
| [simple-aksk-feign-httpsession-client-starter](sdk/auth/aksk/client/http-session/simple-aksk-feign-httpsession-client-starter) | 1.0.1 | Feign 客户端（HttpSession） | [README](sdk/auth/aksk/client/http-session/simple-aksk-feign-httpsession-client-starter/README.md) |
| [simple-aksk-resttemplate-httpsession-client-starter](sdk/auth/aksk/client/http-session/simple-aksk-resttemplate-httpsession-client-starter) | 1.0.1 | RestTemplate 客户端（HttpSession） | [README](sdk/auth/aksk/client/http-session/simple-aksk-resttemplate-httpsession-client-starter/README.md) |

**核心特性**：
- Redis Token 管理器 / HttpSession Token 管理器双模式
- Feign / RestTemplate 客户端

#### Resource

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-aksk-resource-core](sdk/auth/aksk/resource/simple-aksk-resource-core) | 1.0.3 | Resource 核心库 | [README](sdk/auth/aksk/resource/simple-aksk-resource-core/README.md) |
| [simple-aksk-resource-server-starter](sdk/auth/aksk/resource/simple-aksk-resource-server-starter) | 1.0.6 | 资源服务器（JWT 本地验签 / Introspect 远程校验） | [README](sdk/auth/aksk/resource/simple-aksk-resource-server-starter/README.md) |
| [simple-aksk-security-context-starter](sdk/auth/aksk/resource/simple-aksk-security-context-starter) | 1.0.3 | 安全上下文（Header 解析） | [README](sdk/auth/aksk/resource/simple-aksk-security-context-starter/README.md) |

**核心特性**：
- 资源服务器双模式：JWT 本地验签（payload 明文可读，截获即泄露）/ Introspect 远程校验
- Scope 权限控制
- ⚠️ 安全提醒：JWS 明文 Token 在 HTTPS 被截获时，scope 等敏感信息直接暴露，建议升级到 2.x 使用 JWE

---

### 🔒 数据安全

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [smart-keyword-sensitive-starter](sdk/sensitive/keyword/smart-keyword-sensitive-starter) | 1.0.5 | 关键词脱敏（NLR + 规则引擎，三级智能降级） | [README](sdk/sensitive/keyword/smart-keyword-sensitive-starter/README.md) |
| [simple-ip-sensitive-starter](sdk/sensitive/ip/simple-ip-sensitive-starter) | 1.0.0 | IP 脱敏（IPv4/IPv6/CIDR，Jackson 注解支持） | [README](sdk/sensitive/ip/simple-ip-sensitive-starter/README.md) |

---

### 🧠 自然语言处理

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [natural-language-parser-starter](sdk/natural-language/parser/natural-language-parser-starter) | 1.1.4 | 自然语言查询解析器 | [README](sdk/natural-language/parser/natural-language-parser-starter/README.md) |

**核心特性**：
- 智能解析中英文查询，支持 15+ 种操作符
- AND/OR 逻辑组合，聚合查询，排序分页
- 策略模式 + 状态机设计，高性能易扩展

---

### 🎯 表达式解析

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [condition-expression-parser-starter](sdk/expression/condition/condition-expression-parser-starter) | 1.0.5 | 条件表达式解析器（ANTLR 4） | [README](sdk/expression/condition/condition-expression-parser-starter/README.md) |

**核心特性**：
- 6 大类运算符：比较、集合、模糊匹配、空值检查、逻辑运算、括号优先级
- Visitor 模式输出 AST，可转换为 SQL、ES DSL、MongoDB Query 等
- 中英文关键字，大小写不敏感

---

### 📧 通信

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [mail-client-starter](sdk/mail/mail-client-starter) | 1.0.0 | 邮件发送客户端 | [README](sdk/mail/mail-client-starter/README.md) |
| [b2m-sms-client-starter](sdk/sms/b2m/b2m-sms-client-starter) | 1.0.0 | B2M 短信客户端 | [README](sdk/sms/b2m/b2m-sms-client-starter/README.md) |

---

### ☁️ 对象存储

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [s3-client-starter](sdk/oss/s3-client-starter) | 1.0.1-SNAPSHOT | AWS S3 兼容存储客户端（支持 S3 / MinIO / 阿里云 OSS） | [README](sdk/oss/s3-client-starter/README.md) |

**核心特性**：
- 文件上传下载、预签名 URL、自动分片上传

---

### 📄 文档模板

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-doc-template-starter](sdk/template/simple-doc-template-starter) | 1.0.2 | 文档模板渲染（Word .docx，统一 `[suredt.指令:key]` 语法） | [README](sdk/template/simple-doc-template-starter/README.md) |

**核心特性**：
- 统一模板语法 `[suredt.指令:key]`，切换输出格式无需修改模板
- 支持 Word（.docx）：变量替换、条件块、循环展开、图片替换、图表数据填充、页眉页脚
- `classpath:` / `file:` / `http:` 多来源模板加载

---

### 📝 日志

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [log-truncate-starter](sdk/log/truncate/log-truncate-starter) | 1.0.0 | 日志截断工具 | [README](sdk/log/truncate/log-truncate-starter/README.md) |

---

### 📊 监控

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [prometheus-core](sdk/prometheus/prometheus-core) | 1.0.0 | Prometheus 核心库 | [README](sdk/prometheus/prometheus-core/README.md) |
| [prometheus-client-starter](sdk/prometheus/prometheus-client-starter) | 1.0.0 | Prometheus 客户端 | [README](sdk/prometheus/prometheus-client-starter/README.md) |

---

## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。

## 🔗 相关链接

- [GitHub Issues](https://github.com/Sure-Zzzzzz/normal-sdks/issues)
- [Maven Central](https://central.sonatype.com/search?q=io.github.sure-zzzzzz)

---

**Made with ❤️ by Sure-Zzzzzz**