# simple-redis-route-starter 1.1.1

## 本次发布

Redis Cluster 在容器化环境中可能向客户端返回两段式 hostname，例如 `pod.service`；而应用用于初始连接的 `nodes` 往往是可解析的完整 hostname。此前动态拓扑节点地址无法自然复用这套地址体系，导致初始 seed 可连接、后续节点却无法建立连接。

1.1.1 提供受限的 topology hostname 补全能力：应用明确开启后，Route 仅以同一 datasource 的 `nodes` 为地址事实来源，为 Redis 返回的受支持短 hostname 补全唯一尾部。该版本不扩展 Route 的业务查询职责，也不通过猜测地址或修改服务端绕过部署问题。

## 新增

- Cluster datasource 新增 `cluster-topology-address-follow-nodes`，默认 `false`。
- 支持从 `pod.service.namespace` 或 `pod.service.namespace.svc.cluster.local` 形式的 `nodes` 建立服务名到 hostname 尾部的唯一映射。
- Redis 返回 `pod.service` 两段 hostname 时，保留 Redis 返回的动态节点名和端口，仅补全映射得到的 hostname 尾部。
- 开启该能力的 Cluster datasource 使用 Route 管理的专属 Lettuce `ClientResources`；连接工厂关闭时资源仅关闭一次，不向业务侧泄露客户端生命周期。

## 使用前提与边界

- 仅支持 `cluster` datasource；初始 `nodes` 必须是应用可解析的完整地址，并且与 Redis 返回的 topology hostname 属于同一地址体系。
- Redis 服务端必须返回 hostname；业务部署应使用与实际 Cluster 一致的 endpoint 配置。
- 同一 service 在 `nodes` 中对应多个 hostname 尾部，或没有可用映射时，应用启动失败，避免连接到不确定目标。
- 不改写初始 seed，不以 seed 替代动态节点，不提供 seed fallback。
- 不做反向 DNS、IP 到 hostname 推导、任意后缀猜测或 Redis 服务端配置改造。
- IPv4、IPv6、`localhost`、单段 hostname、已完整 hostname、未知 service 及不符合受支持语法的 hostname 均保持原样。
- 不升级或覆盖 Spring Boot 管理的 Lettuce 版本；已有自适应刷新、周期性刷新、断连拒绝入队和请求队列上限默认值保持不变。
- Route 仅负责 datasource 路由、连接、Cluster topology 与客户端生命周期；Redis key 扫描、枚举、cursor 和只读查询属于 Middleware Ops 读查询边界，不纳入 Route API。

## 验证

使用完整模块测试命令 `:sdk:route:redis:simple-redis-route-starter:test --rerun-tasks --no-daemon` 完成以下环境验证：

| Spring Boot | Java | 结果 |
|---|---:|---|
| 2.2.13.RELEASE | 8 | 通过；保留 Lettuce 5.2 对 Redis 7 hostname Cluster 元数据的已知边界 |
| 2.3.12 | 8 | 通过 |
| 2.4.5 | 8 | 通过 |
| 2.7.9 | 11 | 通过 |

每个版本均执行普通 Redis E2E、Redis 3.2.12 / 5.0.14 / 7.2.6 standalone 与 Cluster 矩阵、topology hostname mapper、Route 创建的 Lettuce resolver，以及 `ClientResources` 生命周期验证。

短 hostname topology Cluster E2E 在不修改 Windows hosts、也不依赖 JVM hosts-file 的条件下完成：JVM 对 Redis 返回的短 hostname 和测试 FQDN 均不可解析；测试专用 Lettuce `DnsResolver` 只接受 Route 补全后的 fixture FQDN，并解析到本地 topology Cluster。该测试验证动态节点端口不被改写；Spring Boot 2.3.12、2.4.5、2.7.9 还完成非 seed primary 的真实写读，并跨周期刷新再次写读。

Spring Boot 2.2.13.RELEASE 的 Lettuce 5.2 无法解析 Redis 7 hostname 型 `CLUSTER SLOTS` 节点元数据。测试显式断言该 `UnsupportedOperationException` 链路，不跳过测试、不升级依赖，也不将这个组合标记为可正常读写。

## 升级说明

- 默认关闭，不影响既有 standalone、Cluster 或混合 datasource 配置。
- 只有 Redis 返回两段 hostname、而初始 `nodes` 使用完整 hostname 的 Cluster 部署才需要开启 `cluster-topology-address-follow-nodes`。
- 开启前应确认所有同 service 的 `nodes` 使用同一个 namespace / cluster domain 尾部；配置存在歧义时应先在部署配置中消除歧义，而不是依赖 Route 选择其中一个地址。
