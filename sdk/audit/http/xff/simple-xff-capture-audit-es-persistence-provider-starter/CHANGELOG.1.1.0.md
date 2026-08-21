# simple-xff-capture-audit-es-persistence-provider-starter 1.1.0 Changelog

## 发布信息

- 版本：`1.1.0`
- 类型：Feature / Capture 请求数据持久化
- 基线版本：`1.0.0`

## 依赖升级

| 依赖 | 1.0.0 | 1.1.0 |
| --- | --- | --- |
| `simple-xff-capture-audit-core` | `1.0.0` | `1.1.0` |
| `simple-xff-capture-starter`（测试） | `1.0.0` | `1.1.0` |
| `simple-xff-capture-audit-listener-starter`（测试） | `1.0.0` | `1.1.0` |
| `simple-elasticsearch-persistence-starter` | `1.1.1` | `1.1.1` |

Elasticsearch Persistence Starter 已经提供通用 `PersistenceEngine` 写入能力，本版本不重复升级；它不限制审计文档新增的 `requestData` 字段。

## 主要变更

- Provider 依赖切换为已发布的 Audit Core `1.1.0`，测试链路使用 Capture 和 Listener `1.1.0`。
- Listener 直接把 Event 中已完成的不可变 `requestData` 快照交给本 Provider；本 Provider 不重新读取 HTTP 请求，不增加第二套 Query、Form、Body 采集配置。
- `requestData` 的采集开关、URI 规则、Content-Type 约束、截断和读取状态继续完全由 Capture 管理，并通过通用 `PersistenceEngine` 原样写入 Elasticsearch 文档。
- 保持 `xff-capture-audit` 逻辑索引、`eventId` 文档 ID、Route exact rule 和物理日索引写入边界不变。
- 测试模板保持根级宽松 dynamic，但显式定义 Audit Document 已知字段和 `requestData` 固定结构；未知参数键与未知扩展仍允许动态写入。

## 新增验证

- 真实随机端口 HTTP POST E2E 覆盖 Query 多值、JSON Body、Controller 完整 Body 回显和 `requestData` 写入 `_source`。
- 验证 `requestData` 的 Query、Form、Body 状态、Query 多值、Content-Type、Body 文本和保留字节数。
- 验证 Event → Listener → Persistence → Route → Elasticsearch 首次写入自动创建物理日索引，且不直接写入逻辑索引。
- 验证三个基础 IP 字段的精确查询、CIDR 查询和 `ip` mapping 类型；请求 Query 参数值、Body 原文和业务扩展值使用 `keyword` 直接精确查询，Body `text` 设置 `ignore_above: 32766`。
- 在 Spring Boot 2.7.9 + Elasticsearch 7.17.16、2.4.5 + 7.9.3、2.3.12.RELEASE + 7.9.3、2.2.13.RELEASE + 6.2.2 四组组合完成完整模块测试。

## 向后兼容性

- Provider SPI、逻辑索引、文档 ID、Route exact rule 和 Elasticsearch Persistence 依赖边界保持不变。
- 未开启 Capture 请求数据采集的应用仍由 Capture 产生默认 disabled 快照，Listener 和本 Provider 不新增采集行为。
- 业务扩展继续通过 `extensions` 信封写入，不平铺到审计文档顶层。

## 升级指南

1. 将 `simple-xff-capture-starter` 升级到 `1.1.0`。
2. 将 `simple-xff-capture-audit-listener-starter` 升级到 `1.1.0`。
3. 将本 Provider 升级到 `1.1.0`。
4. 如需保存请求数据，在 Capture Starter 中按需启用 Query、Form、Body 维度和 URI 规则；本 Provider 无需新增配置。
5. 生产模板保持宽松 dynamic，并仅为需要稳定查询类型的基础字段显式声明 mapping。
