# Natural Language Parser Starter

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8+-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.x+-brightgreen.svg)](https://spring.io/projects/spring-boot)

> 一个基于 HanLP 的中文自然语言查询解析器，可将自然语言查询转换为结构化的 Intent 对象，支持条件、聚合、排序、分页等多种查询场景。

## ✨ 特性

- 🎯 **智能解析** - 支持中文、英文关键词，自动识别操作符、逻辑词、聚合函数
- 🚀 **功能完善** - 15+ 种操作符，AND/OR 逻辑组合，复杂聚合（嵌套、并行），排序分页
- 📊 **聚合增强** - 支持桶聚合、时间聚合、嵌套聚合、并行聚合
- ⏱️ **时间范围** - 全局时间范围过滤，支持日期+时间（时分秒），多种格式
- 🔧 **开箱即用** - Spring Boot Starter 自动配置，零配置启动
- 📊 **详细日志** - 完整的 Token 化和解析日志，方便调试
- 🌐 **多分隔符** - 智能处理英文逗号、中文逗号、顿号、空格等分隔符
- 💡 **智能歧义** - 自动区分逗号用途（值分隔 vs 子句分隔）
- ⚠️ **友好错误** - 详细的错误信息、位置提示、智能拼写建议
- 🏗️ **优秀架构** - 策略模式设计，易扩展、高性能、代码整洁

## 🚀 快速开始

### 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:natural-language-parser-starter:1.0.6'
}
```

### 基础使用

```java

@Autowired
private NLParser nlParser;

// 简单查询
Intent intent = nlParser.parse("年龄大于18");
QueryIntent q = (QueryIntent) intent;
// 结果: 年龄 > 18

// 复杂查询（展示所有高级特性）
intent =nlParser.

parse(
    "年龄大于等于18小于60，城市在北京、上海、深圳并且名字包含张或李，按创建时间降序，返回前10条数据"
);
// 结果: (年龄>=18 AND 年龄<60) AND (城市 IN [北京,上海,深圳])
//      AND ((名字 LIKE 张) OR (名字 LIKE 李))
//      ORDER BY 创建时间 DESC LIMIT 10

// 聚合查询
intent =nlParser.

parse("城市是北京统计平均年龄");

AnalyticsIntent a = (AnalyticsIntent) intent;
// 结果: 城市=北京, AVG(年龄)
```

## 📖 支持的查询语法

### 操作符

| 中文     | 英文           | 符号 | 示例          |
|--------|--------------|----|-------------|
| 等于、是、为 | equals       | =  | `年龄等于18`    |
| 不等于、不是 | not equals   | != | `状态不等于已删除`  |
| 大于、超过  | greater than | >  | `年龄大于18`    |
| 大于等于   | gte          | >= | `年龄>=18`    |
| 小于、低于  | less than    | <  | `年龄小于30`    |
| 小于等于   | lte          | <= | `年龄<=30`    |
| 在、属于   | in           | -  | `城市在北京,上海`  |
| 不在     | not in       | -  | `城市不在北京,上海` |
| 包含、匹配  | like         | -  | `名字包含张`     |
| 不包含    | not like     | -  | `名字不包含张`    |
| 介于、范围  | between      | -  | `年龄介于18,30` |

### 逻辑运算

| 中文     | 英文  | 符号   | 说明             |
|--------|-----|------|----------------|
| 并且、且、和 | and | &&   | `年龄>18并且城市=北京` |
| 或者、或   | or  | \|\| | `名字包含张或李`      |

### 聚合函数

| 中文            | 英文          | 说明        |
|---------------|-------------|-----------|
| 平均、平均值        | avg         | 平均值       |
| 求和、总和         | sum         | 求和        |
| 最大、最大值        | max         | 最大值       |
| 最小、最小值        | min         | 最小值       |
| 计数、数量、个数      | count       | 计数        |
| 去重、去重计数       | cardinality | 去重计数      |
| 按...分组        | terms       | 桶聚合（分组）   |
| 按...每天/每小时/每周 | date_histogram | 时间聚合      |

**聚合示例：**

```java
// 简单聚合
nlParser.parse("统计平均年龄");

