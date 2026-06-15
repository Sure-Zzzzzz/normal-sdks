# CHANGELOG - simple-aksk-server-starter 2.0.2

## 发布日期

2026-06-15

## 版本类型

Patch Release - 性能优化 + 依赖升级，向后兼容

## 变更概述

新增 OAuth2 Registered Client Entity 两级缓存（SmartCache L1+L2），将 `/oauth2/token` 请求中 `OAuth2RegisteredClientEntity.findByClientId` 的 JPA 查询次数从 **2~3 次降至 0~1 次**（首次 1 次，后续命中 L1）。同时升级 smart-cache-starter 依赖。

## 变更详情

### 新增：Client Entity 缓存服务

**新增类**：`CachedOAuth2RegisteredClientEntityService`

位置：`io.github.surezzzzzz.sdk.auth.aksk.server.service.CachedOAuth2RegisteredClientEntityService`

封装 `OAuth2RegisteredClientEntityRepository.findByClientId` 的读路径，叠加 SmartCache L1（Caffeine）+ L2（Redis）两级缓存。写路径在 `ClientManagementServiceImpl` 各写操作后显式 evict。

**被改造的调用点**（3 处，均通过 Spring 依赖注入切换到新 service）：

| 调用点 | 变更 |
|--------|------|
| `EnabledAwareRegisteredClientRepository.filterDisabledClient` | 数据源从 `entityRepository` 改为 `cachedClientEntityService` |
| `DefaultScopeAuthenticationConverter` | 构造参数从 `entityRepository` 改为 `cachedClientEntityService` |
| `JwtTokenCustomizer` | 字段类型从 `entityRepository` 改为 `cachedClientEntityService` |

**写路径 evict（10 处）**：`ClientManagementServiceImpl` 的 10 个写操作（createPlatformClient / createUserClient / deleteClient / regenerateSecretKey / syncUserScopes / disableClient / enableClient / updateClientScopes / updateClientName / updateOwnerInfo）在 save/delete 后均调用 `cachedClientEntityService.evict(clientId)`。

### 性能数据

| 场景 | 改造前 | 改造后 |
|------|--------|--------|
| `/oauth2/token` 带 scope 参数 | 2 次 JPA 查询 | 0~1 次 |
| `/oauth2/token` 不带 scope 参数 | 3 次 JPA 查询 | 0~1 次 |

> "0~1"：首次请求 L1/L2 均 miss 回源 1 次；同一请求内后续调用点命中 L1；后续请求全部命中 L1。

### 依赖升级

| 依赖模块 | 旧版本 | 新版本 | 说明 |
|---------|--------|--------|------|
| `simple-aksk-server-core` | 2.0.1 | **2.0.2** | 新增 `RedisKeyHelper.CACHE_OAUTH2_CLIENT_ENTITY` 常量 |
| `smart-cache-starter` | 1.0.4 | **1.1.2** | 1.1.0 为 breaking change（包重构），本版本已修正受影响的 import |

**smart-cache 1.1.0 升级说明**：包重构 `cache.cache.L1Cache` → `cache.layer.L1Cache`，本模块仅 `SmartCachePubSubTest.java` 和 `ClientEntityCachePubSubTest.java` 的测试 import 需要修改，main source 代码零破坏。

### 新增测试

| 测试类 | 说明 |
|--------|------|
| `CachedOAuth2RegisteredClientEntityServiceTest` | 单测：验证 cache hit/miss、evict、SmartCacheManager 为 null 降级、异常透传 |
| `OAuth2EndToEndTest.testClientEntityCacheReducesJpaCalls` | 集成测：通过 `L1Cache` 验证 `/oauth2/token` 命中缓存 |
| `ClientEntityCachePubSubTest` | 多实例测：验证写操作后 Pub/Sub 广播清除其他实例 L1 |

### 事务前提假设

本次设计依赖 `ClientManagementServiceImpl` 当前**不持有 `@Transactional` 注解**。如未来引入事务边界，evict 需改用 `TransactionSynchronizationManager.registerSynchronization(... afterCommit())` 避免脏读。

---

## 贡献者

- @surezzzzzz