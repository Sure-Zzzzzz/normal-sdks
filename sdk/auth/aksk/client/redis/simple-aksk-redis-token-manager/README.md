# Simple AKSK Redis Token Manager

基于 `smart-cache-starter` 的分布式 Token 管理器，提供 L1+L2 两级缓存、分布式锁防击穿、多实例 L1 一致性和 L2 预刷新能力。

## 核心能力

- **L1 缓存（Caffeine）**：JVM 本地缓存，TTL 短（默认 2s），减少 Redis IO
- **L2 缓存（Redis）**：分布式缓存，多实例共享 token
- **分布式锁**：防止多实例并发打 OAuth2 Server
- **多实例 L1 一致性**：`clearToken()` 通过 Pub/Sub 广播 L1 失效，各实例同步清除
- **L2 预刷新**：Redis TTL <= 60s 时触发，异步换 token，当前请求返回旧值不阻塞

---

## 快速开始

### 1. 添加依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-redis-token-manager:2.0.1'

// 必须自行引入（compileOnly，不会传递）
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'org.springframework:spring-web'
implementation 'com.github.ben-manes.caffeine:caffeine:2.9.3'
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

### 2. 配置

```yaml
spring:
  redis:
    host: localhost
    port: 6379

io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              enable: true
              server-url: http://localhost:8080
              client-id: AKP...
              client-secret: SK...

        cache:
          enabled: true
          key-prefix: my-app
          me: instance-1                    # 应用实例标识，多实例保持一致
          l1:
            enabled: true
            expire-seconds: 2               # L1 TTL，建议 1-5s
            max-size: 1000
          l2:
            enabled: true
            expire-seconds: 3600            # TTL 兜底（server 未返回 expiresIn 时使用）
            preload:
              enabled: true                 # 启用 L2 预刷新
              before-expire-seconds: 60      # 预刷新窗口（Redis TTL <= 60s 时触发）
          consistency:
            mode: strong                    # Pub/Sub 多实例 L1 一致
```

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

- **Redis TTL**：使用 server 返回的 `expiresIn` 秒数，框架通过 Redis `TTL` 命令检测是否进入 preload 窗口
- **兜底**：`l2.expireSeconds`（当 server 未返回 expiresIn 时）
- **preload 触发**：`TTL(key) <= beforeExpireSeconds` 时框架自动触发 reload，新 token 写回后 TTL 重新从 expiresIn 算起

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
| 平台级（无 security_context） | `my-app:aksk-client-token:instance-1::{default}` |
| 用户级（有 security_context） | `my-app:aksk-client-token:instance-1::{a3f1b2c4d5e6f7a8b9c0d1e2f3a4b5c6}` |

> 用户级 cacheKey 自 2.0.1 起使用 SHA-256 截断 128-bit hex（32 字符），替换 2.0.0 的 `hashCode()` 32-bit 数字串，消除碰撞风险。Hash Tag 行为由 smart-cache 控制，本模块未做修改。

---

## 依赖说明

| 依赖 | 声明方式 | 说明 |
|------|----------|------|
| `simple-aksk-client-core` | `api` | 客户端核心，自动传递 |
| `smart-cache-starter` | `api` | L1+L2 缓存、分布式锁、Pub/Sub，自动传递 |
| `spring-boot-starter-data-redis` | `compileOnly` | Redis 操作，**使用方必须自行引入** |
| `spring-web` | `compileOnly` | RestTemplate，**使用方必须自行引入** |

---

## 版本历史

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