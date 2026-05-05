# v1.1.0 更新日志

**发布日期：** 2026-05-04

**类型：** 重大重构版本（全量重写）

---

## 💥 破坏性变更

本版本为全量重构，API 与 v1.0.x 不兼容。

| 变更点 | v1.0.x | v1.1.0 |
|--------|--------|--------|
| `PaginationIntent.getLimit()` | 返回 `Integer` | 已移除，改为 `getSize()` |
| `PaginationIntent.getContinueSearch()` | 返回 `boolean` | 已移除，改为 `getSearchAfterMode()` |
| `AggregationIntent.getName()` | 返回 `String` | 已移除，改为 `getNameHint()` |
| `AggregationIntent.getChildren()` | 返回子聚合列表 | 已移除，改为 `getSubAggs()` |
| `ConditionIntent.getFieldHint()` | 逻辑条件时返回 `logic.name()` | 现在始终返回原始字段名 |
| `NLParserAutoConfiguration` | 无 | 已注册到 `spring.factories` |

---

## ✨ 新功能

### 1. 可插拔解析器架构（NLParserPlugin）

新增 `NLParserPlugin` 接口，所有解析器统一实现此接口，通过 `@Order` 控制执行顺序。使用方可注册自定义 Parser（如 GeoParser、PermissionParser）。

```java
@NaturalLanguageParserComponent
@Order(50)
public class MyCustomParser implements NLParserPlugin {
    @Override
    public boolean supports(IntentType intentType) { return true; }

    @Override
    public void parse(List<Token> tokens, KeywordRegistry registry,
                      NLParserProperties properties, ParseResult result) {
        // 写入 result.setExt(...)
    }
}
```

### 2. 可替换分词器（NLTokenizer 接口化）

新增 `NLTokenizer` 接口，默认实现为 `DefaultNLTokenizer`（基于 HanLP）。使用方注册自己的 `NLTokenizer` Bean 即可替换。

```java
@Bean
public NLTokenizer nlTokenizer() {
    return new IkAnalyzerTokenizer(); // 接入 IK 分词
}
```

### 3. 三层关键字扩展体系（KeywordRegistry）

关键字支持三层扩展，优先级从高到低：Bean（`KeywordContributor`）> YAML 配置 > 内置默认值。

```yaml
io.github.surezzzzzz.sdk.natural-language.parser:
  keywords:
    operators:
      eq:
        - 匹配
        - 对应
    stop-words:
      - 帮我
```

```java
@Component
@Order(100)
public class BusinessKeywordContributor implements KeywordContributor {
    @Override
    public void contribute(KeywordRegistry registry) {
        registry.addOperatorKeywords(OperatorType.EQ, "匹配", "对应");
    }
}
```

### 4. 字段折叠（CollapseIntent + CollapseParser）

新增字段折叠（去重）解析，支持多种自然语言模式：

```
按源IP去重 / 源IP去重 / 去重源IP / distinct source_ip
```

### 5. 完整聚合类型支持

`AggType` 枚举补齐至 22 种，新增：`PERCENTILES`、`PERCENTILE_RANKS`、`FILTER`、`FILTERS`、`MISSING`、`IP_RANGE`、`DATE_RANGE`、`BUCKET_SORT`、`BUCKET_SELECTOR`。

新增 `PipelineAggIntent` 和 `AggRangeHint` 模型。

### 6. FieldBinder / IntentTranslator 扩展接口

新增两个 SPI 接口，供使用方接入自己的存储系统：

- `FieldBinder`：将自然语言字段提示绑定到实际字段名
- `IntentTranslator<T>`：将 Intent 转换为特定存储系统的查询对象

### 7. 完整错误码体系

新增 `ErrorCode`、`ErrorMessage` 常量类，`NLParseException` 支持 Builder 模式，携带查询文本、错误位置、修复建议等上下文。

---

## 🔧 解析能力增强

### 操作符识别修复

修复 HanLP 将 `不等于`、`不包含`、`不为空` 等拆分为 `不` + `等于` 导致识别失败的问题。分词器新增跨类型 token 合并：

- `LOGIC(NOT)` + `OPERATOR` → `OPERATOR`（`不` + `等于` → `不等于`）
- `UNKNOWN` + `OPERATOR` → `OPERATOR`（`开头` + `是` → `开头是`）
- `COLLAPSE` + `AGGREGATION` → `AGGREGATION`（`去重` + `计数` → `去重计数`）

