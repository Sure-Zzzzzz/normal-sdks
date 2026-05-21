# CHANGELOG - simple-aksk-server-core 2.0.1

## 发布日期

2026-05-21

## 版本类型

Patch Release - 新增常量，向后兼容

## 变更概述

新增 JWE Token 所需的算法常量和 `encryptionKey` 配置项，新增 Token 数据源常量和 HTTP 认证常量。

## 变更详情

### SimpleAkskServerProperties 新增

| 字段 | 说明 |
|------|------|
| `JwtConfig.encryptionKey` | AES-256 密钥，Base64 编码，32 字节，通过配置注入 |

### SimpleAkskServerConstant 新增

**JWE 算法常量：**

| 常量 | 值 | 说明 |
|------|----|------|
| `JWE_KEY_ENCRYPTION_ALGORITHM` | `A256GCMKW` | AES-256 Key Wrap |
| `JWE_CONTENT_ENCRYPTION_ALGORITHM` | `A256GCM` | AES-256 GCM 内容加密 |
| `JWE_ALGORITHM_HEADER_VALUE` | `A256GCMKW` | JWE header alg 字段值 |
| `JWE_CONTENT_TYPE_JWT` | `JWT` | JWE header cty 字段值 |
| `AES_256_KEY_LENGTH` | `32` | AES-256 密钥字节数 |

**其他常量：**

| 常量 | 值 | 说明 |
|------|----|------|
| `TOKEN_SOURCE_MYSQL` | `mysql` | Token 数据源：MySQL |
| `TOKEN_SOURCE_REDIS` | `redis` | Token 数据源：Redis |
| `HTTP_BASIC_AUTH_PREFIX` | `Basic ` | HTTP Basic 认证前缀 |
| `OAUTH2_PARAM_TOKEN` | `token` | OAuth2 token 请求参数名 |
| `SPRING_PROPERTY_SERVER_PORT` | `server.port` | Spring 端口属性名 |
| `DEFAULT_SERVER_PORT` | `8080` | 默认服务端口 |
| `OAUTH2_ERROR_INVALID_SCOPE` | `invalid_scope` | OAuth2 错误码：scope 无效 |
| `OAUTH2_ERROR_SECURITY_CONTEXT_TOO_LARGE` | `security_context_too_large` | OAuth2 错误码：security_context 超限 |
| `ADMIN_RESPONSE_SUCCESS` | `success` | Admin API 响应字段 |
| `ADMIN_RESPONSE_STATUS` | `status` | Admin API 响应字段 |
| `ADMIN_RESPONSE_MESSAGE` | `message` | Admin API 响应字段 |
| `ADMIN_RESPONSE_DELETED_COUNT` | `deletedCount` | Admin API 响应字段 |

### 依赖升级

| 模块 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-core` | 1.0.1 | 2.0.0 |

---

## 贡献者

- @surezzzzzz
