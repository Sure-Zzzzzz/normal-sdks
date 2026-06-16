# Natural Language Parser Starter

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8+-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.x+-brightgreen.svg)](https://spring.io/projects/spring-boot)

> 一个基于 HanLP 的中文自然语言查询解析器，可将自然语言查询转换为结构化的 Intent 对象，支持条件、聚合、排序、分页、折叠等多种查询场景。

## 特性

- **可插拔解析器架构** — `NLParserPlugin` 接口 + `@Order` 控制执行顺序，可注册自定义 Parser
- **可替换分词器** — `NLTokenizer` 接口化，默认 HanLP，注册自己的 Bean 即可替换
- **三层关键字扩展** — Bean（`KeywordContributor`）> YAML 配置 > 内置默认值
- **20 种操作符** — eq/ne/gt/gte/lt/lte/in/between/like/not_like/prefix/suffix/not_prefix/not_suffix/regex/exists/not_exists/is_null/is_not_null/not_in
- **22 种聚合类型** — 指标聚合、桶聚合、Pipeline 聚合全覆盖
- **字段折叠** — 去重解析，支持多种自然语言模式
- **FieldBinder / IntentTranslator** — SPI 接口，接入自己的存储系统
- **完整错误码体系** — `ErrorCode`、`ErrorMessage` 常量，`NLParseException` 支持 Builder 模式
- **开箱即用** — Spring Boot Starter 自动配置

## 快速开始

### 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:natural-language-parser-starter:1.1.4'
}
```

### 基础使用

```java
@Autowired
private NLParser nlParser;

// 简单查询
Intent intent = nlParser.parse("年龄大于18");
QueryIntent q = (QueryIntent) intent;
// q.getCondition().getFieldHint() → "年龄"
// q.getCondition().getOperator()   → OperatorType.GT
// q.getCondition().getValue()      → 18L

// 复杂查询
intent = nlParser.parse(
    "年龄大于等于18并且城市在北京、上海、深圳并且名字包含张或李，按创建时间降序，返回前10条"
);

// 聚合查询
intent = nlParser.parse("城市是北京统计平均年龄");
AnalyticsIntent a = (AnalyticsIntent) intent;
// a.getAggregations().get(0).getType()      → AggType.AVG
// a.getAggregations().get(0).getFieldHint() → "年龄"
```

## 操作符

| 中文 | 英文 | 符号 | 操作符代码 |
|------|------|------|-----------|
| 等于、是、为 | equals | = | `eq` |
| 不等于、不是 | not equals | != | `ne` |
| 大于、超过 | greater than | > | `gt` |
| 大于等于 | gte | >= | `gte` |
| 小于、低于 | less than | < | `lt` |
| 小于等于 | lte | <= | `lte` |
| 在、属于 | in | - | `in` |
| 不在 | not in | - | `not_in` |
| 包含、匹配 | like | - | `like` |
| 不包含 | not like | - | `not_like` |
| 开头是 | prefix | - | `prefix` |
| 开头不是 | not_prefix | - | `not_prefix` |
| 结尾是 | suffix | - | `suffix` |
| 结尾不是 | not_suffix | - | `not_suffix` |
| 介于、范围 | between | - | `between` |
| 存在 | exists | - | `exists` |
| 不存在 | not_exists | - | `not_exists` |
| 为空 | is_null | - | `is_null` |
| 不为空 | is_not_null | - | `is_not_null` |
| 正则匹配 | regex | - | `regex` |

## 聚合类型

### 指标聚合

| 中文 | 英文 | 代码 |
|------|------|------|
| 平均值 | avg | `avg` |
| 求和 | sum | `sum` |
| 最小值 | min | `min` |
| 最大值 | max | `max` |
| 计数 | count | `count` |
| 去重计数 | cardinality | `cardinality` |
| 统计 | stats | `stats` |
| 扩展统计 | extended_stats | `extended_stats` |
| 百分位数 | percentiles | `percentiles` |
| 百分位排名 | percentile_ranks | `percentile_ranks` |

### 桶聚合

| 中文 | 代码 | 说明 |
|------|------|------|
| 按...分组 | `terms` | 桶聚合 |
| 按...每天/每小时/每周 | `date_histogram` | 时间聚合 |
| 数值直方图 | `histogram` | 数值分段 |
| 范围聚合 | `range` | 自定义范围 |
| 日期范围 | `date_range` | 日期范围 |
| IP范围 | `ip_range` | IP段 |
| 单过滤器 | `filter` | 单条件过滤 |
| 多过滤器 | `filters` | 多条件对比 |
| 缺失值 | `missing` | 缺失值统计 |

### Pipeline 聚合

| 中文 | 代码 | 说明 |
|------|------|------|
| 桶排序 | `bucket_sort` | Top N |
| 桶选择 | `bucket_selector` | HAVING 条件 |

### 聚合示例

```java
// 简单指标
nlParser.parse("统计平均年龄");

