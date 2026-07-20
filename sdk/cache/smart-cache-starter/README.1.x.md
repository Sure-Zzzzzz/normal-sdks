# Smart Cache Starter 1.x

基于 Spring Boot 的二级缓存框架，提供 L1（Caffeine）+ L2（Redis）双层缓存能力，支持缓存穿透/击穿/雪崩防护、强一致性失效通知、最终一致性模式、L2 异步续期、自定义 TTL、循环依赖检测和启动预热。

## 版本状态

当前 1.x 最新版本为 `1.1.2`。

`smart-cache-starter 1.x` 已封版：后续不再规划 `1.1.3` 或 `1.2.0` 的结构性能力，只在确有严重缺陷时做极小 patch。后续结构规范化、redis-route 原生化、上游 SDK 依赖升级和多 Spring Boot 版本矩阵验证统一进入 `2.0.0`。

`smart-cache 1.x` 没有独立 core artifact，核心能力都内聚在 `smart-cache-starter` 内；因此 1.x 封版文档以本文件为准，不再另建 core 版 README。

## 核心特性

- L1 进程内缓存：基于 Caffeine，提供本地高性能读取。
- L2 分布式缓存：基于 Redis，提供多实例共享缓存。
- 缓存穿透防护：loader 返回空值时在 L1 写入空值占位符。
- 缓存击穿防护：使用分布式锁与重试机制控制同一 key 的并发加载。
- 缓存雪崩防护：L2 TTL 支持随机偏移。
- 强一致性模式：通过 Redis Pub/Sub 广播失效消息，清理其他实例 L1。
- 最终一致性模式：通过 L1 短 TTL + L2 长 TTL 平衡性能和一致性。
- 自定义 L2 TTL：编程式 API 和注解式 API 都支持覆盖全局 L2 TTL。
- L2 异步续期：缓存临近过期时异步 reload，当前请求返回旧值。
- 启动预热：支持应用启动后加载指定缓存数据。
- SpEL 注解：支持动态 key、条件缓存、更新和失效。

## 快速开始

### 1. 添加依赖

`smart-cache-starter 1.x` 通过 `compileOnly` 声明 Caffeine、Redis 和 AOP 依赖，使用方需要按实际能力自行引入。

#### Gradle

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:smart-cache-starter:1.1.2'

    implementation 'com.github.ben-manes.caffeine:caffeine:2.9.3'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
}
```

#### Maven

```xml
<dependency>
    <groupId>io.github.sure-zzzzzz</groupId>
    <artifactId>smart-cache-starter</artifactId>
    <version>1.1.2</version>
</dependency>

<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>2.9.3</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

`jackson-datatype-jsr310`、`simple-redis-lock-starter` 和 `task-retry-starter` 由 1.x 自动传递。

### 2. 基础配置

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
          enabled: true
          key-prefix: test-cache
          me: test-instance
          l1:
            enabled: true
            max-size: 10000
            expire-seconds: 300
            refresh-seconds: 240
          l2:
            enabled: true
            expire-seconds: 3600
            ttl-random-offset-ratio: 0.1
            key-format: "{keyPrefix}:{cacheName}:{me}::{key}"
            preload:
              enabled: false
              before-expire-seconds: 300
          consistency:
            mode: strong
          stats:
            enabled: true
```

### 3. 注解式 API

```java
@Service
public class ResourceService {

    @SmartCacheable(cacheName = "resourceCache", key = "#resourceId")
    public Resource getResource(String resourceId) {
        return resourceRepository.findById(resourceId);
    }

    @SmartCachePut(cacheName = "resourceCache", key = "#resource.id")
    public Resource saveResource(Resource resource) {
        return resourceRepository.save(resource);
    }

    @SmartCacheEvict(cacheName = "resourceCache", key = "#resourceId")
    public void deleteResource(String resourceId) {
        resourceRepository.deleteById(resourceId);
    }
}
```

### 4. 编程式 API

```java
@Service
public class ResourceQueryService {

    private final SmartCacheManager cacheManager;

