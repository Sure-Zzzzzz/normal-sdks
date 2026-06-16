# Changelog - v1.0.5

## 发布日期

2026-06-16

## 版本类型

**Minor Release** - 新增 EXISTS/NOT EXISTS 存在性检查操作符

---

## 新增内容

### 存在性检查操作符

`EXISTS` / `NOT EXISTS` 用于判断字段在 ES 索引中是否存在（与 `IS NULL` / `IS NOT NULL` 不同，后者判断字段值为 null）。

| 关键字 | 说明 | 示例 |
|--------|------|------|
| `EXISTS` | 字段存在 | `备注 EXISTS`、`备注 存在`、`备注 有` |
| `NOT EXISTS` | 字段不存在 | `备注 NOT EXISTS` |

中文关键字 `'存在'` / `'有'` 与 NL parser 保持一致。

---

## 改动范围

### 修改文件

| 文件 | 改动 |
|------|------|
| `constant/MatchOperator.java` | 新增 `EXISTS("字段存在")` 和 `NOT_EXISTS("字段不存在")` |
| `antlr/ConditionExpr.g4` | `condition` 规则新增 `ExistsCondition` 和 `NotExistsCondition` 产生式（`NOT EXISTS` 写在 `EXISTS` 之前）；词法新增 `EXISTS` token |
| `parser/AstBuilder.java` | 新增 `visitExistsCondition()` 和 `visitNotExistsCondition()` |
| `version.properties` | 1.0.4 → 1.0.5 |
| `README.md` | 新增"存在性检查"章节，版本号更新 |

### 新增文件

| 文件 | 说明 |
|------|------|
| `CHANGELOG.1.0.5.md` | 本版本变更文档 |

### 新增测试

| 测试 | 说明 |
|------|------|
| `testExistsOperator` | 验证 `EXISTS` 解析为 `MatchOperator.EXISTS` |
| `testNotExistsOperator` | 验证 `NOT EXISTS` 解析为 `MatchOperator.NOT_EXISTS` |

---

## 向后兼容性

**完全向后兼容。** 此次变更仅新增语法规则，无任何破坏性变更。

---

## 升级指南

```gradle
implementation 'io.github.sure-zzzzzz:condition-expression-parser-starter:1.0.5'
```

---

## 贡献者

- @surezzzzzz