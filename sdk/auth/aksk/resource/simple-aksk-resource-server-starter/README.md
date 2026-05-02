# Simple AKSK Resource Server Starter

[![Version](https://img.shields.io/badge/version-1.0.4-blue.svg)](https://github.com/Sure-Zzzzzz/normal-sdks)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

资源服务器端 Token 验证器，支持 Introspect 和 JWT 两种模式，提供便捷的安全上下文 API（适用于无网关场景）。

---

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:1.0.4'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.security:spring-security-oauth2-resource-server'
implementation 'org.springframework.security:spring-security-oauth2-jose'
```

---

## 快速开始

### Introspect 模式（推荐，默认）

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
                # verification-mode 默认为 INTROSPECT，可省略
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

### JWT 模式

本地验签，性能最好，但无法感知 token 撤销：

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
                verification-mode: JWT  # 需显式指定，默认为 INTROSPECT
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

---

## 使用安全上下文

验证通过后，JWT claims 自动注入到请求上下文，通过静态 API 读取：

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
public String readApi() { ...}
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

| 配置项                                                    | 说明                                         | 默认值        |
|---------------------------------------------------------|--------------------------------------------|------------|
| `enabled`                                               | 是否启用                                       | true       |
| `verification-mode`                                     | 验证模式：`INTROSPECT`（推荐）或 `JWT`               | INTROSPECT |
| `jwt.issuer-uri`                                        | OAuth2 授权服务器 Issuer URI                    | -          |
| `jwt.public-key`                                        | RSA 公钥（PEM 格式字符串）                          | -          |
| `jwt.public-key-location`                               | RSA 公钥文件路径（classpath:/file:）               | -          |
| `introspect.endpoint`                                   | introspect 端点地址                            | -          |
| `introspect.client-id`                                  | 调 introspect 用的 clientId（留空则不带认证）          | -          |
| `introspect.client-secret`                              | 调 introspect 用的 clientSecret               | -          |
| `introspect.local-cache.enabled`                        | 是否启用本地缓存                                   | true       |
| `introspect.local-cache.expire-seconds`                 | 本地缓存 TTL（秒），撤销感知延迟 = TTL                   | 3          |
| `introspect.local-cache.max-size`                       | 本地缓存最大条目数                                  | 10000      |
| `introspect.local-cache.stats-log-interval-seconds`     | 统计日志打印间隔（秒）                                | 60         |
| `introspect.local-cache.fallback.enabled`               | 是否启用兜底降级                                   | false      |
| `introspect.local-cache.fallback.stale-ttl-multiplier`  | 兜底 TTL 倍数（兜底 TTL = expire-seconds × 此值）    | 10         |
| `introspect.local-cache.fallback.stale-max-size`        | 兜底缓存最大条目数                                  | 10000      |
| `security.protected-paths`                              | 需要认证的路径                                    | [/api/**]  |
| `security.permit-all-paths`                             | 白名单路径                                      | []         |

---

## 两种模式对比

|        | Introspect 模式（推荐，默认）             | JWT 模式  |
|--------|--------------------------------|---------|
| 验证方式   | 调 introspect 端点                | 本地用公钥验签 |
| 本地缓存   | ✅ 默认开启，热路径无 IO                 | -       |
| 兜底降级   | ✅ 可选开启，端点故障时用历史缓存放行            | -       |
| 即时撤销感知 | ✅（延迟 = TTL，默认 3s）              | ❌       |
| 依赖     | clientId + clientSecret（或开放匿名） | 只需公钥    |

---

## 版本历史

### 1.0.4 (2026-05-02)

新增兜底缓存降级策略、缓存统计日志；默认验证模式改为 INTROSPECT；修复 testMaxSizeEviction flaky；代码重构提取 ConverterHelper。详见 [CHANGELOG.1.0.4.md](CHANGELOG.1.0.4.md)

### 1.0.3 (2026-04-25)

新增 Introspect 本地缓存（默认开启，TTL 3s），消除热路径 HTTP
往返；消除硬编码字符串。详见 [CHANGELOG.1.0.3.md](CHANGELOG.1.0.3.md)

### 1.0.2 (2026-04-13)

新增 Introspect 验证模式，修复 scope claim 解析 bug。详见 [CHANGELOG.1.0.2.md](CHANGELOG.1.0.2.md)

### 1.0.1 (2026-xx-xx)

初始功能完善。

### 1.0.0

初始版本发布。

## 许可证

Apache License 2.0
