# Changelog - v1.0.3

## 发布日期

2026-04-24

## 版本类型

**Patch Release** - 移除无效功能，精简代码，修复问题

## 变更概述

移除所有 `custom-*` 配置项和全部 Keywords 类，将时间范围关键字映射收归 `TimeRange` 枚举内部；重构 ANTLR 词法规则解决中文字段与中文运算符的冲突；所有枚举和异常类统一使用 Lombok `@Getter`；替换 JDK 原生异常为自定义异常；修复 EOF 暴露问题。

---

## 移除内容

### 1. 移除 4 个 `custom-*` 配置项

`ConditionExpressionParserProperties` 中以下配置项已移除：

| 配置项 | 说明 |
|--------|------|
| `custom-comparison-operators` | 自定义比较运算符映射 |
| `custom-logical-operators` | 自定义逻辑运算符映射 |
| `custom-time-ranges` | 自定义时间范围映射 |
| `custom-match-operators` | 自定义匹配运算符映射 |

**移除原因：** ANTLR 词法分析器在解析表达式时优先匹配语法文件中定义的 token，用户配置的自定义关键字无法被 ANTLR 识别，会在词法阶段报语法错误，Java 层的关键字映射永远不会被触发。

### 2. 移除 4 个 Keywords 类 + keyword 包

| 文件 | 说明 |
|------|------|
| `keyword/ComparisonKeywords.java` | 死代码，无任何引用 |
| `keyword/LogicalKeywords.java` | 死代码，无任何引用 |
| `keyword/MatchKeywords.java` | 死代码，无任何引用 |
| `keyword/TimeRangeKeywords.java` | 功能收归 `TimeRange` 枚举，此类不再需要 |

**移除原因：**

- 前三个类是死代码——ANTLR 已在词法/语法层完成了关键字识别，Java 层不需要重复做映射
- `TimeRangeKeywords` 与 `TimeRange` 枚举、g4 语法文件存在三重重复定义，现收归枚举内部

### 3. 移除运算符枚举中的冗余字段

| 枚举 | 移除字段 | 说明 |
|------|----------|------|
| `ComparisonOperator` | `symbol` | 与 g4 定义重复，AST 层只需要枚举值本身 |
| `LogicalOperator` | `code` | 与 g4 定义重复 |
| `MatchOperator` | `code` | 与 g4 定义重复 |

---

## 重构内容

### 4. `TimeRange` 枚举增强

将 `TimeRangeKeywords` 的关键字映射功能收归 `TimeRange` 枚举：

- 新增 `aliases` 构造参数：每个枚举值声明自己的别名
- 新增静态方法 `fromKeyword(String)`：关键字→枚举查找
- 新增静态方法 `isKeyword(String)`：判断是否为时间范围关键字
- 新增静态方法 `getAllKeywords()`：返回所有关键字→枚举的不可变映射
- 补充缺失的阿拉伯数字别名（如 `近3个月`）

### 5. ANTLR 词法规则重构：`CHINESE_FIELD` → `CJK_CHAR`

**问题：** 旧规则 `CHINESE_FIELD: [\u4E00-\u9FA5]+` 使用 `+` 贪婪匹配，导致 `年龄等于25` 被整体识别为一个字段名，中文比较运算符无法生效。

**解决：** 改为单字符规则 `CJK_CHAR: [\u4E00-\u9FA5]`，parser 层通过 `CJK_CHAR+` 聚合。ANTLR 最长匹配机制会优先匹配更长的运算符关键字（如 `等于` 2字符 > `CJK_CHAR` 1字符），从而正确拆分：

```
输入：年龄等于25
旧：CHINESE_FIELD(年龄等于) + NUMBER(25) → 语法错误
新：CJK_CHAR(年) + CJK_CHAR(龄) + EQ(等于) + NUMBER(25) → 正确解析
```

### 6. `AstBuilder` 重构：Context-based 运算符识别

`parseComparisonOperator()` 从文本 switch 重构为 ANTLR Context 类型判断，避免字符串硬编码：

```java
// 旧：switch (ctx.getText()) case "=": ...
// 新：if (ctx.EQ() != null) return ComparisonOperator.EQ;
```

### 7. Lombok `@Getter` 统一

所有枚举和异常类统一使用 `@Getter`，移除手写 getter：

| 类 | 改动 |
|----|------|
| `ComparisonOperator` | 新增 `@Getter`，删除手写 `getSymbol()` |
| `LogicalOperator` | 新增 `@Getter`，删除手写 `getCode()` |
| `MatchOperator` | 新增 `@Getter`，删除手写 `getCode()` |
| `UnaryOperator` | 新增 `@Getter` |
| `ValueType` | 新增 `@Getter`，删除手写 `getDescription()` |
| `ConditionExpressionParseException` | 新增 `@Getter`，删除 6 个手写 getter |
| `ConditionExpressionParseException.ErrorType` | 新增 `@Getter` |
| `ExpressionValidationException` | 新增 `@Getter`，删除 3 个手写 getter |
| `ExpressionValidationException.MetricType` | 新增 `@Getter` |

