# Simple AKSK Resource Server Starter

[![Version](https://img.shields.io/badge/version-1.0.2-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

资源服务器端 Token 验证器，支持 JWT 本地验签和 Introspect 端点验证两种模式，提供便捷的安全上下文 API（适用于无网关场景）。

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:1.0.2'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.security:spring-security-oauth2-resource-server'
implementation 'org.springframework.security:spring-security-oauth2-jose'
```

---

## 快速开始

### JWT 模式（默认）

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
                jwt:
                  issuer-uri: http://localhost:8080   # 推荐，自动获取 JWKS
                  # 或手动配置公钥：
                  # public-key: classpath:keys/public.pem
                security:
                  protected-paths:
                    - /api/**
                  permit-all-paths:
                    - /actuator/health
```

### Introspect 模式

每次请求调 `/oauth2/introspect` 验证，支持即时感知 token 撤销：

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
                verification-mode: INTROSPECT
                introspect:
                  endpoint: http://localhost:8080/oauth2/introspect
                  client-id: AKP...      # 留空则不带认证（需 server 端 require-authentication=false）
                  client-secret: SK...
                security:
                  protected-paths:
                    - /api/**
```

---

## 使用安全上下文

验证通过后，JWT claims 自动注入到请求上下文，通过静态 API 读取：

```java
String clientId   = SimpleAkskSecurityContextHelper.getClientId();
String clientType = SimpleAkskSecurityContextHelper.getClientType();
String userId     = SimpleAkskSecurityContextHelper.getUserId();
String username   = SimpleAkskSecurityContextHelper.getUsername();
String scope      = SimpleAkskSecurityContextHelper.getScope();
```

---

## 权限注解

```java
// 要求请求携带 AKSK 安全上下文
@RequireContext
public String protectedApi() { ... }

// 要求 userId 不为空
@RequireField("userId")
public String userApi() { ... }

// 要求 clientType = platform
@RequireFieldValue(field = "clientType", value = "platform")
public String platformApi() { ... }

// SpEL 表达式
@RequireExpression(
    value = "#context['scope'] != null && #context['scope'].contains('/api/read')",
    message = "需要 /api/read 权限"
)
public String readApi() { ... }
```

---

## 监听 AkskAccessEvent

验证通过后发布 `AkskAccessEvent`，`source` 字段区分验证方式：

```java
@EventListener
public void onAkskAccess(AkskAccessEvent event) {
    log.info("Access: clientId={}, source={}, uri={}",
        event.getClientId(), event.getSource(), event.getRequestUri());
    // source: "jwt" 或 "introspect"
}
```

---

## 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `enabled` | 是否启用 | true |
| `verification-mode` | 验证模式：`JWT` 或 `INTROSPECT` | JWT |
| `jwt.issuer-uri` | OAuth2 授权服务器 Issuer URI | - |
| `jwt.public-key` | RSA 公钥（PEM 格式字符串） | - |
| `jwt.public-key-location` | RSA 公钥文件路径（classpath:/file:） | - |
| `introspect.endpoint` | introspect 端点地址 | - |
| `introspect.client-id` | 调 introspect 用的 clientId（留空则不带认证） | - |
| `introspect.client-secret` | 调 introspect 用的 clientSecret | - |
| `security.protected-paths` | 需要认证的路径 | [/api/**] |
| `security.permit-all-paths` | 白名单路径 | [] |

---

## 两种模式对比

| | JWT 模式 | Introspect 模式 |
|---|---|---|
| 验证方式 | 本地用公钥验签 | 每次请求调 introspect 端点 |
| 性能 | 最好，无 IO | 多一次 HTTP 调用 |
| 即时撤销感知 | ❌ | ✅ |
| 依赖 | 只需公钥 | 需要 clientId + clientSecret（或 server 端开放匿名） |

---

## 版本历史

### 1.0.2 (2026-04-13)
新增 Introspect 验证模式，修复 scope claim 解析 bug。详见 [CHANGELOG.1.0.2.md](CHANGELOG.1.0.2.md)

### 1.0.1 (2026-xx-xx)
初始功能完善。

### 1.0.0
初始版本发布。

## 许可证

Apache License 2.0
