# v1.6.7 更新日志

**发布日期：** 2026-06-17

**类型：** Minor Release - 新增前缀/后缀/正则不匹配操作符 + EXISTS/NOT EXISTS 表达式语法 + negateOp 修复

**依赖升级：**

| 依赖 | 旧版本 | 新版本 |
|------|--------|--------|
| `simple-elasticsearch-search-core` | 1.0.11 | 1.0.12 |
| `natural-language-parser-starter` | 1.1.3 | 1.1.4 |
| `condition-expression-parser-starter` | 1.0.4 | 1.0.5 |

---

## Feature

### 1. 新增 NOT_PREFIX / NOT_SUFFIX / NOT_REGEX 操作符策略

**背景**：SDK 此前有 `prefix` / `suffix` / `regex` 正向操作符，但缺少对应的取反操作符。用户需要"前缀不匹配""后缀不匹配""正则不匹配"时，只能通过 `NOT (field PREFIX LIKE "xxx")` 在表达式层面实现，或手动构造 `bool must_not` DSL。

**解决方案**：在 search-core 新增三个 `QueryOperator` 枚举值，在 search-starter 新增三个 `OperatorStrategy` 实现类，均通过 `boolQuery().mustNot(正向策略)` 包装实现取反。

**新增枚举（`QueryOperator`，search-core 1.0.12）：**
- `NOT_PREFIX("not_prefix", "前缀不匹配")`
- `NOT_SUFFIX("not_suffix", "后缀不匹配")`
- `NOT_REGEX("not_regex", "正则不匹配")`

**新增策略类（search-starter）：**
- `NotPrefixOperatorStrategy`：注入 `PrefixOperatorStrategy`，`boolQuery().mustNot(prefixQuery(...))`
- `NotSuffixOperatorStrategy`：注入 `SuffixOperatorStrategy`，`boolQuery().mustNot(wildcardQuery(field, "*" + value))`
- `NotRegexOperatorStrategy`：注入 `RegexOperatorStrategy`，`boolQuery().mustNot(regexpQuery(...))`

**变更文件：**
- `OperatorStrategyRegistry`：新增 3 个策略字段 + 3 行注册代码，内置策略数从 18 增至 21

> `NOT_REGEX` 仅通过 JSON API（`/api/query`）和 NL parser 可用，表达式语法暂不支持 REGEX 关键字。

---

### 2. NL parser 新增 NOT_PREFIX / NOT_SUFFIX 关键字

**新增枚举（`OperatorType`，NL parser 1.1.4）：**
- `NOT_PREFIX("not_prefix", "前缀不匹配")`
- `NOT_SUFFIX("not_suffix", "后缀不匹配")`

**新增关键字映射（`DefaultKeywordRegistry`）：**
- `NOT_PREFIX`：`开头不是`、`前缀不匹配`、`not_prefix`
- `NOT_SUFFIX`：`结尾不是`、`后缀不匹配`、`not_suffix`

> `NOT_REGEX` 不在 NL parser 中支持：HanLP 会将"正则不匹配"/"不匹配正则"整体切为一个 token，无法触发关键字，且目前无续写正则的需求。

---

### 3. 表达式语法新增 EXISTS / NOT EXISTS

**背景**：表达式语法此前有 `IS NULL` / `IS NOT NULL`（判断字段值为 null），但缺少 `EXISTS` / `NOT EXISTS`（判断字段在 ES 索引中是否存在）。两者语义不同：一个字段可以"存在但值为 null"。

**解决方案**：在 expression parser 1.0.5 的 ANTLR 语法中新增 `EXISTS` token 和两条产生式，在 `AstBuilder` 中新增两个 visit 方法。

**语法变更（`ConditionExpr.g4`）：**
- 新增词法规则：`EXISTS : E X I S T S | '存在' | '有' ;`
- 新增产生式：`field NOT EXISTS # NotExistsCondition`、`field EXISTS # ExistsCondition`
- `NOT EXISTS` 写在 `EXISTS` 之前，确保 ANTLR 优先匹配更长的前缀

**新增方法（`AstBuilder`）：**
- `visitExistsCondition()`：构建 `LikeExpression(operator=EXISTS, value=null)`
- `visitNotExistsCondition()`：构建 `LikeExpression(operator=NOT_EXISTS, value=null)`

**新增枚举（`MatchOperator`）：**
- `EXISTS("字段存在")`
- `NOT_EXISTS("字段不存在")`

**表达式语法示例：**

| 表达式 | 中文等价 | 生成的 op |
|--------|---------|----------|
| `remark EXISTS` | `备注 存在` | `exists` |
| `remark NOT EXISTS` | — | `not_exists` |

> 与 `IS NULL` / `IS NOT NULL` 的区别：`EXISTS` 检查字段是否在 ES 索引 mapping 中存在（`existsQuery`），`IS NULL` 检查字段值是否为 null。

---

### 4. 表达式 NOT PREFIX LIKE / NOT SUFFIX LIKE 翻译打通

