# Simple Redis Route Starter

面向业务应用的 Redis 多数据源路由组件。应用按 Redis key 或显式 datasource 选择目标 Redis，而不替换业务工程已有的 Spring Boot Redis Bean。

适用于缓存、会话、分布式锁等数据分别部署，或同一应用同时使用 Redis Cluster 与 standalone Redis 的场景。

## 模块特性

- 按 key 将操作路由至不同 Redis datasource，支持默认路由和显式 datasource 调用。
- 支持 standalone、Redis Cluster 及两种模式在同一应用内混用。
- 支持 exact、prefix、suffix、wildcard、regex 五种路由规则；优先级数字越小越优先，同优先级按配置声明顺序匹配。
- 多 key 操作强制校验所有 key 命中同一 datasource，避免在一次回调中误跨 Redis 实例操作。
- 提供 Redis Server 版本与部署模式快照，以及保守的命令能力判断和 `UNLINK` 优先删除 helper。
- Cluster 默认启用自适应和周期性拓扑刷新；断连时拒绝命令入队，并限制请求队列上限。
- 可选兼容 Redis Cluster 返回两段式 hostname、初始 `nodes` 配置完整 hostname 的容器化部署。
- 不注册、不覆盖业务工程的全局 `RedisConnectionFactory`、`RedisTemplate`、`StringRedisTemplate` 或 `RedisMessageListenerContainer`。

## 引入依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-redis-route-starter:1.1.1'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

## 最小配置

单数据源也建议通过 Route 接入。后续新增数据源和路由规则时，业务调用代码不需要改动。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: primary
            sources:
              primary:
                mode: standalone
                host: ${REDIS_PRIMARY_HOST}
                port: 6379
```

```java
@Service
public class CacheService {

    private final RedisRouteTemplate redisRouteTemplate;

    public CacheService(RedisRouteTemplate redisRouteTemplate) {
        this.redisRouteTemplate = redisRouteTemplate;
    }

    public String load(String cacheKey) {
        return redisRouteTemplate.execute(cacheKey,
                redisTemplate -> redisTemplate.opsForValue().get(cacheKey));
    }
}
```

未匹配任何规则的 key 使用 `default-source`。

## 最全配置

以下示例覆盖顶层、standalone datasource、Cluster datasource、Lettuce、Redis Server 探测和全部路由规则配置。密码等敏感值应从部署环境注入，不要写入仓库。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: primary
            probe:
              server-info: true
            sources:
              primary:
                mode: cluster
                nodes:
                  - ${REDIS_PRIMARY_NODE_1}
                  - ${REDIS_PRIMARY_NODE_2}
                  - ${REDIS_PRIMARY_NODE_3}
                max-redirects: 3
                database: 0
                username: ${REDIS_PRIMARY_USERNAME:}
                password: ${REDIS_PRIMARY_PASSWORD:}
                ssl: false
                timeout-ms: 3000
                connect-timeout-ms: 3000
                client-name: ${spring.application.name}
                cluster-topology-address-follow-nodes: false
                lettuce:
                  shutdown-timeout-ms: 100
                  auto-reconnect: true
                  reject-commands-when-disconnected: true
                  request-queue-size: 10000
                  cluster-adaptive-refresh: true
                  cluster-periodic-refresh: true
                  cluster-refresh-period-ms: 60000
              cache:
                mode: standalone
                host: ${REDIS_CACHE_HOST}
                port: 6379
                database: 1
                username: ${REDIS_CACHE_USERNAME:}
                password: ${REDIS_CACHE_PASSWORD:}
                ssl: false
                timeout-ms: 3000
                connect-timeout-ms: 3000
                client-name: ${spring.application.name}
                lettuce:
                  shutdown-timeout-ms: 100
                  auto-reconnect: true
                  reject-commands-when-disconnected: true
                  request-queue-size: 10000
                  cluster-adaptive-refresh: true
                  cluster-periodic-refresh: true
                  cluster-refresh-period-ms: 60000
            rules:
              - pattern: "cache:user:"
                type: prefix
                datasource: cache
                priority: 100
                enable: true
              - pattern: ":snapshot"
                type: suffix
                datasource: cache
                priority: 200
                enable: true
              - pattern: "cache:temporary:*"
                type: wildcard
                datasource: cache
                priority: 300
                enable: true
              - pattern: "cache:(invoice|statement):[0-9]+"
                type: regex
                datasource: cache
                priority: 400
                enable: true
              - pattern: "cache:system"
                type: exact
                datasource: cache
                priority: 500
                enable: true
```