// 分组聚合
nlParser.parse("按城市分组");
nlParser.parse("按城市分组前10个");  // 带size参数

// 时间聚合
nlParser.parse("按创建时间每天统计");
nlParser.parse("按创建时间每小时统计");

// 嵌套聚合（分组内计算指标）
nlParser.parse("按城市分组统计平均年龄");
nlParser.parse("按城市分组前10个统计平均年龄");

// 并行聚合（多个聚合同时执行）
nlParser.parse("按城市分组，同时按创建时间每天统计");
nlParser.parse("按城市分组统计平均年龄，同时按创建时间每天统计");
```

### 时间范围

| 中文关键词 | 英文关键词（大小写不敏感）| 说明 |
|---------|------------|------|
| 时间范围  | dateRange, date range, time range, timeRange, date_range, time_range | 全局时间过滤 |
| 日期范围  | - | 全局时间过滤 |

**时间格式支持：**

纯日期（时间默认00:00:00）：
- `YYYY-MM-DD` (2025-01-01)
- `YYYY/MM/DD` (2025/01/01)
- `YYYY年MM月DD日` (2025年1月1日)
- `YYYYMMDD` (20250101)

带时间：
- ISO 8601: `YYYY-MM-DDTHH:mm:ss` (2025-01-01T12:30:45)
- 空格分隔: `YYYY-MM-DD HH:mm:ss` (2025-01-01 12:30:45)
- 斜杠格式: `YYYY/MM/DD HH:mm:ss` (2025/01/01 12:30:45)
- 中文格式: `YYYY年MM月DD日 HH时mm分ss秒` (2025年1月1日 12时30分45秒)

**时间范围示例：**

```java
// 纯日期
nlParser.parse("时间范围2025-01-01到2026-01-01");

// 日期+时间
nlParser.parse("时间范围2025-01-01 10:00:00到2026-01-01 20:00:00");

// 结合其他条件
nlParser.parse("时间范围2025-01-01到2026-01-01，年龄大于18，返回100条");
```

**时区说明：** 使用 `LocalDateTime`（不含时区），按服务器本地时区解释。调用方负责时区转换。

### 排序

| 中文 | 英文   | 示例        |
|----|------|-----------|
| 升序 | asc  | `按年龄升序`   |
| 降序 | desc | `按创建时间降序` |

### 分页

| 关键词           | 示例             |
|---------------|----------------|
| 限制、最多、前、limit | `限制10条`、`前20条` |
| 跳过、offset | `跳过20条` |
| 继续查询、接着 | `继续查询，返回100条` |

**search_after 深度分页：**

支持 Elasticsearch search_after 游标值解析：

```java
// 方式1：只标识续查
nlParser.parse("继续查询，返回100条");
// → continueSearch=true, limit=100

// 方式2：指定游标值（推荐）
nlParser.parse("接着[1704110400000,user_123]继续查询，返回100条");
// → continueSearch=true, searchAfter=[1704110400000L, "user_123"], limit=100
```

**游标值格式：** `[value1,value2,value3]`，自动识别类型（Long/Double/String）

## 💡 高级特性

### 1. 多种分隔符

解析器智能识别多种中英文分隔符：

```java
// 英文逗号
nlParser.parse("城市在北京,上海,深圳");

// 中文逗号
nlParser.

parse("城市在北京，上海，深圳");

// 顿号
nlParser.

parse("城市在北京、上海、深圳");

// 空格
nlParser.

parse("城市在北京 上海 深圳");
```

### 2. 逗号歧义处理

自动区分逗号的不同用途：

```java
// 场景1: 作为值分隔符（IN操作符）
nlParser.parse("城市在北京,上海,深圳");
// → 城市 IN [北京, 上海, 深圳]

// 场景2: 作为子句分隔符
nlParser.

parse("年龄>18,城市=北京,名字包含张");
// → (年龄>18) AND (城市=北京) AND (名字包含张)
```

**处理规则：**

- 在 IN/BETWEEN 等多值操作符后，逗号是**值分隔符**
- 在单值操作符（GT、EQ、LIKE等）后，逗号是**子句分隔符**，等同于 AND

### 3. OR逻辑在值层面

支持在值层面使用 OR 逻辑：

```java
nlParser.parse("名字包含张或李");
// → (名字 LIKE 张) OR (名字 LIKE 李)

