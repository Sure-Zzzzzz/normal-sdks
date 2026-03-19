# 配置说明

本文档详细说明 Smart Cache Starter 的所有配置参数、Redis Key 设计和配置校验规则。

## 基础配置

| 配置项 | 说明 | 默认值 | 必填 |
|--------|------|--------|------|
| `io.github.surezzzzzz.sdk.cache.enabled` | 是否启用缓存 | true | 否 |
| `io.github.surezzzzzz.sdk.cache.key-prefix` | Redis key 前缀 | sure-cache | 否 |
| `io.github.surezzzzzz.sdk.cache.me` | 应用标识(用于区分不同应用,同一应用的多个实例应使用相同的 me) | - | 是 |

## L1 缓存配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `l1.enabled` | 是否启用 L1 缓存 | true |
| `l1.max-size` | 最大缓存条目数 | 10000 |
| `l1.expire-seconds` | 过期时间(秒) | 300 |
| `l1.refresh-seconds` | 异步刷新时间(秒) | 240 |

## L2 缓存配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `l2.enabled` | 是否启用 L2 缓存 | true |
| `l2.expire-seconds` | 过期时间(秒) | 3600 |
| `l2.ttl-random-offset-ratio` | TTL 随机偏移比例 | 0.1 |

## 一致性配置

| 配置项 | 说明 | 可选值 | 默认值 |
|--------|------|--------|--------|
| `consistency.mode` | 一致性模式 | strong / eventual | strong |
| `consistency.pubsub-channel-prefix` | Pub/Sub 频道前缀 | 任意字符串 | {keyPrefix}-cache-invalidation |

## 统计配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `stats.enabled` | 是否启用统计 | true |

## 预热配置

| 配置项 | 说明 | 默认值 | 范围 |
|--------|------|--------|------|
| `warmUp.completion-mark-ttl-seconds` | 预热完成标记 TTL(秒) | 600 | 60-3600 |

## 配置校验

框架在启动时会自动校验配置参数,对于不合理的配置会自动调整并输出警告日志:

### L1 配置校验

- **maxSize**: 范围 [1, 1000000],超出范围使用默认值 10000
- **expireSeconds**: 必须 >= 1,否则使用默认值 300
- **refreshSeconds**: 必须 >= 1 且 < expireSeconds,否则自动调整

### L2 配置校验

- **expireSeconds**: 必须 >= 1,否则使用默认值 3600
- **ttlRandomOffsetRatio**: 范围 [0, 1],超出范围使用默认值 0.1

### 预热配置校验

- **completionMarkTtlSeconds**: 范围 [60, 3600],超出范围使用默认值 600

## Redis Key 设计

### Key 格式

```
{keyPrefix}:{cacheName}:{me}::{key}
```

- `keyPrefix`: 全局前缀,区分不同应用
- `cacheName`: 缓存名称,区分不同业务
- `me`: 应用标识,用于区分不同应用(同一应用的多个实例使用相同的 me)
- `key`: 具体的缓存 key

### Hash Tag 支持

框架自动为 key 添加 Hash Tag,确保 Redis Cluster 下相同业务的 key 在同一个 slot:

```
sure-cache:userCache:instance::{user:123}
                                ^^^^^^^^
                                Hash Tag
```

### Lock Key 格式

分布式锁使用独立的 key 格式:

```
{keyPrefix}-lock:{cacheName}:{me}:{key}
```

- 包含 `me` 确保不同应用的锁隔离
- 同一应用的多个实例共享锁,防止缓存击穿

### Pub/Sub Channel 格式

强一致性模式下的消息频道:

```
{pubsubChannelPrefix}:{me}:{cacheName}
```

- `pubsubChannelPrefix`: Pub/Sub 频道前缀
- `me`: 应用标识,确保不同应用的消息隔离
- `cacheName`: 缓存名称

## 完整配置示例

### 基础配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          enabled: true
          key-prefix: my-app-cache
          me: my-app-instance
          l1:
            enabled: true
            max-size: 10000
            expire-seconds: 300
            refresh-seconds: 240
          l2:
            enabled: true
            expire-seconds: 3600
            ttl-random-offset-ratio: 0.1
          consistency:
            mode: strong
          stats:
            enabled: true

spring:
  redis:
    host: localhost
    port: 6379
```

### 热点数据配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          l1:
            expire-seconds: 300      # 5 分钟
          l2:
            expire-seconds: 3600     # 1 小时
```

### 冷数据配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          l1:
            expire-seconds: 1800     # 30 分钟
          l2:
            expire-seconds: 7200     # 2 小时
```

### 实时数据配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          l1:
            expire-seconds: 60       # 1 分钟
          l2:
            expire-seconds: 300      # 5 分钟
```

### 最终一致性配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          l1:
            expire-seconds: 300      # 5 分钟
          l2:
            expire-seconds: 3600     # 1 小时
          consistency:
            mode: eventual
```

## 性能优化建议

### 1. 合理设置 TTL

```yaml
# 热点数据: 短 L1 TTL + 长 L2 TTL
l1:
  expire-seconds: 300      # 5 分钟
l2:
  expire-seconds: 3600     # 1 小时

# 冷数据: 长 L1 TTL + 更长 L2 TTL
l1:
  expire-seconds: 1800     # 30 分钟
l2:
  expire-seconds: 7200     # 2 小时
```

### 2. 启用 TTL 随机偏移

防止缓存雪崩:

```yaml
l2:
  ttl-random-offset-ratio: 0.1  # TTL ± 10% 随机偏移
```

### 3. 控制 L1 缓存大小

避免内存溢出:

```yaml
l1:
  max-size: 10000  # 根据实际内存调整
```

### 4. 使用批量操作

减少网络开销:

```java
// 批量写入
Map<String, Object> data = new HashMap<>();
data.put("key1", value1);
data.put("key2", value2);
cacheManager.putAll("cache", data);

// 批量读取
List<String> keys = Arrays.asList("key1", "key2");
Map<String, Object> results = cacheManager.getAll("cache", keys);
```

## Redis 配置建议

### 基础配置

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: your-password  # 如果有密码
    database: 0
    timeout: 3000ms          # 连接超时 3 秒
```

### 连接池配置

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 8        # 最大连接数
        max-idle: 8          # 最大空闲连接
        min-idle: 0          # 最小空闲连接
        max-wait: -1ms       # 最大等待时间
```

### Cluster 配置

```yaml
spring:
  redis:
    cluster:
      nodes:
        - 192.168.1.1:6379
        - 192.168.1.2:6379
        - 192.168.1.3:6379
      max-redirects: 3
```

### Sentinel 配置

```yaml
spring:
  redis:
    sentinel:
      master: mymaster
      nodes:
        - 192.168.1.1:26379
        - 192.168.1.2:26379
        - 192.168.1.3:26379
```
