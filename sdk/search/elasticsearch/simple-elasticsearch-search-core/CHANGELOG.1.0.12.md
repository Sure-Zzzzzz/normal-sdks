# Changelog - v1.0.12

## 发布日期

2026-06-15

## 版本类型

**Minor Release** - 新增前缀/后缀/正则不匹配操作符，补齐操作符体系

## 变更内容

### 新增：QueryOperator 枚举新增三个操作符

| 枚举值 | 操作符字符串 | 说明 |
|--------|-------------|------|
| `NOT_PREFIX` | `not_prefix` | 前缀不匹配 |
| `NOT_SUFFIX` | `not_suffix` | 后缀不匹配 |
| `NOT_REGEX` | `not_regex` | 正则不匹配 |

三个新操作符均需要值、不需要多值，`needsValue()` 和 `needsMultipleValues()` 无需修改。

---

## 向后兼容性

✅ **完全向后兼容**

此次变更仅新增枚举值，无任何破坏性变更。

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-core:1.0.12"
```

配合 starter `1.6.7` 使用。

## 关联模块

| 模块 | 版本 | 变更 |
|------|------|------|
| simple-elasticsearch-search-starter | 1.6.7 | 新增 NotPrefix/NotSuffix/NotRegex 策略 + negateOp 修复 |
| natural-language-parser-starter | 1.1.4 | OperatorType 新增 NOT_PREFIX/NOT_SUFFIX/NOT_REGEX + 关键字 |
| condition-expression-parser-starter | 1.0.4 | MatchOperator 新增 NOT_PREFIX/NOT_SUFFIX + ANTLR 语法 |

## 贡献者

- @surezzzzzz