### IN 操作符自然语言支持

修复 `城市在北京、上海、深圳` 无法解析为 IN 条件的问题。`ConditionParser` 新增 `isInOperatorContext` 检测，识别 `在...值,值` 模式。

### 英文索引名保护

修复 `user_behavior` 被 HanLP 拆分为 `user_behavi` + `or`，导致索引名截断的问题。`IndexExtractorPlugin` 新增 ASCII 标识符片段保护，`LogicKeywordSplitStrategy` 新增词边界检测，不再拆分嵌入在标识符中的逻辑关键词。

### 聚合解析增强

- 修复 `统计平均年龄` 被识别为 COUNT 而非 AVG 的问题（`统计` 后跟更具体指标时跳过 COUNT）
- 修复排序字段跨折叠边界收集的问题（`isBoundaryToken` 新增 COLLAPSE/SORT/PAGINATION 类型）

---

## ♻️ 重构与代码质量

### DefaultKeywordRegistry 重构

- 11 个 `add*Keywords` 方法收敛为 `addToMap` / `addToSet` 两个泛型辅助方法
- 11 个 `remove*Keywords` 方法收敛为 `removeFromMap` / `removeFromSet`
- 所有 `resolve*` 和 `is*Keyword` 查询方法从 O(n×m) 嵌套循环改为 O(1)，直接查 `keywordCategory` 反向索引

### 新增 TokenHelper（support 包）

提取 `TokenHelper`，消除 `SortParser`、`CollapseParser`、`IndexExtractorPlugin`、`DateRangeParser` 中的重复代码：

- `isBoundaryToken(Token)` — 原来在 SortParser 和 CollapseParser 各写一遍
- `removeByIndices(List<Token>, List<Integer>)` — 原来在 IndexExtractorPlugin 和 DateRangeParser 各写一遍

### PaginationParser 清理

6 个 `is*Keyword` 方法提取公共逻辑 `isKeywordInSet(String, Set<String>)`。

### ConditionParser 修复

- 修复 `isClauseBoundary` 双重载导致 Java 将 `null` 解析到 `KeywordRegistry` 重载引发 NPE 的问题，合并为单一方法
- EXPECT_FIELD 的 look-ahead 新增 IN 操作符上下文检测，避免 `城市在北京` 整体被当作字段名

---

## 🧪 测试

### 新增 SearchIntentTranslatorTest（集成测试）

验证 nl-parser 解析出的 Intent 能被正确翻译为 search-starter 的 `QueryRequest` / `AggRequest`，同时作为 `FieldBinder` + `IntentTranslator` 接入的最佳实践示例。

覆盖场景：
- 18 种操作符（eq/ne/gt/gte/lt/lte/in/between/like/not_like/prefix/suffix/exists/not_exists/is_null/is_not_null）
- 3 种分页策略（offset page+size / offset+size / search_after）
- AND/OR 逻辑组合
- 日期范围（时间范围关键词 + 具体日期）
- 聚合类型（sum/min/max/cardinality/terms+nested/date_histogram 多间隔/多并行聚合）
- FieldBinder 字段绑定（从 application.yml 的 field-mapping 配置读取，不硬编码）
- indexHint 优先级（TranslateContext.dataSource > Intent.indexHint）

### 测试配置驱动

`SearchIntentTranslatorTest` 的字段映射从 `application.yml` 读取，格式与 search-starter 的 `field-mapping` 配置完全一致，不再硬编码。

---

## 📦 依赖变更

```gradle
// 新增测试依赖（使用 search-core 模型类验证 Intent → QueryRequest/AggRequest 翻译）
testImplementation("io.github.sure-zzzzzz:simple-elasticsearch-search-core:1.0.8")
```

---

## 🔄 升级说明

**兼容性：** 与 v1.0.x 不兼容，需按以下方式迁移。

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:natural-language-parser-starter:1.1.0'
}
```

**API 迁移：**

```java
// PaginationIntent
intent.getPagination().getLimit()          → .getSize()
intent.getPagination().getContinueSearch() → .getSearchAfterMode() == SearchAfterMode.TIEBREAKER

// AggregationIntent
agg.getName()     → agg.getNameHint()
agg.getChildren() → agg.getSubAggs()

// ConditionIntent（逻辑条件时）
condition.getFieldHint() // 现在始终返回原始字段名，不再返回 logic.name()
```
