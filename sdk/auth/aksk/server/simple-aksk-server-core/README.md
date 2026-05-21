# simple-aksk-server-core

`simple-aksk-server-starter` 和扩展模块共享的核心库，提供服务端公共配置、常量和 Token 审计事件定义。

> 本模块是纯共享库，不包含 Spring Boot 自动配置，不能单独使用。
>
> **1.x 封版文档**：如果你使用的是 1.0.x 版本，请查看 [README.1.x.md](README.1.x.md)。

---

## 包含内容

### 配置

`SimpleAkskServerProperties` — 服务端配置类，供 `simple-aksk-server-starter` 和扩展模块读取。

### 常量

`SimpleAkskServerConstant` — 服务端常量，包括配置前缀、默认值、JWT Claim 名称、JWE 算法常量等。

`SimpleAkskServerProperties.JwtConfig.encryptionKey` — AES-256 密钥配置项，通过配置文件注入。

### JWE 加密常量

2.0.0 新增 JWE（JSON Web Encryption）算法常量，为 server-starter JWE Token 增强提供基础设施：

| 常量 | 值 | 说明 |
|------|----|------|
| `JWE_KEY_ENCRYPTION_ALGORITHM` | `A256GCMKW` | AES-256 Key Wrap |
| `JWE_CONTENT_ENCRYPTION_ALGORITHM` | `A256GCM` | AES-256 Galois/Counter Mode |
| `JWE_CONTENT_TYPE_JWT` | `JWT` | JWE header cty 字段值 |
| `AES_256_KEY_LENGTH` | `32` | AES-256 密钥字节数 |

### Redis Key 工具

`RedisKeyHelper` — Redis Key 构建工具，统一管理所有 Redis Key 格式，确保多实例隔离。

### Token 审计事件

| 类 | 说明 |
|----|------|
| `TokenEventType` | 事件类型枚举：`ISSUED`、`REVOKED`、`REMOVED`、`INTROSPECTED` |
| `AbstractTokenEvent` | 所有 Token 事件的基类，包含完整审计字段 |
| `TokenIssuedEvent` | Token 颁发事件（`/oauth2/token`） |
| `TokenRevokedEvent` | Token 撤销事件（`/oauth2/revoke`） |
| `TokenRemovedEvent` | Token 删除事件（Spring Authorization Server 内部触发） |
| `TokenIntrospectedEvent` | Token 自省事件（`/oauth2/introspect`），额外包含 `active` 字段 |

`AbstractTokenEvent` 包含的审计字段：

| 字段 | 说明 |
|------|------|
| `eventType` | 事件类型（`TokenEventType` 枚举） |
| `eventTime` | 事件发生时间 |
| `clientId` | 客户端 ID |
| `clientType` | 客户端类型（platform / user） |
| `userId` | 用户 ID（用户级 AKSK 才有） |
| `username` | 用户名（用户级 AKSK 才有） |
| `tokenValue` | Token 值 |
| `scopes` | 授权范围 |
| `issuedAt` | Token 颁发时间 |
| `expiresAt` | Token 过期时间 |

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-core:2.0.0'
```

通常不需要直接引用，`simple-aksk-server-starter` 会通过 `api` 传递依赖。

---

## 监听审计事件

```java
@EventListener
public void onTokenIssued(TokenIssuedEvent event) {
    log.info("Token issued: clientId={}, scopes={}", event.getClientId(), event.getScopes());
}

@EventListener
public void onTokenRevoked(TokenRevokedEvent event) {
    log.info("Token revoked: clientId={}", event.getClientId());
}

// 统一监听所有 Token 事件
@EventListener
public void onAnyTokenEvent(AbstractTokenEvent event) {
    log.info("Token event: type={}, clientId={}", event.getEventType(), event.getClientId());
}
```

`TokenEventType` 提供标准枚举方法：

```java
TokenEventType.fromCode("issued");       // → ISSUED
TokenEventType.isValid("revoked");       // → true
TokenEventType.getAllCodes();            // → ["issued", "revoked", "removed", "introspected"]
```
