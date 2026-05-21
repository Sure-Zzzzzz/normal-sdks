# Simple AKSK Core

[![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

> **1.x 封版文档**：如果你使用的是 1.x 版本，请查看 [README.1.x.md](README.1.x.md)。

AKSK（Access Key / Secret Key）认证体系的核心模块，提供跨模块共享的常量、模型、工具类和异常定义。

---

## 核心能力

### 1. 常量定义

- **AkskConstant** - AKSK 系统常量
  - Client ID 前缀（AKP、AKU）
  - Secret Key 前缀（SK）
  - ID 长度定义

- **ClientType** - 客户端类型枚举
  - 平台级客户端（PLATFORM = 1）
  - 用户级客户端（USER = 2）

- **JwtClaimConstant** - JWT Claim 名称常量
  - 标准 Claim：`sub`、`iss`、`aud`、`exp`、`iat`
  - AKSK 自定义 Claim：`client_id`、`client_type`、`user_id`、`username`、`scope`、`security_context`

- **HeaderConstant** - HTTP 请求头常量
  - `x-sure-auth-aksk-client-id`
  - `x-sure-auth-aksk-user-id`
  - `x-sure-auth-aksk-username`
  - `x-sure-auth-aksk-scope`
  - `x-sure-auth-aksk-security-context`

- **ErrorCode** - 错误码定义
- **ErrorMessage** - 错误消息模板

### 2. 数据模型

- **ClientInfo** - 客户端信息（Client ID、Secret、类型、名称、用户信息、Scopes、启用状态）
- **TokenInfo** - Token 信息（access token、类型、过期时间、Scopes、TokenStatus）
  - `TokenStatus`：`ACTIVE`、`EXPIRED`、`REVOKED`

### 3. 工具类

- **Base62Helper** - Base62 编解码，用于生成简短的 Client ID
- **SecurityContextHelper** - security_context 大小验证与序列化工具

### 4. 异常定义

- **AkskException** - AKSK 异常基类（含错误码和消息，支持异常链）

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-core:2.0.0'
```

无 Spring 依赖，可被 Server 端和 Client 端同时使用。

---

## 版本历史

### 2.0.0 (2026-05-21)

新增 `JwtClaimConstant`、`HeaderConstant`、`SecurityContextHelper`，支持 JWE Token 体系。详见 [CHANGELOG.2.0.0.md](CHANGELOG.2.0.0.md)

### 1.0.2 (2026-04-09)

`TokenInfo.TokenStatus` 新增 `REVOKED` 状态。详见 [CHANGELOG.1.0.2.md](CHANGELOG.1.0.2.md)

### 1.0.1 (2026-xx-xx)

新增 `ErrorMessage.CLIENT_UPDATE_FAILED`。详见 [CHANGELOG.1.0.1.md](CHANGELOG.1.0.1.md)

### 1.0.0 (2026-01-24)

初始版本发布。

---

## 许可证

Apache License 2.0
