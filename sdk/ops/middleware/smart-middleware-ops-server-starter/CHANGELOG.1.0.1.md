# smart-middleware-ops-server-starter 1.0.1

发布日期：2026-08-19

类型：Feature

## 本次发布

完善 Elasticsearch、Redis、Kafka、MySQL 四个工作区的受控只读观察闭环。Ops 继续只消费 Route 已公开的受控入口，不取得中间件连接、客户端、Cluster 拓扑或生命周期所有权。

## 依赖更新

| 依赖 | 1.0.0 | 1.0.1 | 说明 |
| --- | --- | --- | --- |
| `simple-redis-route-starter` | 1.1.0 | 1.2.2 | 适配 default-source 连接工厂所有权与可选 Lettuce 连接池契约。 |
| `commons-pool2` | 无 | 无版本 `runtimeOnly` | 仅为 Redis Route 显式启用 `lettuce.pool.enabled=true` 的数据源提供运行时类路径；版本由 Spring Boot 2.7.9 BOM 管理。 |

其他 Route、Search、Persistence 与 Web/Security 依赖坐标保持不变。

## 受控只读能力

### Elasticsearch

- 补齐数据源安全摘要、受限索引目录、精确索引字段能力和受限 JSON DSL 首窗口的页面与 HTTP 观察入口。
- 文档查询支持精确索引、`*`/`?` 通配模式及逗号分隔的多个模式，固定仅展开公开 open 索引；隐藏索引、`_all`、路径片段和控制字符仍被拒绝。字段能力目录继续只支持精确非隐藏索引。
- 查询仅通过 Elasticsearch Route 已公开的受控 client 执行，不暴露原始下游响应或通用 DSL 接口。

### Redis

- 新增必填字面量前缀的单次有界 Key 发现；服务端仅由已校验的前缀构造内部 match，并使用 `SCAN`，不使用 `KEYS`。
- 返回固定上限内的精确 Key，不返回 value、cursor、续传令牌、节点、地址、端口、slot 或拓扑信息；不建立查询会话。
- Cluster 只扫描 master。拓扑或任一 master 扫描失败时丢弃候选并返回服务不可用；deadline 到期时丢弃候选并返回超时，不将不完整结果伪装为完成快照。
- 保持精确 Key 元数据与已检测类型的受限值窗口；Ops 不直接创建、关闭或复用 Route 之外的 Redis client。
- 覆盖 standalone 与 Cluster 的可选 Lettuce 连接池，以及默认非池化数据源；连接池默认仍关闭。

### Kafka

- 补齐数据源安全诊断、Topic 清单、Consumer Group 清单、Topic 运行态、Consumer Group 详情与积压窗口的受控观察入口。
- 所有 AdminClient 操作保持在 Kafka Route 提供的同步 callback 作用域内完成，不保留客户端或未完成任务，不开放消息、payload、header、任意 Admin 命令或写操作。

### MySQL

- 补齐数据源安全状态、表/列/索引目录、受控 SELECT 与受控 Explain 的只读观察入口。
- 受控 SELECT 与 Explain 统一使用服务端 AST 白名单：仅允许无 schema 的单表、单条 SELECT；拒绝注释、分号、CTE、JOIN、UNION、SQL 内分页、分组、锁定、INTO、通配投影、函数和表达式。
- 页面在提交前展示规则；服务端按违反类别返回固定中文安全消息，不回显 SQL、表名、解析异常、连接信息或凭据。浏览器不重复解析 SQL，服务端是唯一规则真源。

## 控制台与审计

- 四个工作区提供统一数据源目录、安全概览、受控控制台、浏览器内存分页和脱敏审计查看；控制台输入草稿仅保存在当前浏览器，不持久化查询结果、cursor 或查询会话。
- 页面右上角提供全局北京时间/UTC 显示设置，默认北京时间，选择保存在浏览器 `localStorage`，并统一影响审计展示、有效查询范围和自定义时间输入。
- 自动概览不写审计；操作者显式提交的已发布只读能力写入审计。审计读侧继续复用 Elasticsearch Search 的敏感字段脱敏边界。
- 页面和 API 保持 LDAP 身份认证、HTTP 状态语义、`Cache-Control: no-store` 与服务端请求标识边界。

## 验证

- 在 Spring Boot `2.7.9`、Java 11、Gradle 8.5 基线执行完整 Starter 测试：133 个测试全部通过，0 failure、0 error、0 ignored。
- 真实 Spring 上下文端到端覆盖 LDAP 认证、四类 Route 数据源目录、Redis standalone/Cluster 有界发现、可选连接池与默认非池化 client configuration、Kafka 受控观察、MySQL 受控查询及审计脱敏边界。
- 使用本机 Chrome 通道的 Playwright CLI 完成真实登录后的 Kafka 与 MySQL 核心控制台渲染验收。

## 向后兼容性

- Ops 既有已发布接口和配置前缀不变；新增能力均为受控只读 HTTP 入口和页面区域。
- Redis Route 1.2.2 的 default-source 物理连接工厂所有权是上游既定迁移：已启用 Route 的应用不得保留自建 `RedisConnectionFactory`，业务 `RedisTemplate` 仅可绑定 Route default-source factory。升级前应按 Redis Route 1.2.2 文档完成该迁移。
- Redis Route 升级至 1.2.2 后，未设置 `lettuce.pool.enabled=true` 的数据源保持非池化行为。
- 本版本不改变 Route 所有权，不引入 Redis/Kafka/Elasticsearch/MySQL 的原始命令透传、写操作或泛化搜索能力。

## 升级指南

1. 将依赖坐标升级至 `io.github.sure-zzzzzz:smart-middleware-ops-server-starter:1.0.1`。
2. 先按 Redis Route 1.2.2 文档迁移 default-source：移除宿主自建 `RedisConnectionFactory`，使业务 RedisTemplate 绑定 Route default-source factory。
3. 按各 Route Starter 已公开的配置登记数据源；Ops 不读取或复制物理连接配置。
4. 需要 Redis Key 发现时，以字面量前缀调用 `GET /redis/datasources/{datasourceKey}/keys/discovery`，并只处理当前响应中的固定窗口结果。
5. 需要 Redis 连接池时，仅在目标 datasource 设置 `lettuce.pool.enabled=true`；Ops 已以无版本运行时依赖提供 Commons Pool2，继续由 Spring Boot BOM 管理版本。
6. MySQL 控制台只提交符合页面规则的单表 SELECT；移除 SQL 尾部分号、注释、通配投影及 SQL 内 `LIMIT`，由页面 size 参数控制返回窗口。
