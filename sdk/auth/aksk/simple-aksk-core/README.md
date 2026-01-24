# Simple AKSK Core

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

AKSK（Access Key / Secret Key）认证体系的核心模块，提供跨模块共享的常量、模型、工具类和异常定义。

## 核心能力

### 1. 常量定义

- **AkskConstant** - AKSK 系统常量
  - Client ID 前缀（AKP、AKU）
  - Secret Key 前缀（SK）
  - ID 长度定义

- **ClientType** - 客户端类型枚举
  - 平台级客户端（PLATFORM = 1）
  - 用户级客户端（USER = 2）

- **HeaderConstant** - HTTP 请求头常量
  - Authorization 头
  - Content-Type 定义

- **JwtClaimConstant** - JWT Claim 常量
  - 标准 Claim（sub、iat、exp 等）
  - 自定义 Claim（security_context、client_type 等）

- **ErrorCode** - 错误码定义
  - 通用错误码
  - 认证相关错误码

- **ErrorMessage** - 错误消息定义
  - 中英文错误消息模板

### 2. 数据模型

- **ClientInfo** - 客户端信息模型
  - Client ID、Client Secret
  - 客户端类型、名称
  - 所属用户信息（用户级客户端）
  - 权限范围（Scopes）
  - 签发时间、启用状态

- **TokenInfo** - Token 信息模型
  - Access Token
  - Token 类型（Bearer）
  - 过期时间（秒）
  - 权限范围（Scopes）

### 3. 工具类

- **Base62Helper** - Base62 编解码工具
  - 支持将数字编码为 Base62 字符串
  - 支持将 Base62 字符串解码为数字
  - 用于生成简短的 Client ID

- **SecurityContextHelper** - 安全上下文工具
  - 解析 JWT Token 中的 security_context
  - 提取客户端类型、用户信息等
  - 提供上下文信息的便捷访问

### 4. 异常定义

- **AkskException** - AKSK 异常基类
  - 包含错误码和错误消息
  - 支持异常链传递
  - 统一异常处理规范

## 依赖说明

本模块是纯 Java 模块，仅依赖：
- Lombok - 简化代码编写
- JUnit 5 - 单元测试

**无 Spring 依赖**，可被 Server 端和 Client 端同时使用。

## 使用场景

### Server 端

```java
// 使用 ClientType 枚举
ClientType type = ClientType.PLATFORM;

// 使用常量
String prefix = AkskConstant.CLIENT_ID_PREFIX_PLATFORM; // "AKP"

// 使用 Base62Helper 生成 Client ID
long timestamp = System.currentTimeMillis();
String encodedId = Base62Helper.encode(timestamp);
String clientId = AkskConstant.CLIENT_ID_PREFIX_PLATFORM + encodedId;
```

### Client 端

```java
// 解析 JWT Token 中的 security_context
String token = "eyJhbGc...";
String securityContext = SecurityContextHelper.extractSecurityContext(token);

// 使用 TokenInfo 模型
TokenInfo tokenInfo = new TokenInfo();
tokenInfo.setAccessToken(token);
tokenInfo.setExpiresIn(3600);
```

## 版本历史

### 1.0.0 (2026-01-24)

初始版本发布：
- ✅ 定义 AKSK 常量和枚举
- ✅ 提供核心数据模型
- ✅ 实现 Base62 编解码工具
- ✅ 实现安全上下文解析工具
- ✅ 定义统一异常体系

## 许可证

Apache License 2.0
