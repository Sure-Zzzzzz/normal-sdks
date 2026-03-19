# 高级特性

本文档介绍 Smart Cache Starter 的高级功能,包括 SpEL 表达式、缓存预热、批量操作、beforeInvocation 特性和统计监控。

## SpEL 表达式

支持复杂的 key 生成逻辑:

```java
@Service
public class OrderService {

    // 组合多个参数
    @SmartCacheable(
        cacheName = "orderCache",
        key = "'order:' + #userId + ':' + #orderId"
    )
    public Order getOrder(Long userId, Long orderId) {
        return orderRepository.findByUserIdAndOrderId(userId, orderId);
    }

    // 使用对象属性
    @SmartCacheable(
        cacheName = "orderCache",
        key = "'order:' + #request.userId + ':' + #request.status"
    )
    public List<Order> getOrders(OrderRequest request) {
        return orderRepository.findByUserIdAndStatus(
            request.getUserId(), request.getStatus());
    }

    // 条件缓存
    @SmartCacheable(
        cacheName = "orderCache",
        key = "#orderId",
        condition = "#orderId > 1000"  // 只缓存 ID > 1000 的订单
    )
    public Order getOrderConditional(Long orderId) {
        return orderRepository.findById(orderId);
    }
}
```

### SpEL 安全性

框架使用 `SimpleEvaluationContext` 提供 SpEL 注入防护,自动阻止:
- 类型引用 (如 `T(java.lang.Runtime)`)
- 构造函数调用 (如 `new java.lang.String()`)
- 反射操作 (如 `getClass().forName()`)

## 缓存预热

应用启动时自动加载热点数据:

```java
@Component
public class CacheWarmUpService {

    // order 控制执行顺序,数字越小越先执行
    @SmartCacheWarmUp(cacheName = "configCache", order = 1)
    public Map<String, Object> loadConfigs() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("config:1", configRepository.findById(1));
        configs.put("config:2", configRepository.findById(2));
        return configs;
    }

    @SmartCacheWarmUp(cacheName = "hotProductCache", order = 2)
    public Map<String, Object> loadHotProducts() {
        Map<String, Object> products = new HashMap<>();
        List<Product> hotProducts = productRepository.findTop100ByOrderBySalesDesc();
        for (Product product : hotProducts) {
            products.put("product:" + product.getId(), product);
        }
        return products;
    }
}
```

### 预热特性

- **顺序控制**: 通过 `order` 参数控制预热顺序
- **分布式协调**: 使用 Redis 标记确保多实例环境下只预热一次
- **自动重试**: 预热失败不影响应用启动
- **TTL 配置**: 预热完成标记默认保留 10 分钟

## 批量操作

```java
@Service
public class BatchService {

    @Autowired
    private SmartCacheManager cacheManager;

    public void batchPut() {
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", "value2");
        data.put("key3", "value3");

        cacheManager.putAll("batchCache", data);
    }

    public Map<String, Object> batchGet() {
        List<String> keys = Arrays.asList("key1", "key2", "key3");
        return cacheManager.getAll("batchCache", keys);
    }
}
```

### 批量操作优势

- **性能优化**: 减少网络往返次数
- **原子性**: L1 和 L2 分别保证批量操作的原子性
- **智能降级**: Redis 不可用时自动降级到 L1

## beforeInvocation 特性

控制缓存删除的时机,适用于需要确保缓存一定被删除的场景:

```java
@Service
public class UserService {

    // 默认: 方法执行成功后删除缓存
    @SmartCacheEvict(cacheName = "userCache", key = "#userId")
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
        // 如果这里抛出异常,缓存不会被删除
    }

    // beforeInvocation=true: 方法执行前删除缓存
    @SmartCacheEvict(cacheName = "userCache", key = "#userId", beforeInvocation = true)
    public void deleteUserBefore(Long userId) {
        // 缓存已经被删除
        userRepository.deleteById(userId);
        // 即使这里抛出异常,缓存也已经被删除了
    }
}
```

### 使用场景

- `beforeInvocation=false` (默认): 适合大部分场景,只有操作成功才删除缓存
- `beforeInvocation=true`: 适合必须确保缓存被删除的场景,即使操作失败

## 统计监控

```java
@Service
public class MonitorService {

    @Autowired
    private SmartCacheManager cacheManager;

    public void printStats() {
        CacheStats stats = cacheManager.getStats("userCache");

        System.out.println("L1 命中次数: " + stats.getL1HitCount());
        System.out.println("L2 命中次数: " + stats.getL2HitCount());
        System.out.println("未命中次数: " + stats.getMissCount());
        System.out.println("L1 命中率: " + stats.getL1HitRate() + "%");
        System.out.println("总命中率: " + stats.getHitRate() + "%");
    }
}
```

### 统计指标

- **L1 命中次数**: 从 Caffeine 缓存命中的次数
- **L2 命中次数**: 从 Redis 缓存命中的次数
- **未命中次数**: 需要从数据源加载的次数
- **L1 命中率**: L1 命中次数 / 总请求次数
- **总命中率**: (L1 + L2 命中次数) / 总请求次数

### 监控建议

- 定期检查命中率,优化缓存策略
- L1 命中率低可能需要增加 L1 容量或延长 TTL
- 总命中率低可能需要优化缓存 key 设计或预热策略

## 一致性模式对比

### 强一致性模式 (strong) - 默认推荐

**特点**:
- 使用 Redis Pub/Sub 实时同步
- 缓存失效时通知所有实例
- 多实例间秒级同步
- 单实例也可用,无副作用

**适用场景**:
- 多实例部署(推荐)
- 对数据一致性要求高
- 数据更新需要立即生效
- 单实例部署也可用

**示例配置**:
```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          consistency:
            mode: strong  # 默认值,可以不写
```

### 最终一致性模式 (eventual)

**特点**:
- L1 短 TTL (如 5 分钟)
- L2 长 TTL (如 1 小时)
- 性能略优,适合特殊场景

**适用场景**:
- 允许短时间数据不一致
- 极致性能要求
- 数据更新频率极低

**示例配置**:
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

## 常见问题

### Q: 如何选择一致性模式?

**A**:
- **推荐使用默认的 `strong` 模式**: 适合绝大多数场景,包括单实例和多实例部署
- 只有在极致性能要求且允许短时间不一致时,才考虑 `eventual` 模式

### Q: 循环依赖检测是什么?

**A**: 防止在缓存加载过程中出现死锁。例如:

```java
// 错误示例: 会触发循环依赖检测
cacheManager.get("cache", "key1", () -> {
    return cacheManager.get("cache", "key1", () -> "value");  // 循环依赖!
});
```

框架会自动检测并抛出 `SmartCacheException`。

### Q: 如何调试缓存问题?

**A**:
1. 启用 DEBUG 日志:
```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.cache: DEBUG
```

2. 查看统计信息:
```java
CacheStats stats = cacheManager.getStats("cacheName");
System.out.println(stats);
```

### Q: 支持哪些数据类型?

**A**: 支持所有可序列化的 Java 对象。建议使用:
- 基本类型及其包装类
- String
- 实现了 Serializable 的 POJO
- 集合类(List、Map、Set)
