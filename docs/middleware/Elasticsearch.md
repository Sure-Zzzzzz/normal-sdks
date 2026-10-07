# Elasticsearch 工具链

ES 生态接入：从连接路由（日期分片/代理）到搜索框架、写入框架，再到指标与审计伴生件。自底向上组织。

## 连接与路由（底层）

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-elasticsearch-route-starter](../../sdk/route/elasticsearch/simple-elasticsearch-route-starter) | 1.2.1 | 1.0.0 | 多数据源路由（日期分片+异步写+可配置代理+ES 兼容公共 Helper） | [README](../../sdk/route/elasticsearch/simple-elasticsearch-route-starter/README.md) |

## 搜索

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-elasticsearch-search-core](../../sdk/search/elasticsearch/simple-elasticsearch-search-core) | 1.0.12 | — | 搜索核心库（事件发布） | [README](../../sdk/search/elasticsearch/simple-elasticsearch-search-core/README.md) |
| [simple-elasticsearch-search-starter](../../sdk/search/elasticsearch/simple-elasticsearch-search-starter) | 1.7.2 | 1.0.0 | 查询框架（API/NL/表达式/countOnly/`_id` 查询/通配符具体索引匹配） | [README](../../sdk/search/elasticsearch/simple-elasticsearch-search-starter/README.md) |
| [simple-elasticsearch-search-metrics-starter](../../sdk/metrics/elasticsearch/simple-elasticsearch-search-metrics-starter) | 1.0.2 | — | 指标采集 | [README](../../sdk/metrics/elasticsearch/simple-elasticsearch-search-metrics-starter/README.md) |
| [simple-elasticsearch-search-audit-listener-starter](../../sdk/audit/search/elasticsearch/simple-elasticsearch-search-audit-listener-starter) | 1.0.4 | 1.0.0 | 审计事件 | [README](../../sdk/audit/search/elasticsearch/simple-elasticsearch-search-audit-listener-starter/README.md) |

**搜索核心特性**：零代码配置驱动的查询和聚合、RESTful API 自动生成、查询/聚合执行后自动发布事件（审计与监控扩展）、ES 6.x 与 7.x+、Boot 2.2/2.3/2.4/2.7。

## 写入

| SDK | javax | jakarta | 说明 | 文档 |
|-----|-------|---------|------|------|
| [simple-elasticsearch-persistence-core](../../sdk/persistence/elasticsearch/simple-elasticsearch-persistence-core) | 1.0.3 | — | 写入核心模型（request/option/result/query） | [README](../../sdk/persistence/elasticsearch/simple-elasticsearch-persistence-core/README.md) |
| [simple-elasticsearch-persistence-starter](../../sdk/persistence/elasticsearch/simple-elasticsearch-persistence-starter) | 1.1.1 | 1.0.0 | 写侧框架（index/create/update/delete/bulk/byQuery/create 冲突转 update），内置集成 route | [README](../../sdk/persistence/elasticsearch/simple-elasticsearch-persistence-starter/README.md) |
| [simple-elasticsearch-persistence-audit-listener-starter](../../sdk/audit/persistence/elasticsearch/simple-elasticsearch-persistence-audit-listener-starter) | 1.0.0 | 1.0.0 | 写侧审计监听（单条/bulk/byQuery/错误事件） | [README](../../sdk/audit/persistence/elasticsearch/simple-elasticsearch-persistence-audit-listener-starter/README.md) |

**写入核心特性**：自动继承 route 的多数据源路由与日期分片、`DocumentPreProcessor` 写前扩展链、稳定 ID 生成、routing/pipeline/refreshPolicy/retryOnConflict 透传、bulk 分批与失败明细、Boot 2.2.x/2.3.12/2.4.5/2.7.9 × ES 6.x/7.x。

## 依赖关系

- search/persistence 均 api 传递 es-route（内置集成，引 starter 即得路由能力）
- search-starter 另引 [自然语言解析](../tool/自然语言解析.md)、[表达式解析](../tool/表达式解析.md)、[日志截断](../tool/日志.md)（api 传递）
- metrics/audit 以事件零侵入挂接，SDK 零依赖 listener
- Jakarta 线（route/search/persistence/audit×2）已发 central；矩阵深度见各模块 README（当前实测 Boot 3.4.2 × JDK 17/21，route 为 3.2/3.3/3.4 全矩阵）

## 版本映射（唯一事实源）

**Search 版本兼容**：

| search-starter | search-core | route-starter | metrics | audit-listener |
|----------------|-------------|---------------|---------|----------------|
| 1.7.2 / 1.7.1 / 1.7.0 | 1.0.12 | 1.2.0 | 1.0.2 | 1.0.4 |
| 1.6.10 / 1.6.9 | 1.0.12 | 1.1.2 | 1.0.2 | 1.0.4 |
| 1.6.8 / 1.6.7 | 1.0.12 | 1.0.10 | 1.0.2 | 1.0.4 |
| 1.6.6 | 1.0.11 | 1.0.10 | 1.0.1 | 1.0.3 |
| 1.6.5 | 1.0.10 | 1.0.10 | 1.0.0 | 1.0.2 |
| 1.6.4 / 1.6.3 / 1.6.2 | 1.0.10 | 1.0.10 | - | 1.0.2 |
| 1.6.1 / 1.6.0 | 1.0.8 | 1.0.10 | - | 1.0.1 |
| 1.5.8 | 1.0.8 | 1.0.8 | - | 1.0.1 |
| 1.5.7 ~ 1.5.4 | 1.0.7 / 1.0.6 / 1.0.5 | 1.0.8 | - | 1.0.0 |
| 1.5.3 ~ 1.5.0 / 1.4.0 | 1.0.5 / 1.0.4 | 1.0.7 | - | 1.0.0 |
| 1.3.1 / 1.3.0 | 1.0.3 | 1.0.7 | - | 1.0.0 |
| 1.2.1 / 1.2.0 | 1.0.1 | 1.0.5 | - | 1.0.0 |
| ≤ 1.1.x | - | 1.0.5 | - | - |

**Persistence 版本兼容**：

| persistence-starter | persistence-core | route-starter | audit-listener |
|---------------------|------------------|---------------|----------------|
| 1.1.1 | 1.0.3 | 1.2.1 | 1.0.0 |
| 1.1.0 | 1.0.2 | 1.2.0 | 1.0.0 |
| 1.0.2 | 1.0.2 | 1.1.2 | - |
| 1.0.1 / 1.0.0 | 1.0.1 | 1.1.2 | - |
