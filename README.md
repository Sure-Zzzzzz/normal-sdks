# Normal SDKs

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.2%20%7C%202.3%20%7C%202.4%20%7C%202.7-brightgreen.svg)](https://spring.io/projects/spring-boot)

> 企业级通用 Spring Boot Starter 集合，提供开箱即用的基础设施组件，助力快速开发。

## 📦 SDK 目录

### 🔍 Elasticsearch

#### 路由

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-elasticsearch-route-starter](sdk/route/elasticsearch/simple-elasticsearch-route-starter) | 1.2.1 | 多数据源路由（日期分片 + 异步写 + 可配置代理 + ES 兼容公共 Helper） | [README](sdk/route/elasticsearch/simple-elasticsearch-route-starter/README.md) |

#### 搜索

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-elasticsearch-search-core](sdk/search/elasticsearch/simple-elasticsearch-search-core) | 1.0.12 | 搜索核心库（事件发布） | [README](sdk/search/elasticsearch/simple-elasticsearch-search-core/README.md) |
| [simple-elasticsearch-search-starter](sdk/search/elasticsearch/simple-elasticsearch-search-starter) | 1.7.2 | 查询框架（API / NL / 表达式 / countOnly / `_id` 查询 / 通配符具体索引匹配） | [README](sdk/search/elasticsearch/simple-elasticsearch-search-starter/README.md) |
| [simple-elasticsearch-search-metrics-starter](sdk/metrics/elasticsearch/simple-elasticsearch-search-metrics-starter) | 1.0.2 | 指标采集 | [README](sdk/metrics/elasticsearch/simple-elasticsearch-search-metrics-starter/README.md) |
| [simple-elasticsearch-search-audit-listener-starter](sdk/audit/search/elasticsearch/simple-elasticsearch-search-audit-listener-starter) | 1.0.4 | 审计事件 | [README](sdk/audit/search/elasticsearch/simple-elasticsearch-search-audit-listener-starter/README.md) |

**Search 版本兼容**：

| search-starter | search-core | route-starter | metrics-starter | audit-listener-starter |
|----------------|-------------|---------------|-----------------|----------------------|
| 1.7.2 | 1.0.12 | 1.2.0         | 1.0.2 | 1.0.4 |
| 1.7.1 | 1.0.12 | 1.2.0         | 1.0.2 | 1.0.4 |
| 1.7.0 | 1.0.12 | 1.2.0         | 1.0.2 | 1.0.4 |
| 1.6.10 | 1.0.12 | 1.1.2         | 1.0.2 | 1.0.4 |
| 1.6.9 | 1.0.12 | 1.1.2         | 1.0.2 | 1.0.4 |
| 1.6.8 | 1.0.12 | 1.0.10        | 1.0.2 | 1.0.4 |
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
- 支持 Spring Boot 2.2 / 2.3 / 2.4 / 2.7
- 零代码配置驱动的查询和聚合
- RESTful API 自动生成
- 查询/聚合执行后自动发布事件，支持审计和监控扩展

#### 写入

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-elasticsearch-persistence-core](sdk/persistence/elasticsearch/simple-elasticsearch-persistence-core) | 1.0.3 | 写入核心模型（request / option / result / query） | [README](sdk/persistence/elasticsearch/simple-elasticsearch-persistence-core/README.md) |
| [simple-elasticsearch-persistence-starter](sdk/persistence/elasticsearch/simple-elasticsearch-persistence-starter) | 1.1.1 | 写侧框架（index / create / update / delete / bulk / byQuery / create 冲突转 update），内置集成 route-starter | [README](sdk/persistence/elasticsearch/simple-elasticsearch-persistence-starter/README.md) |
| [simple-elasticsearch-persistence-audit-listener-starter](sdk/audit/persistence/elasticsearch/simple-elasticsearch-persistence-audit-listener-starter) | 1.0.0 | 写侧审计事件监听器 | [README](sdk/audit/persistence/elasticsearch/simple-elasticsearch-persistence-audit-listener-starter/README.md) |

**Persistence 版本兼容**：

| persistence-starter | persistence-core | route-starter | audit-listener-starter |
|---------------------|------------------|---------------|------------------------|
| 1.1.1 | 1.0.3 | 1.2.1 | 1.0.0 |
| 1.1.0 | 1.0.2 | 1.2.0 | 1.0.0 |
| 1.0.2 | 1.0.2 | 1.1.2 | - |
| 1.0.1 | 1.0.1 | 1.1.2 | - |
| 1.0.0 | 1.0.1 | 1.1.2 | - |

