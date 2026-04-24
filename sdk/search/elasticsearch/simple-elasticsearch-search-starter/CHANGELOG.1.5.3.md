# Changelog - v1.5.3

## 发布日期

2026-04-24

## 版本类型

**Minor Release** - 新增表达式提示 API，field-mapping 配置格式调整，升级 condition-expression-parser 依赖

## 变更概述

新增 `GET /expression/hints` API，供前端获取表达式自动补全所需的字段、运算符、时间范围、值规则等提示信息。

`field-mapping` 配置格式调整：key 由中文标签改为 ES 字段名，value 为中文标签列表，支持多个中文别名映射到同一字段。

同步升级 `condition-expression-parser-starter` 至 v1.0.3。

依赖升级：
- `condition-expression-parser-starter` 1.0.2 → 1.0.3

---

## 新增功能

### 1. 表达式提示 API

```
GET /api/expression/hints?index={alias}
```

`index` 参数可选，不传时仅返回全局提示（运算符、时间范围、值规则），传入时额外返回该索引的字段列表。

**响应结构：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `fields` | `List<FieldHint>` | 字段列表，来自索引 `field-mapping` 配置 |
| `operators` | `List<OperatorHint>` | 运算符列表（含中文写法） |
| `timeRanges` | `List<String>` | 时间范围主关键字列表 |
| `valueRules` | `ValueRules` | 值规则（引号要求、布尔关键字等） |

**FieldHint：**

| 字段 | 说明 |
|------|------|
| `name` | ES 字段名 |
| `label` | 中文标签列表（来自 `field-mapping` 配置，第一个为主标签） |

**OperatorHint：**

| 字段 | 说明 |
|------|------|
| `op` | 运算符符号 |
| `description` | 中文描述 |
| `chinese` | 中文别名 |

**ValueRules：**

| 字段 | 说明 |
|------|------|
| `stringNeedsQuote` | 字符串值是否必须加引号（true） |
| `supportedQuotes` | 支持的引号类型（`'` 和 `"`） |
| `booleanKeywords` | 布尔值关键字（true/false/真/假） |
| `numberNoQuote` | 数字是否不需要引号（true） |

**设计决策：**

- `fields` 直接从 `field-mapping` 配置构建，不依赖 ES 连接；只有配置了映射的字段才参与表达式查询
- `operators` 和 `valueRules` 为静态数据，由 g4 语法定义决定，不随配置变化
- `timeRanges` 只返回主关键字（`TimeRange.getKeyword()`），不返回全部别名

---

## 配置变更

### field-mapping 格式调整（Breaking Change）

**旧格式（v1.5.2）：** key 为中文标签，value 为 ES 字段名

```yaml
field-mapping:
  "[订单状态]": status
  "[金额]": amount
```

**新格式（v1.5.3）：** key 为 ES 字段名，value 为中文标签列表

```yaml
field-mapping:
  status:
    - 订单状态
    - 状态
  amount:
    - 金额
```

**变更原因：**
- 支持多个中文别名映射到同一 ES 字段（如"状态"和"订单状态"都能查 `status`）
- key 为 ES 字段名，语义更清晰，也便于 `getHints` 直接构建字段列表

**启动校验新增：**
- 同一索引内中文标签不允许重复（会导致表达式翻译歧义，启动时报错）
- `sensitive-fields` 不允许重复配置同一字段
- `strategy=FORBIDDEN` 的字段不允许同时出现在 `field-mapping` 中（防止表达式查询绕过敏感字段保护）

---

## 依赖升级

### condition-expression-parser-starter 1.0.2 → 1.0.3

v1.0.3 主要变更：

- 移除 `custom-*` 配置项（ANTLR 词法阶段无法识别 Java 层配置的关键字）
- 删除 4 个 Keywords 类，关键字映射收归 `TimeRange` 枚举
- 移除 `ComparisonOperator.symbol`、`LogicalOperator.code`、`MatchOperator.code`
- `CHINESE_FIELD` → `CJK_CHAR` 词法重构，解决中文运算符无法识别的问题
- 修复 `近3个月` 关键字缺失 bug
- Lombok `@Getter` 统一、JDK 异常替换、EOF 暴露修复

**search-starter 影响评估：**

- 未直接引用 `TimeRangeKeywords`、`getSymbol()`、`getCode()`，无需代码适配
- `ExpressionToQueryConditionVisitor` 对 `TimeRange` 的使用方式不变
- 升级后 `时间='近3个月'` 可正确解析为 `TimeRange.LAST_3_MONTHS`

---

## 改动范围汇总

**新增文件**

| 文件 | 说明 |
|------|------|
| `endpoint/response/ExpressionHintsResponse.java` | 提示信息响应 DTO（含 FieldHint、OperatorHint、ValueRules 内部类） |

**修改文件**

| 文件 | 改动 |
|------|------|
| `build.gradle` | condition-expression-parser-starter 1.0.2 → 1.0.3 |
| `version.properties` | 1.5.2 → 1.5.3 |
| `endpoint/SimpleElasticsearchSearchApiEndpoint.java` | 新增 `expressionHints` 端点（`index` 参数可选） |
| `expression/service/ExpressionService.java` | 新增 `getHints()` 方法，fields 直接从 field-mapping 构建 |
| `expression/visitor/ExpressionVisitorRegistry.java` | 启动时预建 `labelMapRegistry`，新增 `resolveLabelMap()` |
| `configuration/SimpleElasticsearchSearchProperties.java` | `IndexConfig.fieldMapping` 类型改为 `Map<String, List<String>>`，新增启动校验 |

---

## 向后兼容性

⚠️ **field-mapping 配置格式不兼容**

`field-mapping` 的 key/value 含义互换，升级前需按新格式重写配置（见上方配置变更说明）。

其余变更完全向后兼容：
- 所有已有 API 行为不变
- 依赖升级无破坏性
- 新增端点为独立接口

---

## 升级指南

```gradle
implementation "io.github.sure-zzzzzz:simple-elasticsearch-search-starter:1.5.3"
```

1. 按新格式重写 `field-mapping` 配置（key 改为 ES 字段名，value 改为中文标签列表）
2. 如之前配置了 `condition-expression-parser` 的 `custom-*` 项，请从配置中移除

## 贡献者

- @surezzzzzz
