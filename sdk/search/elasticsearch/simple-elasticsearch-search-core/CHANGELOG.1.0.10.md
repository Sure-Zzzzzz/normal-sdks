# Changelog - v1.0.10

## 发布日期

2026-05-20

## 版本类型

**Patch Release** - Error Events 补齐 sourceType

---

## 变更内容

### 修改：EsQueryErrorEvent / EsAggErrorEvent 新增 sourceType

错误事件新增 `sourceType` 字段，与成功事件（`EsQueryEvent` / `EsAggEvent`）的 `ExecutionContext.sourceType` 对齐，方便监控按来源分类统计失败率。

| 字段 | 类型 | 说明 |
|------|------|------|
| `sourceType` | `String` | 请求来源类型（QUERY_API / NL_API / EXPRESSION_API），取自 request.sourceType |

> 构造函数签名不变，`sourceType` 从 `request.getSourceType()` 自动提取，无需调用方修改。

---

## 向后兼容性

✅ **完全向后兼容**

- 构造函数签名不变，新增字段在构造时自动从 request 提取
- 现有事件监听器代码零改动

---

## 升级指南

```gradle
implementation "io.github.surezzzzz:simple-elasticsearch-search-core:1.0.10"
```

## 贡献者

- @surezzzzzz
