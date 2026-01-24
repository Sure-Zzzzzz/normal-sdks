# Simple AKSK Client Core

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

AKSK 客户端的核心模块，提供 Token 管理的抽象接口、安全上下文管理、Token 刷新执行器等基础能力。

## 核心能力

### 1. Token 管理抽象

- **TokenManager** - Token 管理器接口
  - `getToken()` - 获取有效的 Access Token
  - `clearToken()` - 清除缓存的 Token
  - 定义统一的 Token 管理规范

- **TokenCacheStrategy** - Token 缓存策略接口
  - `get(key)` - 从缓存获取 Token
  - `put(key, token, ttl)` - 缓存 Token
  - `delete(key)` - 删除缓存的 Token
  - `generateCacheKey(securityContext)` - 生成缓存 Key
  - 支持多种缓存实现（Redis、HttpSession 等）

### 2. Token 刷新执行器

- **TokenRefreshExecutor** - Token 刷新执行器
  - 使用 AKSK 向 OAuth2 Server 换取 Access Token
  - 支持 Client Credentials Grant 流程
  - 自动解析 JWT Token 并检查状态
  - Token 状态检查（VALID、EXPIRED、EXPIRING_SOON、UNPARSABLE）
  - 单例 RestTemplate（DCL 双重检查锁）

### 3. 安全上下文管理

- **SecurityContextProvider** - 安全上下文提供者接口
  - `getSecurityContext()` - 获取当前安全上下文
  - 支持多租户、多用户场景

- **DefaultSecurityContextProvider** - 默认实现
  - 返回 null（平台级客户端场景）

- **StaticSecurityContextProvider** - 静态实现
  - 返回固定的安全上下文（测试场景）

### 4. 配置管理

- **SimpleAkskClientCoreProperties** - 客户端配置
  - `enable` - 是否启用客户端
  - `client-id` - Client ID（AKSK）
  - `client-secret` - Client Secret（SK）
  - `server-url` - OAuth2 Server 地址
  - `token-endpoint` - Token 端点路径
  - `token.refresh-before-expire` - Token 提前刷新时间（秒）

### 5. 异常体系

- **SimpleAkskClientCoreException** - 客户端异常基类
- **ConfigurationException** - 配置异常
- **TokenFetchException** - Token 获取异常
- **TokenLockException** - Token 锁异常
- **TokenParseException** - Token 解析异常

### 6. 常量定义

- **SimpleAkskClientCoreConstant** - 客户端常量
  - 配置前缀：`io.github.surezzzzzz.sdk.auth.aksk.client`
  - Token 类型：Bearer
  - 默认端点路径

- **ClientErrorCode** - 客户端错误码
- **ClientErrorMessage** - 客户端错误消息

## 依赖说明

本模块依赖：
- simple-aksk-core - 核心模型和常量
- Spring Boot - 配置管理
- Spring Web - RestTemplate
- Lombok - 简化代码

## 使用场景

### 实现自定义 TokenManager

```java
@Component
public class MyTokenManager implements TokenManager {

    @Autowired
    private TokenRefreshExecutor tokenRefreshExecutor;

    @Autowired
    private TokenCacheStrategy tokenCacheStrategy;

    @Override
    public String getToken() {
        // 1. 从缓存获取
        String cachedToken = tokenCacheStrategy.get("my-key");
        if (cachedToken != null) {
            return cachedToken;
        }

        // 2. 从服务器获取
        String newToken = tokenRefreshExecutor.fetchToken(null);

        // 3. 缓存 Token
        tokenCacheStrategy.put("my-key", newToken, 3600);

        return newToken;
    }

    @Override
    public void clearToken() {
        tokenCacheStrategy.delete("my-key");
    }
}
```

### 实现自定义 SecurityContextProvider

```java
@Component
public class MySecurityContextProvider implements SecurityContextProvider {

    @Override
    public String getSecurityContext() {
        // 从 ThreadLocal、Request Header 等获取用户上下文
        String userId = getCurrentUserId();
        String tenantId = getCurrentTenantId();

        return String.format("{\"user_id\":\"%s\",\"tenant_id\":\"%s\"}",
            userId, tenantId);
    }
}
```

## 版本历史

### 1.0.0 (2026-01-24)

初始版本发布：
- ✅ 定义 Token 管理抽象接口
- ✅ 实现 Token 刷新执行器
- ✅ 提供安全上下文管理
- ✅ 定义客户端配置规范
- ✅ 建立统一异常体系

## 许可证

Apache License 2.0
