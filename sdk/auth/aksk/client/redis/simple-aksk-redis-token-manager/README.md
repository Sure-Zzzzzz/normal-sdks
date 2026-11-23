# Simple AKSK Redis Token Manager

> **2.x 已封版**：2.x 文档冻结快照见 [README.2.x.md](README.2.x.md)；本文档对应 **3.1.0**。

基于 `smart-cache-starter` 的分布式 Token 管理器，提供 L1+L2 两级缓存、分布式锁防击穿、多实例 L1 一致性和 L2 预刷新能力。

## 核心能力

- **L1 缓存（Caffeine）**：JVM 本地缓存，TTL 短（默认 2s），减少 Redis IO
- **L2 缓存（Redis）**：分布式缓存，多实例共享 token
- **分布式锁**：防止多实例并发打 OAuth2 Server
- **多实例 L1 一致性**：`clearToken()` 通过 Pub/Sub 广播 L1 失效，各实例同步清除
- **L2 预刷新**：Redis TTL 进入配置的预刷新窗口时异步换 Token，当前请求不阻塞

### AKP Profile 隔离

AKP 是 AKSK 的服务机器凭据，一组 AKP 即一对 AccessKey / SecretKey；一个 Profile 对应一组 AKP。

`RedisTokenManager` 同时实现默认 `TokenManager` 和 `ProfileTokenManager`。顶层凭据继续走 `getToken()`；命名 AKP 走 `getToken(profile)`。Redis key、L1/L2 缓存、分布式锁和预刷新任务按 Profile 与凭据身份隔离。

- `clearToken(profile)` 只清当前安全上下文对应的命名 Profile 缓存。
- 旧版本默认缓存没有 Profile 元数据时，默认链路会刷新并补写元数据；命名 Profile 不复用旧默认缓存。
- 凭据身份由 AKSK Server origin 归一化结果和 `clientId` 组成；secret 不写入 Redis key 或缓存值。

---

## 快速开始

### 1. 添加依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-redis-token-manager:3.1.0'

// 必须自行引入（compileOnly，不会传递）
implementation 'io.github.sure-zzzzzz:simple-redis-route-starter:1.2.2'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'org.springframework:spring-web'
implementation 'com.github.ben-manes.caffeine:caffeine:2.9.3'
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

### 2. 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: default
            sources:
              default:
                mode: standalone
                host: localhost
                port: 6379

        auth:
          aksk:
            client:
              enable: true
              server-url: https://aksk.example.test
              client-id: ${AKSK_CLIENT_ID}
              client-secret: ${AKSK_CLIENT_SECRET}
              # 命名 AKP Profile：每组独立凭据；default 是保留名，默认凭据仍写在顶层
              profiles:
                crm:
                  server-url: https://aksk.example.test
                  token-endpoint: /oauth2/token   # 缺省即 /oauth2/token
                  client-id: ${CRM_AKSK_CLIENT_ID}
                  client-secret: ${CRM_AKSK_CLIENT_SECRET}

        cache:
          enabled: true
          key-prefix: my-app
          me: my-aksk-app                   # 应用组标识（必配）：同一应用的多个实例必须一致，共享缓存 / 锁互斥 / PubSub
          l1:
            enabled: true
            expire-seconds: 2               # L1 TTL，建议 1-5s
            max-size: 1000
          l2:
            enabled: true
            expire-seconds: 3600            # SmartCache 的非 Token 条目默认 TTL
            preload:
              enabled: true                 # 启用 L2 预刷新
              before-expire-seconds: 60      # 预刷新窗口（Redis TTL <= 60s 时触发）
          consistency:
            mode: strong                    # Pub/Sub 多实例 L1 一致
```

> **redis-route 接管是硬前提**（smart-cache 2.2.0 起）：`cache.l2.enabled=true` 或 `consistency.mode=strong` 时容器必须存在 `RedisRouteTemplate` bean（即启用 `io.github.surezzzzzz.sdk.redis.route` 接管），否则启动期 fail-fast 拒绝启动；`spring.redis.*` 直连形态不再受支持。lock 无需任何开关——容器存在 route 接管的标准 `stringRedisTemplate` 时 lock 1.2.2 自动让位复用。

### 3. 使用

```java
@Autowired
private TokenManager tokenManager;