### 8. 替换 JDK 原生异常

| 文件 | 旧 | 新 |
|------|----|----|
| `AstBuilder` | `IllegalArgumentException` | `ConditionExpressionParseException` |
| `ValueParser` | `IllegalStateException` | `ConditionExpressionParseException` |

### 9. 修复 EOF 暴露问题

`ConditionExpressionParser.CustomErrorListener` 不再将 `<EOF>` 作为 offendingToken 暴露给用户，改为友好的错误提示：

| 场景 | 旧消息 | 新消息 |
|------|--------|--------|
| 表达式不完整（如 `年龄>`） | `语法错误：不期望的输入 "<EOF>"` | `语法错误：表达式不完整` |

### 10. 性能优化

`NumberValueParseStrategy` 将正则预编译为 `static final Pattern`，避免每次调用时重新编译。

---

## 改动范围汇总

**删除文件**

| 文件 | 说明 |
|------|------|
| `keyword/ComparisonKeywords.java` | 死代码 |
| `keyword/LogicalKeywords.java` | 死代码 |
| `keyword/MatchKeywords.java` | 死代码 |
| `keyword/TimeRangeKeywords.java` | 功能收归 TimeRange 枚举 |

**修改文件**

| 文件 | 改动 |
|------|------|
| `antlr/ConditionExpr.g4` | `CHINESE_FIELD` → `CJK_CHAR`；补充 `近3个月` |
| `configuration/ConditionExpressionParserProperties.java` | 移除 4 个 custom 配置项，仅保留 `enabled` |
| `constant/ComparisonOperator.java` | `@Getter`，删除 `symbol` 字段 |
| `constant/LogicalOperator.java` | `@Getter`，删除 `code` 字段 |
| `constant/MatchOperator.java` | `@Getter`，删除 `code` 字段 |
| `constant/UnaryOperator.java` | `@Getter` |
| `constant/ValueType.java` | `@Getter`，删除手写 getter |
| `constant/TimeRange.java` | `aliases`/`fromKeyword()`/`isKeyword()`/`getAllKeywords()`，补充 `近3个月` |
| `parser/AstBuilder.java` | Context-based 运算符识别，替换 JDK 异常 |
| `parser/ValueParser.java` | 替换 JDK 异常 |
| `parser/NumberValueParseStrategy.java` | 正则预编译 |
| `parser/TimeRangeValueParseStrategy.java` | 改用 `TimeRange.fromKeyword()` |
| `parser/ConditionExpressionParser.java` | EOF 过滤，友好错误消息 |
| `configuration/ConditionExpressionParserAutoConfiguration.java` | 移除 custom 日志 |
| `exception/ConditionExpressionParseException.java` | `@Getter`，删除手写 getter |
| `exception/ExpressionValidationException.java` | `@Getter`，删除手写 getter |
| `model/LikeExpression.java` | 规范 import |
| `version.properties` | 1.0.2 → 1.0.3 |

---

## 向后兼容性

### 配置不兼容（影响极小）

- 如果用户配置了 `custom-*` 项，升级后 Spring Boot 启动时会报 `Unknown configuration properties` 警告，但不影响功能（这些配置本身就不生效）
- 建议升级后移除这些配置项

### API 不兼容（影响极小）

| 旧 API | 新 API | 说明 |
|--------|--------|------|
| `ComparisonOperator.getSymbol()` | 已移除 | 枚举值本身就是类型标识，不需要 symbol |
| `LogicalOperator.getCode()` | 已移除 | 同上 |
| `MatchOperator.getCode()` | 已移除 | 同上 |
| `TimeRangeKeywords.fromKeyword()` | `TimeRange.fromKeyword()` | 功能一致 |
| `TimeRangeKeywords.isKeyword()` | `TimeRange.isKeyword()` | 功能一致 |
| `TimeRangeKeywords.getAllKeywords()` | `TimeRange.getAllKeywords()` | 功能一致 |

### 行为改进

- `年龄等于25` 等中文运算符表达式现在可以正确解析（旧版需要空格分隔）
- 错误消息不再暴露 `<EOF>` 内部 token

---

## 升级指南

```gradle
implementation "io.github.surezzzzzz:condition-expression-parser-starter:1.0.3"
```

1. 移除配置文件中的 `custom-*` 配置项
2. 如业务代码引用了 `TimeRangeKeywords`，替换为 `TimeRange` 的对应方法
3. 如业务代码使用了 `getSymbol()`/`getCode()`，改为 `getDescription()`

## 贡献者

- @surezzzzzz
