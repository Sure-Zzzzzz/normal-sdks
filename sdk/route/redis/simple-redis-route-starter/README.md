# Simple Redis Route Starter

基于 Spring Boot 的 Redis 多数据源路由组件，支持按 Redis key 将操作路由到不同 Redis 数据源。

## 核心特性

- 多数据源：支持 standalone / cluster 两种 Redis 部署模式，并支持同一路由实例混合使用 standalone 与 cluster 数据源。
- Lettuce 生产安全默认值：Cluster 拓扑自适应刷新 / 周期刷新默认开启，断连默认拒绝命令入队，并限制请求队列上限。
- 显式路由入口：通过 `RedisRouteTemplate` 按 key 或 datasource 获取 `StringRedisTemplate` / `RedisConnectionFactory`。
- 多匹配模式：支持 exact / prefix / suffix / wildcard / regex。
- 规则优先级：priority 数字越小越优先，同 priority 按配置声明顺序匹配。
- 多 key 保护：批量 key 必须命中同一 datasource，跨 datasource 会直接抛出路由异常。
- 不污染业务上下文：不注册全局 `RedisConnectionFactory` / `RedisTemplate` / `StringRedisTemplate`。
- 可扩展：`RedisRouteResolver`、`RedisConnectionFactoryFactory`、`RedisRouteTemplate` 支持业务侧覆盖。
- 兼容 Spring Boot 2.2.x / 2.3.12 / 2.4.5 / 2.7.9。

## 依赖配置

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-redis-route-starter:1.0.0'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

## 快速开始

### 1. 启用路由

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        redis:
          route:
            enable: true
            default-source: default
```

### 2. 配置多数据源

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
                host: localhost
                port: 6379
                database: 0
                timeout-ms: 3000
                connect-timeout-ms: 3000
              cache:
                mode: standalone
                host: localhost
                port: 6379
                database: 1
                timeout-ms: 3000
                connect-timeout-ms: 3000
              lock:
                mode: standalone
                host: localhost
                port: 6379
                database: 2
                timeout-ms: 3000
                connect-timeout-ms: 3000
```

### 3. 配置路由规则

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
              # ... 数据源配置 ...
            rules:
              - pattern: "cache:"
                type: prefix
                datasource: cache
                priority: 1
                enable: true
              - pattern: "lock:"
                type: prefix
                datasource: lock
                priority: 2
                enable: true
              - pattern: "session:*"
                type: wildcard
                datasource: cache
                priority: 10
                enable: true
```

## 使用示例

### 按 Redis key 自动路由

```java
@Service
public class DemoCacheService {

    private final RedisRouteTemplate redisRouteTemplate;

    public DemoCacheService(RedisRouteTemplate redisRouteTemplate) {
        this.redisRouteTemplate = redisRouteTemplate;
    }

    public void writeCache() {
        redisRouteTemplate.execute("cache:user:001", redisTemplate -> {
            redisTemplate.opsForValue().set("cache:user:001", "mock-value");
            return null;
        });
    }

    public String readCache() {
        return redisRouteTemplate.execute("cache:user:001",
                redisTemplate -> redisTemplate.opsForValue().get("cache:user:001"));
    }
}
```

### 指定 datasource 执行

```java
redisRouteTemplate.executeOn("lock", redisTemplate -> {
    redisTemplate.opsForValue().set("lock:order:001", "mock-lock");
    return null;
});
```

### 获取模板或连接工厂

```java
StringRedisTemplate cacheTemplate = redisRouteTemplate.stringTemplate("cache");
StringRedisTemplate routedTemplate = redisRouteTemplate.stringTemplateByKey("cache:user:001");
RedisConnectionFactory lockFactory = redisRouteTemplate.connectionFactoryByKey("lock:order:001");
```

### 多 key 操作

```java
redisRouteTemplate.execute(Arrays.asList("cache:user:001", "cache:order:001"), redisTemplate -> {
    redisTemplate.opsForValue().set("cache:user:001", "mock-user");
    redisTemplate.opsForValue().set("cache:order:001", "mock-order");
    return null;
});
```

多 key 操作要求所有 key 命中同一 datasource。比如 `cache:user:001` 和 `lock:order:001` 同时传入时会抛出 `RouteException`，避免一次回调里误操作多个 Redis 数据源。

## Cluster 配置示例

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
                mode: cluster
                nodes:
                  - localhost:6379
                  - localhost:6380
                  - localhost:6381
                max-redirects: 3
                timeout-ms: 3000
                connect-timeout-ms: 3000
                lettuce:
                  auto-reconnect: true
                  reject-commands-when-disconnected: true
                  request-queue-size: 10000
                  cluster-adaptive-refresh: true
                  cluster-periodic-refresh: true
                  cluster-refresh-period-ms: 60000
```

Cluster 模式下 `database` 固定为 0。本模块只负责选择 datasource，不计算 slot，不改写 hash tag，也不改写 Redis key。multi-key 回调只保证命中同一个 datasource，不保证 Redis Cluster 同 slot；如果 callback 内执行 multi-key Redis 命令或 Lua，调用方必须使用 `{...}` hash tag 等方式保证同 slot。

## 混合部署配置示例

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
                mode: cluster
                nodes:
                  - localhost:7000
                  - localhost:7001
                  - localhost:7002
                max-redirects: 5
                timeout-ms: 3000
                connect-timeout-ms: 3000
              secondary:
                mode: standalone
                host: localhost
                port: 6379
                database: 1
                timeout-ms: 3000
                connect-timeout-ms: 3000
            rules:
              - pattern: "secondary:"
                type: prefix
                datasource: secondary
                priority: 1