`mode=cluster` 时 `database` 必须为 `0`，并且必须配置至少一个 `nodes`；每个环境变量值都应为 `host:port`。`mode=standalone` 使用 `host`、`port` 与 `database`。

## 业务调用方式

### 按 key 自动路由

优先使用按 key 调用，让数据归属由统一路由规则决定。

```java
redisRouteTemplate.execute("cache:user:42", redisTemplate -> {
    redisTemplate.opsForValue().set("cache:user:42", "cached-value");
    return null;
});
```

### 在指定 datasource 上执行

仅在业务明确知道目标 datasource 且不适合以 key 描述时使用。

```java
redisRouteTemplate.executeOn("primary", redisTemplate -> {
    redisTemplate.opsForValue().set("system:maintenance", "enabled");
    return null;
});
```

### 获取模板或连接工厂

```java
StringRedisTemplate cacheTemplate = redisRouteTemplate.stringTemplate("cache");
StringRedisTemplate routedTemplate = redisRouteTemplate.stringTemplateByKey("cache:user:42");
RedisConnectionFactory primaryFactory = redisRouteTemplate.connectionFactory("primary");
```

### 多 key 操作

同一次回调中的所有 key 必须路由到同一个 datasource。

```java
redisRouteTemplate.execute(Arrays.asList("cache:user:42", "cache:user:43"), redisTemplate -> {
    redisTemplate.delete(Arrays.asList("cache:user:42", "cache:user:43"));
    return null;
});
```

在 Redis Cluster 中，同 datasource 不代表同 slot。业务如果在回调里执行 multi-key Redis 命令或 Lua，仍应使用 `{...}` hash tag 确保同 slot。

## 最佳实践

### 按数据职责划分 datasource

将高频缓存、核心状态和锁等不同数据职责配置为不同 datasource，并为每类数据使用稳定前缀。例如 `cache:`、`state:`、`lock:`。路由规则只描述数据归属，不承担业务权限或数据查询职责。

### 使用默认 datasource 承接未迁移 key

先把现有 Redis 配置作为 `default-source`，再逐步为需要拆分的数据增加 prefix 规则。这样可以避免一次性改造全部业务 key，也能让未配置规则的存量 key 保持原有归属。

### 为 Cluster 保留安全连接默认值

除非有明确的容量与故障恢复依据，不要关闭 `cluster-adaptive-refresh`、`cluster-periodic-refresh` 或 `reject-commands-when-disconnected`。请求队列上限应结合业务并发和故障可承受时间设置，避免 Redis 不可用时在客户端积压大量命令。

### 容器化 Cluster 的短 hostname 拓扑

当 Redis Cluster 返回 `pod.service` 两段 hostname，而应用初始 `nodes` 配置为 `pod.service.namespace` 或 `pod.service.namespace.svc.cluster.local` 时，可开启：

```yaml
cluster-topology-address-follow-nodes: true
```

开启后，`nodes` 是唯一地址事实来源。Route 仅保留 Redis 返回的动态节点名与端口，并按相同 service 的唯一 hostname 尾部补全地址；不会修改初始 seed、回退到 seed、反向解析 DNS、由 IP 推导 hostname 或猜测其他域名。

同一 service 在 `nodes` 中出现不同尾部，或没有可用映射时，应用会启动失败。仅在 Redis 服务端已返回 hostname、初始 `nodes` 已配置为可解析完整地址且这些地址属于同一集群地址体系时开启。