**Persistence 核心特性**：
- 统一写侧入口：index / create / update / delete / bulk / updateByQuery / deleteByQuery
- 自动继承 route 的多数据源路由、日期分片索引渲染和 async-write 规则
- 支持 `DocumentPreProcessor` 写入前扩展链、稳定 ID 生成和字段标准化 Helper
- 支持 routing / pipeline / refreshPolicy / retryOnConflict / detectNoop 等写入参数透传
- 支持 bulk 分批、失败后停止、失败明细与可重试分类
- 支持写侧审计监听，覆盖单条写入、bulk、byQuery 和错误事件
- 支持 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9，覆盖 ES 6.x / 7.x

---

### 💾 缓存

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [smart-cache-starter](sdk/cache/smart-cache-starter) | 2.1.0 | 两级缓存（L1 本地 Caffeine + L2 分布式 Redis） | [README](sdk/cache/smart-cache-starter/README.md) |

**核心特性**：
- L1 本地缓存 + L2 Redis 分布式缓存，Pub/Sub 多实例 L1 失效广播
- 防护缓存穿透、缓存击穿、缓存雪崩
- 注解式 API：`@SmartCache`、`@SmartCacheEvict`、`@SmartCachePut`
- 自定义 L2 TTL，支持 `CachePreloadHandler` 预加载即将过期的缓存

---

### 🗄️ MySQL

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-mysql-route-starter](sdk/route/mysql/simple-mysql-route-starter) | 1.1.1 | MySQL 数据源路由（Route-owned datasource、显式 primary datasource、单事务 datasource 边界） | [README](sdk/route/mysql/simple-mysql-route-starter/README.md) |

---

### 🔒 Redis

#### 路由

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-redis-route-starter](sdk/route/redis/simple-redis-route-starter) | 1.1.0 | 多数据源路由（按 Redis key 路由，支持 standalone / cluster 及混合部署，不注册全局 Redis Bean） | [README](sdk/route/redis/simple-redis-route-starter/README.md) |

#### 锁

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-redis-lock-starter](sdk/lock/redis/simple-redis-lock-starter) | 1.2.1 | 分布式锁（SETNX + 过期时间，Lua 原子解锁；route 模式按 lockKey 路由到不同 Redis datasource） | [README](sdk/lock/redis/simple-redis-lock-starter/README.md) |

#### 限流

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-redis-limiter-starter](sdk/limiter/redis/simple-redis-limiter-starter) | 1.0.1 | 令牌桶 + Set 去重，定时重置，适合配额制和幂等控制（每日 API 配额、支付去重、消息去重、定时任务保护） | [README](sdk/limiter/redis/simple-redis-limiter-starter/README.md) |
| [smart-redis-limiter-core](sdk/limiter/redis/smart-redis-limiter-core) | 2.1.0 | 滑动窗口 / 固定窗口限流核心库（事件契约、动态策略模型） | [README](sdk/limiter/redis/smart-redis-limiter-core/README.md) |
| [smart-redis-limiter-starter](sdk/limiter/redis/smart-redis-limiter-starter) | 2.0.0 | 滑动窗口 / 固定窗口限流（Lua 脚本，2.x 基于 simple-redis-route-starter 原生路由），适合精度限流（防短信突刺、支付保护、严格 API 限速） | [README](sdk/limiter/redis/smart-redis-limiter-starter/README.md) |
| [smart-redis-limiter-management-starter](sdk/limiter/redis/smart-redis-limiter-management-starter) | 1.0.0 | 动态策略管理（REST 接口 + 持久化，供运营侧调整限流规则） | [README](sdk/limiter/redis/smart-redis-limiter-management-starter/README.md) |
| [smart-redis-limiter-metrics-starter](sdk/metrics/limiter/smart-redis-limiter-metrics-starter) | 1.0.0 | 指标采集 | [README](sdk/metrics/limiter/smart-redis-limiter-metrics-starter/README.md) |
| [smart-redis-limiter-audit-listener-starter](sdk/audit/limiter/smart-redis-limiter-audit-listener-starter) | 2.0.0 | 限流执行审计（Route / fallback / 动态策略快照） | [README](sdk/audit/limiter/smart-redis-limiter-audit-listener-starter/README.md) |

**smart-redis-limiter 版本映射**：

| 架构线 | limiter-starter | limiter-core | route-starter | management-starter | metrics-starter | audit-listener-starter |
|--------|-----------------|--------------|---------------|--------------------|-----------------|----------------------|
| 当前 2.x 架构（已发布） | 2.0.0 | 2.1.0 | 1.1.0 | 1.0.0 | 尚未发布 | 2.0.0 |
| 历史 1.x（已封版） | 1.1.4 | 1.1.7 | 不强制 | 不适用 | 1.0.0 | 1.0.0 |

**历史 1.x 子版本映射（已封版）**：

