# Smart Cache Starter

一个基于 Spring Boot 的智能二级缓存框架,提供 L1(Caffeine) + L2(Redis) 双层缓存,支持缓存穿透/击穿/雪崩防护、L2 异步续期、循环依赖检测、缓存预热等企业级特性。

## 核心特性

### 双层缓存架构
- **L1 缓存 (Caffeine)**: 进程内高速缓存,微秒级响应
- **L2 缓存 (Redis)**: 分布式缓存,支持集群共享
- **智能降级**: L1 未命中自动查询 L2,L2 未命中触发数据加载
- **Redis 降级**: Redis 不可用时自动切换到 L1-only 模式,保证服务可用性
- **L2 异步续期 (Preload)**: L2 条目快过期时异步提前续期,当前请求返回旧值不阻塞,提供容错窗口

### 缓存三大问题防护
- **缓存穿透**: 空值缓存(L1),防止恶意查询击穿数据库
- **缓存击穿**: 分布式锁 + 重试机制,高并发下只有一个线程访问数据库
- **缓存雪崩**: TTL 随机偏移,避免大量缓存同时失效

### 一致性保障
- **强一致性模式(默认)**: Redis Pub/Sub 实时同步缓存失效,多实例间秒级同步,单实例也可用
- **最终一致性模式**: L1 短 TTL + L2 长 TTL,平衡性能与一致性

### 企业级功能
- **循环依赖检测**: ThreadLocal 追踪加载链,防止死锁
- **缓存预热**: 应用启动时自动加载热点数据,支持顺序控制和分布式协调
- **统计监控**: L1/L2 命中率、未命中次数实时统计
- **SpEL 支持**: 动态 key 生成,支持复杂表达式
- **条件缓存**: 基于 SpEL 的条件判断,灵活控制缓存行为
- **Redis Key 格式自定义**: 支持自定义 key 格式模板,兼容不同 key 规范
- **L2 异步续期**: 实现 `CachePreloadHandler` 接口即可接入,支持覆盖 TTL 判断逻辑

---

## 快速开始

### 1. 添加依赖

**本框架通过 `compileOnly` 声明了部分依赖，使用方必须自行引入，否则启动会报错。**

#### Gradle

```gradle
// 框架本身
implementation 'io.github.sure-zzzzzz:smart-cache-starter:1.1.0'

// 必须自行引入（框架 compileOnly，不会传递）
implementation 'com.github.ben-manes.caffeine:caffeine:2.9.3'          // L1 缓存
implementation 'org.springframework.boot:spring-boot-starter-data-redis' // L2 缓存
implementation 'org.springframework.boot:spring-boot-starter-aop'        // 注解式 API（仅使用注解时需要）
```

#### Maven

```xml
<!-- 框架本身 -->
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>smart-cache-starter</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- 必须自行引入（框架 compileOnly，不会传递） -->
<!-- L1 缓存 -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>2.9.3</version>
</dependency>
<!-- L2 缓存 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<!-- 注解式 API（仅使用注解时需要） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

> **说明**：`jackson-datatype-jsr310` 已通过 `api` 传递，无需手动引入。

---

### 2. 配置文件

```yaml
spring:
  redis:
    host: localhost
    port: 6379

io:
  github:
    surezzzzzz:
      sdk:
        cache:
          key-prefix: my-app              # Redis key 前缀（默认 sure-cache）
          me: instance-1                  # 应用实例标识，同一应用多实例保持一致
          l1:
            enabled: true                 # 启用 L1 缓存（默认 true）
            max-size: 10000               # L1 最大条目数（默认 10000）
            expire-seconds: 300           # L1 过期时间，秒（默认 300）
            refresh-seconds: 240          # L1 异步刷新时间，秒（默认 270）
          l2:
            enabled: true                 # 启用 L2 缓存（默认 true）
            expire-seconds: 3600          # L2 过期时间，秒（默认 3600）
            ttl-random-offset-ratio: 0.1  # TTL 随机偏移比例，防雪崩（默认 0.1）
            key-format: "{keyPrefix}:{cacheName}:{me}::{key}"  # key 格式模板（默认值如左）
            preload:
              enabled: false              # 启用 L2 异步续期（默认 false）
              before-expire-seconds: 300  # 提前多少秒触发续期，需 < expire-seconds
          consistency:
            mode: strong                  # strong（强一致，默认）/ eventual（最终一致）
          stats:
            enabled: true                 # 启用统计（默认 true）
