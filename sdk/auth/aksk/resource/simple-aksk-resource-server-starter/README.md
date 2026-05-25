# Simple AKSK Resource Server Starter

[![Version](https://img.shields.io/badge/version-2.0.0-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

> **1.x 封版文档**：如果你使用的是 1.x 版本，请查看 [README.1.x.md](README.1.x.md)。

资源服务器端 Token 验证器，仅支持 INTROSPECT 模式，提供便捷的安全上下文 API（适用于无网关场景）。

---

## 特性

- ✅ **INTROSPECT 模式**：调 `/oauth2/introspect` 验证，支持即时感知 token 撤销
- ✅ **本地缓存**：热路径命中缓存时无 HTTP 调用（默认 TTL 3s）
- ✅ **兜底降级**：可选开启端点故障时用历史缓存放行
- ✅ **安全上下文 API**：通过静态方法读取 claims，无需注入
- ✅ **权限注解**：支持 `@RequireContext` / `@RequireField` / `@RequireExpression`
- ✅ **审计事件**：每次验证通过后发布 `AkskAccessEvent`

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:2.0.0'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.security:spring-security-oauth2-resource-server'
implementation 'org.springframework.security:spring-security-oauth2-jose'
```

---

## 快速开始

每次请求调 `/oauth2/introspect` 验证，支持即时感知 token 撤销。内置本地缓存（默认开启，TTL 3s），热路径命中缓存时无 HTTP 调用：

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
                  client-id: AKP...      # 留空则不带认证（需 server 端 require-authentication=false）
                  client-secret: SK...
                security:
                  protected-paths:
                    - /api/**
```

本地缓存默认开启，可按需调整；可选开启兜底缓存，端点不可用时用历史缓存放行：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            resource:
              server:
                introspect:
                  local-cache:
                    enabled: true
                    expire-seconds: 3              # TTL（秒），默认 3s，撤销感知延迟 = TTL
                    max-size: 10000
                    stats-log-interval-seconds: 60 # 统计日志间隔（秒）
                    fallback:
                      enabled: false               # 兜底降级，默认关闭，开启后接受安全与可用性的权衡
                      stale-ttl-multiplier: 10     # 兜底 TTL = expire-seconds × 此值
                      stale-max-size: 10000
```

---

## 使用安全上下文

验证通过后，claims 自动注入到请求上下文，通过静态 API 读取：

```java
String clientId = SimpleAkskSecurityContextHelper.getClientId();
String clientType = SimpleAkskSecurityContextHelper.getClientType();
String userId = SimpleAkskSecurityContextHelper.getUserId();
String username = SimpleAkskSecurityContextHelper.getUsername();
String scope = SimpleAkskSecurityContextHelper.getScope();
```

---

## 权限注解

```java
// 要求请求携带 AKSK 安全上下文
@RequireContext
public String protectedApi() { ...}

// 要求 userId 不为空
@RequireField("userId")
public String userApi() { ...}

// 要求 clientType = platform
@RequireFieldValue(field = "clientType", value = "platform")
public String platformApi() { ...}

// SpEL 表达式
@RequireExpression(
        value = "#context['scope'] != null && #context['scope'].contains('/api/read')",
        message = "需要 /api/read 权限"
)
public String readApi() { ... }
```

---

## 监听 AkskAccessEvent

验证通过后发布 `AkskAccessEvent`，可用于审计日志：

```java

@EventListener
public void onAkskAccess(AkskAccessEvent event) {
    log.info("Access: clientId={}, source={}, uri={}",
            event.getClientId(), event.getSource(), event.getRequestUri());
    // source: "introspect"
}
```

---

## 配置说明

| 配置项                                               | 说明                                    | 默认值     |
|---------------------------------------------------|---------------------------------------|---------|
| `enabled`                                          | 是否启用                                  | true    |
| `introspect.endpoint`                              | introspect 端点地址                        | -       |
| `introspect.client-id`                             | 调 introspect 用的 clientId（留空则不带认证）    | -       |
| `introspect.client-secret`                         | 调 introspect 用的 clientSecret              | -       |
| `introspect.local-cache.enabled`                   | 是否启用本地缓存                              | true    |
| `introspect.local-cache.expire-seconds`            | 本地缓存 TTL（秒），撤销感知延迟 = TTL              | 3       |
| `introspect.local-cache.max-size`                  | 本地缓存最大条目数                             | 10000   |
| `introspect.local-cache.stats-log-interval-seconds` | 统计日志打印间隔（秒）                          | 60      |
| `introspect.local-cache.fallback.enabled`           | 是否启用兜底降级                              | false   |
| `introspect.local-cache.fallback.stale-ttl-multiplier` | 兜底 TTL 倍数（兜底 TTL = expire-seconds × 此值） | 10      |
| `introspect.local-cache.fallback.stale-max-size`   | 兜底缓存最大条目数                             | 10000   |
| `security.protected-paths`                          | 需要认证的路径                               | [/api/**] |
| `security.permit-all-paths`                         | 白名单路径                                 | []      |

---

## 版本历史

### 2.0.0 (2026-05-25)

Breaking Change：移除 JWT 本地验证模式，仅保留 INTROSPECT 模式（JWE 无法本地解密）。删除 VerificationMode、JwtAuthenticationConverter 等 JWT 相关类；AkskJwtContextProvider 重命名为 AkskUserContextProvider；移除 jwt.* 配置；resource-core 升级至 2.0.0。

详见 [CHANGELOG.2.0.0.md](CHANGELOG.2.0.0.md)

### 1.0.6 (2026-05-06)

`com.nimbusds:oauth2-oidc-sdk` 依赖范围由 `compileOnly` 改为 `api`，确保接入方无需手动引入该依赖。

### 1.0.5 (2026-05-02)

升级 core 至 1.0.3，常量和工具方法迁移到 core，消除模块间重复代码。

### 1.0.4 (2026-05-02)

新增兜底缓存降级策略、缓存统计日志；默认验证模式改为 INTROSPECT；修复 testMaxSizeEviction flaky；代码重构提取 ConverterHelper。

### 1.0.3 (2026-04-25)

新增 Introspect 本地缓存（默认开启，TTL 3s），消除热路径 HTTP 往返；消除硬编码字符串。

### 1.0.2 (2026-04-13)

新增 Introspect 验证模式，修复 scope claim 解析 bug。

### 1.0.1

初始功能完善。

### 1.0.0

初始版本发布。

---

## 许可证

Apache License 2.0