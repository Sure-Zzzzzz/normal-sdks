# redis-retry-starter 1.1.0 CHANGELOG

## 发布日期

2026-07-11

## 类型

优化 / 兼容增强

## 变更内容

### 代码规范化

- 将自定义组件注解移动到 `annotation/RedisRetryComponent`
- 新增 `RedisRetryConstant`、`ErrorCode`、`ErrorMessage`
- `RedisRetryException` 增加 `errorCode`
- 将 Redis Key 构建逻辑收敛到 `RetryKeyHelper`
- 将配置校验逻辑收敛到 `RetryValidationHelper`
- 清理过程性注释和乱码注释

### Redis Key 增强

- 新增 `key-prefix` 配置，默认 `sure-redis-retry`
- 新增 `me` 配置，默认 `default`
- 新写入记录使用标准 Key：`{keyPrefix}:retry:{retryType}:{me}::{identifierHash}`
- Redis Cluster 场景支持 hash tag：`{keyPrefix}:retry:{retryType}:{me}::{{identifierHash}}`

### 向后兼容

- 兼容读取 1.0.0 legacy Key：`{retryType}:retry:{identifierHash}`
- 兼容读取 1.0.0 Cluster legacy Key：`{{retryType}}:retry:{identifierHash}`
- 继续记录 legacy Key 时会迁移到 1.1.0 标准 Key，并清理旧 Key
- 保留原有 `RedisRetryService` API 和 `RetryContext` API

### 测试补强

- 测试包调整为 `io.github.surezzzzzz.sdk.retry.redis.test.cases`
- 测试启动类调整为 `RedisRetryTestApplication`
- 测试类增加 `@Slf4j`
- 使用嵌入式 Redis 验证真实存储，不只断言 service 返回值
- 新增标准 Key 精确结构断言，确认包含 `keyPrefix`、`retryType`、`me` 和 `SHA1(retryKey)`
- 新增 Redis JSON 与查询结果字段级一致断言
- 新增 TTL 大于一小时缓冲时间断言
- 新增 `nextRetryTime = lastFailTime + retryIntervalMs` 断言
- 新增 `clearRetry` 后实际 Redis Key 删除断言
- 新增 `getRetryKeys` 精确 Key 集合断言
- 新增 legacy Key 读取、继续记录迁移、迁移后清理旧 Key 断言
- 新增旧 fullKey API 记录失败、查询错误信息、清理记录兼容断言
- 保留首次重试、次数累加、最大次数、间隔、上下文、隔离性等核心断言

## 向后兼容性

1.1.0 保留 1.0.0 主要 API。调用方无需修改代码即可继续使用。

如调用方依赖 Redis 中已有 1.0.0 重试记录，1.1.0 会优先读取新标准 Key，未命中时读取 legacy Key；继续记录后会把 legacy 记录迁移到标准 Key。

## 升级指南

默认无需新增配置即可升级。

如果多个应用共用同一个 Redis，建议显式配置：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        retry:
          redis:
            key-prefix: sure-redis-retry
            me: test-app
```
