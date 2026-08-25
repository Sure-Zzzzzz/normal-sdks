# Simple AKSK Client Core

[![Version](https://img.shields.io/badge/version-3.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

> **2.x 封版文档**：如果你使用的是 2.x 版本，请查看 [README.2.x.md](README.2.x.md)。
>
> **1.x 封版文档**：如果你使用的是 1.x 版本，请查看 [README.1.x.md](README.1.x.md)。

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
  - `remove(key)` - 删除缓存的 Token
  - `generateCacheKey(securityContext)` - 生成缓存 Key
  - 支持多种缓存实现（Redis、HttpSession 等）

### 2. Token 刷新执行器

- **TokenRefreshExecutor** - Token 刷新执行器
  - 使用 AKSK 向 OAuth2 Server 换取 Access Token
  - 支持 Client Credentials Grant 流程
  - 校验响应中的 `access_token` 和 `expires_in`，无效响应返回 `HTTP_RESPONSE_INVALID`
  - Token 有效性由缓存 TTL 保证，兼容 JWE 加密格式

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

### 5. 异常体系

- **SimpleAkskClientCoreException** - 客户端异常基类
- **ConfigurationException** - 配置异常
- **TokenFetchException** - Token 获取异常
- **TokenLockException** - Token 锁异常

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
- task-retry-starter - Token 刷新重试执行器
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
        String newToken = tokenRefreshExecutor.fetchTokenFromServer(null, null);

        // 3. 缓存 Token
        tokenCacheStrategy.put("my-key", newToken, 3600);

        return newToken;
    }

    @Override
    public void clearToken() {
        tokenCacheStrategy.remove("my-key");
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

### 3.0.0 (2026-08-24)

依赖升级至 `simple-aksk-core:3.0.0`、`task-retry-starter:2.0.0`，并修复 Token 刷新重试延迟参数和无效 Token 响应处理问题。详见 [CHANGELOG.3.0.0.md](CHANGELOG.3.0.0.md)

### 2.0.0 (2026-05-26)

Breaking Change。移除 JWT Token 解析能力，移除 `nimbus-jose-jwt` 依赖，兼容 JWE 加密格式。`TokenRefreshExecutor` 不再解析 Token 内容，Token 有效性改由缓存 TTL 保证。详见 [CHANGELOG.2.0.0.md](CHANGELOG.2.0.0.md)

### 1.0.1 (2026-05-03)

功能增强 + 重构：新增 HTTP 超时配置、`AbstractTokenManager` 模板方法基类、`TokenCacheStrategy.calculateTtl()` default 方法。详见 [CHANGELOG.1.0.1.md](CHANGELOG.1.0.1.md)

### 1.0.0 (2026-01-24)

初始版本发布：定义 Token 管理抽象接口、Token 刷新执行器、安全上下文管理、客户端配置规范、统一异常体系。

## 许可证

Apache License 2.0