nlParser.

parse("年龄等于18或25");
// → (年龄=18) OR (年龄=25)
```

### 4. 混合中英文

支持中英文混用，大小写不敏感：

```java
nlParser.parse("age>18 and city=Beijing");
nlParser.

parse("avg age");
nlParser.

parse("年龄 > 18 AND 城市 = 北京");
```

## ⚠️ 错误处理

解析器提供了完善的错误处理机制，帮助用户快速定位和修复查询问题。

### NLParseException（自定义异常）

所有解析错误都会抛出 `NLParseException`，包含详细的错误上下文：

```java
try{
Intent intent = nlParser.parse("年龄大于");
}catch(
NLParseException e){
// 错误类型
ErrorType type = e.getErrorType();  // MISSING_VALUE

// 原始查询
String query = e.getQuery();  // "年龄大于"

// 错误位置
int position = e.getPosition();  // -1（无法确定）

// 修复建议
String suggestion = e.getSuggestion();  // "请在操作符"大于"后添加一个值"

// 完整错误消息
    System.out.

println(e.getMessage());
        }
```

**输出示例：**

```
操作符后缺少值
错误类型: 缺少值
相关token: "大于"
建议: 请在操作符"大于"后添加一个值
```

### 错误类型

| 错误类型                    | 说明             | 示例             |
|-------------------------|----------------|----------------|
| `MISSING_VALUE`         | 操作符后缺少值        | `年龄大于`         |
| `MISSING_OPERATOR`      | 字段和值之间缺少操作符    | `年龄 18`        |
| `UNRECOGNIZED_OPERATOR` | 无法识别的操作符（拼写错误） | `年龄大雨18`       |
| `EMPTY_QUERY`           | 空查询或只包含停用词     | `""` 或 `"查一下"` |
| `TYPE_MISMATCH`         | 值与字段类型不匹配      | -              |
| `SYNTAX_ERROR`          | 其他语法错误         | -              |

### 智能拼写建议（OperatorSuggester）

解析器内置了操作符拼写检查工具，使用 **Levenshtein 距离算法**检测拼写错误并提供建议：

```java
// 示例1：用户输入错误的操作符
try{
        nlParser.parse("年龄大雨18");  // "大雨" 应该是 "大于"
}catch(
NLParseException e){
        // e.getErrorType() → UNRECOGNIZED_OPERATOR
        // e.getRelatedToken() → "大雨"
        // e.getSuggestion() → "您是否想输入：大于"
        }

// 示例2：多个相似建议
        try{
        nlParser.

parse("名字包刮张三");  // "包刮" 应该是 "包含"
}catch(
NLParseException e){
        // e.getSuggestion() → "您是否想输入：包含"
        }
```

**拼写检查特性：**

- 相似度阈值：编辑距离 ≤ 2
- 最多返回 3 个建议
- 支持中英文操作符
- 线程安全、高性能

**常见拼写错误示例：**

- `大雨` → 建议 `大于`
- `小鱼` → 建议 `小于`
- `包刮` → 建议 `包含`
- `等玉` → 建议 `等于`
- `不在于` → 建议 `不在`

### 错误处理最佳实践

```java

@RestController
public class QueryController {

    @Autowired
    private NLParser nlParser;

    @PostMapping("/query")
    public ResponseEntity<?> query(@RequestBody String naturalLanguage) {
        try {
            Intent intent = nlParser.parse(naturalLanguage);
            // 执行查询...
            return ResponseEntity.ok(result);

        } catch (NLParseException e) {
            // 返回友好的错误消息
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", e.getErrorType().getDescription(),
                            "message", e.getMessage().split("\n")[0],
                            "suggestion", e.getSuggestion() != null ? e.getSuggestion() : "",
                            "query", e.getQuery()
                    ));
        }
    }
}
```

## 📚 核心类说明

### Intent（意图基类）

所有查询意图的基类：

```java
public abstract class Intent {
    private IntentType type;  // QUERY 或 ANALYTICS
}
```

**子类：**

- `QueryIntent` - 普通查询（条件、排序、分页）
- `AnalyticsIntent` - 分析查询（条件、聚合）

### QueryIntent（查询意图）

```java
public class QueryIntent extends Intent {
    private ConditionIntent condition;      // 查询条件
    private List<SortIntent> sorts;         // 排序列表
    private PaginationIntent pagination;    // 分页信息