| limiter-starter | limiter-core | metrics-starter | audit-listener-starter |
|-----------------|--------------|-----------------|----------------------|
| 1.1.4 | 1.1.7 | - | - |
| 1.1.3 | 1.1.6 | 1.0.0 | 1.0.0 |
| 1.1.2 | 1.1.3 | - | - |
| 1.1.1 | 1.1.3 | - | - |
| 1.1.0 | 1.1.2 | - | - |
| 1.0.3 | 1.0.1 | - | - |
| 1.0.0 ~ 1.0.2 | 内置实现，无独立 core artifact | - | - |

> 历史 1.x 已封版，不再维护，也不再规划功能或维护版本；1.x 不要求 Redis Route，也不适用 Management。`management-starter:1.0.0` 的 artifact 主版本虽为 1.x，但它属于当前 2.x 架构。`metrics-starter:1.0.0` 与 `audit-listener-starter:1.0.0` 均仅对应历史 1.1.3 / core 1.1.6 组合；其中 Audit `2.0.0` 对应当前 2.x 架构，完整记录 Route、fallback 与动态策略执行快照。Metrics 当前仍依赖 core 1.1.6，2.x 尚无对应发布版本。

**核心特性**：
- 注解驱动和拦截器模式，`@SmartRedisLimiter` / `SmartRedisLimiterInterceptor` 双入口
- 滑动窗口 / 固定窗口，Lua 脚本保证原子性；2.x 强制基于 redis-route 执行，单 Redis 场景以 `default-source` 表达
- 动态策略：management-starter 提供 REST 接口运行时调整 key / limit / policy，无需重启
- 基于 Spring 事件机制，零侵入接入 metrics 和 audit
- limiter starter / Redis Route 兼容 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9；2.2.x 不支持 Redis 7 cluster，Management 独立服务仅支持 Spring Boot 2.7.x

---

### 📨 Kafka

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-kafka-route-starter](sdk/route/kafka/simple-kafka-route-starter) | 1.0.4 | 多数据源路由（按 topic / route key 路由，不注册全局 KafkaTemplate；提供 callback 作用域 AdminClient 入口） | [README](sdk/route/kafka/simple-kafka-route-starter/README.md) |
| [simple-kafka-publisher-starter](sdk/messaging/kafka/simple-kafka-publisher-starter) | 1.1.0 | Kafka 消息发布（topic 路由 + 可选 Envelope 包装 + 通用 header，不持有 KafkaTemplate） | [README](sdk/messaging/kafka/simple-kafka-publisher-starter/README.md) |
| [simple-kafka-outbox-core](sdk/messaging/kafka/simple-kafka-outbox-core) | 1.0.0 | Outbox 共享领域模型（状态、payload 分类、无 payload 记录视图和文本安全规则） | [README](sdk/messaging/kafka/simple-kafka-outbox-core/README.md) |
| [simple-kafka-outbox-starter](sdk/messaging/kafka/simple-kafka-outbox-starter) | 1.0.1 | 本地事务 Outbox（业务事务内落库 + 后台 Worker 至少一次投递，防消息丢失） | [README](sdk/messaging/kafka/simple-kafka-outbox-starter/README.md) |
| [simple-kafka-outbox-management-starter](sdk/messaging/kafka/simple-kafka-outbox-management-starter) | 1.0.0 | Outbox 管理页面（查询状态、定位记录并受控重置单条 POISON 消息） | [README](sdk/messaging/kafka/simple-kafka-outbox-management-starter/README.md) |

**Kafka / Outbox 版本对应**：

| outbox-core | outbox-starter | outbox-management-starter | publisher-starter | route-starter |
|-------------|----------------|---------------------------|-------------------|---------------|
| 1.0.0 | 1.0.1 | 1.0.0 | 1.1.0 | 1.0.1 |

**publisher vs outbox 选型**：

| | simple-kafka-publisher-starter | simple-kafka-outbox-starter |
|---|---|---|
| 适用场景 | 业务逻辑已确定、允许极低概率丢失（进程崩溃窗口）的非关键消息 | 业务写入与消息发送必须原子保证的关键消息（订单、支付、状态变更） |
| 发送时机 | 调用即发，同步或异步 Future | 业务事务提交后由后台 Worker 异步投递 |
| 事务保证 | 无（发送成功但 DB 回滚，或 DB 提交但发送失败，均可能不一致） | 有（同一本地事务落库，DB 提交即消息不丢，broker 故障自动重试） |
| 依赖 | simple-kafka-route-starter | simple-kafka-route-starter + simple-kafka-publisher-starter（transitive）+ MySQL |
| 消费端要求 | 无额外要求 | 必须按 messageId 幂等（at-least-once，broker ack 后回写前故障会重复投递） |

