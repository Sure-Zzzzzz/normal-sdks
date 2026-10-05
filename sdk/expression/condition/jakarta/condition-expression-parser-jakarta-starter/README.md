# Condition Expression Parser Jakarta Starter

把结构化条件字符串解析为 AST（按运算顺序组织的条件树）。调用方通过 Visitor（逐类处理节点的接口）转换结果，可用于筛选规则、查询条件和条件校验。

模块只做解析：不执行数据库查询、不计算当前时间、不提供 HTTP 接口，也不包含自然语言能力。

适合已有查询执行器、规则引擎或条件校验流程，需要将明确语法的条件字符串转为可编程处理对象的 Spring Boot 应用。SQL、Elasticsearch 等目标格式的转换及执行由调用方实现；引入本模块不会自动获得数据库查询能力。

## 最小配置

Spring Boot 3.4.2、Java 17 或 21：

```groovy
dependencies {
    implementation 'io.github.sure-zzzzzz:condition-expression-parser-jakarta-starter:1.0.0'
}
```

默认启用，无需额外配置。需要明确表达启用状态时：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        expression:
          condition:
            parser:
              enabled: true
```

在宿主自己的 Spring 组件中注入解析器，无需扫描 SDK 包或添加启用注解。下面使用宿主提供的 Lombok 做构造器注入：

```java
import io.github.surezzzzzz.sdk.expression.condition.parser.model.Expression;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.ConditionExpressionParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConditionService {
    private final ConditionExpressionParser parser;

    public Expression parse(String condition) {
        return parser.parse(condition);
    }
}
```

取得解析器后，可读取条件树、字段和值，而不是依赖 `toString()` 判断解析结果：

```java
Expression ast = parser.parse("status='active' AND amount>=18");
Set<String> fields = ExpressionCollectors.collectFields(ast);
List<ValueNode> values = ExpressionCollectors.collectValues(ast);
int conditions = ExpressionMetrics.countConditions(ast);
// fields 按顺序为 status、amount；conditions 为 2。
// values 分别为 STRING / "active"、INTEGER / Long 值 18。
```

这些工具位于 `io.github.surezzzzzz.sdk.expression.condition.parser.support`，模型位于同一基础包下的 `model`。`fields` 按首次出现顺序去重；`values` 按树顺序保留值及重复项。两种返回集合均只读，值节点本身仍可修改。存在性、空值检查没有值，不会产生一个假的 null 值节点。

### 不启动 Spring

不启动 Spring 时，可手动创建相同解析器：

```java
ValueParser values = new ValueParser(Arrays.asList(
        new BooleanValueParseStrategy(),
        new TimeRangeValueParseStrategy(),
        new NumberValueParseStrategy(),
        new StringValueParseStrategy()));
ConditionExpressionParser parser = new ConditionExpressionParser(values);
Expression ast = parser.parse("status='active'");
```

值策略与解析器都位于 `io.github.surezzzzzz.sdk.expression.condition.parser.parser`。手动使用仍需提供运行库：ANTLR runtime 4.10.1（生成解析器的运行库）、Spring Core/AOP 和 SLF4J（日志接口）；Spring Boot 宿主通常已提供后两者，不要求 Web 或 JSON 组件。ANTLR 构建工具只供 SDK 生成语法代码，消费方不需要重新生成。

## 全量配置

配置前缀为 `io.github.surezzzzzz.sdk.expression.condition.parser`。下面展示全部可配置项及默认值：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        expression:
          condition:
            parser:
              enabled: true
              limits:
                max-length: 16384
                max-tokens: 4096
                max-parse-depth: 32
                max-ast-depth: 64
                max-ast-nodes: 1000
                max-conditions: 256
                max-in-values: 1000
```

## 逐项配置解释

| 配置项 | 默认值 | 含义 |
| --- | --- | --- |
| enabled | true | 是否装配默认解析器、值解析器和四种策略；禁用不移除调用方自行注册的 Bean |
| limits.max-length | 16384 | 输入 String.length 上限，单位是 Java UTF-16 单元，不是字节 |
| limits.max-tokens | 4096 | 非 EOF（输入终点）词法单元上限；受限读取，不先无限加载 |
| limits.max-parse-depth | 32 | 表达式括号及前缀 NOT 的累计递归负担；最大允许配置为 64 |
| limits.max-ast-depth | 64 | 实际条件树深度，计入括号、一元与二元节点 |
| limits.max-ast-nodes | 1000 | 树节点数量，容器和条件均计数 |
| limits.max-conditions | 256 | 叶子条件总数；IN 是一个条件，不是每个值一个条件 |
| limits.max-in-values | 1000 | 每个 IN / NOT IN 列表的值数 |

