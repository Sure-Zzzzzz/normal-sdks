# simple-aksk-server-core

[![Version](https://img.shields.io/badge/version-3.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

AKSK Server 的共享契约模块，提供服务端配置、常量、Redis Key 规则和 Token 审计事件。

> **历史版本**：2.x 最终发布版本为 2.0.3，详见 [README.2.x.md](README.2.x.md)；1.x 文档见 [README.1.x.md](README.1.x.md)。

---

## 核心能力

### 服务端配置

`SimpleAkskServerProperties` 提供 JWT、Redis、Admin 和 OAuth2 限流配置模型，供 `simple-aksk-server-starter` 与扩展模块共同使用。

- JWT：密钥标识、有效期、密钥材料、JWE AES-256 加密密钥与 `security_context` 大小限制。
- Redis：Token 命名空间配置。
- Admin：本地管理页面开关、管理员账户及会话时长。
- 限流：OAuth2 Token、Introspect、Revoke 端点的开关、算法、降级策略、Key 策略和规则。

### 服务端常量

`SimpleAkskServerConstant` 定义服务端公共契约：

- JWT Claim、OAuth2 参数与响应字段、JWE 算法及默认配置。
- 管理 API 的应用编码、资源、动作、权限和数据权限维度。
- `JWT_CLAIM_APPLICATION_AUTHORIZATION` 复用 `simple-aksk-core` 的应用授权 Claim，保证签发、管理鉴权和内省消费同一契约。

### Redis Key 工具

`RedisKeyHelper` 统一构建 AKSK 授权记录、Token 与多实例命名空间的 Redis Key。

### Token 审计事件

| 类 | 说明 |
|---|---|
| `TokenEventType` | 事件类型：`ISSUED`、`REVOKED`、`REMOVED`、`INTROSPECTED` |
| `AbstractTokenEvent` | Token 审计事件基类 |
| `TokenIssuedEvent` | Token 颁发事件 |
| `TokenRevokedEvent` | Token 撤销事件 |
| `TokenRemovedEvent` | Token 删除事件 |
| `TokenIntrospectedEvent` | Token 自省事件，包含 `active` 状态 |

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-core:3.0.0'
```

通常无需直接引入，`simple-aksk-server-starter` 会通过 `api` 传递此依赖。

---

## 版本历史

### 3.0.0

对齐 AKSK Server 3.0 的应用授权与数据权限管理契约，移除不再支持的匿名 introspect 配置。详见 [CHANGELOG.3.0.0.md](CHANGELOG.3.0.0.md)。

### 2.0.3 (2026-06-17)

删除 Redis 可选开关，新增 OAuth2 限流配置模型。详见 [CHANGELOG.2.0.3.md](CHANGELOG.2.0.3.md)。

### 2.0.0

新增 JWE Token 基础设施契约。详见 [CHANGELOG.2.0.0.md](CHANGELOG.2.0.0.md)。

---

## 许可证

Apache License 2.0
