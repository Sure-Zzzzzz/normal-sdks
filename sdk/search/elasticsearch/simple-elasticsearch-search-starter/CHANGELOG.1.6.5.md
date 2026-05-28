# v1.6.5 更新日志

**发布日期：** 2026-05-28

**类型：** Bug Fix

**依赖升级：** 无（`simple-elasticsearch-search-core` 版本不变，仍为 1.0.10）

---

## Bug Fix

### 1. TEXT 字段 `=` 操作符未使用 keyword 子字段

**问题**：字段类型为 `text` 且存在 `keyword` 子字段时，`=` 操作符生成的是 `matchQuery(field, value)`，而非 `termQuery(field.keyword, value)`，导致精确匹配语义不正确。

**修复**：`EqOperatorStrategy` 在构建等值查询时，优先检查字段是否存在 `keyword` 子字段。若存在，则使用 `termQuery(field.keyword, value)` 进行精确匹配；否则回退到原有逻辑（TEXT 用 `matchQuery`，其他类型用 `termQuery`）。

**受影响场景**：表达式搜索中对 `text+keyword` 字段使用 `=` 操作符，例如 `name = 'Alice'`。

---

### 2. 多条件 AND/OR 表达式生成嵌套 bool 查询

**问题**：`A AND B AND C` 经表达式解析器生成左结合树 `AND(AND(A, B), C)`，`QueryDslBuilder` 未对同逻辑节点做扁平化处理，导致生成两层嵌套的 `bool` 查询，影响 ES 查询性能和可读性。

**修复**：重构 `QueryDslBuilder.collectFlatQueries()`，引入 `rootLogic` 参数。递归遍历条件树时，若子节点逻辑与根节点相同（AND+AND 或 OR+OR），则继续展开；若逻辑不同（AND 内嵌 OR，或 OR 内嵌 AND），则将子树构建为完整的嵌套 `bool` 后加入。

**效果**：`A AND B AND C` 生成单层 `bool.must[A, B, C]`；`A AND (B OR C)` 生成 `bool.must[A, bool.should[B, C]]`，结构正确且无多余嵌套。

---

## 升级说明

**兼容性**：与 v1.6.4 完全兼容，无 API 变更，无配置变更。

```gradle
dependencies {
    implementation 'io.github.surezzzzz:simple-elasticsearch-search-starter:1.6.5'
}
```
