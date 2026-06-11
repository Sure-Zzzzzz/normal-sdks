# v1.6.6 更新日志

**发布日期：** 2026-06-11

**类型：** Feature + Bug Fix

**依赖升级：** `simple-elasticsearch-search-core` 1.0.10 → 1.0.11

---

## Feature

### 1. 新增 `countOnly=true` 独立计数查询

**背景**：SDK 此前缺少独立的总数查询能力，`POST /query` 只能在返回文档的同时附带 `total`。对于只需要总数的场景（UI 统计、PIT 翻页前预估、大数据量预检），`size=0` 的 `_search` 仍需跨 shard 协调精确计数，代价不可接受。

**解决方案**：新增 `countOnly=true` 请求参数，走 ES 原生 `_count` API，仅返回匹配文档数，无文档 fetch、无 sort，性能远优于 `_search + size=0`。

**新增文件：**
- `CountExecutor`：独立执行器，继承 `AbstractExecutor`，不走 `QueryRequestValidatorChain`，直接注入 `IndexAliasValidator`、`DefaultDateRangeValidator`、`CountOnlyValidator` 三个针对性校验器；`needsDateFilter` 使用日期边界常量；`onExecutionError` 使用 7 参数 `EsQueryErrorEvent` 构造器
- `CountOnlyValidator`：校验 `countOnly=true` + PIT 分页冲突（ES `_count` 不支持 PIT，语义冲突时报错 `SEARCH_QUERY_011`）

**新增方法：**
- `ElasticsearchCompatibilityHelper.executeCount()`：低级 API 透传 `_count` 调用（6.x/7.x 无差异）
- `XContentReflectionHelper.parseCountResponse()`：解析 `_count` 响应 JSON，取 `count` 字段值

**变更文件：**
- `QueryRequest`、`NLQueryRequest`、`ExpressionQueryRequest`：新增 `countOnly` 字段
- `SourceType`：新增枚举类，定义 `QUERY_API` / `NL_API` / `EXPRESSION_API` / `COUNT_API` 四种来源类型，提供标准 `code`/`description` 字段和 `fromCode()`/`isValid()`/`getAllCodes()` 方法
- `ErrorCode` / `ErrorMessage`：新增 `COUNT_ONLY_PIT_NOT_SUPPORTED`（`SEARCH_QUERY_011`）、`COUNT_RESPONSE_FIELD_MISSING`、`COUNT_RESPONSE_PARSE_FAILED`
- `SimpleElasticsearchSearchConstant`：新增 `ES_API_COUNT`、`ES_COUNT_EMPTY_QUERY`、`ES_COUNT_QUERY_TEMPLATE`、日期边界常量（`DATE_TIME_SEPARATOR` / `DATE_START_OF_DAY` / `DATE_END_OF_DAY` 等）、反射方法常量（`METHOD_NEXT_TOKEN` / `METHOD_CURRENT_NAME` / `METHOD_GET_LONG_VALUE` 等）
- `SimpleElasticsearchSearchApiEndpoint`：三个查询接口均支持 `countOnly=true` 路由到 `CountExecutor`
- `QueryExecutor`：`needsDateFilter` 方法中硬编码日期边界字符串替换为常量；`onExecutionError` 改用 7 参数 `EsQueryErrorEvent` 构造器

**代码规范修复：**
- `QueryExecutor` / `CountExecutor` / `AggExecutor`：`downgradeLevel` 统一使用 `DowngradeLevel.getValue()` 替代 `ordinal()`，默认值使用 `DowngradeLevel.LEVEL_0.getValue()` 替代硬编码 `0`

**行为说明：**
- `countOnly=true` 时：返回 `{"total": 12345, "items": null, "pagination": null}`
- `countOnly=true` + PIT 分页：400 报错 `SEARCH_QUERY_011`
- `countOnly=true` + scroll / offset / size：参数静默忽略，`_count` API 不支持
- `countOnly=true` + NL query / expression query：透传 `countOnly` 到内部 `QueryRequest`，路由行为一致

**事件发布：**
- `EsQueryEvent`：发布时 `context.sourceType=COUNT_API`；下游监听器通过 `event.getRequest().getCountOnly()` 判断是否为 count 请求
- `EsQueryErrorEvent`：新增 7 参数构造器，传递 `downgradeLevel` / `countOnly`（独立字段，不在 context 中） / `context`

---

## Bug Fix

### 2. Error 事件缺少 `downgradeLevel` 和 `context`

**问题**：`EsQueryErrorEvent` / `EsAggErrorEvent` 在执行失败时发布，原有 4 参数构造器硬编码 `downgradeLevel=0`，且不携带执行上下文。

**修复**：新增 6/7 参数构造器，调用方传入真实 `downgradeLevel`（从 `DowngradeFailedException.finalLevel` 提取）和 `ExecutionContext`。若调用方传 `context=null`，则在构造器内部构建最小 context（`datasource` + `downgradeLevel` + `sourceType`），确保下游监听器总能获取有效上下文。

**新增字段（`EsQueryErrorEvent`，@since 1.0.11）：**
- `countOnly`：boolean，供审计/监控下游区分 count 查询与普通查询
- `downgradeLevel`：int，真实降级级别
- `context`：QueryExecutionContext，执行上下文

---

## 升级说明

**兼容性**：与 v1.6.5 完全兼容。`countOnly` 默认为 `null`/`false`，现有调用无需修改。新增 `EsQueryErrorEvent` / `EsAggErrorEvent` 构造器向下兼容原有 4 参数版本。

```gradle
dependencies {
    implementation 'io.github.surezzzzz:simple-elasticsearch-search-starter:1.6.6'
}
```
