# 最佳实践

本文档提供 Smart Cache Starter 的最佳实践指南,包括新手入门、常见场景速查表、命名规范、TTL 配置建议和常见错误解决方案。

## 1. 新手入门: 3 步搞定缓存

### 第一步: 加依赖

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>smart-cache-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 第二步: 写配置(复制粘贴即可)

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          enabled: true
          me: my-app-instance  # 改成你的应用名(同一应用的多个实例使用相同的 me)
          l1:
            expire-seconds: 300   # 5分钟
          l2:
            expire-seconds: 3600  # 1小时

spring:
  redis:
    host: localhost  # 改成你的 Redis 地址
    port: 6379
```

### 第三步: 加注解

```java
@Service
public class UserService {

    // 查询时自动缓存
    @SmartCacheable(cacheName = "userCache", key = "#userId")
    public User getUser(Long userId) {
        return userRepository.findById(userId);
    }

    // 更新时自动刷新缓存
    @SmartCachePut(cacheName = "userCache", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    // 删除时自动清除缓存
    @SmartCacheEvict(cacheName = "userCache", key = "#userId")
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }
}
```

**完成!** 你的应用已经有了双层缓存,自动防止缓存穿透、击穿、雪崩。

## 2. 常见场景速查表

### 场景 1: 查询用户信息

```java
// 直接加注解,自动缓存
@SmartCacheable(cacheName = "userCache", key = "#userId")
public User getUser(Long userId) {
    return userRepository.findById(userId);
}
```

### 场景 2: 查询商品列表(带条件)

```java
// 组合多个参数作为 key
@SmartCacheable(
    cacheName = "productCache",
    key = "'list:' + #category + ':' + #page"
)
public List<Product> getProducts(String category, int page) {
    return productRepository.findByCategoryAndPage(category, page);
}
```

### 场景 3: 更新用户信息

```java
// 更新数据库后自动刷新缓存
@SmartCachePut(cacheName = "userCache", key = "#user.id")
public User updateUser(User user) {
    return userRepository.save(user);
}
```

### 场景 4: 删除用户

```java
// 删除数据库后自动清除缓存
@SmartCacheEvict(cacheName = "userCache", key = "#userId")
public void deleteUser(Long userId) {
    userRepository.deleteById(userId);
}
```

### 场景 5: 批量查询

```java
@Autowired
private SmartCacheManager cacheManager;

public List<User> getUsers(List<Long> userIds) {
    List<String> keys = userIds.stream()
        .map(String::valueOf)
        .collect(Collectors.toList());

    Map<String, Object> cached = cacheManager.getAll("userCache", keys);
    // 处理缓存结果...
}
```

### 场景 6: 清空某个业务的所有缓存

```java
// 清空 userCache 的所有数据
@SmartCacheEvict(cacheName = "userCache", allEntries = true)
public void clearAllUsers() {
    // 执行清理逻辑
}
```

## 3. 缓存命名规范(照着做就对了)

```java
// ✅ 推荐: 业务含义清晰
@SmartCacheable(cacheName = "userCache", key = "'user:' + #userId")
@SmartCacheable(cacheName = "productCache", key = "'product:' + #productId")
@SmartCacheable(cacheName = "orderCache", key = "'order:' + #orderId")

// ❌ 不推荐: 名称模糊
@SmartCacheable(cacheName = "cache1", key = "#id")
@SmartCacheable(cacheName = "data", key = "#key")
```

### 命名建议

- **cacheName**: `业务名 + Cache`,如 `userCache`、`productCache`
- **key**: `业务前缀 + 参数`,如 `'user:' + #userId`

## 4. TTL 配置速查表(根据业务选择)

| 业务类型 | L1 TTL | L2 TTL | 说明 |
|---------|--------|--------|------|
| 热点数据(用户信息) | 5分钟 | 1小时 | 高频访问,L1 短期缓存 |
| 配置数据 | 30分钟 | 2小时 | 更新频率低,可以长期缓存 |
| 实时数据(库存) | 1分钟 | 5分钟 | 需要较高实时性 |
| 冷数据(历史订单) | 1小时 | 24小时 | 访问频率低,长期缓存 |

### 配置示例

```yaml
# 热点数据配置
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          l1:
            expire-seconds: 300    # 5分钟
          l2:
            expire-seconds: 3600   # 1小时
```

## 5. 防止缓存雪崩(必须开启)

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          l2:
            ttl-random-offset-ratio: 0.1  # TTL ± 10% 随机偏移
```

**原理**: 假设 TTL 是 3600 秒,实际 TTL 会在 3240-3960 秒之间随机,避免大量缓存同时失效。

## 6. 一致性模式说明

### 默认使用强一致性模式(推荐)

框架默认开启 `strong` 模式,适合所有场景:
- 多实例部署: 自动同步缓存失效
- 单实例部署: 也能正常工作,无副作用

```yaml
# 默认配置,无需修改
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          consistency:
            mode: strong  # 默认值
```

### 如需切换到最终一致性(仅在极致性能要求时)

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          consistency:
            mode: eventual
```

## 7. 应用启动时预热缓存

```java
@Component
public class CacheWarmUpService {

    // 应用启动时自动执行,order 越小越先执行
    @SmartCacheWarmUp(cacheName = "configCache", order = 1)
    public Map<String, Object> loadConfigs() {
        Map<String, Object> configs = new HashMap<>();
        // 加载配置数据
        configs.put("config:1", configService.getConfig(1));
        configs.put("config:2", configService.getConfig(2));
        return configs;
    }

    @SmartCacheWarmUp(cacheName = "hotProductCache", order = 2)
    public Map<String, Object> loadHotProducts() {
        Map<String, Object> products = new HashMap<>();
        // 加载热门商品
        List<Product> hotProducts = productService.getTop100();
        for (Product p : hotProducts) {
            products.put("product:" + p.getId(), p);
        }
        return products;
    }
}
```