    // 便捷方法
    public boolean hasCondition();

    public boolean hasSort();

    public boolean hasPagination();
}
```

### AnalyticsIntent（分析意图）

```java
public class AnalyticsIntent extends Intent {
    private ConditionIntent condition;              // 查询条件
    private List<AggregationIntent> aggregations;   // 聚合列表

    // 便捷方法
    public boolean hasCondition();

    public boolean hasAggregation();
}
```

### ConditionIntent（条件意图）

```java
public class ConditionIntent {
    private String fieldHint;               // 字段提示
    private OperatorType operator;          // 操作符
    private Object value;                   // 单个值
    private List<Object> values;            // 多个值（IN、BETWEEN）
    private LogicType logic;                // 逻辑类型（AND/OR）
    private List<ConditionIntent> children; // 子条件（逻辑组合）

    // 便捷方法
    public boolean isLogicCondition();  // 是否为逻辑组合条件
}
```

## 🎯 完整示例

### 示例1：简单查询

```java
Intent intent = nlParser.parse("年龄大于18");
QueryIntent q = (QueryIntent) intent;

ConditionIntent condition = q.getCondition();
// condition.getFieldHint() → "年龄"
// condition.getOperator() → OperatorType.GT
// condition.getValue() → 18L
```

### 示例2：复杂查询

```java
String query = "年龄大于等于18并且年龄小于60并且城市在北京,上海,深圳按创建时间降序限制10条";
Intent intent = nlParser.parse(query);
QueryIntent q = (QueryIntent) intent;

// 条件部分
ConditionIntent rootCondition = q.getCondition();
// rootCondition.getLogic() → LogicType.AND
// rootCondition.getFieldHint() → "年龄"
// rootCondition.getOperator() → OperatorType.GTE
// rootCondition.getValue() → 18L
// rootCondition.getChildren() → List<ConditionIntent> (2个子条件)

// 排序部分
SortIntent sort = q.getSorts().get(0);
// sort.getFieldHint() → "创建时间"
// sort.getOrder() → SortOrder.DESC

// 分页部分
PaginationIntent pagination = q.getPagination();
// pagination.getLimit() → 10
```

### 示例3：聚合查询

```java
Intent intent = nlParser.parse("城市是北京并且年龄介于25,45统计平均年龄");
AnalyticsIntent a = (AnalyticsIntent) intent;

// 条件部分
ConditionIntent condition = a.getCondition();
// condition.getLogic() → LogicType.AND

// 聚合部分
AggregationIntent agg = a.getAggregations().get(0);
// agg.getType() → AggType.AVG
// agg.getFieldHint() → "年龄"
```

### 示例4：业务集成

```java

@Service
public class UserQueryService {

    @Autowired
    private NLParser nlParser;

    public List<User> queryUsers(String naturalLanguage) {
        Intent intent = nlParser.parse(naturalLanguage);

        if (intent instanceof QueryIntent) {
            QueryIntent q = (QueryIntent) intent;

            // 转换为 JPA 查询
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<User> query = cb.createQuery(User.class);
            Root<User> root = query.from(User.class);

            // 添加条件
            if (q.hasCondition()) {
                query.where(buildPredicate(q.getCondition(), cb, root));
            }

            // 添加排序
            if (q.hasSort()) {
                List<Order> orders = new ArrayList<>();
                for (SortIntent sort : q.getSorts()) {
                    Path<?> path = root.get(sort.getFieldHint());
                    orders.add(sort.getOrder() == SortOrder.ASC ?
                            cb.asc(path) : cb.desc(path));
                }
                query.orderBy(orders);
            }

            TypedQuery<User> typedQuery = em.createQuery(query);

            // 添加分页
            if (q.hasPagination()) {
                typedQuery.setMaxResults(q.getPagination().getLimit());
            }

            return typedQuery.getResultList();
        }

        return Collections.emptyList();
    }

