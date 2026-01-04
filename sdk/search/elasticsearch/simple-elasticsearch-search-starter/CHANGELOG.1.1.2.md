# 1.1.2 更新日志

## 发布日期

2026-01-04

## 主要功能

### search_after 深度分页支持

新增对自然语言 search_after 深度分页的支持，可以从自然语言中解析 search_after 游标值，实现 Elasticsearch 的深度分页查询。

#### 功能特性

- **search_after 解析**：支持从自然语言中提取 search_after 游标值（如"接着[value1,value2]继续查询"）
- **多值支持**：支持多个排序字段的 search_after 值（如 `[timestamp, doc_id]`）
- **智能类型推断**：自动识别数值（Long/Double）和字符串类型
- **深度分页**：避免 Elasticsearch offset 分页的 10000 条限制

#### 支持的自然语言语法

基于 `natural-language-parser-starter:1.0.6` 提供的解析能力：

**search_after 表达：**

- "接着[value]继续查询"
- "接着[value1,value2]继续查询"
- "接着[timestamp,doc_id]继续查询，返回500条"

**语法规则：**

- 使用方括号 `[]` 包裹 search_after 值
- 多个值用逗号 `,` 分隔
- 自动识别数值类型（纯数字解析为 Long/Double）
- 非数字保持为字符串

#### 使用示例

**示例 1：单值 search_after**

```bash
GET /api/nl/dsl?text=查询订单索引，status等于completed，按createTime降序，接着[1704110400000]继续查询，返回20条
```

响应：

```json
{
  "index": "订单",
  "query": {
    "field": "status",
    "op": "eq",
    "value": "completed"
  },
  "pagination": {
    "type": "search_after",
    "searchAfter": [1704110400000],
    "size": 20,
    "sort": [
      {
        "field": "createTime",
        "order": "desc"
      }
    ]
  }
}
```

**示例 2：多值 search_after（多字段排序）**

```bash
GET /api/nl/dsl?text=查询app_access_log-*索引，clientIP等于192.168.1.1，时间范围2025-01-01到2026-01-01，按timestamp降序，接着[1704110400000,user_123,doc_456]继续查询，返回500条
```

响应：

```json
{
  "index": "app_access_log-*",
  "query": {
    "field": "clientIP",
    "op": "eq",
    "value": "192.168.1.1"
  },
  "dateRange": {
    "from": "2025-01-01T00:00:00",
    "to": "2026-01-01T00:00:00"
  },
  "pagination": {
    "type": "search_after",
    "searchAfter": [1704110400000, "user_123", "doc_456"],
    "size": 500,
    "sort": [
      {
        "field": "timestamp",
        "order": "desc"
      }
    ]
  }
}
```

**示例 3：字符串类型 search_after**

```bash
GET /api/nl/dsl?text=查询用户索引，接着[user_abc123]继续查询
```

响应：

```json
{
  "index": "用户",
  "pagination": {
    "type": "search_after",
    "searchAfter": ["user_abc123"],
    "size": 20
  }
}
```

## 核心改动

### SimpleElasticsearchIntentTranslator

**无需修改**：

- `translatePagination` 方法（206-210行）已在 1.1.0 中实现了 searchAfter 支持
- 当 `PaginationIntent.searchAfter` 不为空时，自动切换到 search_after 分页模式
- 自动设置 `type: "search_after"` 和 `searchAfter` 字段

**现有实现**：

```java
if (paginationIntent != null && paginationIntent.getSearchAfter() != null && !paginationIntent.getSearchAfter().isEmpty()) {
    // search_after分页
    builder.type(SimpleElasticsearchSearchConstant.PAGINATION_TYPE_SEARCH_AFTER)
            .searchAfter(paginationIntent.getSearchAfter())
            .size(paginationIntent.getLimit() != null ? paginationIntent.getLimit() : defaultPageSize);
}
```

## 依赖变更

### 升级依赖

```gradle
// 自然语言解析器（从 1.0.5 升级到 1.0.6）
api "${group}:natural-language-parser-starter:1.0.6"
```

**natural-language-parser-starter:1.0.6 主要特性：**

- **新增 search_after 解析**：支持 `[value1,value2]` 格式的游标值解析
- **智能类型推断**：自动识别 Long、Double、String 类型
- **逗号分隔支持**：支持多值场景（对应多字段排序）
- 保留 1.0.5 的所有特性（时间范围、复杂条件、聚合等）

**1.0.6 新增能力：**

- `extractSearchAfter()` 方法：使用正则提取方括号内的值
- `parseValue()` 方法：智能类型推断（数字优先，失败则保持字符串）
- `PaginationIntent.searchAfter` 字段填充