## 8. 条件缓存(只缓存有效数据)

```java
// 只缓存非空结果
@SmartCacheable(
    cacheName = "userCache",
    key = "#userId",
    condition = "#result != null"
)
public User getUser(Long userId) {
    return userRepository.findById(userId);
}

// 只缓存 VIP 用户
@SmartCacheable(
    cacheName = "userCache",
    key = "#userId",
    condition = "#result != null && #result.vip == true"
)
public User getVipUser(Long userId) {
    return userRepository.findById(userId);
}
```

## 9. 监控缓存命中率

```java
@RestController
public class CacheMonitorController {

    @Autowired
    private SmartCacheManager cacheManager;

    @GetMapping("/cache/stats")
    public String getStats() {
        CacheStats stats = cacheManager.getStats("userCache");
        return String.format(
            "L1命中: %d, L2命中: %d, 未命中: %d, 命中率: %.2f%%",
            stats.getL1HitCount(),
            stats.getL2HitCount(),
            stats.getMissCount(),
            stats.getHitRate()
        );
    }
}
```

## 10. 常见错误及解决方案

### 错误 1: 缓存没生效

**原因**: 方法必须是 public,且不能在同一个类内部调用。

```java
// ❌ 错误: private 方法
@SmartCacheable(cacheName = "userCache", key = "#userId")
private User getUser(Long userId) { ... }

// ❌ 错误: 同类内部调用
public void test() {
    this.getUser(1L);  // 缓存不生效
}

// ✅ 正确: public 方法,外部调用
@SmartCacheable(cacheName = "userCache", key = "#userId")
public User getUser(Long userId) { ... }
```

### 错误 2: Redis 连接失败

**检查清单**:
1. Redis 是否启动: `redis-cli ping`
2. 配置是否正确: 检查 `spring.redis.host` 和 `spring.redis.port`
3. 网络是否通畅: `telnet redis-host 6379`

### 错误 3: 缓存数据不一致

**原因**: 默认已开启强一致性,如果仍有问题,检查 Redis Pub/Sub 是否正常。

**检查方法**:
```bash
# 检查 Redis Pub/Sub 是否正常
redis-cli PUBSUB CHANNELS "*cache-invalidation*"
```

**如果确实需要最终一致性**:
```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          consistency:
            mode: eventual
```

## 11. 性能优化 Checklist

- [ ] 开启 TTL 随机偏移(防雪崩)
- [ ] 根据业务设置合理的 TTL
- [ ] 使用批量操作减少网络开销
- [ ] 控制 L1 缓存大小(避免 OOM)
- [ ] 预热关键数据(提升启动后性能)
- [ ] 启用统计监控(观察命中率)

## 12. 快速排查问题

### 开启 DEBUG 日志

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.cache: DEBUG
```

### 查看缓存统计

```java
CacheStats stats = cacheManager.getStats("userCache");
System.out.println(stats);
```

### 检查 Redis 数据

```bash
# 查看所有 key
redis-cli keys "sure-cache:*"

# 查看某个 key 的值
redis-cli get "sure-cache:userCache:instance::{user:123}"

# 查看 TTL
redis-cli ttl "sure-cache:userCache:instance::{user:123}"
```

## 13. 生产环境建议

### 监控告警

- 监控 Redis 连接状态
- 监控缓存命中率
- 监控 L1 缓存内存使用
- 设置命中率告警阈值(如 < 80%)

### 容量规划

- 根据业务量评估 L1 缓存大小
- 预留 Redis 内存空间(建议 2-3 倍数据量)
- 定期清理过期数据

### 高可用配置

- 使用 Redis Sentinel 或 Cluster
- 配置合理的连接池参数
- 启用 Redis 持久化(AOF/RDB)

### 安全配置

- 设置 Redis 密码
- 限制 Redis 访问 IP
- 使用独立的 Redis 实例或数据库

## 14. 迁移指南

### 从 Spring Cache 迁移

```java
// 原 Spring Cache 注解
@Cacheable(value = "userCache", key = "#userId")
public User getUser(Long userId) { ... }

// 迁移到 Smart Cache
@SmartCacheable(cacheName = "userCache", key = "#userId")
public User getUser(Long userId) { ... }
```

### 从 Caffeine 迁移

```java
// 原 Caffeine 代码
Cache<String, User> cache = Caffeine.newBuilder()
    .maximumSize(10000)
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .build();

User user = cache.get(userId, k -> loadUser(k));

// 迁移到 Smart Cache
@Autowired
private SmartCacheManager cacheManager;

User user = cacheManager.get("userCache", userId, () -> loadUser(userId));
```

### 从 Redis 迁移

```java
// 原 Redis 代码
String key = "user:" + userId;
String json = redisTemplate.opsForValue().get(key);
if (json == null) {
    User user = loadUser(userId);
    redisTemplate.opsForValue().set(key, toJson(user), 1, TimeUnit.HOURS);
    return user;
}
return fromJson(json, User.class);

// 迁移到 Smart Cache
@SmartCacheable(cacheName = "userCache", key = "#userId")
public User getUser(Long userId) {
    return loadUser(userId);
}
```
