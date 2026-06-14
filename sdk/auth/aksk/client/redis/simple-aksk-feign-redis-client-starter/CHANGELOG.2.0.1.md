# CHANGELOG - simple-aksk-feign-redis-client-starter 2.0.1

## 发布日期

2026-06-14

## 版本类型

Patch / Security Hardening - 级联升级 token-manager

## 变更概述

`simple-aksk-redis-token-manager` 从 2.0.0 升级至 2.0.1（Security Hardening + 内部规范合规重构）：

1. **缓存 Key 算法升级**：从 `String.hashCode()`（32-bit）替换为 SHA-256 截断 128-bit hex
   - 消除生日悖论碰撞风险（10w 多租户上下文从 ~70% 降至 ~1.5e-29）
   - 防止跨用户 / 跨 scope Token 串号导致的潜在越权风险
   - 经典 hashCode 碰撞输入（如 `"Aa"` / `"BB"` 同 hashCode = 2112）现已映射到不同 cacheKey
2. **常量集中**：token-manager 内部新增 `SimpleAkskRedisTokenManagerConstant` / `ErrorCode` / `ErrorMessage`
3. **新增模块异常体系**：`SimpleAkskRedisTokenManagerException` / `CacheKeyGenerationException`
4. **多 securityContext 端到端测试**：新增 `RedisTokenManagerMultiSecurityContextEndToEndTest` 覆盖多租户隔离、`clearToken()` 隔离、hashCode 碰撞输入全流程不串台

## 依赖变更

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-aksk-redis-token-manager` | 2.0.0 | 2.0.1 |

## 行为变更

- `AkskFeignRequestInterceptor` 行为不变
- 用户级 cacheKey 编码由 hashCode 数字串改为 SHA-256 hex（32 字符），Redis Key 命名空间形态变化
- 升级后旧缓存条目自然过期失效，首次升级后短时间可能 cache miss → fetch（最大窗口 = L2 expireSeconds），由 SmartCache 锁兜底

## 兼容性

- **API / 配置零变更**，业务代码无需修改
- 多租户场景（不同 SecurityContext 拉不同 Token）从此 100% 安全，杜绝 hashCode 碰撞串台

## 升级指南

```gradle
implementation 'io.github.sure-zzzzzz:simple-aksk-feign-redis-client-starter:2.0.1'
```

## 贡献者

- @surezzzzzz
