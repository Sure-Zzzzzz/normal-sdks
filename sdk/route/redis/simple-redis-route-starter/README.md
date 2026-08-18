# Simple Redis Route Starter

面向业务应用的 Redis 多数据源路由组件。启用后，`default-source` 同时是 Route 的默认 datasource 和应用标准 Spring Redis 的默认入口；命名 datasource 仍通过 `RedisRouteTemplate` 按 key 或显式指定访问。

适用于缓存、会话、分布式锁等数据分别部署，或同一应用同时使用 Redis Cluster 与 standalone Redis 的场景。

## 模块特性

- `enable=true` 时，`default-source` 发布为标准 Spring Redis 默认 Bean：
  - `redisConnectionFactory`：`@Primary`；
  - `stringRedisTemplate`：非 `@Primary`；
  - `redisTemplate`：`@Primary`。
- 标准 `redisConnectionFactory` 与 `stringRedisTemplate` 分别复用 Route 注册表持有的 default-source 实例；标准 `redisTemplate` 绑定同一个连接工厂，不会创建第二套 Redis client。
- Route 注册表是所有连接工厂的唯一生命周期 owner；标准 `redisConnectionFactory` 不会被 Spring 容器重复销毁。
- 按 key 将操作路由至不同 datasource，支持默认路由和显式 datasource 调用。
- 支持 standalone、Redis Cluster 及两种模式在同一应用内混用。
- 支持 exact、prefix、suffix、wildcard、regex 五种路由规则；优先级数字越小越优先，同优先级按配置声明顺序匹配。
- 多 key 操作强制校验所有 key 命中同一 datasource，避免在一次回调中误跨 Redis 实例操作。
- 提供 Redis Server 版本与部署模式快照，以及保守的命令能力判断和 `UNLINK` 优先删除 helper。
- Cluster 默认启用自适应和周期性拓扑刷新；断连时拒绝命令入队，并限制请求队列上限。
- 每个 datasource 可独立启用 Lettuce 连接池，支持 standalone 与 Redis Cluster；默认保持非池化。
- 可选兼容 Redis Cluster 返回两段式 hostname、初始 `nodes` 配置完整 hostname 的容器化部署。
- 不为命名 datasource 注册额外的全局 `RedisConnectionFactory`、`RedisTemplate` 或 `StringRedisTemplate`。

## 最小配置

单数据源也建议通过 Route 接入。`enable=true` 后，未带 Route scope 的标准 Spring Redis 调用固定使用 `default-source`；后续新增数据源和路由规则时，默认调用代码不需要改动。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-redis-route-starter:1.2.2'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: default
            sources:
              default:
                mode: standalone
                host: ${REDIS_HOST}
                port: ${REDIS_PORT:6379}
