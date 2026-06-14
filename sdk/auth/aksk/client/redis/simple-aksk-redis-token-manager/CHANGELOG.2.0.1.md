# Changelog - 2.0.1

## Security Hardening

- **缓存 Key 算法升级**：将 `String.hashCode()`（32-bit）替换为 SHA-256 截断 128-bit hex
  - 消除生日悖论碰撞风险（10w 上下文从 ~70% 降到 ~1.5e-29）
  - 防止跨用户 / 跨 scope Token 串号导致的潜在越权风险
  - 经典 hashCode 碰撞输入（如 `"Aa"` / `"BB"` 同 hashCode = 2112）现已映射到不同 cacheKey

## Internal

- 新增 `support/CacheKeyHelper`：集中 Key 生成逻辑（SHA-256 + UTF-8 + 截断 hex）
- 新增 `constant/SimpleAkskRedisTokenManagerConstant` / `ErrorCode` / `ErrorMessage`：集中常量
- 新增 `exception/SimpleAkskRedisTokenManagerException` / `CacheKeyGenerationException`：模块异常体系

### 规范合规修复（与本次升级一并处理）

- `SimpleAkskRedisTokenManagerProperties` 配置前缀引用 `SimpleAkskClientCoreConstant.CONFIG_PREFIX`，去除硬编码字符串
- `cacheName` 默认值常量化为 `SimpleAkskRedisTokenManagerConstant.DEFAULT_TOKEN_CACHE_NAME`
- `RedisTokenManager` 中 `L2_POLL_INTERVAL_MS` / `DEFAULT_LOCK_TIMEOUT_SECONDS` 上提至 `SimpleAkskRedisTokenManagerConstant`，符合「常量集中管理」规范
- `TokenWithExpiry.token` / `securityContext` 字段补充 javadoc

## Test Coverage

新增三层测试覆盖 securityContext 多租户隔离场景，确保升级动机有可证伪的回归网：

| 层级 | 测试类 | 关键用例 |
|------|--------|---------|
| 算法 | `CacheKeyHelperTest` | null/空/blank → "default"；1000 条多租户 JSON 全不重复；Aa/BB hashCode 碰撞被 SHA-256 区分；中文/emoji UTF-8 编码 |
| 集成 | `RedisTokenManagerTest` | 非空 securityContext 走 Helper 生成 cacheKey |
| 集成 | `RedisTokenManagerDefaultCacheKeyTest` | null securityContext 兜底为 "default" |
| **端到端（新增）** | `RedisTokenManagerMultiSecurityContextEndToEndTest` | 双租户 L2 隔离；clearToken 仅清当前 SC；hashCode 碰撞输入 Aa/BB 全流程独立缓存 |

测试样本：41/41 通过。

## Compatibility

- **API / 配置零变更**，业务代码无需修改
- 旧缓存条目自然过期失效，首次升级后短时间可能 cache miss → fetch（最大窗口 = L2 expireSeconds）

## Cascade Releases

依赖本模块的下游同步发版：
- `simple-aksk-feign-redis-client-starter:2.0.1`
- `simple-aksk-resttemplate-redis-client-starter:2.0.1`

## Dependencies

- `simple-aksk-client-core:2.0.0`
- `smart-cache-starter:1.1.2`
