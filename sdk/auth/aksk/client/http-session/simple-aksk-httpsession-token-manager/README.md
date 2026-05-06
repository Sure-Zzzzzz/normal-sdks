# Simple AKSK HttpSession Token Manager

基于 HttpSession 的 AKSK Token 管理器，提供简单直接的 Token 缓存和管理能力。

## 核心能力

### 1. HttpSession 缓存
- 使用 HttpSession 存储 Token，无需额外依赖（如 Redis）
- Token 自动过期管理（提前 30 秒失效避免边界情况）
- 支持基于 `security_context` 的多租户/多用户 Token 隔离

### 2. 自动 Token 刷新
- Token 过期自动重新获取
- Token 即将过期时异步刷新（避免请求阻塞）
- 统一使用 `TokenRefreshExecutor` 处理刷新逻辑

### 3. 并发安全
- JVM 本地锁（`synchronized`）防止同一实例内并发获取
- Double-check 机制减少不必要的服务器请求
- 线程安全的 Token 读写操作

### 4. 优雅降级
- 无 HttpSession 环境下自动从服务器获取 Token
- 缺少 Request Context 时不抛异常，返回 null
- 清除操作容错处理

## 适用场景

### ✅ 推荐使用
- 单实例应用
- 多实例应用（每个实例维护独立 Token）
- 不需要跨实例共享 Token 的场景
- 希望简化依赖的项目

### ⚠️ 注意事项
- **多实例场景**：每个实例维护自己的 Token 缓存，实例之间不共享
- **Session 管理**：需要正确配置 Session（如 Session 粘性或分布式 Session）
- **跨实例共享**：如需跨实例共享 Token，建议使用 `simple-aksk-redis-token-manager`

## 依赖关系

### 核心依赖
- `simple-aksk-client-core`: AKSK 客户端核心模块
- `task-retry-starter`: 任务重试支持

### 可选依赖
- Spring Session + Redis: 用于分布式 Session（多实例场景推荐）

## 实现特性

### Token 缓存策略
- **默认 Key**: `simple_aksk_access_token`（无 security_context 时）
- **多租户 Key**: `simple_aksk_access_token:{hashCode}`（有 security_context 时）
- **TTL 计算**: `max(expiresIn - 30, 60)` 秒

### Token 状态管理
支持四种 Token 状态：
- `VALID`: Token 有效，直接返回
- `EXPIRING_SOON`: Token 即将过期，触发异步刷新并返回当前 Token
- `EXPIRED`: Token 已过期，同步获取新 Token
- `UNPARSABLE`: Token 无法解析，同步获取新 Token

### 并发控制
- 使用 JVM 本地锁 `synchronized(TOKEN_FETCH_LOCK)`
- 锁内 Double-check 缓存避免重复请求
- 适用于单 JVM 实例场景

## 配置说明

与 `simple-aksk-client-core` 配置相同：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              enable: true
              server-url: http://localhost:8080
              token-endpoint: /oauth2/token
              client-id: YOUR_CLIENT_ID
              client-secret: YOUR_CLIENT_SECRET
              token:
                refresh-before-expire: 300  # Token 提前刷新时间（秒）
```

## 使用方式

### 1. 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-aksk-httpsession-token-manager:1.0.0'
}
```

**重要说明**：为了避免版本冲突，starter 使用 `compileOnly` 声明依赖，不会传递依赖到您的项目。请根据您的 Spring Boot 版本自行引入以下依赖：

**必需依赖：**

```gradle
dependencies {
    // Spring Boot Web（提供 HttpSession 支持）
    implementation 'org.springframework.boot:spring-boot-starter-web'
}
```

**版本说明**：
- 建议使用 Spring Boot 2.7.x 版本
- HttpSession 支持会通过 spring-boot-starter-web 自动提供

### 2. 注入使用

```java
@Autowired
private TokenManager tokenManager;

public void someMethod() {
    // 获取 Token
    String token = tokenManager.getToken();

    // 使用 Token 调用 API
    // ...

    // 清除 Token（可选）
    tokenManager.clearToken();
}
```

## 与 Redis Token Manager 的区别

| 特性 | HttpSession Token Manager | Redis Token Manager |
|------|--------------------------|---------------------|
| 依赖 | 无需额外依赖 | 需要 Redis |
| 跨实例共享 | ❌ 不支持 | ✅ 支持 |
| 并发控制 | 本地锁（synchronized） | 分布式锁（Redis） |
| 适用场景 | 单实例或独立缓存 | 多实例共享缓存 |
| 复杂度 | 简单 | 中等 |
| 性能 | 高（本地缓存） | 中（网络开销） |

## 多实例部署说明

HttpSession Token Manager 适用于单实例应用，每个实例维护独立的 Token 缓存。

**如果需要多实例共享 Token**，请直接切换到 `simple-aksk-redis-token-manager`。

## 测试覆盖

- ✅ 基本功能测试（5 个）
- ✅ 并发测试（3 个）
- ✅ 端到端测试（3 个）
- ✅ 总计：11 个测试，100% 通过率

## 版本

当前版本: **1.0.0**
