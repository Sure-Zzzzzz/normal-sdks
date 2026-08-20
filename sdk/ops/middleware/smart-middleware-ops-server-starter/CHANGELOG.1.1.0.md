# smart-middleware-ops-server-starter 1.1.0

类型：Maintenance / Reliability

## 本次发布

本版本在 `1.0.1` 基础上修复 Elasticsearch 文档查询可靠性问题，并按能力域整理四类中间件 Java 包结构。

## Elasticsearch 文档查询可靠性修复

- 文档查询响应解析改用专用的 256 KiB 响应上限，不再误用通用 `query.max-response-length` 的默认 4096 字节限制。
- 正常超过 4 KiB 但未超过 256 KiB 的 Elasticsearch 文档响应不再仅因响应大小被错误映射为 HTTP 503。
- 响应超限、空响应、格式错误、超时和下游失败均保留内部异常 cause，便于服务端排障；公共错误消息和 HTTP 状态语义保持安全且稳定。
- 服务端诊断仅记录脱敏的数据源标识、HTTP 方法、相对路径、可取得的下游状态和异常类型/栈帧，不记录 DSL、原始响应、凭据、认证信息、主机或完整 URL。
- Elasticsearch Route 的 client、连接、协议和生命周期所有权不变，未新增 Route 能力或通用搜索能力。

## Java 包结构整理

四个中间件领域的 Java 类型按具体运维能力迁移到新的能力域包：

- Elasticsearch：`adapter`、`summary`、`catalog`、`document`、`field`
- Redis：`adapter`、`datasource`、`summary`、`key.discovery`、`key.metadata`、`key.read`
- Kafka：`adapter`、`datasource`、`topic.list`、`topic.config`、`topic.runtime`、`consumer.list`、`consumer.detail`、`consumer.lag`
- MySQL：`adapter`、`datasource`、`table`、`query`

旧 FQCN 不通过包装类、别名、`@Deprecated` 转发类或重复 DTO 保留。使用方需要更新 Java import；特别是 `MysqlTableRequestValidator` 与表目录能力仍共同位于 `mysql.table`，`MysqlControlledSelectPolicy` 与 SELECT/EXPLAIN 校验器仍共同位于 `mysql.query`，以保持既有包内安全规则。

## 保持不变

- HTTP 路径、方法、请求参数、响应 JSON 字段、状态码和页面行为。
- 配置前缀、配置默认值、通用 `query.max-response-length` 语义，以及 Spring 自动配置 Bean 名称、条件和注册顺序。
- LDAP 认证、授权、并发/截止时间、审计字段与脱敏边界。
- Elasticsearch、Redis、Kafka、MySQL Route 边界，以及 Search/Persistence 和其他模块。

## 升级说明

1. 将依赖版本从 `1.0.1` 升级到 `1.1.0`。
2. 若 Java 代码直接引用本 Starter 的中间件类型，按 `DESIGN.1.1.0.md` 的 FQCN 迁移表更新 import。
3. HTTP 调用方、配置文件和 Route 接入方式无需因本版本改变。