// 分组聚合 + size
nlParser.parse("按城市分组前10个");

// 时间聚合
nlParser.parse("按创建时间每天统计");
nlParser.parse("按创建时间每小时统计");
nlParser.parse("按创建时间每月统计");

// 嵌套聚合
nlParser.parse("按城市分组前10个统计平均年龄");

// 并行聚合
nlParser.parse("按城市分组，同时按创建时间每天统计");

// 去重计数
nlParser.parse("去重计数城市");
```

## 逻辑组合

| 中文 | 英文 | 符号 |
|------|------|------|
| 并且、且、和 | and | && |
| 或者、或 | or | \|\| |

```java
// AND
nlParser.parse("年龄大于18并且城市在北京");

// OR
nlParser.parse("名字包含张或李");

// 混合
nlParser.parse("状态等于active并且年龄大于等于18并且城市在北京、上海");
```

## 字段折叠（去重）

```java
nlParser.parse("按源IP去重");
nlParser.parse("源IP去重");
nlParser.parse("去重源IP");
nlParser.parse("用户名唯一");

// 组合
nlParser.parse("年龄大于18按源IP去重按创建时间降序返回100条");
```

## 排序

| 中文 | 英文 | 示例 |
|------|------|------|
| 升序 | asc | `按年龄升序` |
| 降序 | desc | `按创建时间降序` |

## 分页

| 关键词 | 示例 |
|--------|------|
| 限制、最多、前、limit | `限制10条`、`前20条` |
| 跳过、offset | `跳过20条` |
| 第N页、每页M条 | `第3页，每页10条` |
| 返回第M到N条 | `返回第21到30条` |
| 继续查询、接着 | `继续查询，返回100条` |
| search_after 游标 | `接着[1704110400000,user_123]继续查询，返回100条` |

## 时间范围

| 中文关键词 | 英文关键词 |
|-----------|-----------|
| 时间范围 | dateRange, date range, time range, timeRange, date_range, time_range |
| 日期范围 | - |

**时间格式：** `YYYY-MM-DD`、`YYYY/MM/DD`、`YYYY年MM月DD日`、ISO 8601 等

```java
nlParser.parse("时间范围2025-01-01到2025-12-31");
nlParser.parse("时间范围2025-01-01 10:00:00到2026-01-01 20:00:00");
nlParser.parse("年龄大于18，时间范围2025-01-01到2025-12-31，返回100条");
```

## 可插拔架构

### 自定义 Parser

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

### 自定义分词器

```java
@Bean
public NLTokenizer nlTokenizer() {
    return new IkAnalyzerTokenizer(); // 替换默认 HanLP 分词
}
```

### 关键字扩展

三层优先级：Bean > YAML 配置 > 内置默认值

**YAML 方式：**

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        naturallanguage:
          parser:
            keywords:
              operators:
                eq:
                  - 匹配
                  - 对应
              stop-words:
                - 帮我
```

**Bean 方式：**

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

## FieldBinder + IntentTranslator

### FieldBinder — 字段绑定

将自然语言字段提示（如"年龄"）绑定到实际字段名（如"age"）：

```java
public class MyFieldBinder implements FieldBinder {
    @Override
    public String bind(String fieldHint, String dataSource) {
        // "年龄" → "age"，"城市" → "city"
        return mapping.getOrDefault(fieldHint, fieldHint);
    }
}
```

### IntentTranslator — Intent 翻译

将 Intent 转换为目标系统的查询对象（如 search-core 的 `QueryRequest`）：

```java
public class SearchIntentTranslator implements IntentTranslator<Object> {

    private final FieldBinder fieldBinder;

    @Override
    public Object translate(Intent intent, TranslateContext context) {
        if (intent instanceof QueryIntent) {
            return translateQuery((QueryIntent) intent, context);
        }
        if (intent instanceof AnalyticsIntent) {
            return translateAnalytics((AnalyticsIntent) intent, context);
        }
        throw NLParseException.unsupportedIntent(intent.getClass().getSimpleName());
    }

    private QueryRequest translateQuery(QueryIntent intent, TranslateContext context) {
        String index = context.getDataSource() != null
                ? context.getDataSource() : intent.getIndexHint();
        QueryRequest.QueryRequestBuilder builder = QueryRequest.builder().index(index);

        if (intent.hasCondition()) {
            builder.query(translateCondition(intent.getCondition(), context));
        }
        if (intent.hasCollapse()) {
            String field = fieldBinder.bind(intent.getCollapse().getFieldHint(), index);
            builder.collapse(QueryRequest.CollapseField.builder().field(field).build());
        }
        builder.pagination(translatePagination(intent.getPagination(), intent.getSorts(), index));
        return builder.build();
    }
    // ... 完整示例见 SearchIntentTranslatorTest
}
```

