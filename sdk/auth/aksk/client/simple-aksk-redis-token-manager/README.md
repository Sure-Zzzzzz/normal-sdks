# Simple AKSK Redis Token Manager

基于 `smart-cache-starter` 的分布式 Token 管理器，提供 L1+L2 两级缓存、分布式锁防击穿、多实例 L1 一致性和 L2 预刷新能力。

## 核心能力

- **L1 缓存（Caffeine）**：JVM 本地缓存，TTL 短（默认 2s），减少 Redis IO
- **L2 缓存（Redis）**：分布式缓存，多实例共享 token
- **分布式锁**：SmartCacheManager 内置，防止多实例并发打 OAuth2 Server
- **多实例 L1 一致性**：`clearToken()` 通过 Pub/Sub 广播 L1 失效，各实例同步清除
- **L2 预刷新**：`TokenCachePreloadHandler` 解析 JWT 判断 EXPIRING_SOON，异步换 token，当前请求返回旧值不阻塞

---

## 快速开始

### 1. 添加依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-redis-token-manager:1.1.0'

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
              token:
                refresh-before-expire: 300  # 提前 300s 触发预刷新（默认）

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
            expire-seconds: 3600            # 与 jwt.expires-in 对齐
            preload:
              enabled: true                 # 启用 L2 预刷新
              before-expire-seconds: 300    # 与 refresh-before-expire 对齐
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

| 场景 | Key 示例 |
|------|---------|
| 平台级（无 security_context） | `my-app:aksk-client-token:instance-1::{default}` |
| 用户级（有 security_context） | `my-app:aksk-client-token:instance-1::{hashCode}` |

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