    public ResourceQueryService(SmartCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public Resource getResource(String resourceId) {
        return cacheManager.get("resourceCache", resourceId,
                () -> resourceRepository.findById(resourceId));
    }

    public void refreshResource(Resource resource) {
        cacheManager.put("resourceCache", resource.getId(), resource);
    }

    public void evictResource(String resourceId) {
        cacheManager.evict("resourceCache", resourceId);
    }
}
```

## 关键能力说明

### 自定义 L2 TTL

```java
@SmartCacheable(cacheName = "shortResourceCache", key = "#resourceId", l2TtlSeconds = 60)
public Resource getShortResource(String resourceId) {
    return resourceRepository.findById(resourceId);
}

cacheManager.put("shortResourceCache", resourceId, resource, 60);
```

`l2TtlSeconds = 0` 表示使用全局 `l2.expire-seconds`。显式 TTL 也会叠加随机偏移。

### 强一致性失效通知

```yaml
io.github.surezzzzzz.sdk.cache:
  consistency:
    mode: strong
```

强一致性模式下，`put`、`evict`、`clear` 会通过 Redis Pub/Sub 发布失效消息，其他实例收到后清理本地 L1。

### L2 异步续期

```java
@Component
public class ResourceCachePreloadHandler implements CachePreloadHandler {

    @Override
    public boolean support(String cacheName) {
        return "resourceCache".equals(cacheName);
    }

    @Override
    public Object reload(String cacheName, String key) {
        return resourceRepository.findById(key);
    }

    @Override
    public Optional<Boolean> needPreload(String cacheName, String key, Object cachedValue) {
        return Optional.empty();
    }

    @Override
    public int getReloadTtlSeconds(String cacheName, String key) {
        return 0;
    }
}
```

```yaml
io.github.surezzzzzz.sdk.cache.l2.preload:
  enabled: true
  before-expire-seconds: 300
```

### 启动预热

`1.x` 同时提供 `@SmartCacheWarmUp` 启动预热和 `CachePreloadHandler` L2 异步续期。两者用途不同：

- `@SmartCacheWarmUp`：应用启动后批量写入热点缓存。
- `CachePreloadHandler`：L2 条目临近过期时异步 reload 单个 key。

## 依赖说明

| 依赖 | 1.x 声明方式 | 说明 |
|------|--------------|------|
| `caffeine` | `compileOnly` | L1 缓存实现，使用方需要自行引入 |
| `spring-boot-starter-data-redis` | `compileOnly` | L2 缓存实现，使用方需要自行引入 |
| `spring-boot-starter-aop` | `compileOnly` | 注解式 API 支持，使用注解时需要自行引入 |
| `jackson-datatype-jsr310` | `api` | Java 8 时间类型序列化支持 |
| `simple-redis-lock-starter:1.0.1` | `api` | 1.x 缓存击穿锁依赖 |
| `task-retry-starter:1.0.1` | `api` | 1.x 重试依赖 |

## 1.x 版本记录

| 版本 | 定位 |
|------|------|
| `1.0.1` | 早期修复版本 |
| `1.0.2` | Redis Key 格式自定义版本 |
| `1.0.3` | 早期修复版本 |
| `1.0.4` | 早期修复版本 |
| `1.1.0` | L2 异步续期版本 |
| `1.1.1` | 自定义 L2 TTL 版本 |
| `1.1.2` | task-retry-starter 1.0.1 依赖修复版本，1.x 封版版本 |

## 测试说明

`1.x` 既有测试覆盖注解 API、编程式 API、L1/L2、强一致性、最终一致性、Pub/Sub、并发、边界条件、批量操作、循环依赖、启动预热、L2 异步续期和自定义 TTL。

`1.x` 早期 README 曾声明固定测试用例数量和通过率；封版文档不再保留未经当前环境复验的数量声明。`2.0.0` 将按 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9 重新建立发布验证矩阵。

## 1.x 注意事项

1. `1.x` 使用应用主 `RedisConnectionFactory` 创建 `smartCacheRedisTemplate`，不支持 redis-route 原生多数据源。
2. `1.x` 的 L2、Pub/Sub、预热标记和分布式锁可能依赖同一个应用主 Redis；多 Redis 隔离场景建议等待 `2.0.0`。
3. Redis 不可用时，部分路径会降级到 L1-only 或本地锁，强一致性 Pub/Sub 不保证生效。
4. `1.x` README 只维护封版说明，不再追加新能力规划。

## 许可证

Apache License 2.0