全部限值必须为正，不支持用 0 或负数关闭保护。默认容量不是已测得的性能上限。

`max-parse-depth` 统计表达式括号和前缀 `NOT`，不把 `IN (1,2)` 的列表括号或 `field NOT IN (...)` 的 `NOT` 当作表达式递归。`max-ast-depth` 统计构建出的实际树，因此没有深括号的长 AND/OR 链也可能超限。

Spring 配置在默认解析器创建时复制并校验；修改 Properties 对象不会自动更新已创建的解析器。非法配置会使默认解析器装配失败。`enabled: false` 只关闭本模块默认自动装配，不禁止手动创建解析器，也不移除宿主自己注册的组件。

### 单次调用覆盖

构造时与每次解析的显式选项均独立复制。显式选项完整使用，不与默认值合并，null 拒绝：

```java
ParseOptions options = ParseOptions.builder()
        .maxLength(4096)
        .maxConditions(64)
        .build();
Expression ast = parser.parse("status='active'", options);
```

`ParseOptions` 位于 `model` 包。Builder 中没有指定的字段取 SDK 默认值，而不是继承当前解析器从 YAML 读取的值。上例仅对该次调用使用 4096 长度、64 条件的限制，不改变后续 `parse(String)`。

树工具默认使用同一组 SDK 容量。通过显式解析选项提高容量后，如需遍历更大的树，使用 `ExpressionTreeHelper.walk(ast, options, consumer)`；普通 Collectors、Metrics 和 Printer 不会自动继承那次请求的选项。

## 语法与结果

| 分类 | 支持形式 |
| --- | --- |
| 比较 | =、==、!=、<>、>、>=、<、<= |
| 集合 | IN、NOT IN |
| 匹配 | LIKE、NOT LIKE、PREFIX LIKE、NOT PREFIX LIKE、SUFFIX LIKE、NOT SUFFIX LIKE |
| 空值 | IS NULL、IS NOT NULL |
| 存在性 | EXISTS、NOT EXISTS |
| 逻辑 | NOT、AND、OR、括号 |

英文关键字大小写不敏感，优先级为括号、NOT、AND、OR，同级从左向右构树。解析必须消费全部输入，不接受合法前缀之后的残留内容。

```java
parser.parse("amount>=18 AND status IN ('active','pending')");
parser.parse("(amount<10 OR amount>100) AND NOT status='disabled'");
parser.parse("title NOT PREFIX LIKE 'sample' AND note IS NOT NULL");
parser.parse("record.extraField EXISTS");
parser.parse("数量大于等于18 且 状态包含于('有效','待定')");
```

中文别名包括：等于、不等于、大于/晚于、大于等于/不小于、小于/早于、小于等于/不大于、包含于、模糊匹配/包含、前缀、后缀、是空、是非空、存在/有、且/并且、或/或者、非。否定形式按同一语法组合，例如 `名称 非 前缀 模糊匹配 'sample'`。

英文标识符和连续中文字段可直接使用；点路径如 `record.status` 只表示字段文本，不代表数据库嵌套查询。字段与保留字冲突、含空格时用反引号，例如 `` `AND`=1 ``、`` `字段 1`='sample' ``。反引号不能为空、不能跨行或包含反引号，不定义转义。

普通字符串必须使用 ASCII 单/双引号，数字、布尔和已定义的时间快捷词可不加引号。引号中的反斜线是普通字符，不解释转义；需要包含单引号时使用双引号，反之亦然。不能用反斜线表示同类引号，不支持多行、科学计数、函数、算术、正则和变量插值。

`IN` 使用圆括号且至少一个值，保留值次序、重复项和实际类型；不支持 `IN []`。`LIKE` 等匹配节点只记录运算符和值，不自动添加 `%` 或 `*`，具体匹配语义由目标执行器决定。

### 值类型

去掉引号后仍按策略识别，**引号不强制字符串类型**：