**核心特性**：
- route：多匹配模式（exact / prefix / suffix / wildcard / regex）、规则优先级、broker 诊断层
- publisher：统一发布入口，异步 / 显式同步双模式，serializer / resolver / customizer 扩展点
- outbox：ownerToken + version CAS 短事务租约领取，5 态状态机（PENDING / PROCESSING / RETRY_WAIT / SENT / POISON），指数退避重试，多实例无共享状态

---

### 🔄 重试

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [task-retry-starter](sdk/retry/task-retry-starter) | 2.0.0 | 本地任务重试框架 | [README](sdk/retry/task-retry-starter/README.md) |
| [redis-retry-starter](sdk/retry/redis-retry-starter) | 1.1.0 | Redis 持久化重试，支持跨实例 | [README](sdk/retry/redis-retry-starter/README.md) |
| [smart-redis-retry-starter](sdk/retry/smart-redis-retry-starter) | 1.0.0 | Redis 分布式重试决策与状态管理（Hash + Lua 原子记录，基于 simple-redis-route-starter 多 datasource 路由） | [README](sdk/retry/smart-redis-retry-starter/README.md) |

**核心特性**：
- `task-retry-starter` 提供本地任务重试，支持指数退避和固定延迟策略
- `redis-retry-starter` 提供 Redis 持久化记录，支持跨实例重试
- `smart-redis-retry-starter` 基于 Redis Hash + Lua 提供重试决策和状态管理，并通过 redis-route 路由至多个 Redis datasource

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

### 🔑 密钥管理

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [smart-kms-core](sdk/kms/smart-kms-core) | 1.0.1 | KMS 核心模型与审计契约（审计操作标识、metadata 白名单及格式校验） | [README](sdk/kms/smart-kms-core/README.md) |
| [smart-kms-server-starter](sdk/kms/smart-kms-server-starter) | 1.0.0 | KMS Server | [README](sdk/kms/smart-kms-server-starter/README.md) |
| [simple-kms-client-starter](sdk/kms/simple-kms-client-starter) | 1.0.1 | KMS HTTP Client（策略响应解析与四档 Spring Boot 客户端矩阵） | [README](sdk/kms/simple-kms-client-starter/README.md) |

---

### 🧾 CRM

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-crm-server-core](sdk/crm/simple-crm-server-core) | 1.0.2 | CRM 领域核心（报价签发与确认的可重放幂等结果契约） | [README](sdk/crm/simple-crm-server-core/README.md) |

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
| [mail-client-starter](sdk/mail/mail-client-starter) | 2.0.0 | 邮件发送和读取客户端 | [README](sdk/mail/mail-client-starter/README.md) |
| [b2m-sms-client-starter](sdk/sms/b2m/b2m-sms-client-starter) | 1.0.0 | B2M 短信客户端 | [README](sdk/sms/b2m/b2m-sms-client-starter/README.md) |

---

### ☁️ 对象存储

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [s3](sdk/oss/s3) | 1.0.0 | S3 回调事件实体类 | [README](sdk/oss/s3/README.md) |
| [s3-client-starter](sdk/oss/s3-client-starter) | 2.1.0 | AWS S3 兼容存储客户端（支持 S3 / MinIO / 阿里云 OSS，可选私有 CA 信任） | [README](sdk/oss/s3-client-starter/README.md) |

**核心特性**：
- `s3` 模块提供对象存储回调事件实体，不是 `s3-client-starter` 的基础依赖
- `s3-client-starter` 支持存储桶管理、文件夹管理、对象上传下载、删除、查询和复制
- 支持对象版本历史、STS 临时凭证、预签名下载 URL 和预签名上传 URL
- 支持对象标签、自动分段上传、手动分段上传、分段列表查询和中止分段上传
- `s3-client-starter` 2.x 包名统一为 `io.github.surezzzzzz.sdk.oss.s3`，1.x 旧包名已封版

---

### 📄 文档模板

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [simple-doc-template-starter](sdk/template/simple-doc-template-starter) | 1.3.0 | 文档模板渲染（Word .docx / Markdown，统一 `[suredt.指令:key]` 语法） | [README](sdk/template/simple-doc-template-starter/README.md) |

**核心特性**：
- 统一模板语法 `[suredt.指令:key]`，切换输出格式无需修改模板
- 支持 Word（.docx）：变量替换、条件块、循环展开、图片替换、图表数据填充、页眉页脚
- `classpath:` / `file:` / `http:` 多来源模板加载

---

### 📝 日志

| SDK | 版本 | 说明 | 文档 |
|-----|------|------|------|
| [log-truncate-starter](sdk/log/truncate/log-truncate-starter) | 1.1.0 | 日志截断（严格 UTF-8 总字节与 Unicode 字段上限） | [README](sdk/log/truncate/log-truncate-starter/README.md) |

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