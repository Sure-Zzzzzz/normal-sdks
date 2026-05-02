# CHANGELOG - simple-aksk-resource-server-starter 1.0.4

## 发布日期

2026-05-02

## 新增

### 1. Introspect 兜底缓存（降级策略）

端点不可用时，可配置使用兜底缓存放行，避免因 AKSK Server 短暂故障导致全量 401。

**行为**：
- 兜底缓存 TTL = 主缓存 TTL × `stale-ttl-multiplier`（默认 10 倍，即 30s）
- 只对 `active=true` 的条目兜底，已撤销 token（`active=false`）不受益
- `active=false` 也写入兜底缓存，使撤销信息尽快传播到兜底层
- 端点恢复后自然回归正常路径，兜底缓存随下次成功调用更新
- 默认关闭，需显式开启

**配置**：

```yaml
introspect:
  local-cache:
    enabled: true
    expire-seconds: 3
    fallback:
      enabled: true              # 默认 false，需显式开启
      stale-ttl-multiplier: 10   # 兜底 TTL = expire-seconds × 此值，建议范围 [2, 100]
      stale-max-size: 10000      # 兜底缓存最大条目数
```

> **注意**：开启兜底降级意味着接受安全与可用性的权衡——端点挂掉期间被撤销的 token，在兜底 TTL 内仍可放行。

### 2. 缓存统计日志

启用 Caffeine `recordStats()`，在两个时机打印统计：

- **cache miss 时**：按 `stats-log-interval-seconds` 间隔输出，不刷屏
- **应用关闭时**：打印最终统计

统计内容：hitCount / missCount / hitRate / evictionCount / 兜底命中次数。

```yaml
introspect:
  local-cache:
    stats-log-interval-seconds: 60   # 默认 60s
```

## 优化

### 1. 默认验证模式改为 INTROSPECT

`verification-mode` 默认值从 `JWT` 改为 `INTROSPECT`，更安全，支持即时撤销感知。

> **Breaking change**：已有未配置 `verification-mode` 的用户会切换到 introspect 模式，需同时配置 `introspect.endpoint`。

### 2. INTROSPECT 模式下不再要求配置公钥

`JwtDecoder` bean 在 INTROSPECT 模式下返回占位实现，不再因缺少公钥配置而启动报错。

### 3. 代码重构

提取 `ConverterHelper`，消除 `AkskJwtAuthenticationConverter` 和 `AkskIntrospectionAuthenticationConverter` 中的重复代码（`buildAccessEvent`、`getCurrentRequest`、`extractAuthorities`、`claimValueToString`）。

## Bug 修复

### 1. testMaxSizeEviction 偶发失败

Caffeine 淘汰是异步的，`IntrospectLocalCacheHelper` 新增 `cleanUp()` 方法，测试中插入后调用触发同步淘汰再断言，消除 flaky。

## 新增类

| 类 | 包 | 说明 |
|---|---|---|
| `ConverterHelper` | `support` | Converter 公共工具方法 |

## 新增常量（SimpleAkskResourceServerConstant）

| 常量 | 值 | 说明 |
|---|---|---|
| `ACCESS_SOURCE_JWT` | `"jwt"` | AkskAccessEvent source 标识：JWT 验证 |
| `DEFAULT_FALLBACK_ENABLED` | `false` | 兜底缓存默认关闭 |
| `DEFAULT_STALE_TTL_MULTIPLIER` | `10` | 兜底 TTL 倍数默认值 |
| `DEFAULT_STALE_MAX_SIZE` | `10000` | 兜底缓存默认最大条目数 |
| `DEFAULT_STATS_LOG_INTERVAL_SECONDS` | `60` | 统计日志默认间隔（秒） |
| `MIN_STALE_TTL_MULTIPLIER` | `2` | 兜底 TTL 倍数建议最小值 |
| `WARN_STALE_TTL_MULTIPLIER_MAX` | `100` | 兜底 TTL 倍数建议最大值（超出打 WARN） |