```

**Redis Key 格式说明**

`key-format` 支持以下占位符：

| 占位符 | 说明 |
|--------|------|
| `{keyPrefix}` | key 前缀，来自 `key-prefix` 配置 |
| `{cacheName}` | 缓存名称 |
| `{me}` | 实例标识，来自 `me` 配置 |
| `{key}` | 缓存 key，自动添加 hash tag `{xxx}`，确保 Redis Cluster 同一 cacheName 的 key 在同一 slot |

常见格式示例：

```yaml
# 默认格式
key-format: "{keyPrefix}:{cacheName}:{me}::{key}"
# 生成: my-app:userCache:instance-1::{userId}

# me 和 cacheName 位置互换（兼容老格式）
key-format: "{keyPrefix}:{me}:{cacheName}::{key}"
# 生成: my-app:instance-1:userCache::{userId}
```

---

### 3. 使用注解

```java
@Service
public class UserService {

    @SmartCacheable(cacheName = "userCache", key = "#userId")
    public User getUserById(Long userId) {
        return userRepository.findById(userId);
    }

    @SmartCachePut(cacheName = "userCache", key = "#user.id")
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @SmartCacheEvict(cacheName = "userCache", key = "#userId")
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

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
        return cacheManager.get("productCache", String.valueOf(productId),
            () -> productRepository.findById(productId));
    }

    public void updateProduct(Product product) {
        productRepository.save(product);
        cacheManager.put("productCache", String.valueOf(product.getId()), product);
    }

    public void deleteProduct(Long productId) {
        productRepository.deleteById(productId);
        cacheManager.evict("productCache", String.valueOf(productId));
    }
}
```

### 5. L2 异步续期（Preload）

实现 `CachePreloadHandler` 接口并注册为 Spring Bean，当 L2 条目剩余 TTL 进入预刷新窗口时，框架异步调用 `reload()` 提前续期：

```java
@Component
public class ProductCachePreloadHandler implements CachePreloadHandler {

    @Autowired
    private ProductRepository productRepository;

    @Override
    public boolean support(String cacheName) {
        return "productCache".equals(cacheName);
    }

    @Override
    public Object reload(String cacheName, String key) {
        // key 即缓存时使用的 key，如 "123"
        Long productId = Long.parseLong(key);
        return productRepository.findById(productId);
    }

    // 可选：覆盖此方法替代框架的 TTL 查询，避免额外 Redis IO
    // 返回 Optional.empty() 表示交给框架查 TTL 决定（默认行为）
    @Override
    public Optional<Boolean> needPreload(String cacheName, String key, Object cachedValue) {
        return Optional.empty();
    }
}
```

同时开启配置：

```yaml
io.github.surezzzzzz.sdk.cache.l2.preload:
  enabled: true
  before-expire-seconds: 300  # L2 剩余 TTL < 300s 时触发
```

**行为说明**：
- 触发时当前请求返回旧值，不阻塞
- `reload()` 失败时使用指数退避重试，旧值在容错窗口内仍可返回
- 使用分布式锁防止多实例重复触发同一 key
- 无 handler 注册时静默跳过

---



| 依赖 | 声明方式 | 说明 |
|------|----------|------|
| `caffeine` | `compileOnly` | L1 缓存实现，**使用方必须自行引入** |
| `spring-boot-starter-data-redis` | `compileOnly` | L2 缓存实现，**使用方必须自行引入** |
| `spring-boot-starter-aop` | `compileOnly` | 注解式 API 支持，**使用注解时必须自行引入** |
| `jackson-datatype-jsr310` | `api` | Java 8 时间类型序列化支持，自动传递 |
| `simple-redis-lock-starter` | `api` | 分布式锁，防缓存击穿，自动传递 |
| `task-retry-starter` | `api` | 重试机制，自动传递 |

> `compileOnly` 依赖不会传递给使用方，必须手动引入；`api` 依赖会自动传递，无需重复声明。

---

## 测试覆盖

框架提供了完整的测试覆盖（共 **97 个测试用例**，100% 通过），包括：

- **端到端测试**: 14 个
- **注解 API 测试**: 7 个
- **缓存管理器测试**: 8 个
- **强一致性测试**: 6 个
- **最终一致性测试**: 3 个
- **Pub/Sub 验证测试**: 5 个
- **并发测试**: 8 个
- **压力测试**: 5 个
- **L1/L2 集成测试**: 6 个
- **边界条件测试**: 10 个
- **批量操作和统计测试**: 5 个
- **循环依赖测试**: 3 个
- **缓存预热测试**: 2 个
- **降级和异常测试**: 4 个
- **Key 格式自定义测试**: 7 个（v1.0.2+）
- **L2 异步续期测试**: 4 个（v1.1.0+）

所有测试支持 Redis 可用/不可用两种场景，Redis 不可用时自动降级到 L1-only 模式，测试依然通过。

---

## 技术栈

- Spring Boot 2.7.9
- Caffeine 2.9.3
- Spring Data Redis 2.7.9
- Jackson 2.x（含 JSR310 模块）
- Lombok

## 许可证

Apache License 2.0