```

默认 datasource 可直接使用标准 Spring Redis API：

```java
@Service
public class CacheService {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String load(String cacheKey) {
        return stringRedisTemplate.opsForValue().get(cacheKey);
    }
}
```

`StringRedisTemplate`、`RedisTemplate` 和 `RedisConnectionFactory` 都绑定 default-source。标准模板不按 key 自动切换 datasource；需要按规则路由或访问命名 datasource 时，使用 `RedisRouteTemplate`：

```java
redisRouteTemplate.execute("cache:item:42", redisTemplate -> {
    redisTemplate.opsForValue().set("cache:item:42", "cached-value");
    return null;
});
```

未匹配任何规则的 key 使用 `default-source`。

## 完整配置

以下示例覆盖顶层、standalone datasource、Cluster datasource、Lettuce、Redis Server 探测和全部路由规则配置。密码等敏感值应从部署环境注入，不要写入仓库。

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: default
            probe:
              server-info: true
            sources:
              default:
                mode: cluster
                nodes:
                  - ${REDIS_DEFAULT_NODE_1}
                  - ${REDIS_DEFAULT_NODE_2}
                  - ${REDIS_DEFAULT_NODE_3}
                max-redirects: 3
                database: 0
                username: ${REDIS_DEFAULT_USERNAME:}
                password: ${REDIS_DEFAULT_PASSWORD:}
                ssl: false
                ssl-verify-peer: true
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
                  cluster-dynamic-refresh-sources: true
                  cluster-close-stale-connections: true
                  read-from: master
                  pool:
                    enabled: true
                    max-active: 16
                    max-idle: 10
                    min-idle: 2
                    max-wait-ms: 1000
                    time-between-eviction-runs-ms: 30000
              cache:
                mode: standalone
                host: ${REDIS_CACHE_HOST}
                port: ${REDIS_CACHE_PORT:6379}
                database: 1
                username: ${REDIS_CACHE_USERNAME:}
                password: ${REDIS_CACHE_PASSWORD:}
                ssl: false
                ssl-verify-peer: true
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
                  cluster-dynamic-refresh-sources: true
                  cluster-close-stale-connections: true
                  read-from: master
                  pool:
                    enabled: true
                    max-active: 8
                    max-idle: 8
                    min-idle: 0
                    max-wait-ms: -1
                    time-between-eviction-runs-ms: -1
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

`mode=cluster` 时 `database` 必须为 `0`，并且必须配置至少一个 `nodes`；每个节点环境变量值都应为 `host:port`。`mode=standalone` 使用 `host`、`port` 与 `database`。

default-source 可以通过标准 Spring Redis Bean 或 `RedisRouteTemplate` 使用。`cache` 等命名 datasource 不会成为全局标准 Bean，只能通过 `RedisRouteTemplate` 的按 key 路由、`executeOn`、`stringTemplate` 或 `connectionFactory` 访问。

## 最佳实践

### 先迁移默认 Redis，再拆分数据源

先将原 `spring.redis` 的有效连接契约迁移为 `sources.<default-source>`，让现有 `StringRedisTemplate`、`RedisTemplate` 与 `RedisConnectionFactory` 注入无代码切换到 default-source。之后再为需要隔离的数据增加命名 datasource 和路由规则。

Route 启用后，物理 `RedisConnectionFactory` 只能由 Route Registry 创建和销毁；任意宿主自建 factory（包括异名、`@Lazy` 与 `FactoryBean` 产品）都会以 `REDIS_ROUTE_015` fail-fast，不能通过重命名、`@Primary` 或 Bean 覆盖顺序绕开。

宿主可以保留任意名称、泛型、序列化器和 `@Primary` 语义的 `RedisTemplate` 或 `StringRedisTemplate`，前提是它们注入并实际绑定 Route 发布的 default-source `RedisConnectionFactory`。这适用于授权、同意记录等业务专用模板；Route 不替换、包装或重设这些模板。模板未绑定 factory 或绑定独立 factory 时会以 `REDIS_ROUTE_016` fail-fast。

1.1.x 的 `enable=true` 是仅显式使用 `RedisRouteTemplate` 的边界，不接管应用标准 Redis Bean。不能接受 1.2.0 及以后 default-source 接管语义的应用应继续使用 1.1.x；Route 不提供同配置下的隐藏回退。

### 按 datasource 配置 Lettuce 连接池

Route 只使用 Lettuce。连接池默认关闭；未设置 `lettuce.pool.enabled=true` 时，连接工厂保持 1.2.0 的非池化行为。启用连接池时，应用需自行提供 Commons Pool2 运行时依赖；Spring Boot 应用可交由自身 BOM 管理版本：

```gradle
dependencies {
    implementation 'org.apache.commons:commons-pool2'
}
```

将既有 Spring Redis 的 Lettuce pool 容量值迁移到目标 datasource 的 `lettuce.pool`。`max-active` 对应最大借出连接数、`max-idle` 对应最大空闲连接数、`min-idle` 对应最小空闲连接数、`max-wait-ms` 对应借连接最大等待时间、`time-between-eviction-runs-ms` 对应空闲连接驱逐任务间隔。`max-wait-ms=-1` 表示无限等待，`max-wait-ms=0` 表示不等待；`time-between-eviction-runs-ms=-1` 表示不启动周期驱逐任务。

连接池可以用于 standalone 和 Cluster datasource。Cluster 中 `max-active` 约束 Spring Data Redis 的专用连接借还容量，不是所有 Cluster 节点 TCP 连接数的硬上限；拓扑刷新、共享连接、阻塞命令、事务、Lua 与多节点连接仍遵循 Spring Data Redis 和 Lettuce 的运行机制。

仅需为单个 standalone datasource 启用连接池时，在最小配置的 datasource 下补充：

```yaml
lettuce:
  pool:
    enabled: true
    max-active: 8
    max-idle: 8
    min-idle: 0
    max-wait-ms: -1
```

### 按数据职责划分 datasource

将高频缓存、核心状态和锁等不同数据职责配置为不同 datasource，并为每类数据使用稳定前缀。例如 `cache:`、`state:`、`lock:`。路由规则只描述数据归属，不承担业务权限或数据查询职责。

默认 datasource 的普通读写使用标准 Spring Redis API。需要按 key 规则选择 datasource、访问命名 datasource 或明确指定目标时，使用 `RedisRouteTemplate`。

### 为 Cluster 保留安全连接默认值

除非有明确的容量与故障恢复依据，不要关闭 `cluster-adaptive-refresh`、`cluster-periodic-refresh`、`cluster-dynamic-refresh-sources`、`cluster-close-stale-connections` 或 `reject-commands-when-disconnected`。`read-from` 默认 `master`；改为 `master-preferred`、`replica`、`replica-preferred`、`nearest` 或 `any` 前，应确认业务可接受副本复制延迟与节点选择差异。请求队列上限应结合业务并发和故障可承受时间设置，避免 Redis 不可用时在客户端积压大量命令。

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
redisRouteTemplate.executeOn("default", redisTemplate -> {
    redisTemplate.opsForValue().set("system:maintenance", "enabled");
    return null;
});
```

### 获取命名 datasource 的模板或连接工厂