    private Predicate buildPredicate(ConditionIntent condition,
                                     CriteriaBuilder cb,
                                     Root<User> root) {
        if (condition.isLogicCondition()) {
            // 处理逻辑组合
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(buildSimplePredicate(condition, cb, root));
            for (ConditionIntent child : condition.getChildren()) {
                predicates.add(buildPredicate(child, cb, root));
            }
            return condition.getLogic() == LogicType.AND ?
                    cb.and(predicates.toArray(new Predicate[0])) :
                    cb.or(predicates.toArray(new Predicate[0]));
        } else {
            return buildSimplePredicate(condition, cb, root);
        }
    }

    private Predicate buildSimplePredicate(ConditionIntent condition,
                                           CriteriaBuilder cb,
                                           Root<User> root) {
        Path<?> path = root.get(condition.getFieldHint());

        switch (condition.getOperator()) {
            case EQ:
                return cb.equal(path, condition.getValue());
            case GT:
                return cb.greaterThan((Path<Comparable>) path,
                        (Comparable) condition.getValue());
            case IN:
                return path.in(condition.getValues());
            case LIKE:
                return cb.like((Path<String>) path,
                        "%" + condition.getValue() + "%");
            // ... 其他操作符
            default:
                throw new UnsupportedOperationException();
        }
    }
}
```

## 🔧 配置

```yaml
# application.yml
nl-parser:
  enabled: true                 # 是否启用（默认 true）
  custom-stop-words: # 自定义停用词
    - 帮我
    - 请
    - 麻烦
```

## 🏗️ 工作原理

### 架构设计

解析器采用清晰的分层架构和策略模式：

```
用户输入 (自然语言)
    ↓
NLParser (解析器核心)
    ├─ NLTokenizer (分词器)
    │   ├─ HanLP (中文分词)
    │   ├─ DelimiterSplitStrategy (分隔符拆分策略)
    │   ├─ OperatorSplitStrategy (操作符拆分策略)
    │   └─ LogicKeywordSplitStrategy (逻辑词拆分策略)
    ├─ ConditionParser (条件解析 - 状态机)
    ├─ AggregationParser (聚合解析)
    ├─ SortParser (排序解析)
    ├─ PaginationParser (分页解析)
    └─ OperatorSuggester (拼写检查 - Levenshtein距离)
    ↓
Intent (结构化意图对象)
    ├─ QueryIntent (普通查询)
    └─ AnalyticsIntent (聚合查询)
    ↓
异常处理 (NLParseException)
    ├─ 错误类型识别
    ├─ 位置信息标注
    └─ 智能修复建议
```

**关键设计模式：**

1. **策略模式 (Strategy Pattern)** - Token拆分策略
    - 每个策略独立处理一种复合token拆分场景
    - 易于扩展新的拆分规则
    - 代码清晰、职责单一

2. **状态机 (State Machine)** - 条件解析
    - 4个状态：EXPECT_FIELD → EXPECT_OPERATOR → EXPECT_VALUE → EXPECT_LOGIC_OR_END
    - 严格的状态转换规则
    - 健壮的错误处理

3. **建造者模式 (Builder Pattern)** - Intent构建 & 异常构建
    - Lombok @Builder 注解
    - 链式调用，代码优雅
    - NLParseException 使用 Builder 模式构建复杂异常对象

4. **门面模式 (Facade Pattern)** - NLParser
    - 为多个子系统提供统一接口
    - 封装复杂的解析流程

### 1. Token化（Tokenization）

使用 HanLP 分词，然后识别关键词类型：

```
输入: "年龄大于18并且城市在北京,上海"