## 错误处理

所有解析错误抛出 `NLParseException`，支持 Builder 模式：

```java
try {
    Intent intent = nlParser.parse("年龄大于");
} catch (NLParseException e) {
    e.getErrorType();    // MISSING_VALUE
    e.getQuery();        // "年龄大于"
    e.getSuggestion();   // "请在操作符"大于"后添加一个值"
}
```

### 智能拼写建议

`OperatorSuggester` 使用 Levenshtein 距离算法检测拼写错误：

```java
OperatorSuggester suggester = new OperatorSuggester(keywordRegistry);
suggester.findMostSimilar("大雨");       // → "大于"
suggester.isPossibleTypo("大雨");        // → true
```

## 多分隔符

```java
nlParser.parse("城市在北京,上海,深圳");   // 英文逗号
nlParser.parse("城市在北京，上海，深圳"); // 中文逗号
nlParser.parse("城市在北京、上海、深圳"); // 顿号
```

自动区分逗号用途 — IN 操作符后是值分隔，其他是子句分隔（等同于 AND）。

## 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        naturallanguage:
          parser:
            enabled: true
            custom-stop-words:
              - 帮我
              - 请
            keywords:
              operators:
                eq:
                  - 匹配
              stop-words:
                - 麻烦
```

## 核心模型

### Intent 类型

| 类 | 说明 | 字段 |
|----|------|------|
| `QueryIntent` | 查询意图 | condition, sorts, pagination, dateRange, collapse, fieldProjections |
| `AnalyticsIntent` | 聚合意图 | condition, aggregations |

### ConditionIntent — 递归树结构

简单条件：`fieldHint` + `operator` + `value`/`values`
逻辑组合：`logic`（AND/OR/NOT）+ `children`（递归子条件）

### AggregationIntent

支持嵌套子聚合（`subAggs`）、Pipeline 聚合（`pipelineAggs`）、filter/filters 条件、composite 翻页。

## 依赖

- Java 8+
- Spring Boot 2.x+
- HanLP portable 1.8.6

## 版本历史

### v1.1.4 (2026-06-15)

Minor Release。详见 [CHANGELOG.1.1.4.md](CHANGELOG.1.1.4.md)

- OperatorType 新增 `NOT_PREFIX` / `NOT_SUFFIX` 两个不匹配操作符
- DefaultKeywordRegistry 新增对应关键字映射

### v1.1.3 (2026-05-13)

Bug Fix。详见 [CHANGELOG.1.1.3.md](CHANGELOG.1.1.3.md)

- 修复 IndexExtractorPlugin 无法提取通配符索引名（如 `app_access_log-*`）

### v1.1.2 (2026-05-12)

Bug Fix。详见 [CHANGELOG.1.1.2.md](CHANGELOG.1.1.2.md)

- 修复 EXPECT_LOGIC_OR_END 中 `sameFieldOrMode` 误触发导致 BETWEEN 等场景解析异常
- 修复 BETWEEN "在X到Y之间" 解析返回 null（如 `"年龄在18到30之间"`）

### v1.1.1 (2026-05-11)

Bug Fix + 重构 Release。详见 [CHANGELOG.1.1.1.md](CHANGELOG.1.1.1.md)

- 修复 OR 条件字段错乱（同字段 OR "名字包含张或李"、跨字段 OR "年龄小于25或城市等于深圳"）
- 修复 IN 列表多含"中"（"城市在北京、上海、深圳中"）
- ConditionParser 状态模式重构，if-else 最大嵌套从 4 层降至 1 层

### v1.1.0 (2026-05-04)

全量重构版本。详见 [CHANGELOG.1.1.0.md](CHANGELOG.1.1.0.md)

**破坏性变更：**
- `PaginationIntent.getLimit()` → `getSize()`
- `PaginationIntent.getContinueSearch()` → `getSearchAfterMode()`
- `AggregationIntent.getName()` → `getNameHint()`
- `AggregationIntent.getChildren()` → `getSubAggs()`
- `ConditionIntent.getFieldHint()` 始终返回原始字段名，不再返回 logic.name()

**新功能：** 可插拔解析器、可替换分词器、三层关键字扩展、字段折叠、22 种聚合类型、FieldBinder/IntentTranslator SPI、完整错误码体系

### v1.0.4 (2026-01-03)

修复逗号分隔并行聚合、分页"取N条"、嵌套聚合字段识别

### v1.0.3 (2026-01-03)

聚合解析增强：桶聚合、时间聚合、嵌套聚合、并行聚合

### v1.0.2 (2026-01-03)

修复索引名提取（含逻辑关键词的索引名如 user_behavior）

### v1.0.1 (2026-01-02)

Token 化日志等级从 INFO 改为 DEBUG

### v1.0.0 (2026-01-01)

初始版本

## 许可证

Apache License 2.0
