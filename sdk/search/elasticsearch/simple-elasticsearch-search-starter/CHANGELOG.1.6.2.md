# v1.6.2 更新日志

**发布日期：** 2026-05-20

**类型：** Bug Fix + Enhancement Release

**依赖升级：** `simple-elasticsearch-search-core` 1.0.8 → **1.0.10**（必须）

---

## Bug 修复

### PIT 分页不支持 alias 修复

**问题**：`PitPaginationStrategy.openOrRenewPit()` 直接使用 `request.getIndex()`（alias）构建 PIT URL，ES 的 `/<alias>/_pit` 返回 404，导致 PIT 分页对带别名的索引完全不可用。

**修复**：新增 `resolvePhysicalIndex()` 方法，通过 `MappingManager` 将 alias 解析为物理索引名（`IndexMetadata.indexName`），再用物理名构建 PIT URL。`MappingException` 等异常时 fallback 到原始值并打印 warn 日志。

### AggExecutor dateRange 优先级修复

**问题**：`AggRequest` 传入显式 `dateRange` 时，聚合执行器仍尝试从 `QueryCondition` 中以 `BETWEEN` 操作符推断，导致 `gte`/`lte` 格式的 dateRange 被忽略，日期分割索引路由失效。

**修复**：`AggExecutor` 新增 `resolveDateRange()` 方法，优先使用显式 `dateRange`，仅在未传入时 fallback 到查询条件推断。

---

## 增强功能

### ExecutionContext 重构 + 错误事件

**新增 ExecutionContext 基类**（`simple-elasticsearch-search-core` 1.0.9）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `actualIndices` | String[] | 实际命中的索引列表（经过降级路由后的索引名数组） |
| `datasource` | String | 本次请求路由到的数据源名称 |
| `downgradeLevel` | int | 降级级别（0=未降级，1+=逐级降级） |
| `sourceType` | String | 请求来源（`QUERY_API` / `NL_API` / `EXPRESSION_API`） |

`QueryExecutionContext` 与 `AggExecutionContext` 继承该基类，事件中现在可完整获取索引、数据源、降级级别和来源类型。

**新增错误事件**（`simple-elasticsearch-search-core` 1.0.9，1.0.10 补齐 sourceType）：

- `EsQueryErrorEvent`：查询执行失败时发布，包含 request、error、datasource、sourceType
- `EsAggErrorEvent`：聚合执行失败时发布，包含 request、error、datasource、sourceType

**用途**：可在 Application 层监听这两个事件，记录降级情况或向监控系统推送指标。

---

### NL API 参数透传增强

`NLQueryRequest` 和 `NLAggRequest` 新增可选字段，传入后可覆盖 NL 翻译解析出的对应参数：

**`NLQueryRequest` 新增字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `pagination` | PaginationInfo | 覆盖 NL 解析出的分页参数，支持 scroll/search_after/pit 等所有翻页模式 |
| `dateRange` | DateRange | 覆盖 NL 解析出的时间范围，用于日期分割索引路由 |
| `fields` | List\<String\> | 字段投影，只返回指定字段（空列表不覆盖） |
| `collapse` | CollapseField | 字段折叠去重 |

**`NLAggRequest` 新增字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `dateRange` | DateRange | 覆盖 NL 解析出的时间范围 |
| `after` | Map | composite 聚合翻页游标 |

---

### scroll 续页支持

`POST /api/query/nl` 新增 scroll 续页支持：当请求中 `pagination.scrollId` 非空时，跳过 NL 解析，直接用 `index` + `pagination` 构建查询请求，实现 scroll 翻页。

**续页请求示例：**

```json
POST /api/query/nl
{
  "dataSource": "test_user",
  "pagination": {
    "type": "scroll",
    "size": 2,
    "scrollTtl": "1m",
    "scrollId": "FGluY2x1ZGVfY29udGV4dF91dWlk..."
  }
}
```

---

## 代码优化

- `PitPaginationStrategy`：消除全限定类名（`ObjectMapper`、`Request`、`Response`），统一使用 import
- `PitPaginationStrategy`：`parseKeepAliveToMillis` 从 `public` 收窄为 `private`
- `PitPaginationStrategy`：`closePitQuietly` 补充注释说明 index 仅用于路由，与 openOrRenewPit 保持一致
- `NLQueryRequest.fields` 空列表不再覆盖 NL 解析结果（`null` 与 `[]` 语义区分）

---

## 升级说明

**兼容性**：与 v1.6.1 完全兼容，无破坏性 API 变更。

**必须升级 `simple-elasticsearch-search-core` 至 1.0.10**：

```gradle
dependencies {
    implementation 'io.github.surezzzzz:simple-elasticsearch-search-starter:1.6.2'
}
```