```java
StringRedisTemplate cacheTemplate = redisRouteTemplate.stringTemplate("cache");
StringRedisTemplate routedTemplate = redisRouteTemplate.stringTemplateByKey("cache:user:42");
RedisConnectionFactory defaultFactory = redisRouteTemplate.connectionFactory("default");
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

## 配置参考

### 顶层配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `enable` | `false` | 是否启用 Redis Route；启用后 default-source 接管标准 Spring Redis 默认入口 |
| `default-source` | `default` | 默认 datasource，也是标准 Spring Redis Bean 的绑定目标 |
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
| `ssl-verify-peer` | `true` | SSL 启用时是否校验服务端证书；仅在受控环境临时排障时才可设为 `false` |
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
| `lettuce.cluster-refresh-period-ms` | `60000` | Cluster 周期性刷新间隔，单位毫秒；仅 Cluster 可配置非默认值 |
| `lettuce.cluster-dynamic-refresh-sources` | `true` | Cluster 拓扑刷新是否使用已发现节点作为刷新来源；仅 Cluster 可配置非默认值 |
| `lettuce.cluster-close-stale-connections` | `true` | Cluster 拓扑变更后是否关闭过期节点连接；仅 Cluster 可配置非默认值 |
| `lettuce.read-from` | `master` | Cluster 读偏好，可选 `master`、`master-preferred`、`replica`、`replica-preferred`、`nearest`、`any`；standalone 仅允许默认值 `master` |

### Lettuce 连接池配置

连接池默认关闭，只有 `lettuce.pool.enabled=true` 时才创建池化 Lettuce client。下列约束仅在启用连接池时校验。

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `lettuce.pool.enabled` | `false` | 是否为当前 datasource 启用 Lettuce 连接池 |
| `lettuce.pool.max-active` | `8` | 最大借出连接数，对应 Pool2 `maxTotal`，必须大于 `0` |
| `lettuce.pool.max-idle` | `8` | 最大空闲连接数，对应 Pool2 `maxIdle`，不能小于 `0` |
| `lettuce.pool.min-idle` | `0` | 最小空闲连接数，对应 Pool2 `minIdle`，不能小于 `0` 且不能大于 `max-idle` |
| `lettuce.pool.max-wait-ms` | `-1` | 获取连接最大等待时间，对应 Pool2 `maxWaitMillis`，必须大于等于 `-1`；`-1` 表示无限等待 |
| `lettuce.pool.time-between-eviction-runs-ms` | `-1` | 空闲连接驱逐任务间隔，对应 Pool2 `timeBetweenEvictionRunsMillis`，必须大于等于 `-1`；`-1` 表示不启动周期任务 |

### 路由规则配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `pattern` | 无 | 匹配表达式 |
| `type` | `exact` | `exact`、`prefix`、`suffix`、`wildcard`、`regex` |
| `datasource` | 无 | 目标 datasource |
| `priority` | `1000` | 数字越小越优先 |
| `enable` | `true` | 是否启用规则 |

## 扩展点

业务侧可通过自定义 Bean 覆盖下列 Route 扩展点：

| 扩展点 | 默认实现 | 用途 |
|---|---|---|
| `RedisRouteResolver` | `DefaultRedisRouteResolver` | 自定义 key 到 datasource 的解析逻辑 |
| `RedisConnectionFactoryFactory` | `DefaultRedisConnectionFactoryFactory` | 自定义连接工厂创建逻辑 |
| `RedisRoutePropertiesValidator` | `RedisRoutePropertiesValidator` | 增强配置校验 |
| `RedisRouteTemplate` | `RedisRouteTemplate` | 自定义路由门面 |

不要通过自定义 `RedisConnectionFactory` 改变 default-source 接管语义；这会触发所有权冲突校验。自定义模板必须绑定 Route 的 default-source factory。

## 使用边界

- Route 负责 datasource 路由、连接、Cluster topology 与客户端生命周期；不提供 Redis key 扫描、枚举、cursor、query 或 search 能力。
- `enable=true` 时 Route 独占物理 `RedisConnectionFactory` 并接管 default-source 标准 Spring Redis 入口；命名 datasource 仍只通过 `RedisRouteTemplate` 使用。
- `spring.redis.url`、`spring.redis.client-type`、Jedis/Jedis pool、Redis Sentinel（包括 Sentinel 认证）、TLS keystore/truststore/custom SSLContext/StartTLS、ClientResources 线程或 DNS 透传、socket TCP 细节、reconnect delay、timeoutOptions、细粒度拓扑触发条件，以及 Pool2 JMX/fairness/lifo/abandoned/validator 等实现参数不属于 Route YAML 契约。需要此类能力时，通过 `RedisConnectionFactoryFactory` 扩展点完成受控实现与验证。
- `read-from` 不接受已废弃的 `slave` 术语；使用 `replica` 或 `replica-preferred`。
- `probe.server-info=false` 可用于 Redis 禁用 `INFO` 命令的环境；探测失败不阻断启动，但 Server 信息会标记为未知，能力判断会保守返回不支持。
