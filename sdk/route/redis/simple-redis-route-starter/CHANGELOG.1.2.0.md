# simple-redis-route-starter 1.2.0

## 默认 Spring Redis Bean 接管

`enable=true` 后，Route 的 `default-source` 成为应用唯一的标准 Spring Redis 默认 datasource，并发布以下标准 Bean：

| Bean 名称 | 类型 | Primary | 绑定关系 |
|---|---|---:|---|
| `redisConnectionFactory` | `RedisConnectionFactory` | 是 | 复用 Route 注册表的 default-source 连接工厂实例 |
| `stringRedisTemplate` | `StringRedisTemplate` | 否 | 复用 Route 注册表的 default-source 模板实例 |
| `redisTemplate` | `RedisTemplate<Object, Object>` | 是 | 绑定 Route default-source 的同一连接工厂 |

`StringRedisTemplate` 继承 `RedisTemplate`，因此它不标记为 `@Primary`，避免按原始 `RedisTemplate` 类型注入时出现多个 Primary 候选。

标准 `redisConnectionFactory` 通过 `FactoryBean` 发布，Route 注册表仍是所有物理连接工厂的唯一生命周期 owner。容器关闭时不会重复销毁 default-source 工厂，也不会创建第二套默认 Redis client。

## 命名 datasource 访问边界

- 标准 Spring Redis Bean 固定访问 default-source，不会按 key 自动切换 datasource。
- 命名 datasource 不发布额外全局 `RedisConnectionFactory`、`StringRedisTemplate` 或 `RedisTemplate` Bean。
- 访问命名 datasource 或按 key 路由时，使用 `RedisRouteTemplate` 的 `execute`、`executeOn`、`stringTemplate`、`connectionFactory` 等 API。
- Route 继续只负责 datasource 路由、连接、Cluster topology 与客户端生命周期；不提供 Redis key 扫描、枚举、cursor、query 或 search 能力。

## 宿主标准 Bean 冲突保护

Route 启用时，宿主若声明任意 `RedisConnectionFactory`、`StringRedisTemplate` 或 `RedisTemplate` Bean，应用会在启动期以 `REDIS_ROUTE_015` fail-fast。该保护不依赖 Bean 名称，不能通过重命名、`@Primary` 或 Bean 覆盖顺序绕开。

冲突错误只说明 default-source 接管与宿主标准 Redis Bean 不兼容，不输出 Redis 连接地址、端口、用户名、密码或客户端原始异常信息。

## 1.1.x 升级说明

1.1.x 中，Route 仅通过 `RedisRouteTemplate` 显式访问，不发布或接管应用标准 Spring Redis Bean。1.2.0 将 `enable=true` 明确定义为 default-source 接管标准 Spring Redis 默认入口的行为变更，不提供同配置下的隐藏回退。

升级步骤：

1. 将原默认 Redis 的有效连接契约迁移到 `sources.<default-source>`。
2. 移除应用自行声明的 `RedisConnectionFactory`、`StringRedisTemplate`、`RedisTemplate` Bean。
3. 启用 Route 后，验证普通 Spring Redis 注入访问 default-source。
4. 保持命名 datasource 的访问通过 `RedisRouteTemplate` 显式或按 key 路由完成。

依赖 1.1.x “仅显式 Route、不接管标准 Bean”语义的应用，应继续使用 1.1.x；必须保留宿主标准 Redis Bean 的应用不能同时启用 1.2.0 Route 接管。

## 验证

- 覆盖 Route 启用/禁用时标准 Bean 的发布边界、名称、类型、Primary 关系与实例一致性。
- 验证 Spring Boot Redis 自动配置 back-off，不创建第二个默认连接工厂或默认模板。
- 覆盖宿主自定义标准 Redis Bean、标准 Bean 名称冲突、同名 factory method 等冲突场景的安全 fail-fast。
- 覆盖 default-source 连接工厂仅由 Route 注册表销毁一次。
- 真实 Redis E2E 覆盖普通 `StringRedisTemplate` 默认 source 读写、命名 source 隔离和 Route 显式访问。
- 已完成 Spring Boot 2.2.13.RELEASE、2.3.12、2.4.5、2.7.9 的完整模块测试；保留 Spring Boot 2.2.13.RELEASE 管理的 Lettuce 5.2 对 Redis 7 Cluster hostname 元数据的既有兼容边界断言。