| 输入 | ValueType | parsedValue |
| --- | --- | --- |
| 18、'18' | INTEGER | Long |
| -1.25、'-1.25' | DECIMAL | 有限 Double |
| true、'True'、'真' | BOOLEAN | Boolean |
| false、'FaLsE'、'假'、'否' | BOOLEAN | Boolean |
| 今天、'近7天' | TIME_RANGE | TimeRange |
| 'sample'、'' | STRING | String |

`rawValue` 保留去引号后的文本，`parsedValue` 保留实际 Java 类型。默认数字策略拒绝数字溢出和非有限 Double；命中任意策略后转换失败均拒绝，不退回字符串。

`ValueParser.parse(null)` 返回 NULL 节点；表达式本身不支持 `field=NULL`，应使用 `field IS NULL`。

读取值时先检查类型，再调用对应方法；`asInteger()` 返回 Long，不是 Integer，`asDecimal()` 不会把整数自动转成 Double：

```java
ComparisonExpression condition = (ComparisonExpression) parser.parse("amount=18");
ValueNode value = condition.getValue();
if (value.isInteger()) {
    Long amount = value.asInteger();
}
```

### 时间范围

支持最近 5/10/15/30 分钟，1/6/12/24 小时，1/3/7/14/30/60/90 天，1/2 周，1/2/3/6 个月，1/2/3 年，以及今天、昨天、前天、本周/上周、本月/上月、本季度/上季度、今年/去年。主词和中文数字别名通过 `TimeRange.getAllKeywords()` 获取。

时间枚举只给出关键字、数量和单位，不读取时钟。14 天和 1 个月、30 天和 1 个月不等价；调用方应明确时区、截止点和日历周期语义。

```java
ComparisonExpression condition = (ComparisonExpression) parser.parse("createdAt='近14天'");
TimeRange range = condition.getValue().asTimeRange();
// range 为 LAST_14_DAYS，amount 为 14，unit 为 DAYS。
// 这里不会产生开始/结束时间，也不会访问数据库。
```

`TimeRange` 位于 `constant` 包。`fromKeyword` 按已定义关键字查找，未知值返回 null；`getAllKeywords()` 返回只读映射，`getAliases()` 返回独立数组。协议标识使用 `getCode()` / `fromCode()`，不要用中文展示词当稳定代码。

### 条件树与常用工具

| 节点 | 内容 |
| --- | --- |
| ComparisonExpression | field、比较 operator、ValueNode |
| InExpression | field、notIn、ValueNode 列表 |
| LikeExpression | field、匹配/存在性 operator；EXISTS / NOT EXISTS 的 value 为 null |
| NullExpression | field、isNull；true 为 IS NULL，false 为 IS NOT NULL，没有值节点 |
| BinaryExpression | left、right、AND / OR |
| UnaryExpression | operand、NOT |
| ParenthesisExpression | 被括号包围的 expression |

| API | 用途 |
| --- | --- |
| ExpressionCollectors.collectFields / collectValues | 收集字段或实际值 |
| ExpressionMetrics.calculateDepth / countConditions | 计算逻辑深度、叶子条件数 |
| ExpressionMetrics.validateDepth / validateConditionCount | 校验正数上限，超限抛 ExpressionValidationException |
| ExpressionTreeHelper.walk | 有界校验并遍历，返回实际深度、逻辑深度、节点数与条件数 |
| BaseExpressionVisitor.containsField / findFieldCondition | 查找字段及其首次出现的条件 |
| BaseExpressionVisitor.findConditions | 按谓词查找叶子条件 |
| ExpressionPrinter.toCompactString / toTreeString | 显式展示内容，不是安全日志或序列化协议 |

空树的 Collectors 返回空集合，Metrics 返回 0，Printer 返回空字符串。主入口解析 null、空字符串或纯空白时抛 `EMPTY_EXPRESSION`，不会把它解释为“匹配所有数据”。

## 最佳实践

### 字段与权限先校验再执行

解析成功只证明语法及容量满足要求，不证明字段存在、调用方有权限或数据库查询可执行。使用 `collectFields` 核对白名单；执行器仍须校验字段类型、权限、成本和索引范围。

```java
Set<String> allowedFields = Set.of("status", "amount");
Expression ast = parser.parse(input);
boolean fieldsAllowed = allowedFields.containsAll(ExpressionCollectors.collectFields(ast));
// fieldsAllowed 为 false 时终止执行；通过也不代表数据权限和字段类型校验已完成。
```