Token化:
[0] UNKNOWN    '年龄'
[1] OPERATOR   '大于' → GT
[2] NUMBER     '18' → 18
[3] LOGIC      '并且' → AND
[4] UNKNOWN    '城市'
[5] OPERATOR   '在' → IN
[6] UNKNOWN    '北京'
[7] DELIMITER  ','
[8] UNKNOWN    '上海'
```

### 2. 解析（Parsing）

状态机解析 Token 序列：

```
状态转换:
EXPECT_FIELD → EXPECT_OPERATOR → EXPECT_VALUE → EXPECT_LOGIC_OR_END
```

### 3. 构建Intent

生成结构化的 Intent 对象。

## ❓ 常见问题

### Q: 如何添加自定义操作符？

在 `OperatorKeywords.java` 中注册：

```java
register("自定义",OperatorType.CUSTOM);
```

### Q: 如何添加停用词？

配置文件或代码：

```java

@Autowired
private NLTokenizer tokenizer;

tokenizer.

addStopWord("某词");
```

### Q: 解析失败怎么办？

解析失败会抛出 `NLParseException`，包含详细的错误信息：

```java
try{
Intent intent = nlParser.parse("年龄大于");
}catch(
NLParseException e){
        // 查看错误类型
        System.out.

println("错误类型: "+e.getErrorType());

        // 查看建议
        System.out.

println("建议: "+e.getSuggestion());

        // 查看完整消息
        System.out.

println(e.getMessage());
        }
```

**调试技巧：**

1. 查看日志中的 Token 化结果，检查关键词识别
2. 检查异常中的 `relatedToken` 字段，定位问题 token
3. 根据 `suggestion` 字段的建议修改查询

### Q: 如何扩展自定义拆分策略？

实现 `TokenSplitStrategy` 接口：

```java
public class CustomSplitStrategy implements TokenSplitStrategy {

    @Override
    public boolean canHandle(String word) {
        // 判断是否可以处理该word
        return word.contains("自定义模式");
    }

    @Override
    public List<Token> split(String word, int position, SplitContext context) {
        // 拆分逻辑
        List<Token> tokens = new ArrayList<>();
        // ... 拆分并识别token
        return tokens;
    }
}
```

然后在 `NLTokenizer` 构造函数中注册：

```java
this.splitStrategies =Arrays.

asList(
    new DelimiterSplitStrategy(),
    new

OperatorSplitStrategy(),
    new

LogicKeywordSplitStrategy(),
    new

CustomSplitStrategy()  // 添加自定义策略
);
```

### Q: 如何自定义拼写建议的相似度阈值？

`OperatorSuggester` 使用 Levenshtein 距离算法，默认阈值为 2。如需调整：

```java
// OperatorSuggester 是工具类，可以直接调用
String suggestion = OperatorSuggester.findMostSimilar("大雨");
// 返回: "大于"

List<String> suggestions = OperatorSuggester.findSimilar("包刮", 3);
// 返回: ["包含", ...]