// 获取 token（自动处理缓存、分布式锁、预刷新）
String token = tokenManager.getToken();

// 清除缓存（多实例同步）
tokenManager.clearToken();
```

---

## 架构

```

命名 AKP 需要注入 `ProfileTokenManager`，并用 Profile 名称显式选择凭据：

```java
@Autowired
private ProfileTokenManager profileTokenManager;

// 使用 crm 这组 AKP 凭据；缓存、锁和预刷新都与默认凭据隔离
String crmToken = profileTokenManager.getToken("crm");

// 只清理 crm 的缓存，不影响默认凭据和其他 Profile
profileTokenManager.clearToken("crm");
```

- 未配置的 Profile 不会回落到默认凭据，直接抛出 `ConfigurationException`。
- 只使用命名 Profile 时，顶层 `client-id` / `client-secret` 可以缺省；此时调用无参 `getToken()` 抛出 `ConfigurationException`。
缓存流程：L1 → L2 → 抢分布式锁 → fetch → 写回 L1 + L2

L1: Caffeine（2s），减少 Redis IO
L2: Redis（server 返回的 expiresIn 秒），多实例共享
分布式锁: 防止缓存击穿
L2 预刷新: Redis TTL <= 60s 时触发（smart-cache 内置机制）
```

### TokenWithExpiry 模型

Token 存储结构包含 `{ token, expiresAt, securityContext }`：

- `token`：OAuth2 access_token
- `expiresAt`：绝对过期时间（epoch 秒），由 fetchTime + server返回的expiresIn 计算得出
- `securityContext`：用户上下文，用于 reload() 时保证分布式一致性

### TTL 策略

- **Redis TTL**：不超过服务端 `expiresIn`；正常有效期预留 30 秒提前失效窗口，有效期不足 30 秒时最多缓存 1 秒
- **无效响应**：缺失或非正 `expiresIn` 由 client-core 拒绝，Token 不会写入缓存
- **preload 触发**：`TTL(key) <= beforeExpireSeconds` 时框架自动触发 reload，新 Token 写回后仍按同一安全 TTL 规则计算

---

## 多用户场景

实现自定义 `SecurityContextProvider`，不同用户获取不同 token：

```java
@Component
public class UserSecurityContextProvider implements SecurityContextProvider {

