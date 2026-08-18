# simple-redis-route-starter 1.2.2

## 本次发布

完善 default-source 的物理连接工厂所有权边界，并将 Route 已支持的 Lettuce 运行配置明确为稳定契约。业务专用 RedisTemplate 可继续保持其序列化和注入语义，只要复用 Route 的 default-source 连接工厂。

## default-source 所有权

- Route Registry 是唯一的物理 `RedisConnectionFactory`、Lettuce client、socket、Cluster topology 与销毁生命周期 owner。
- 启用 Route 时，任何宿主自建 `RedisConnectionFactory` 都会以 `REDIS_ROUTE_015` 阻断启动或首次创建；校验覆盖异名 Bean、`@Lazy` Bean、可预判类型的 `FactoryBean` 和产品类型不透明的延迟 `FactoryBean`。
- `RedisTemplate` 与 `StringRedisTemplate` 不再按原始类型一律视为冲突。宿主可保留任意名称、泛型、序列化器和 `@Primary` 语义的业务模板，只要其连接工厂与 Route default-source factory 是同一实例。
- 模板缺少连接工厂或绑定独立 factory 时以 `REDIS_ROUTE_016` 阻断；Route 不替换、包装或重设宿主模板。
- 标准 `redisTemplate`、`stringRedisTemplate` 名称也遵循相同规则：宿主模板绑定 Route factory 时可保留原有语义；不存在时 Route 继续发布默认标准 Bean。

## 新增配置

- `ssl-verify-peer`：默认 `true`；SSL 启用时校验服务端证书，只有显式设为 `false` 才关闭 peer verification。
- `lettuce.cluster-dynamic-refresh-sources`：默认 `true`；控制拓扑刷新是否使用已发现节点作为刷新来源。
- `lettuce.cluster-close-stale-connections`：默认 `true`；控制拓扑变更后是否关闭过期节点连接。
- `lettuce.read-from`：默认 `master`；Cluster 可选 `master`、`master-preferred`、`replica`、`replica-preferred`、`nearest`、`any`。standalone 仅允许默认值。
- `lettuce.pool.time-between-eviction-runs-ms`：默认 `-1`；映射 Pool2 空闲连接驱逐任务间隔，`-1` 表示不启动周期任务。

## 配置边界

- Route 继续只支持 Lettuce，不支持 `spring.redis.url`、`client-type`、Jedis/Jedis pool 或 Redis Sentinel。
- TLS keystore/truststore/custom SSLContext/StartTLS、ClientResources 线程与 DNS 透传、socket TCP 细节、reconnect delay、timeoutOptions、细粒度拓扑触发条件，以及 Pool2 JMX/fairness/lifo/abandoned/validator 参数不进入 Route YAML 契约；需要时通过 `RedisConnectionFactoryFactory` 扩展点受控实现。
- 不接受已废弃的 `slave` read-from 术语，使用 `replica` 或 `replica-preferred`。
- Route 不提供 key 扫描、枚举、cursor、query、search 或全量 RedisTemplate 路由代理能力。

## 验证

使用完整模块测试命令 `:sdk:route:redis:simple-redis-route-starter:test --rerun-tasks --no-daemon` 完成以下环境验证：

| Spring Boot | Java | 结果 |
|---|---:|---|
| 2.2.13.RELEASE | 8 | 通过；保留 Lettuce 5.2 对 Redis 7 Cluster hostname metadata 的既有兼容边界 |
| 2.3.12 | 8 | 通过 |
| 2.4.5 | 8 | 通过 |
| 2.7.9 | 11 | 通过 |

四个基线均覆盖普通 Route E2E、Redis 3.2.12 / 5.0.14 / 7.2.6 standalone 与 Cluster 矩阵、连接池、拓扑 hostname mapper、default-source 标准 Bean 与连接工厂生命周期。新增用例还覆盖 OIDC 形态的业务 RedisTemplate 共存、标准名模板共存、独立 factory、延迟 factory、FactoryBean 产品、错误模板 factory、Lettuce 新增配置的默认值、映射与非法组合。Spring Boot 2.7.9 还覆盖短 hostname topology Cluster 真实读写。