字段标签转换只修改节点的 field，不要全局替换表达式文本，否则会同时改掉引号内的值。

转换 SQL 时，字段通过允许列表映射到固定列名，值通过参数绑定传递，禁止把 `rawValue` 拼接成 SQL。转换其他查询协议时同样使用目标协议的结构化值，不把表达式直接当脚本执行。必选数据权限条件由宿主强制组合，不能交给用户输入决定。

### 使用 Visitor 消费所有操作符

根据 Comparison、In、Like、Null、Binary、Unary、Parenthesis 七类节点处理结果。EXISTS/NOT EXISTS 位于 LikeExpression，其 value 为 null；不要无条件读取该值。

`BaseExpressionVisitor` 提供递归访问和组合钩子。默认叶子返回 getDefaultResult，若用它编写执行器，未实现的条件必须明确拒绝，不能忽略。直接访问手工树前先通过 `ExpressionTreeHelper` 校验；返回树可变，禁止遍历期间并发修改。

前缀 NOT 应由执行器保留补集语义，不应盲目将 > 翻为 <=；缺字段和多值数据下两者不等价。`isAllAnd/isAllOr` 只检查二元连接拓扑，不消除 NOT、不证明逻辑语义。

下面演示 `BaseExpressionVisitor` 的叶子处理和组合钩子，只统计条件数量，不执行查询；常规计数可直接使用 `ExpressionMetrics.countConditions`：

```java
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.LogicalOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.UnaryOperator;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.BaseExpressionVisitor;

class ConditionCountVisitor extends BaseExpressionVisitor<Integer> {
    @Override
    public Integer visitComparison(ComparisonExpression expression) { return 1; }
    @Override
    public Integer visitIn(InExpression expression) { return 1; }
    @Override
    public Integer visitLike(LikeExpression expression) { return 1; }
    @Override
    public Integer visitNull(NullExpression expression) { return 1; }
    @Override
    protected Integer combineBinaryResults(Integer left, Integer right, LogicalOperator operator) {
        return left + right;
    }
    @Override
    protected Integer combineUnaryResult(Integer operand, UnaryOperator operator) {
        return operand;
    }
    @Override
    protected Integer getDefaultResult() { return 0; }
}
```

调用 `parser.parse("amount=18 AND NOT (status IN ('active','pending') OR note EXISTS)").accept(new ConditionCountVisitor())` 返回 3。括号递归由基类处理；计数时忽略逻辑真假是有意的，编写查询执行器时则必须保留 AND、OR、NOT 的语义。

### 按业务需要替换值策略

内置优先级依次为布尔 1、时间 2、数字 3、字符串 99，以 getPriority 为准，不使用 Spring @Order。注册列表复制；并列按目标实现类全限定名排序，同类重复注册拒绝。

新增实现 ValueParseStrategy 的 Bean 可参与识别；替换单个内置策略需提供该具体类型或子类 Bean。提供自己的 ValueParser 可替换整套识别，提供 ConditionExpressionParser 或其子类可替换主入口。

需要保留 '001' 或 'true' 为精确字符串时，提供自己的 ValueParser 或较高优先级策略。自定义策略应返回与 ValueType 一致的值，自己保证线程安全，不向异常消息写入输入。

例如只把前导零数字识别为字符串，其余值继续走内置策略：

```java
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ValueType;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.ValueNode;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.ValueParseStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ValueStrategyConfiguration {
    @Bean
    ValueParseStrategy leadingZeroStringStrategy() {
        return new ValueParseStrategy() {
            @Override
            public boolean canParse(String raw) { return raw != null && raw.matches("0[0-9]+"); }
            @Override
            public ValueNode parse(String raw) {
                return ValueNode.builder().type(ValueType.STRING).rawValue(raw).parsedValue(raw).build();
            }
            @Override
            public int getPriority() { return 0; }
        };
    }
}
```

此配置下 `code='001'` 的值为 String `"001"`。策略拿到的是去引号后的值，不知道原文是否带引号，因此 `code=001` 也会采用相同规则；它不接收字段名，字段专属转换应在消费条件树时处理。不要依赖运行期间修改策略优先级，注册时的排序信息已经冻结。

### 区分逻辑度量与资源保护