// 检查是否可能是拼写错误
boolean isPossibleTypo = OperatorSuggester.isPossibleTypo("大雨");
// 返回: true
```

**注意：** 相似度阈值硬编码在 `OperatorSuggester.SIMILARITY_THRESHOLD` 常量中，如需修改请直接编辑源码。

### Q: 支持哪些数据库？

解析器与数据库无关，生成的 Intent 可用于任何查询引擎（JPA、MyBatis、Elasticsearch、MongoDB等）。

## 📦 依赖

- Java 8+
- Spring Boot 2.x+
- HanLP portable 1.8.6

## 📝 版本历史

### v1.0.4 (2026-01-03)

**🐛 Bug修复（紧急）**

v1.0.3 发布后用户立即反馈了3个关键缺陷，本版本进行了紧急修复。

- 🔧 **聚合分段失败** - 修复了逗号分隔的并行聚合无法解析的问题
  - 添加逗号（`,` 和 `，`）到聚合分隔符列表
  - 修复场景：`按city分组前10名计算age平均值，按createTime每天统计`
  - 详见：[CHANGELOG.1.0.4.md](CHANGELOG.1.0.4.md#bug-1-聚合分段失败---逗号分隔符支持-)

- 🔧 **分页解析失败** - 修复了"取N条"分页表达无法识别的问题
  - 添加"取"关键词到分页关键词列表
  - 修复场景：`取50条`、`跳过20条，取10条`
  - 详见：[CHANGELOG.1.0.4.md](CHANGELOG.1.0.4.md#bug-2-分页解析失败---取关键词缺失-)

- 🔧 **字段识别失败** - 修复了嵌套聚合中字段识别失败的问题
  - 实现字段双向查找（向前+向后）
  - 修复场景：`按城市分组前10名计算年龄平均值`
  - 详见：[CHANGELOG.1.0.4.md](CHANGELOG.1.0.4.md#bug-3-字段识别失败---字段双向查找-)

**⚠️ 强烈推荐所有 v1.0.3 用户升级到 v1.0.4**

### v1.0.3 (2026-01-03)

**✨ 新特性**

- 🎯 **聚合解析增强** - 支持复杂聚合查询
  - 桶聚合（TERMS）：支持 size 参数 `按城市分组前10个`
  - 时间聚合（DATE_HISTOGRAM）：支持 interval 参数 `按创建时间每天统计`
  - 嵌套聚合：分组内计算指标 `按城市分组统计平均年龄`
  - 并行聚合：同时执行多个聚合 `按城市分组，同时按创建时间每天统计`

**🔧 技术改进**

- 消除硬编码，关键词统一管理
- 增强分词容错性，适配 HanLP 分词变化
- 优先级解析，避免误识别（如 `按...降序` 不会被识别为聚合）
- 字段名合并，支持多 token 字段名

### v1.0.2 (2026-01-03)

**🐛 Bug修复**

- 🔧 **索引名提取bug修复** - 修复了包含逻辑关键词的索引名无法提取的问题
  - 修复场景：`user_behavior` 中的 "or" 被HanLP识别为LOGIC类型，导致索引名提取失败
  - 修改 `IndexExtractor` 接受 LOGIC、OPERATOR 类型的 token 作为索引名的一部分
  - 支持更多复杂的索引命名场景（包含 or、and、in 等关键词的索引名）

### v1.0.1 (2026-01-02)

**🐛 Bug修复**

- 🔧 **日志等级优化** - 将 Token 化结果日志从 `INFO` 改为 `DEBUG`，减少生产环境日志输出
  - 修改 `NLParser.logTokenizationResult()` 中的所有日志调用
  - 默认不打印调试信息，需要时可通过配置启用 DEBUG 级别

### v1.0.0 (2026-01-01)

**✨ 核心功能**

- 🎯 支持15+种操作符（等于、大于、小于、包含、在范围内等）
- 🚀 支持AND/OR逻辑组合
- 📊 支持聚合查询（AVG、SUM、MIN、MAX、COUNT、去重计数等）
- 🔧 支持排序和分页
- 🌐 多分隔符支持（`,`、`，`、`、`、空格）
- 💡 智能逗号歧义处理
- 🔥 OR逻辑关键词拆分（"名字包含张或李"）
- 📍 索引/表名提示支持
- ⚠️ **错误处理增强** - NLParseException 自定义异常，详细错误信息和位置提示
- 🔍 **智能拼写检查** - OperatorSuggester 基于 Levenshtein 距离算法的拼写建议

**🏗️ 架构设计**

- ✨ **策略模式** - Token拆分采用策略模式，代码清晰易扩展
    - `DelimiterSplitStrategy` - 分隔符拆分策略
    - `OperatorSplitStrategy` - 操作符拆分策略
    - `LogicKeywordSplitStrategy` - 逻辑关键词拆分策略
- 🔄 **状态机解析** - 4状态严格转换，保证解析正确性
- 🏛️ **SPI接口设计** - FieldBinder、IntentTranslator支持多数据源
- 🚨 **异常体系** - Builder模式构建异常，支持6种错误类型

**⚡ 性能优化**

- 预编译正则表达式（static final）
- 策略列表构造时初始化，避免重复创建
- 使用SLF4J日志框架
- Token列表复用，减少对象创建
- Levenshtein距离算法优化（动态规划）

## 📄 许可证

Apache License 2.0

## 👤 作者

**surezzzzzz**

- GitHub: [@Sure-Zzzzzz](https://github.com/Sure-Zzzzzz)

## 🙏 致谢

- [HanLP](https://github.com/hankcs/HanLP) - 优秀的中文分词工具
- [Spring Boot](https://spring.io/projects/spring-boot) - 强大的应用框架