### 以能力快照保护版本差异

需要使用特定 Redis 命令前，先读取 Server 信息并判断能力；未知版本一律按不支持处理。

```java
RedisServerInfo serverInfo = redisRouteTemplate.serverInfo("cache");
if (RedisCommandCapabilityHelper.supportsGetEx(serverInfo)) {
    // 使用 Redis 6.2+ GETEX 语义
}
```

`RedisCommandCompatibilityHelper.deletePreferUnlink` 在已知支持时优先使用 `UNLINK`，否则使用 `DEL`；模块不提供改变业务语义的透明降级。

## 配置参考

### 顶层配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `enable` | `false` | 是否启用 Redis Route |
| `default-source` | `default` | 未命中规则时使用的 datasource |
| `sources` | 空 | datasource 配置集合 |
| `rules` | 空 | 路由规则集合 |
| `probe.server-info` | `true` | 启动时是否探测 Redis Server 信息 |

### datasource 配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `mode` | `standalone` | `standalone` 或 `cluster` |
| `host` / `port` | `localhost` / `6379` | standalone 地址 |
| `nodes` | 空 | Cluster 初始节点，格式为 `host:port` |
| `max-redirects` | `3` | Cluster 最大重定向次数 |
| `database` | `0` | Cluster 固定为 `0` |
| `username` / `password` | 空 | Redis 认证信息 |
| `ssl` | `false` | 是否使用 SSL |
| `timeout-ms` | `3000` | 命令超时时间，单位毫秒 |
| `connect-timeout-ms` | `3000` | 连接超时时间，单位毫秒 |
| `client-name` | 空 | Redis 客户端名称 |
| `cluster-topology-address-follow-nodes` | `false` | 是否补全 Cluster 两段式 topology hostname |

### Lettuce 配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `lettuce.shutdown-timeout-ms` | `100` | 连接工厂关闭超时，单位毫秒 |
| `lettuce.auto-reconnect` | `true` | 是否自动重连 |
| `lettuce.reject-commands-when-disconnected` | `true` | 断连时是否拒绝命令入队 |
| `lettuce.request-queue-size` | `10000` | 客户端请求队列上限 |
| `lettuce.cluster-adaptive-refresh` | `true` | 是否启用 Cluster 自适应拓扑刷新 |
| `lettuce.cluster-periodic-refresh` | `true` | 是否启用 Cluster 周期性拓扑刷新 |
| `lettuce.cluster-refresh-period-ms` | `60000` | Cluster 周期性刷新间隔，单位毫秒 |

### 路由规则配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `pattern` | 无 | 匹配表达式 |
| `type` | `exact` | `exact`、`prefix`、`suffix`、`wildcard`、`regex` |
| `datasource` | 无 | 目标 datasource |
| `priority` | `1000` | 数字越小越优先 |
| `enable` | `true` | 是否启用规则 |

## 扩展点

业务侧可通过自定义 Bean 覆盖下列默认实现：

| 扩展点 | 默认实现 | 用途 |
|---|---|---|
| `RedisRouteResolver` | `DefaultRedisRouteResolver` | 自定义 key 到 datasource 的解析逻辑 |
| `RedisConnectionFactoryFactory` | `DefaultRedisConnectionFactoryFactory` | 自定义连接工厂创建逻辑 |
| `RedisRoutePropertiesValidator` | `RedisRoutePropertiesValidator` | 增强配置校验 |
| `RedisRouteTemplate` | `RedisRouteTemplate` | 自定义路由门面 |

## 使用边界

- Route 负责 datasource 路由、连接、Cluster topology 与客户端生命周期；不提供 Redis key 扫描、枚举或只读查询能力。
- 业务工程原有的 Spring Boot Redis Bean 保持不变；通过 `RedisRouteTemplate` 显式使用 Route 数据源。
- `probe.server-info=false` 可用于 Redis 禁用 `INFO` 命令的环境；探测失败不阻断启动，但 Server 信息会标记为未知，能力判断会保守返回不支持。
