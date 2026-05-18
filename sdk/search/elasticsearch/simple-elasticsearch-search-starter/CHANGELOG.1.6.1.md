# v1.6.1 更新日志

**发布日期：** 2026-05-17

**类型：** Feature Release

---

## 新功能

### 自然语言直接查询（NL Direct Query）

新增两个端点，支持直接用中文自然语言发起查询和聚合，无需手动构造 `QueryRequest`/`AggRequest`：

- `POST /api/query/nl`：自然语言查询，返回标准 `QueryResponse`
- `POST /api/agg/nl`：自然语言聚合，返回标准 `AggResponse`

**请求格式：**

```json
POST /api/query/nl
{
  "nl": "查询test_user索引，城市等于北京",
  "dataSource": "test_user"
}
```

- `nl`：中文自然语言查询文本（必填）
- `dataSource`：索引别名，优先级高于 NL 文本中的索引提示（可选）

**错误码：**

| 错误码 | 含义 | HTTP 状态 |
|--------|------|-----------|
| `[SEARCH_NL_001]` | NL 解析失败（文本为空或语法错误） | 400 |
| `[SEARCH_NL_002]` | 未指定索引（NL 文本和 dataSource 均未提供索引） | 400 |
| `[SEARCH_NL_003]` | NL 转 DSL 翻译失败 | 500 |

### NL 翻译器 Bug 修复

- **聚合名称绑定修复**：`generateAggName()` 现在通过 `FieldBinder` 将中文字段名映射为 ES 字段名，避免生成 `avg_年龄` 这类中文聚合名（如 `avg_age`）
- **AND 多条件翻译修复**：修复 nl-parser 将第一个叶子条件复用为逻辑节点时，父节点自身的 `field/op/value` 被丢弃的问题，现在 AND 多条件可正确生成完整的 bool must 查询

---

## 升级说明

**兼容性**：与 v1.6.0 完全兼容，无破坏性 API 变更。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.6.1'
}
```
