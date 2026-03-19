# Smart Cache Starter

一个基于 Spring Boot 的智能二级缓存框架,提供 L1(Caffeine) + L2(Redis) 双层缓存,支持缓存穿透/击穿/雪崩防护、循环依赖检测、缓存预热等企业级特性。

## 核心特性

### 🚀 双层缓存架构
- **L1 缓存 (Caffeine)**: 进程内高速缓存,微秒级响应
- **L2 缓存 (Redis)**: 分布式缓存,支持集群共享
- **智能降级**: L1 未命中自动查询 L2,L2 未命中触发数据加载
- **Redis 降级**: Redis 不可用时自动切换到 L1-only 模式,保证服务可用性

### 🛡️ 缓存三大问题防护
- **缓存穿透**: 空值缓存,防止恶意查询击穿数据库
- **缓存击穿**: 分布式锁 + 重试机制,高并发下只有一个线程访问数据库
- **缓存雪崩**: TTL 随机偏移,避免大量缓存同时失效

### 🔄 一致性保障
- **强一致性模式(默认)**: Redis Pub/Sub 实时同步缓存失效,多实例间秒级同步,单实例也可用
- **最终一致性模式**: L1 短 TTL + L2 长 TTL,平衡性能与一致性

### 📊 企业级功能
- **循环依赖检测**: ThreadLocal 追踪加载链,防止死锁
- **缓存预热**: 应用启动时自动加载热点数据,支持顺序控制和分布式协调
- **统计监控**: L1/L2 命中率、未命中次数实时统计
- **SpEL 支持**: 动态 key 生成,支持复杂表达式,表达式缓存优化
- **条件缓存**: 基于 SpEL 的条件判断,灵活控制缓存行为
- **本地锁兜底**: 分布式锁失败时使用本地锁防止缓存击穿
- **统一异常包装**: 所有异常统一包装为 SmartCacheException,保留原始异常链
- **配置自动校验**: 启动时自动校验配置参数,防止配置错误
- **健康检查**: 自动检测 Redis 可用性,动态调整缓存策略
- **快速失败**: Redis 连接超时 3 秒,避免长时间阻塞

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>smart-cache-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置文件

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        cache:
          enabled: true                    # 启用缓存
          key-prefix: my-app-cache         # Redis key 前缀(可选,默认 sure-cache)
          me: instance                     # 应用标识(同一应用的多个实例使用相同的 me)
          l1:
            enabled: true                  # 启用 L1 缓存
            max-size: 10000                # L1 最大条目数
            expire-seconds: 300            # L1 过期时间(秒)
            refresh-seconds: 240           # L1 异步刷新时间(秒)
          l2:
            enabled: true                  # 启用 L2 缓存
            expire-seconds: 3600           # L2 过期时间(秒)
            ttl-random-offset-ratio: 0.1   # TTL 随机偏移比例(防雪崩)
          consistency:
            mode: strong                   # 一致性模式: strong(强一致,默认) / eventual(最终一致)
          stats:
            enabled: true                  # 启用统计

spring:
  redis:
    host: localhost
    port: 6379
```

### 3. 使用注解

```java
@Service
public class UserService {

    // 查询缓存
    @SmartCacheable(cacheName = "userCache", key = "#userId")
    public User getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    // 更新缓存
    @SmartCachePut(cacheName = "userCache", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    // 删除缓存
    @SmartCacheEvict(cacheName = "userCache", key = "#userId")
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    // 方法执行前删除缓存(即使方法失败也会删除)
    @SmartCacheEvict(cacheName = "userCache", key = "#userId", beforeInvocation = true)
    public void deleteUserBefore(Long userId) {
        userRepository.deleteById(userId);
    }

    // 清空所有缓存
    @SmartCacheEvict(cacheName = "userCache", allEntries = true)
    public void clearAllUsers() {
        userRepository.deleteAll();
    }
}
```

### 4. 编程式 API

```java
@Service
public class ProductService {

    @Autowired
    private SmartCacheManager cacheManager;

    public Product getProduct(Long productId) {
        // 带 loader 的查询,未命中时自动加载
        return cacheManager.get("productCache", String.valueOf(productId),
            () -> productRepository.findById(productId));
    }

    public void updateProduct(Product product) {
        productRepository.save(product);
        // 手动更新缓存
        cacheManager.put("productCache", String.valueOf(product.getId()), product);
    }

    public void deleteProduct(Long productId) {
        productRepository.deleteById(productId);
        // 手动删除缓存
        cacheManager.evict("productCache", String.valueOf(productId));
    }
}
```

## 文档导航

- [高级特性](docs/advanced.md) - SpEL 表达式、缓存预热、批量操作等高级功能
- [配置说明](docs/configuration.md) - 完整的配置参数说明和 Redis Key 设计
- [最佳实践](docs/best-practices.md) - 傻瓜式指南、常见场景速查表、性能优化建议

## 测试覆盖

框架提供了完整的测试覆盖(共 **86 个测试用例**,100% 通过),包括:

### 核心功能测试
- **端到端测试** ([EndToEndTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/EndToEndTest.java)): 14 个测试用例
- **注解 API 测试** ([AnnotationApiTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/AnnotationApiTest.java)): 7 个测试用例
- **缓存管理器测试** ([SmartCacheManagerTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/SmartCacheManagerTest.java)): 8 个测试用例

### 一致性测试
- **强一致性测试** ([StrongConsistencyTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/StrongConsistencyTest.java)): 6 个测试用例
- **最终一致性测试** ([EventualConsistencyTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/EventualConsistencyTest.java)): 3 个测试用例
- **Pub/Sub 验证测试** ([PubSubVerificationTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/PubSubVerificationTest.java)): 5 个测试用例

### 性能和稳定性测试
- **并发测试** ([ConcurrencyTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/ConcurrencyTest.java)): 8 个测试用例
- **压力测试** ([StressTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/StressTest.java)): 5 个测试用例

### 集成和边界测试
- **L1/L2 集成测试** ([L1L2CacheIntegrationTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/L1L2CacheIntegrationTest.java)): 6 个测试用例
- **边界条件测试** ([BoundaryConditionsTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/BoundaryConditionsTest.java)): 10 个测试用例
- **批量操作和统计测试** ([BatchOperationsAndStatsTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/BatchOperationsAndStatsTest.java)): 5 个测试用例

### 特殊场景测试
- **循环依赖测试** ([CircularDependencyTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/CircularDependencyTest.java)): 3 个测试用例
- **缓存预热测试** ([CacheWarmUpTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/CacheWarmUpTest.java)): 2 个测试用例
- **降级和异常测试** ([FallbackAndExceptionTest.java](src/test/java/io/github/surezzzzzz/sdk/cache/test/cases/FallbackAndExceptionTest.java)): 4 个测试用例

### Redis 降级测试

所有测试用例都支持 **Redis 可用/不可用** 两种场景,验证框架的优雅降级能力:

- **Redis 可用**: 完整测试 L1+L2 双层缓存、Pub/Sub 同步等功能
- **Redis 不可用**: 自动降级到 L1-only 模式,所有测试依然通过

**降级特性**:
- 快速失败: Redis 连接超时 3 秒,避免长时间阻塞
- 自动跳过: 需要 Redis 的测试(如 Pub/Sub)自动跳过
- 动态调整: 测试规模根据 Redis 可用性和 L1 容量自适应
- 零影响: Redis 不可用不影响应用启动和运行

## 技术栈

- Spring Boot 2.7.9
- Caffeine 2.9.3
- Spring Data Redis 2.7.9
- Lombok

## 许可证

Apache License 2.0