```

混合部署是本模块的主要端到端场景：同一个 `RedisRouteTemplate` 可以把默认 key 路由到 cluster 数据源，把指定前缀 key 路由到 standalone 数据源。1.0.0 不支持 Sentinel；如后续有真实场景，需要补固定 Sentinel 环境和端到端测试后再引入。

## 配置说明

### 顶层配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `enable` | `false` | 是否启用 Redis route |
| `default-source` | `default` | 默认 datasource key |
| `sources` | 空 | datasource 配置 |
| `rules` | 空 | 路由规则配置 |

### 数据源配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `mode` | `standalone` | Redis 部署模式：`standalone` / `cluster` |
| `host` | `localhost` | standalone 主机 |
| `port` | `6379` | standalone 端口 |
| `nodes` | 空 | cluster 节点，格式为 `host:port` |
| `max-redirects` | `3` | cluster 最大重定向次数 |
| `database` | `0` | Redis database，cluster 模式必须为 0 |
| `username` | 空 | Redis data node 用户名 |
| `password` | 空 | Redis data node 密码 |
| `ssl` | `false` | 是否启用 SSL |
| `timeout-ms` | `3000` | 命令超时时间，毫秒 |
| `connect-timeout-ms` | `3000` | 连接超时时间，毫秒 |
| `client-name` | 空 | Redis client name |
| `lettuce.shutdown-timeout-ms` | `100` | Lettuce 连接工厂关闭超时时间，毫秒 |
| `lettuce.auto-reconnect` | `true` | 是否启用 Lettuce 自动重连 |
| `lettuce.reject-commands-when-disconnected` | `true` | 连接断开时是否拒绝命令入队 |
| `lettuce.request-queue-size` | `10000` | Lettuce 请求队列上限 |
| `lettuce.cluster-adaptive-refresh` | `true` | cluster 模式是否启用自适应拓扑刷新 |
| `lettuce.cluster-periodic-refresh` | `true` | cluster 模式是否启用周期性拓扑刷新 |
| `lettuce.cluster-refresh-period-ms` | `60000` | cluster 周期性拓扑刷新间隔，毫秒 |

空白的 `username` / `password` / `client-name` 会按未配置处理。`password` 不会出现在配置对象 `toString()` 中。

### 路由规则配置

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `pattern` | 空 | 匹配表达式 |
| `type` | `exact` | 匹配类型：`exact` / `prefix` / `suffix` / `wildcard` / `regex` |
| `datasource` | 空 | 命中的 datasource key |
| `priority` | `1000` | 优先级，数字越小越优先 |
| `enable` | `true` | 是否启用规则 |

## 路由语义

- `exact`：key 与 pattern 完全一致。
- `prefix`：key 以 pattern 开头。
- `suffix`：key 以 pattern 结尾。
- `wildcard`：支持 `*` 和 `?` 通配符。
- `regex`：按 Java 正则表达式匹配。
- 没有命中任何启用规则时，使用 `default-source`。

## Bean 边界

模块启用后只注册 Redis route 自身 Bean，例如：

- `SimpleRedisRouteRegistry`
- `RedisRouteResolver`
- `RedisRouteTemplate`
- `RedisRoutePatternMatcher`

模块不会注册或替换业务项目的全局 Redis Bean：

- `RedisConnectionFactory`
- `RedisTemplate`
- `StringRedisTemplate`
- `RedisMessageListenerContainer`

如果业务项目已经使用 Spring Boot 默认 Redis 自动配置，可以继续保留；Redis route 通过 `RedisRouteTemplate` 独立使用。

## 扩展点

业务侧可以通过自定义 Bean 覆盖默认实现：

| 扩展点 | 默认实现 | 说明 |
|--------|----------|------|
| `RedisRouteResolver` | `DefaultRedisRouteResolver` | 自定义 key 到 datasource 的解析逻辑 |
| `RedisConnectionFactoryFactory` | `DefaultRedisConnectionFactoryFactory` | 自定义 RedisConnectionFactory 创建逻辑 |
| `RedisRoutePropertiesValidator` | `RedisRoutePropertiesValidator` | 自定义或增强配置校验 |
| `RedisRouteTemplate` | `RedisRouteTemplate` | 自定义路由门面 |

## 本地测试

模块测试依赖真实 Redis：`localhost:6379`。

测试用同一 Redis 的不同 database 模拟多数据源：

- `default`：database 0
- `cache`：database 1
- `lock`：database 2

默认端到端测试是 `RedisRouteEndToEndTest`，覆盖链路为：配置绑定 → 自动配置 → 组件扫描 / Bean 组装 → 路由解析 → registry 获取数据源 → `RedisRouteTemplate` 写读真实 Redis。

Cluster / Mixed 端到端测试默认跳过；需要先通过 [docker-compose.redis-cluster.yml](docker-compose.redis-cluster.yml) 启动固定本地 Redis Cluster，再用 `-Dredis.route.cluster.test=true` 显式运行。

- `RedisRouteMixedEndToEndTest` 是主要混合部署端到端测试，覆盖 `primary=cluster` + `secondary=standalone` 的真实路由、写读隔离、连接工厂模式和跨 datasource multi-key 拦截。
- `RedisRouteClusterEndToEndTest` 是补充 Cluster 端到端测试，覆盖全 Cluster 数据源和 Cluster slot 语义边界。

多版本本地测试命令、Cluster 维护结论和 1.0.0 不支持 Sentinel 的说明见 [LOCAL_TEST_COMMANDS.md](LOCAL_TEST_COMMANDS.md)。

## 版本兼容

| Spring Boot | Java | 状态 |
|-------------|------|------|
| 2.7.9 | 11 | 已验证 |
| 2.4.5 | 8 | 已验证 |
| 2.3.12 | 8 | 已验证 |
| 2.2.x | 8 | 已验证 |