## 注意事项

### 1. search_after 语法要求

必须使用方括号包裹游标值：

- ✅ 正确：`接着[1704110400000]继续查询`
- ✅ 正确：`接着[value1,value2]继续查询`
- ❌ 错误：`接着1704110400000继续查询`（缺少方括号）
- ❌ 错误：`接着(value1,value2)继续查询`（使用了圆括号）

### 2. 值的类型匹配

search_after 的值类型必须与排序字段类型一致：

- 时间戳字段：使用数值（如 `1704110400000`）
- 字符串字段：使用字符串（如 `user_123`）
- 多字段排序：值的顺序必须与排序字段顺序一致

**示例**：

```bash
# 按时间戳降序，然后按用户ID升序
# search_after 必须是 [timestamp, userId]
接着[1704110400000,user_123]继续查询
```

### 3. 与排序字段的关系

- search_after 必须配合排序使用
- search_after 的值数量应与排序字段数量一致
- 建议排序字段包含唯一字段（如 `_id`）以保证稳定性

### 4. 深度分页场景

**推荐使用 search_after 的场景**：

- 需要翻页超过 10000 条（Elasticsearch 的 `max_result_window` 限制）
- 实时数据流式导出
- 需要稳定的排序结果（不受新数据插入影响）

**不推荐使用 search_after 的场景**：

- 需要跳页（如直接跳到第 100 页）
- 数据量小于 10000 条

### 5. 获取 search_after 值

通常从上一次查询的响应中获取：

```javascript
// 第一次查询（使用 offset 或不指定分页）
const result1 = await fetch('/api/query', {
  method: 'POST',
  body: JSON.stringify({
    index: "orders",
    query: {...},
    pagination: {
      type: "offset",
      page: 1,
      size: 20,
      sort: [{ field: "createTime", order: "desc" }]
    }
  })
}).then(res => res.json());

// 从响应中获取最后一条记录的排序值
const lastDoc = result1.data.hits[result1.data.hits.length - 1];
const searchAfterValue = lastDoc.sort; // 例如：[1704110400000, "doc_123"]

// 第二次查询（使用 search_after）
const text = `查询订单索引，接着[${searchAfterValue.join(',')}]继续查询`;
const dsl = await fetch(`/api/nl/dsl?text=${encodeURIComponent(text)}`)
  .then(res => res.json());
```

## 使用场景

### 场景 1：日志导出

```bash
# 第一页
GET /api/nl/dsl?text=查询应用日志索引，level等于ERROR，按时间戳降序，返回1000条

# 第二页（使用上次的最后一条记录的时间戳）
GET /api/nl/dsl?text=查询应用日志索引，level等于ERROR，按时间戳降序，接着[1704110400000]继续查询，返回1000条

# 第三页
GET /api/nl/dsl?text=查询应用日志索引，level等于ERROR，按时间戳降序，接着[1704100000000]继续查询，返回1000条
```

### 场景 2：访问日志查询

```bash
# 查询大量访问日志，使用 search_after 避免深度分页限制
GET /api/nl/dsl?text=查询app_access_log-*索引，clientIP等于192.168.1.1，时间范围2025-01-01到2026-01-01，按timestamp降序，接着[1704110400000,access_456]继续查询，返回500条
```

### 场景 3：用户行为分析

```bash
# 多字段排序：先按活跃度，再按用户ID
GET /api/nl/dsl?text=查询用户行为索引，接着[95,user_abc123]继续查询
```

## 升级建议

### 从 1.1.1 升级

**1. 无需修改代码**

- search_after 功能为新增功能，不影响现有 API
- 所有现有查询接口保持向后兼容

**2. 依赖自动升级**

- `natural-language-parser-starter` 从 1.0.5 升级到 1.0.6
- 升级是向后兼容的，不影响现有功能（时间范围、条件查询等）

**3. 可选启用**

- 如果不使用 search_after 功能，无需任何配置
- search_after 语法是自动识别的，不需要额外配置

**4. 测试建议**

- 在开发环境测试 search_after 查询效果
- 验证 search_after 值的类型匹配
- 确认多字段排序的值顺序正确

## 兼容性

- **向后兼容**：完全兼容 1.1.1 及之前版本
- **新增功能**：search_after 深度分页支持（可选使用）
- **依赖升级**：`natural-language-parser-starter:1.0.5` → `1.0.6`（向后兼容）
- **API 兼容**：所有现有 API 保持不变

## 贡献者

- surezzzzzz
