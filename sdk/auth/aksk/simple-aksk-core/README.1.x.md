# Simple AKSK Core 1.x

> **⚠️ 1.x 已封版**：此文档对应 1.x 系列最终版本 **1.0.2**，不再接受新功能开发，仅做安全修复。
>
> 新项目请使用 [2.0.0](README.md)。

[![Version](https://img.shields.io/badge/version-1.0.2-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

AKSK（Access Key / Secret Key）认证体系的核心模块，提供跨模块共享的常量、模型、工具类和异常定义。

---

## 核心能力

### 1. 常量定义

- **AkskConstant** - AKSK 系统常量（Client ID 前缀、Secret Key 前缀、ID 长度）
- **ClientType** - 客户端类型枚举（PLATFORM = 1，USER = 2）
- **ErrorCode** - 错误码定义
- **ErrorMessage** - 错误消息模板

### 2. 数据模型

- **ClientInfo** - 客户端信息（Client ID、Secret、类型、名称、用户信息、Scopes）
- **TokenInfo** - Token 信息（access token、类型、过期时间、Scopes、TokenStatus）

### 3. 工具类

- **Base62Helper** - Base62 编解码，用于生成简短的 Client ID

### 4. 异常定义

- **AkskException** - AKSK 异常基类（含错误码和消息）

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-core:1.0.2'
```

无 Spring 依赖，可被 Server 端和 Client 端同时使用。

---

## 版本历史

### 1.0.2 (2026-04-09)

- `TokenInfo.TokenStatus` 新增 `REVOKED` 状态

### 1.0.1 (2026-xx-xx)

- 新增 `ErrorMessage.CLIENT_UPDATE_FAILED`

### 1.0.0 (2026-01-24)

初始版本发布。

---

## 许可证

Apache License 2.0