**背景**：表达式语法此前已有 `field NOT PREFIX LIKE value` 和 `field NOT SUFFIX LIKE value`（g4 语法），但 `ExpressionToQueryConditionVisitor.matchOp()` 缺少 `NOT_PREFIX` / `NOT_SUFFIX` 的 case，导致这两种表达式无法翻译为正确的 `QueryCondition.op`。

**修复**：`matchOp()` 新增 4 个 case：

| MatchOperator | op 字符串 | 对应表达式语法 |
|---------------|----------|--------------|
| `NOT_PREFIX` | `not_prefix` | `field NOT PREFIX LIKE value` |
| `NOT_SUFFIX` | `not_suffix` | `field NOT SUFFIX LIKE value` |
| `EXISTS` | `exists` | `field EXISTS` |
| `NOT_EXISTS` | `not_exists` | `field NOT EXISTS` |

> 此前 `NOT_PREFIX LIKE` / `NOT SUFFIX LIKE` 写在表达式中会走 `matchOp()` 的 `default` 分支返回 `like`，查询语义错误。

---

## Bug Fix

### 5. negateOp() 缺少 exists / not_exists / prefix / suffix 映射

**问题**：`ExpressionToQueryConditionVisitor.negateOp()` 此前没有 `exists` / `not_exists` 的映射，`NOT (field EXISTS)` 走 `default` 分支返回 `exists`，NOT 不生效。同时 `prefix` / `suffix` 也缺少单向取反映射。

**修复**：新增 6 个映射：

| 操作符 | 取反结果 | 映射类型 | 说明 |
|--------|----------|---------|------|
| `prefix` | `not_prefix` | 单向 | 有 AST 专用语法 `field NOT PREFIX LIKE value` |
| `suffix` | `not_suffix` | 单向 | 有 AST 专用语法 `field NOT SUFFIX LIKE value` |
| `exists` | `not_exists` | 双向 | 无 AST 专用语法，必须通过 `NOT (field EXISTS)` 处理 |
| `not_exists` | `exists` | 双向 | 同上 |
| `is_null` | `is_not_null` | 双向 | 已有，无变更 |
| `is_not_null` | `is_null` | 双向 | 已有，无变更 |

> `like` → `not_like` 已有单向映射，无需变更。`regex` / `not_regex` 不在表达式语法中，不涉及。

---

## 新增测试

### SearchEndToEndTest（6 个端到端测试）

| 测试 | Order | 说明 |
|------|-------|------|
| `testExpressionNotPrefixLike` | 210 | `NOT PREFIX LIKE 'i'` 排除 iPhone 15/iPad Air，返回 3 条 |
| `testExpressionNotSuffixLike` | 211 | `NOT SUFFIX LIKE 'Pro'` 排除 MacBook Pro/AirPods Pro，返回 3 条 |
| `testExpressionNotPrefixLikeNegate` | 213 | `NOT (PREFIX LIKE 'i')` 等价于 `NOT PREFIX LIKE`，返回 3 条 |
| `testExpressionExists` | 214 | `orderId EXISTS` 返回 4 条（所有文档均有该字段） |
| `testExpressionNotExists` | 215 | `orderId NOT EXISTS` 返回 0 条 |
| `testExpressionNotExistsNegate` | 216 | `NOT (orderId EXISTS)` 等价于 `NOT EXISTS`，返回 0 条 |

### ExpressionTest（4 个 translate 测试）

| 测试 | 说明 |
|------|------|
| `testTranslateExists` | `备注 EXISTS` → `op=exists` |
| `testTranslateNotExists` | `备注 NOT EXISTS` → `op=not_exists` |
| `testNegateExists` | `NOT (备注 EXISTS)` → `op=not_exists`（修复 bug 验证） |
| `testNegateNotExists` | `NOT (备注 NOT EXISTS)` → `op=exists`（修复 bug 验证） |

### OperatorStrategyRegistryTest

- 策略数断言从 18 更新为 21
- 新增 `testResolveNotPrefix` / `testResolveNotSuffix` / `testResolveNotRegex` 三个策略解析测试

### ConditionExprParserEndToEndTest（expression parser 1.0.5）

- `testExistsOperator`：验证 `EXISTS` 解析为 `MatchOperator.EXISTS`
- `testNotExistsOperator`：验证 `NOT EXISTS` 解析为 `MatchOperator.NOT_EXISTS`

---

## 文档更新

- `README.md`：重写表达式语法表（新增 PREFIX/SUFFIX LIKE、EXISTS/NOT EXISTS、中文关键字、值类型），修正错误的 BETWEEN 文档，新增表达式最佳实践小节，操作符表区分 expression vs JSON API 支持范围

---

## 向后兼容性

✅ **完全向后兼容**

此次变更仅新增操作符策略、表达式语法规则和修复 negateOp bug，无任何破坏性变更。现有调用无需修改。

---

## 升级指南

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.6.7'
}
```

依赖的 `simple-elasticsearch-search-core:1.0.12`、`natural-language-parser-starter:1.1.4`、`condition-expression-parser-starter:1.0.5` 均通过 starter 自动传递，无需单独声明。
