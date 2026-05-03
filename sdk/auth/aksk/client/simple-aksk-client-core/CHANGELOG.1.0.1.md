# Changelog - v1.0.1

## 发布日期

2026-05-03

## 版本类型

功能增强 + 重构

## 变更概述

### 新增常量（SimpleAkskClientCoreConstant）

| 常量 | 值 | 说明 |
|------|------|------|
| `TOKEN_EARLY_EXPIRY_SECONDS` | `30` | Token 提前过期缓冲（秒） |
| `TOKEN_MIN_TTL_SECONDS` | `60` | Token 最小缓存 TTL（秒） |
| `DEFAULT_CONNECT_TIMEOUT_MS` | `5000` | 默认连接超时（毫秒） |
| `DEFAULT_READ_TIMEOUT_MS` | `15000` | 默认读取超时（毫秒） |
| `DEFAULT_LOCK_TIMEOUT_SECONDS` | `10` | 分布式锁默认超时（秒） |
| `LOCK_RETRY_SLEEP_MS` | `100` | 锁等待重试间隔（毫秒） |
| `LOCK_MAX_RETRY_TIMES` | `50` | 锁等待最大重试次数 |

### 新增配置项（SimpleAkskClientCoreProperties）

新增 `HttpConfig` 嵌套配置，支持自定义 HTTP 超时：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            client:
              http:
                connect-timeout-ms: 5000   # 默认 5000
                read-timeout-ms: 15000     # 默认 15000
```

### TokenRefreshExecutor 重构

- `RestTemplate` 改为实例变量，超时参数从 `properties.getHttp()` 读取，不再静态单例
- 向后兼容：默认值与原硬编码一致（5000ms / 15000ms）

### TokenCacheStrategy 新增 default 方法

新增 `calculateTtl(long expiresInSeconds)` default 方法，统一 TTL 计算逻辑，消除子模块重复代码：

```java
default long calculateTtl(long expiresInSeconds) {
    return Math.max(
        expiresInSeconds - TOKEN_EARLY_EXPIRY_SECONDS,
        TOKEN_MIN_TTL_SECONDS
    );
}
```

### 新增 AbstractTokenManager（Template Method 模式）

新增抽象基类，封装 `getToken()` 的通用流程，子类只需实现 `fetchTokenWithLock()` 提供各自的加锁策略：

- `RedisTokenManager`：分布式锁（多实例部署）
- `HttpSessionTokenManager`：JVM 本地锁（单实例部署）

### StaticSecurityContextProvider 规范修复

`@Getter @Setter @AllArgsConstructor` 改为 `@Data @AllArgsConstructor`，符合 SDK 规范。

## 向后兼容性

完全向后兼容，所有新增配置项均有默认值与原行为一致。

## 贡献者

- @surezzzzzz
