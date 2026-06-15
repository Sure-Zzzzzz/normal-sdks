# Changelog - v1.0.4

## 发布日期

2026-06-15

## 版本类型

**Minor Release** - 新增前缀/后缀不匹配运算符，补齐 LIKE 操作符体系

## 变更概述

新增 `NOT PREFIX LIKE`（前缀不匹配）和 `NOT SUFFIX LIKE`（后缀不匹配）两个语法规则，补齐操作符体系短板。同时配合 simple-elasticsearch-search-starter 1.6.7 的需求，为 search-starter 提供 expression parser 层面的完整支持。

---

## 新增内容

### 1. `NOT PREFIX LIKE` - 前缀不匹配

排除以指定前缀开头的字段值。语法：

```
名称 NOT PREFIX LIKE '张'
名称 非 前缀 包含 '张'
```

等价于 ES `boolQuery().mustNot(prefixQuery(field, value))`。

### 2. `NOT SUFFIX LIKE` - 后缀不匹配

排除以指定后缀结尾的字段值。语法：

```
邮箱 NOT SUFFIX LIKE '@spam.com'
邮箱 非 后缀 包含 '@spam.com'
```

等价于 ES `boolQuery().mustNot(wildcardQuery(field, "*" + value))`。

---

## 改动范围

### 新增文件

| 文件 | 说明 |
|------|------|
| `CHANGELOG.1.0.4.md` | 本版本变更文档 |

### 修改文件

| 文件 | 改动 |
|------|------|
| `constant/MatchOperator.java` | 新增 `NOT_PREFIX("前缀不匹配")` 和 `NOT_SUFFIX("后缀不匹配")` 两个枚举值 |
| `antlr/ConditionExpr.g4` | condition 规则新增 `NotPrefixLikeCondition` 和 `NotSuffixLikeCondition` 两个产生式；注意 NOT 前缀规则写在正向规则之前，避免 ANTLR 预测冲突 |
| `parser/AstBuilder.java` | 新增 `visitNotPrefixLikeCondition()` 和 `visitNotSuffixLikeCondition()` 两个 visit 方法 |
| `version.properties` | 1.0.3 → 1.0.4 |
| `README.md` | 同步更新版本号和支持的运算符列表 |
| `.../cases/ConditionExprParserEndToEndTest.java` | 新增 5 个测试用例（Order 60~65）：英文语法、中文 NOT、驼峰/全小写/全大写、语法冲突检测、复杂表达式组合 |

---

## 语法说明

### ANTLR 规则顺序

`condition` 规则中，**带 NOT 前缀的产生式必须写在正向产生式之前**：

```antlr
condition
    : field NOT PREFIX LIKE value            # NotPrefixLikeCondition  ←写在前面
    | field PREFIX LIKE value                # PrefixLikeCondition
    | field NOT SUFFIX LIKE value            # NotSuffixLikeCondition  ←写在前面
    | field SUFFIX LIKE value                # SuffixLikeCondition
    | field NOT LIKE value                   # NotLikeCondition
    | field LIKE value                       # LikeCondition
    ...
    ;
```

原因：ANTLR LL(*) 预测算法中，如果正向规则在前，`field NOT PREFIX LIKE value` 会被 `field PREFIX LIKE value` 错误吞掉 NOT 前缀。

### 中文 NOT 支持

中文 `非` 等价于英文 `NOT`：

```
名称 非 前缀 包含 '张'    → NOT_PREFIX
名称 非 后缀 包含 '@x'  → NOT_SUFFIX
```

### 大小写不敏感

`NOT` / `not` / `Not` 均可；`PREFIX` / `prefix` / `Prefix` 均可。

---

## 向后兼容性

**完全向后兼容。** 此次变更仅新增枚举值和语法规则，无任何破坏性变更。

## 升级指南

```gradle
implementation 'io.github.sure-zzzzzz:condition-expression-parser-starter:1.0.4'
```

## 贡献者

- @surezzzzzz