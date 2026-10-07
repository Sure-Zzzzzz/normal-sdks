# Redis 工具链

Redis 生态的完整接入：从连接路由（多数据源/Cluster）到缓存、锁、限流、重试能力层，再到限流策略管理面。本篇自底向上组织——先接入 route，再按需叠加上层能力。

## 连接与路由（底层）

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-redis-route-starter](../../sdk/route/redis/simple-redis-route-starter) | 1.2.2 | 1.0.0 | 多数据源路由（default-source 独占物理连接工厂，standalone/Cluster、按 key 路由、可选 Lettuce 连接池） | [README](../../sdk/route/redis/simple-redis-route-starter/README.md) |
| [simple-redis-route-jakarta-starter](../../sdk/route/redis/jakarta/simple-redis-route-jakarta-starter) | — | 1.0.0 | Jakarta 生态对等件（Boot 3.2–3.4 × JDK 17/21） | [README](../../sdk/route/redis/jakarta/simple-redis-route-jakarta-starter/README.md) |

## 能力层

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [smart-cache-starter](../../sdk/cache/smart-cache-starter) | 2.1.0 | 1.0.0 | 两级缓存：L1 本地 Caffeine + L2 分布式 Redis，Pub/Sub 多实例 L1 失效广播；防穿透/击穿/雪崩；`@SmartCache` 注解族；`CachePreloadHandler` 预加载 | [README](../../sdk/cache/smart-cache-starter/README.md) |
| [simple-redis-lock-starter](../../sdk/lock/redis/simple-redis-lock-starter) | 1.2.1 | 1.0.0 | 分布式锁（SETNX+过期，Lua 原子解锁；route 模式按 lockKey 路由） | [README](../../sdk/lock/redis/simple-redis-lock-starter/README.md) |
| [simple-redis-limiter-starter](../../sdk/limiter/redis/simple-redis-limiter-starter) | 1.0.1 | — | 令牌桶+Set 去重（配额制/幂等控制：每日 API 配额、支付与消息去重） | [README](../../sdk/limiter/redis/simple-redis-limiter-starter/README.md) |
| [smart-redis-limiter-core](../../sdk/limiter/redis/smart-redis-limiter-core) | 2.1.0 | — | 滑动/固定窗口限流核心库（事件契约、动态策略模型） | [README](../../sdk/limiter/redis/smart-redis-limiter-core/README.md) |
| [smart-redis-limiter-starter](../../sdk/limiter/redis/smart-redis-limiter-starter) | 2.0.0 | 1.1.0 | 滑动/固定窗口限流（Lua 原子；2.x 基于 redis-route 原生路由；防短信突刺、支付保护、严格限速） | [README](../../sdk/limiter/redis/smart-redis-limiter-starter/README.md) |
| [smart-redis-limiter-management-starter](../../sdk/limiter/redis/smart-redis-limiter-management-starter) | 1.0.0 | — | 限流策略管理面（REST+持久化，Portal 形态，身份依赖 IAM，见[认证与授权-IAM](../business/认证与授权-IAM.md)） | [README](../../sdk/limiter/redis/smart-redis-limiter-management-starter/README.md) |
| [redis-retry-starter](../../sdk/retry/redis-retry-starter) | 1.1.0 | — | Redis 持久化重试（跨实例） | [README](../../sdk/retry/redis-retry-starter/README.md) |
| [smart-redis-retry-starter](../../sdk/retry/smart-redis-retry-starter) | 1.1.0 | — | 重试决策与状态管理（Hash+Lua，route 多 datasource 路由） | [README](../../sdk/retry/smart-redis-retry-starter/README.md) |

伴生组件：[smart-redis-limiter-metrics-starter](../../sdk/metrics/limiter/smart-redis-limiter-metrics-starter)（1.0.0，指标采集）、[smart-redis-limiter-audit-listener-starter](../../sdk/audit/limiter/smart-redis-limiter-audit-listener-starter)（javax 2.0.0，限流执行审计：Route/fallback/动态策略快照）。

## 依赖关系

- cache / limiter 2.x / lock / smart-retry 均依赖 redis-route（javax 对齐 1.2.2 版本线；jakarta 对齐 1.0.0）
- limiter-management 依赖 limiter-core 与 MySQL 路由（见 [MySQL](MySQL.md)）；其管理面身份走 IAM 可信应用
- 事件消费端（metrics/audit）以 Spring 事件零侵入挂接，SDK 零依赖 listener

## 版本映射（唯一事实源）

**smart-redis-limiter（当前 2.x 架构）**：

| limiter-starter | limiter-core | route-starter | management-starter | metrics-starter | audit-listener |
|-----------------|--------------|---------------|--------------------|-----------------|----------------|
| 2.0.0 | 2.1.0 | 1.1.0 | 1.0.0 | 尚无 2.x 发布 | 2.0.0 |

**历史 1.x（已封版）**：

| limiter-starter | limiter-core | metrics | audit |
|-----------------|--------------|---------|-------|
| 1.1.4 | 1.1.7 | - | - |
| 1.1.3 | 1.1.6 | 1.0.0 | 1.0.0 |
| 1.1.2 / 1.1.1 | 1.1.3 | - | - |
| 1.1.0 | 1.1.2 | - | - |
| 1.0.3 | 1.0.1 | - | - |
| 1.0.0 ~ 1.0.2 | 内置实现，无独立 core | - | - |

> 1.x 已封版不再维护；1.x 不要求 redis-route。`management-starter:1.0.0` 主版本虽为 1.x，但属当前 2.x 架构。metrics 1.0.0 仅对应 1.1.3/core 1.1.6 组合。

## 版本映射（jakarta 线）

| limiter-jakarta | limiter-core（javax 共用） | management-client-core | redis-route-jakarta |
|-----------------|---------------------------|------------------------|---------------------|
| 1.1.0 | 2.2.0 | 1.0.0 | 1.0.0 |

> jakarta 装配件复用 javax 线的纯 Java core（无 Spring 依赖，双线共用）；route-jakarta 1.0.0 为底层，无内部依赖。cache/lock/retry 的 jakarta 件当前各自独立 1.0.0，无跨件约束。

## 兼容矩阵

- javax 线：Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9；2.2.x 不支持 Redis 7 Cluster；management 独立服务仅 Boot 2.7.x
- jakarta 线：route/cache/lock 已实测 Boot 3.2.12 / 3.3.13 / 3.4.2 × JDK 17/21；limiter-jakarta 1.1.0 已实测 3.2.12 / 3.3.13 × JDK 17 全量通过（3.4.2 基线原有）
