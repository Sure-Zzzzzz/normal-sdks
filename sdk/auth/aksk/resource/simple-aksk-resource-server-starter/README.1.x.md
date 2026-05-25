# Simple AKSK Resource Server Starter 1.x 封版文档

> **说明**：此文档为 1.x 版本（1.0.6）的冻结快照。如果你使用的是 1.x 版本，请参考此文档。

## 版本

1.0.6（2026-05-06）

## 特性

- ✅ **INTROSPECT 模式**：调 `/oauth2/introspect` 验证，支持即时感知 token 撤销
- ✅ **本地缓存**：热路径命中缓存时无 HTTP 调用（默认 TTL 3s）
- ✅ **兜底降级**：可选开启端点故障时用历史缓存放行
- ✅ **安全上下文 API**：通过静态方法读取 claims，无需注入
- ✅ **权限注解**：支持 `@RequireContext` / `@RequireField` / `@RequireExpression`
- ✅ **审计事件**：每次验证通过后发布 `AkskAccessEvent`
- ✅ **JWT 本地验证模式**（默认 INTROSPECT，JWT 模式需配置 `jwt.issuer-uri`）

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-resource-server-starter:1.0.6'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.security:spring-security-oauth2-resource-server'
implementation 'org.springframework.security:spring-security-oauth2-jose'
```

## 配置

### INTROSPECT 模式（默认）

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
                  client-id: AKP...
                  client-secret: SK...
```

### JWT 模式（需配置 RSA 公钥）

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
                verification-mode: JWT
                jwt:
                  issuer-uri: http://localhost:8080
                  public-key: classpath:keys/public.pem
```

## 版本历史

详见 [CHANGELOG.1.0.6.md](CHANGELOG.1.0.6.md)