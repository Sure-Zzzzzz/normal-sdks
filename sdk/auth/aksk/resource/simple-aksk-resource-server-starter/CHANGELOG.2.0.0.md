# CHANGELOG - simple-aksk-resource-server-starter 2.0.0

## 发布日期

2026-05-25

## 版本类型

Breaking Change - 移除 JWT 模式，1.x 封版

## 变更概述

1. **移除 JWT 本地验证模式**：JWE 无法本地解密（需 AES 密钥），仅保留 INTROSPECT 模式
2. **删除 JWT 相关代码**：删除 VerificationMode 枚举、JwtAuthenticationConverter、jwtDecoder 等
3. **创建 AkskUserContextProvider**：实现 `SimpleAkskSecurityContextProvider` 接口，替代原 AkskJwtContextProvider
4. **简化配置**：移除 `verificationMode` 和 `jwt.*` 配置项
5. **依赖升级**：resource-core 升级至 2.0.0

## 变更详情

### 删除的类

| 类 | 说明 |
|----|------|
| `VerificationMode` | JWT/INTROSPECT 枚举，已无意义 |
| `AkskJwtAuthenticationConverter` | JWT 模式专用，删除 |
| `AkskJwtContextProvider` | 重命名为 AkskUserContextProvider |

### 简化配置

| 配置项 | 变更 |
|--------|------|
| `verification-mode` | 删除，仅 INTROSPECT |
| `jwt.*` | 删除，不再需要 |

### 依赖升级

| 模块 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-resource-core` | 1.0.3 | 2.0.0 |

## 升级指南

### 从 1.x 升级到 2.0.0

**1. 升级依赖**

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:2.0.0'
```

**2. 移除 JWT 相关配置**

删除 `jwt.issuer-uri` 等 JWT 配置，只保留 INTROSPECT 配置：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              server:
                enabled: true
                introspect:
                  endpoint: http://localhost:8080/oauth2/introspect
                  client-id: AKP...
                  client-secret: SK...
```

**3. 若使用 JwtAuthenticationConverter**

改为使用 `SimpleAkskSecurityContextHelper` 静态 API：

```java
// 旧
String clientId = akskJwtContextProvider.get("clientId");

// 新
String clientId = SimpleAkskSecurityContextHelper.getClientId();
```

## 贡献者

- @surezzzzzz