    @Override
    public String getSecurityContext() {
        String userId = getCurrentUserId();
        return String.format("{\"user_id\":\"%s\"}", userId);
    }
}
```

---

## Redis Key 格式

由 `smart-cache-starter` 统一管理，格式：`{keyPrefix}:{cacheName}:{me}::{cacheKey}`

> 注：尾部 `cacheKey` 部分由 smart-cache 框架自动包裹大括号 `{...}`，作为 Redis Cluster Hash Tag，保证同一 `cacheName` 下所有 key 落到同一 slot，便于跨 key 操作（如 `SCAN` / `MGET`）。

| 场景 | Key 示例 |
|------|---------|
| 平台级（无 security_context） | `my-app:aksk-client-token:my-aksk-app::{default}` |
| 用户级（有 security_context） | `my-app:aksk-client-token:my-aksk-app::{a3f1b2c4d5e6f7a8b9c0d1e2f3a4b5c6}` |
| 命名 AKP Profile（3.1.0 起） | `my-app:aksk-client-token:my-aksk-app::{crm:5f8c1b0d9a734e2f8b1c0d9a734e2f8b:default}` |

> 用户级 cacheKey 自 2.0.1 起使用 SHA-256 截断 128-bit hex（32 字符），替换 2.0.0 的 `hashCode()` 32-bit 数字串，消除碰撞风险。Hash Tag 行为由 smart-cache 控制，本模块未做修改。

> 命名 Profile 的 cacheKey 为三段：Profile 名称、凭据身份哈希（AKSK Server origin 与 `clientId` 组合后取 SHA-256 截断 128-bit hex）、安全上下文段（规则同默认链路）。secret 不参与计算；同一凭据的不同写法（默认端口、尾部斜杠、协议大小写）得到同一哈希。

---

## 兼容矩阵

| 应用运行时 | 使用模块 | 版本 | Spring Boot | Java |
| --- | --- | --- | --- | --- |
| Spring Boot 2.x（javax） | `simple-aksk-redis-token-manager` | `3.1.0` | 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE | 8 |
| Spring Boot 3.x（Jakarta） | `simple-aksk-redis-token-manager-jakarta-starter` | `1.1.0` | 3.4.2 | 17 / 21 |

两条线不可同时引入。

---

## 依赖说明

| 依赖 | 声明方式 | 说明 |
|------|----------|------|
| `simple-aksk-client-core` | `api` | 客户端核心（`TokenManager` 等公开契约），自动传递 |
| `smart-cache-starter` | `implementation` | L1+L2 缓存、分布式锁、Pub/Sub，运行时自动传递、开箱即用；直接使用 smart-cache API 需自行引入 |
| `simple-redis-lock-starter` | `implementation` | `SimpleRedisLock` 供 `RedisTokenManager` 构造（main 源码直接使用，cache 2.2.0 起不再编译期传递，按自闭环原则显式声明），运行时自动传递 |
| `task-retry-starter` | `implementation` | `TaskRetryExecutor` 供 `TokenRefreshExecutor` 自动装配注入（同上），运行时自动传递 |
| `simple-redis-route-starter` | `testImplementation` | main 源码零 route 类型引用；但使用方启用 L2 或强一致模式时必须自行引入并配置，提供 `RedisRouteTemplate` |
| `spring-boot-starter-data-redis` | `compileOnly` | Redis 操作，**使用方必须自行引入** |
| `spring-web` | `compileOnly` | RestTemplate，**使用方必须自行引入** |

---

## 版本历史

### 3.1.0

- 升级 `simple-aksk-client-core` 至 3.1.0，支持多 AKP Profile。
- Token 缓存、锁、预刷新和清理按 Profile 隔离；默认单 AKP 的 key 与调用方式保持兼容。
- 旧缓存默认链路刷新后补写 Profile 与凭据身份元数据；命名 Profile 拒绝旧默认缓存并刷新。
- Token 获取、刷新与清理的诊断日志携带 Profile 维度，不记录 secret、原始 clientId、Token 或 Authorization。
- `RedisTokenManager` 由自动配置创建并注入 `AkskClientProfileRegistry`；排除自动配置又扫描该组件的宿主会在启动期失败。

详见 [CHANGELOG.3.1.0.md](CHANGELOG.3.1.0.md)。

### 3.0.2

- 统一 Token 首次写入与 L2 预刷新的有效期计算：服务端 Token 已过期时不再回退使用全局 L2 TTL 缓存；正常 Token 保留 30 秒提前失效窗口。
- 本地刷新收敛由按缓存键建锁改为固定分片锁，避免多安全上下文持续增长时本地锁容器无界增长；跨实例仍使用分布式锁。
- Redis 缓存读写和清理失败统一关闭为 `TokenFetchException`，避免调用方继续携带不可靠 Token 请求下游服务。
- 本模块不主动将 Token、安全上下文或缓存键写入日志；测试断言失败报告不回显这些值。
- Spring Boot 2.7.9 真实 Redis 与 AKSK Server E2E 回归 44 项全绿。

详见 [CHANGELOG.3.0.2.md](CHANGELOG.3.0.2.md)。

### 3.0.1

- 升级 `smart-cache-starter` 2.1.0 → 2.2.0，对齐 `simple-redis-route-starter` 1.2.2 版本线。
- 补自闭环依赖声明：cache 2.2.0 起 route/lock/task-retry 三件从 `api` 收为 `implementation` 不再编译期传递，本模块 main 源码直接使用 `SimpleRedisLock` 与 `TaskRetryExecutor`，按直接依赖显式声明（`implementation`）；`simple-redis-route-starter` 仅测试源码引用，声明为 `testImplementation`。
- lock 开关移除：lock 1.2.2 起容器存在 route 接管的标准 `stringRedisTemplate` 时自动让位，`lock.redis.route.enable` 配置不再需要。
- 使用方必须启用 redis-route 接管：cache 2.2.0 启动校验要求 `l2.enabled=true` 或 `consistency.mode=strong` 时容器存在 `RedisRouteTemplate`，`spring.redis.*` 直连形态启动失败。
- 公开 API、配置键（`auth.aksk.client.redis.*` / `cache.*`）零变化，业务代码无需修改。
- 经 Spring Boot 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE 四版本真实 E2E 矩阵验证（每版本 41 个测试全绿）。

详见 [CHANGELOG.3.0.1.md](CHANGELOG.3.0.1.md)。

### 3.0.0

- 适配 `simple-aksk-client-core:3.0.0`，继续支持 AKSK 3.0 的 Client Credentials Token 获取和安全上下文传递；升级 `smart-cache-starter` 1.1.2 → 2.1.0。
- 适配 smart-cache 2.x 序列化安全边界：缓存值类型化读写，`TokenWithExpiry` 模型与 2.x 反序列化契约兼容。
- L2 预刷新写回的 TTL 严格等于服务端 `expires_in`，修复预刷新值可能被全局 L2 TTL 覆盖的问题。
- 分布式锁键与 SmartCacheManager 同源构造，按应用组 `me` 互斥；锁不可用时本地锁兜底。
- 业务代码无需修改；`me` 为必配项（应用组标识，同一应用多实例保持一致）。
- 依赖边界收紧：`simple-aksk-client-core` 保持 `api`（公开契约），`smart-cache-starter` 调整为 `implementation`——运行时仍自动传递、开箱即用不变；直接使用 smart-cache API 的使用方需自行引入。
- 经 Spring Boot 2.7.9 / 2.4.5 / 2.3.12.RELEASE / 2.2.13.RELEASE 四版本真实 E2E 矩阵验证（含依赖收紧后的全量回归）。

详见 [CHANGELOG.3.0.0.md](CHANGELOG.3.0.0.md)。

### 2.0.1

- **Security Hardening**：缓存 Key 算法升级，`String.hashCode()`（32-bit）→ SHA-256 截断 128-bit hex
  - 消除生日悖论碰撞风险（10w 上下文从 ~70% 降至 ~1.5e-29）
  - 防止跨用户 / 跨 scope Token 串号
  - 经典 hashCode 碰撞输入（如 `"Aa"` / `"BB"` 同 hashCode = 2112）现已映射到不同 cacheKey
  - API / 配置零变更，业务代码无需修改
  - 旧缓存条目自然过期失效，首次升级后短时间 cache miss 由 SmartCache 锁兜底
- **测试覆盖**：新增 `RedisTokenManagerMultiSecurityContextEndToEndTest` 端到端测试，覆盖多 securityContext 隔离、`clearToken()` 按 cacheKey 隔离、hashCode 碰撞输入全流程不串台
- **内部重构**：常量集中（`SimpleAkskRedisTokenManagerConstant` / `ErrorCode` / `ErrorMessage`），新增模块异常体系（`SimpleAkskRedisTokenManagerException` / `CacheKeyGenerationException`），`Properties` 配置前缀引用 core 常量去除硬编码

### 2.0.0

- **Breaking Change**：移除 JWT 解析，Token 有效性完全由 Redis TTL 保证
  - 不再依赖 `nimbus-jose-jwt`
  - 移除 `checkTokenStatus()`、`TokenStatus` 枚举
  - 移除 `TokenCacheStrategy`、`AbstractTokenManager` 继承
- 新增 `TokenWithExpiry` 模型，存储 `token` + `expiresIn` + `securityContext`
- L2 预刷新由框架 TTL 机制驱动，不再手动解析 JWT
- 分布式锁直接注入，防止击穿

### 1.1.0

- 用 `smart-cache-starter` 替换手写分布式锁 + Redis 缓存逻辑
- 新增 L1 缓存（Caffeine），减少 Redis IO
- 新增 `TokenCachePreloadHandler`，接入 L2 预刷新，恢复 token 容错窗口
- 多实例 L1 一致性（Pub/Sub）
- 移除 `RedisTokenCacheStrategy`、`RedisKeyHelper`

### 1.0.1

- 重构为 Template Method 模式，继承 `AbstractTokenManager`
- 递归重试改为循环，修复栈溢出风险

### 1.0.0

- 初始版本，基于 Redis 分布式锁的 Token 管理

---

## 许可证

Apache License 2.0
