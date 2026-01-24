# Simple AKSK Redis Token Manager

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

基于 Redis 的 Token 管理器实现，提供分布式 Token 缓存和并发控制能力。

## 核心能力

### 1. Redis Token 管理

- **RedisTokenManager** - Redis Token 管理器
  - 实现 `TokenManager` 接口
  - 支持 Redis 缓存 Token
  - 分布式锁保证并发安全
  - 自动 Token 过期检查和刷新
  - 递归重试策略（无固定超时）

### 2. 分布式锁机制

- **并发控制**
  - 使用 `simple-redis-lock-starter` 实现分布式锁
  - 锁超时时间：10秒
  - 递归重试策略：
    - 未获取锁 → 等待 100ms → 检查缓存 → 递归重试
    - 获取锁成功 → 从服务器获取 Token → 缓存
  - 避免重复请求服务器

### 3. Token 缓存策略

- **RedisTokenCacheStrategy** - Redis 缓存策略
  - 实现 `TokenCacheStrategy` 接口
  - 缓存 Key 格式：`sure-auth-aksk-client:{me}:token::{hashCode}`
  - 默认 Key（无 security_context）：`sure-auth-aksk-client:{me}:token::{default}`
  - 用户级 Key（有 security_context）：基于 hashCode 隔离
  - 支持自定义应用标识（me）

### 4. 配置管理

- **SimpleAkskRedisTokenManagerProperties** - Redis Token Manager 配置
  - 继承 `SimpleAkskClientCoreProperties`
  - `redis.token.me` - 应用标识（默认：default）
  - 支持多实例部署

### 5. 自动配置

- **SimpleAkskRedisTokenManagerAutoConfiguration** - 自动配置类
  - 条件装配：
    - `@ConditionalOnClass` - Redis 和 SimpleRedisLock 存在
    - `@ConditionalOnProperty` - `enable=true`
  - 自动注册 Bean：
    - RedisTokenManager
    - RedisTokenCacheStrategy
    - RedisKeyHelper
    - DefaultSecurityContextProvider（如果不存在）

## 依赖说明

本模块依赖：
- simple-aksk-client-core - 客户端核心
- simple-redis-lock-starter - 分布式锁
- Spring Boot - 自动配置
- Spring Data Redis - Redis 操作
- Lombok - 简化代码

## 配置示例

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0

io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              enable: true
              client-id: AKP1234567890abcdefgh
              client-secret: SK1234567890abcdefghijklmnopqrstuvwxyz1234
              server-url: http://localhost:8080
              token-endpoint: /oauth2/token
              token:
                refresh-before-expire: 300  # 提前5分钟刷新
              redis:
                token:
                  me: my-app  # 应用标识
```

## 使用方式

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-redis-token-manager:1.0.0'
}
```

**重要说明**：为了避免版本冲突，starter 使用 `compileOnly` 声明依赖，不会传递依赖到您的项目。请根据您的 Spring Boot 版本自行引入以下依赖：

**必需依赖：**

```gradle
dependencies {
    // Spring Data Redis（Redis 操作）
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework:spring-web'

}
```

**版本说明**：
- 建议使用 Spring Boot 2.7.x 版本
- Redis 驱动会通过 spring-boot-starter-data-redis 自动引入

### 2. 基本使用

```java
@Autowired
private TokenManager tokenManager;

// 获取 Token
String token = tokenManager.getToken();

// 清除缓存
tokenManager.clearToken();
```

### 3. 多用户场景

需要实现自定义 `SecurityContextProvider`：

```java
@Component
public class UserSecurityContextProvider implements SecurityContextProvider {

    @Override
    public String getSecurityContext() {
        // 从当前请求获取用户信息
        String userId = getCurrentUserId();
        return String.format("{\"user_id\":\"%s\"}", userId);
    }
}
```

不同用户会获取不同的 Token（基于 hashCode 隔离）。

## 并发场景

### 场景 1：多个线程同时获取 Token

- 第一个线程获取锁 → 从服务器获取 Token → 缓存
- 其他线程等待 100ms → 从缓存获取 Token
- 保证只请求服务器一次

### 场景 2：Token 失败重试

- 线程 A 获取锁但失败（网络超时）
- 线程 A 释放锁
- 线程 B 或 C 递归重试获取锁
- 其他线程接替失败的线程继续尝试

## Redis Key 说明

### 平台级客户端（无 security_context）

```
Key: sure-auth-aksk-client:my-app:token::{default}
Value: eyJhbGciOiJSUzI1NiIs...
TTL: 3600秒
```

### 用户级客户端（有 security_context）

```
Key: sure-auth-aksk-client:my-app:token::{hashCode}
Value: eyJhbGciOiJSUzI1NiIs...
TTL: 3600秒

示例：
- User A: sure-auth-aksk-client:my-app:token::123456789
- User B: sure-auth-aksk-client:my-app:token::987654321
```

### 分布式锁 Key

```
Lock Key: sure-auth-aksk-client:my-app:lock::token::{hashCode}
TTL: 10秒
```

## 测试覆盖

✅ **基础功能测试**（RedisTokenManagerTest）
- 首次获取 Token（缓存为空）
- 从缓存获取 Token
- 清除 Token
- 带 security_context 获取 Token
- 检查 Token 状态

✅ **并发测试**（RedisTokenManagerConcurrencyTest）
- 10 个线程并发获取 Token
- 50 个线程高并发获取 Token
- 并发清除和获取 Token
- 不同 security_context 并发获取 Token

✅ **端到端测试**（RedisTokenManagerEndToEndTest）
- 获取 Token 并调用 Server API
- 获取 Token 并查询 Token 统计
- 验证无 Token 调用 API 返回 401
- 验证 Token 缓存机制

**总计：13 个测试，100% 通过**

## 版本历史

### 1.0.0 (2026-01-24)

初始版本发布：
- ✅ 实现基于 Redis 的 Token 管理
- ✅ 支持分布式锁并发控制
- ✅ 支持递归重试策略
- ✅ 支持多用户 Token 隔离
- ✅ 完整的测试覆盖（基础、并发、端到端）

## 许可证

Apache License 2.0
