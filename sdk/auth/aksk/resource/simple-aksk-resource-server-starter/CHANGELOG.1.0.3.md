# CHANGELOG - simple-aksk-resource-server-starter 1.0.3

## 发布日期

2026-04-25

## 新增

### Introspect 本地缓存

在 `INTROSPECT` 验证模式下，resource-server-starter 侧新增 Caffeine 本地缓存，消除热路径的 HTTP 往返。

**行为**：
- 缓存命中时直接返回，跳过对 AKSK Server 的 HTTP 调用
- 撤销感知延迟 = TTL（默认 3s）
- `active=false` 的结果同样写入缓存，避免被撤销的 token 反复打 AKSK Server
- HTTP 调用失败时不写缓存，本次请求正常报错

**默认配置**（开箱即用，无需额外配置）：

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
                    enabled: true          # 默认开启
                    expire-seconds: 3      # TTL 3s
                    max-size: 10000        # 最大条目数
```

**关闭缓存**：

```yaml
io.github.surezzzzzz.sdk.auth.aksk.resource.server.introspect.local-cache.enabled: false
```

## 变更

- `AkskIntrospectionAuthenticationConverter`：消除硬编码字符串，全部提取到 `SimpleAkskResourceServerConstant`
- `build.gradle`：Caffeine 依赖由 `implementation` 改为 `api`，接入方无需重复声明

## 新增类

| 类 | 包 | 说明 |
|---|---|---|
| `IntrospectResult` | `model` | Introspect 结果缓存模型（active + attributes） |
| `IntrospectLocalCacheHelper` | `support` | Caffeine 缓存封装，管理缓存生命周期 |

## 新增常量（SimpleAkskResourceServerConstant）

| 常量 | 值 | 说明 |
|---|---|---|
| `DEFAULT_LOCAL_CACHE_ENABLED` | `true` | 本地缓存默认开启 |
| `DEFAULT_LOCAL_CACHE_EXPIRE_SECONDS` | `3` | 默认 TTL（秒） |
| `DEFAULT_LOCAL_CACHE_MAX_SIZE` | `10000` | 默认最大条目数 |
| `INTROSPECT_CLAIM_ACTIVE` | `"active"` | Introspect 响应 active 字段名 |
| `JWT_CLAIM_SUB` | `"sub"` | JWT subject claim 名 |
| `AUTHORITY_SCOPE_PREFIX` | `"SCOPE_"` | Spring Security 权限前缀 |
| `HEADER_USER_AGENT` | `"User-Agent"` | HTTP User-Agent 请求头名 |
| `ACCESS_SOURCE_INTROSPECT` | `"introspect"` | AkskAccessEvent source 标识 |
| `FIELD_TRACE_ID` | `"traceId"` | 链路追踪 ID 字段名 |

## 依赖变更

新增：`com.github.ben-manes.caffeine:caffeine:2.9.3`（api 依赖）