`ExpressionMetrics.calculateDepth` 保留括号透明的逻辑深度；主解析器的实际树深度包含括号。长平链也会触发树深度限制，不能只数括号。

各树工具在默认容量下拒绝循环、空孩子、未知类型和无效叶子。共享子树按每次出现统计，而不是去重后的对象数量。

`ExpressionTreeHelper.walk` 在开始调用消费函数之前校验完整树；树非法时不执行部分回调。消费函数自己的异常、副作用和遍历期间的修改不由 SDK 回滚。直接调用 `ast.accept(visitor)` 不会自动进行该完整校验，手工构树后应先校验再访问。

### 明确时间和并发约束

“近14天”等最近范围可以按数量与单位计算；“本月”“上季度”等日历周期需要单独定义周期起点和截止点，不能统一套用一次减法。一次转换固定时钟与时区，避免同一条件树里的时间范围基于不同的当前时间。Condition 不替目标系统定义边界是否包含或缺字段语义。

解析器每次调用独占词法、语法和构树状态，内置策略可以随解析器复用；自定义策略须自行保证线程安全。返回树由调用方持有，解析器不缓存它；共享树时禁止并发修改字段、值或子节点。

### 安全处理错误与诊断

捕获 ConditionExpressionParseException，使用 errorType、line、column 和固定默认消息。行号从 1、列号从 0 开始，按 ANTLR 的 Unicode 码点计列，无位置为 -1。

```java
try {
    Expression ast = parser.parse(input);
    // 继续字段校验和条件转换。
} catch (ConditionExpressionParseException exception) {
    String category = exception.getErrorType().getCode();
    int line = exception.getLine();
    int column = exception.getColumn();
    // 仅用上述安全事实生成宿主错误响应，不返回整个异常对象或原始输入。
}
```

语法、值转换及解析容量超限使用 `ConditionExpressionParseException`；显式选项非法使用 `IllegalArgumentException`；手工树无效或树工具容量超限使用 `ExpressionValidationException`。后者提供 `metricType`、`actualValue`、`maxValue`，`INVALID_TREE` 应按结构错误处理，不当作一个有效的数量测量。

为兼容保留的 expression/offendingToken getter 含原文，只供主动诊断；不要直接序列化整个异常为 HTTP 响应。显式 builder 消息和原因由调用方负责脱敏。

默认模型 toString 不包含字段和值。解析器 DEBUG 只输出长度、计数、类别、位置与耗时。`ExpressionPrinter` 是主动内容展示，不适合通用日志，也不是可重新解析的序列化协议。

排障时按解析器的日志名称开启现有 DEBUG，不额外配置 SDK 诊断开关：

```yaml
logging:
  level:
    io.github.surezzzzzz.sdk.expression.condition.parser.parser.ConditionExpressionParser: DEBUG
```

这不是结果数据脱敏组件。默认日志不回显内容，不意味着 AST、ValueNode 或原文 getter 中的敏感值已被清除；对外结果脱敏仍由宿主或目标查询组件负责。

## 兼容与迁移

| 项目 | 版本与边界 |
| --- | --- |
| Maven 坐标 | io.github.sure-zzzzzz:condition-expression-parser-jakarta-starter:1.0.0 |
| Spring Boot | 3.4.2；其他补丁版本不包含在当前验证结论中 |
| Java | 编译目标 17；运行验证 17.0.20.1、21.0.12.1 |
| ANTLR | 工具与 runtime 均为 4.10.1，构建工具不进入运行依赖 |
| 自动配置 | Boot3 AutoConfiguration.imports；无需消费方手工生成语法代码 |

宿主主动收敛为其他 ANTLR runtime 版本时需单独验证，不能忽略生成版本不匹配。本模块不依赖 Search、Elasticsearch、数据源路由或自然语言组件；与其他查询组件的适配不属于本模块的内置能力。

旧 javax Starter 与本 Jakarta Starter 保持相同 Java 包路径，不能同时加载。保留解析器、AST、Visitor、值策略及常用工具 API；新增 ParseOptions 和实际树遍历工具。直接调用公开 AstBuilder、生成 Lexer/Parser 属于低层 API，不享受主入口全部前置保护。

迁移时核对以下行为：天数快捷词不再近似月份、布尔值支持混合大小写、策略按 getPriority 而不是 @Order 排序、默认异常及模型不再回显内容、存在性收集不返回 null、无效手工树及资源超限明确拒绝。
