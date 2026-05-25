# CHANGELOG - simple-aksk-server-starter 2.0.0

## 发布日期

2026-05-21

## 版本类型

Breaking Change - 安全增强 + 包结构重构，1.x 封版

## 变更概述

1. **JWE Token**：access token 从 JWS 改为 JWE（A256GCMKW + A256GCM），payload 密文不可读
2. **强制 JWE**：不兼容 JWS，旧 token 过期后直接 401，客户端重新获取
3. **AES 密钥注入**：AES-256 密钥通过配置注入，server/resource 共用同一密钥
4. **包结构重构**：按后缀规范拆分 `support/` 包，各类按职责归入独立包

## 变更详情

### 新增类

| 类 | 包路径 | 说明 |
|----|--------|------|
| `JweOAuth2TokenGenerator` | `token/` | 替代默认 JwtGenerator，签发 JWE token（RSA 签名 + AES 加密） |
| `JweJwtDecoder` | `token/` | server 侧 JWE 解密（用于 introspect/revoke） |

### 包结构重构

原 `support/` 包按后缀规范拆分：

| 类 | 旧包 | 新包 |
|----|------|------|
| `AnonymousIntrospectionFilter` | `support/` | `filter/` |
| `SimpleAkskSecurityExceptionHandler` | `support/` | `handler/` |
| `JwtKeyProvider` | `support/` | `provider/` |
| `CustomJwtContextProvider` | `support/` | `provider/` |
| `JwtTokenCustomizer` | `support/` | `customizer/` |
| `DefaultScopeAuthenticationConverter` | `support/` | `converter/` |
| `OAuth2SettingsHelper` | `support/` | `helper/` |

### 配置变更

| 配置项 | 说明 |
|--------|------|
| `jwt.encryption-key` | **新增必填**：AES-256 密钥，Base64 编码，32 字节 |

### 依赖升级

| 模块 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-core` | 1.0.2 | 2.0.0 |
| `simple-aksk-server-core` | 1.x | 2.0.1 |
| `simple-aksk-resource-core` | 1.x | 2.0.0 |

### 行为变更

- Token 格式从 JWS 改为 JWE，introspect 响应中的 token 内容也随之变化
- 旧版 JWS token 在过期前仍可使用，过期后客户端需重新获取 JWE token

## 升级指南

### 从 1.x 升级到 2.0.0

**1. 生成 AES-256 密钥**

```bash
openssl rand -base64 32
```

**2. 配置密钥**

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            server:
              jwt:
                encryption-key: <生成的 Base64 密钥>
```

**3. resource-core 同步配置相同密钥**

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              jwt:
                encryption-key: <与 server 相同的 Base64 密钥>
```

**4. 升级依赖**

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:2.0.0'
```

---

## 贡献者

- @surezzzzzz
