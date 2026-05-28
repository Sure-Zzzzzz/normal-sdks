# Simple AKSK Server Starter 1.x 封版文档

> **说明**：此文档为 1.x 版本（1.1.3）的冻结快照。2.0.0 文档请查看 [README.md](README.md)。

## 版本

1.1.3（2026-05-01）

## 特性

- ✅ **双层级 AKSK 管理**：平台级（AKP）和用户级（AKU）
- ✅ **OAuth2 标准协议**：基于 Spring Authorization Server 0.4.1
- ✅ **JWT Token 签发**：RSA 算法签发（JWS 格式，payload 明文可读）
- ✅ **Token 即时撤销**：`/oauth2/revoke` 撤销后 introspect 立即返回 `active=false`
- ✅ **L1+L2 两级缓存**：Caffeine 本地缓存 + Redis 分布式缓存
- ✅ **多实例缓存一致性**：Redis Pub/Sub 广播缓存失效
- ✅ **Token 审计事件**：颁发、撤销、删除、introspect 全生命周期事件
- ✅ **Admin 管理界面**：AKSK 和 Token 的完整管理操作

## 依赖

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-server-starter:1.1.3'
```

## 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        auth:
          aksk:
            server:
              jwt:
                key-id: sure-auth-aksk-2026
                expires-in: 3600
                public-key: classpath:keys/public.pem
                private-key: classpath:keys/private.pem
              admin:
                enabled: true
                username: admin
                password: your_password
```

## 版本历史

详见各 [CHANGELOG.1.x.x.md](CHANGELOG.1.1.3.md)
