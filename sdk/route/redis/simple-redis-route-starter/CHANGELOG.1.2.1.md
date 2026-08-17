# simple-redis-route-starter 1.2.1

## 本次发布

为每个 Redis Route datasource 新增 Lettuce 连接池容量配置。该版本继续只支持 Lettuce；默认严格保持 1.2.0 的非池化连接行为。

## 新增

- datasource 的 `lettuce.pool` 新增 `enabled`、`max-active`、`max-idle`、`min-idle`、`max-wait-ms` 配置。
- 仅当 `lettuce.pool.enabled=true` 时创建 `LettucePoolingClientConfiguration`；未启用时继续使用普通 Lettuce client configuration。
- `max-active`、`max-idle`、`min-idle`、`max-wait-ms` 分别映射为 Commons Pool2 的 `maxTotal`、`maxIdle`、`minIdle`、`maxWaitMillis`。
- standalone 与 Cluster datasource 都可以独立启用连接池；连接池分支保留命令超时、关闭超时、SSL、client name、连接超时、自动重连、断连拒绝、请求队列、Cluster 自适应与周期性拓扑刷新，以及 topology hostname 补全行为。
- 发布物携带 Commons Pool2 运行期依赖；应用启用连接池时无需额外声明该依赖。
- 对旧版 Spring Data Redis 的 pooled Cluster 关闭顺序增加受限兼容处理，在连接池 provider 仍可用时先关闭 Cluster 命令执行器，避免归还已关闭连接池。

## 配置约束

- 连接池默认关闭；`enabled=false` 时不校验其他 pool 字段，也不改变既有连接行为。
- 启用后，`max-active` 必须大于 `0`，`max-idle` 与 `min-idle` 必须大于等于 `0`，且 `min-idle` 不得大于 `max-idle`。
- `max-wait-ms` 必须大于等于 `-1`；`-1` 表示无限等待，`0` 表示不等待。
- 配置错误仅输出 datasource key、字段名和非敏感数值，不回显 Redis 连接信息或认证信息。
- Cluster 的 `max-active` 表示 Spring Data Redis 专用连接借还容量，不是整个 Cluster 所有节点 TCP 连接数的硬上限。

## 升级说明

- 从 1.2.0 升级不需要修改现有配置；只有需要连接池时，才在目标 datasource 下显式设置 `lettuce.pool.enabled=true`。
- 迁移已有 Spring Redis pool 容量设置时，仅映射 `max-active`、`max-idle`、`min-idle`、`max-wait-ms` 四项；Route 不提供其他 Pool2 实现细节配置。
- Route 继续只支持 Lettuce，不提供 Jedis driver 或双 driver 切换能力。
- 连接池不改变 default-source 标准 Spring Redis Bean 接管、`RedisRouteTemplate` 显式/规则路由、Registry 唯一连接工厂生命周期或 Route 查询能力边界。

## 验证

使用完整模块测试命令 `:sdk:route:redis:simple-redis-route-starter:test --rerun-tasks --no-daemon` 完成以下环境验证：

| Spring Boot | Java | 结果 |
|---|---:|---|
| 2.2.13.RELEASE | 8 | 通过；保留 Lettuce 5.2 对 Redis 7 Cluster hostname metadata 的既有兼容边界 |
| 2.3.12 | 8 | 通过 |
| 2.4.5 | 8 | 通过 |
| 2.7.9 | 11 | 通过 |

四个基线均覆盖普通 Route E2E、池化 standalone 默认 datasource 与按规则 datasource、Redis 3.2.12 / 5.0.14 / 7.2.6 standalone 与 Cluster 矩阵、池化 Redis 5 / 7 Cluster、非池化回归、topology hostname mapper 与连接工厂生命周期。Spring Boot 2.7.9 还覆盖短 hostname topology Cluster 真实读写。
