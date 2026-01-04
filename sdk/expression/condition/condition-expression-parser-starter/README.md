# Condition Expression Parser Starter

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8+-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.x+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![ANTLR](https://img.shields.io/badge/ANTLR-4.10.1-orange.svg)](https://www.antlr.org/)

> 一个基于 ANTLR 的条件表达式解析器，将结构化条件表达式解析为 AST（抽象语法树），支持比较、集合、模糊匹配、空值检查等多种运算符，配合 Visitor 模式灵活转换为任意目标格式（SQL、ES DSL、MongoDB Query等）。

## ✨ 特性

- 🎯 **ANTLR 驱动** - 基于 ANTLR 4.10.1，语法严谨，性能优异
- 🚀 **功能完善** - 6大类运算符：比较（=、!=、>、<、>=、<=）、集合（IN、NOT IN）、模糊（LIKE、PREFIX LIKE、SUFFIX LIKE、NOT LIKE）、空值（IS NULL、IS NOT NULL）、逻辑（AND、OR、NOT）、括号优先级
- 📊 **多值类型** - 字符串、整数、浮点数、布尔值、时间范围枚举（30+种预定义范围）
- 🔧 **开箱即用** - Spring Boot Starter 自动配置，零配置启动
- 🏗️ **Visitor 模式** - AST 输出，业务层通过 Visitor 自由转换为目标格式
- 📦 **策略模式** - 值解析采用策略模式，优先级可配置，易扩展
- 🌐 **中英文支持** - 关键字支持中英文，大小写不敏感
- ⚠️ **友好错误** - 自定义异常，详细错误信息、位置提示、友好消息
- 💡 **可扩展** - 关键字映射支持用户自定义扩展（合并/覆盖）

## 🚀 快速开始

### 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:condition-expression-parser-starter:1.0.0-SNAPSHOT'
}
```

### 基础使用

```java
@Autowired
private ConditionExpressionParser parser;

// 简单比较
Expression expr = parser.parse("年龄>18");
// → ComparisonExpression(field=年龄, operator=GT, value=18)

// 复杂表达式
Expression expr = parser.parse(
    "类型='活跃' AND 分类 IN ('高','中') AND 名称 LIKE '测试' AND 备注 IS NOT NULL"
);
// → BinaryExpression(AND) 树形结构

// 使用 Visitor 转换为 SQL
String sql = expr.accept(new SqlVisitor());
// → "type='活跃' AND category IN ('高','中') AND name LIKE '%测试%' AND remark IS NOT NULL"
```

## 📖 支持的语法

### 比较运算符

| 符号  | 中文       | 英文           | 示例             |
|-----|----------|--------------|----------------|
| =   | 等于、是、为   | equals, eq   | `年龄=25`        |
| !=  | 不等于、不是   | not equals, ne, neq | `状态!='已删除'`    |
| >   | 大于       | greater than, gt | `年龄>18`        |
| >=  | 大于等于     | gte          | `年龄>=18`       |
| <   | 小于       | less than, lt | `年龄<60`        |
| <=  | 小于等于     | lte          | `年龄<=60`       |

### 集合运算符

| 关键字       | 中文    | 英文     | 示例                     |
|-----------|-------|--------|------------------------|
| IN        | 在、属于  | in     | `城市 IN ('北京','上海','深圳')` |
| NOT IN    | 不在    | not in | `状态 NOT IN ('已删除','已禁用')` |

### 模糊匹配运算符

| 关键字          | 中文     | 英文           | 说明       | 示例                |
|--------------|--------|--------------|----------|-------------------|
| LIKE         | 包含、匹配  | like         | 模糊匹配     | `名称 LIKE '测试'`   |
| PREFIX LIKE  | 前缀匹配   | prefix like  | 前缀匹配     | `名称 PREFIX LIKE '测试'` |
| SUFFIX LIKE  | 后缀匹配   | suffix like  | 后缀匹配     | `名称 SUFFIX LIKE '测试'` |
| NOT LIKE     | 不包含    | not like     | 模糊不匹配    | `名称 NOT LIKE '删除'` |

**说明：** SDK 只识别运算符类型，业务层根据类型自行决定通配符位置（`%test%`、`test%`、`%test`）。

### 空值检查

| 关键字         | 中文   | 英文          | 示例             |
|-------------|------|-------------|----------------|
| IS NULL     | 空、为空 | is null     | `备注 IS NULL`   |
| IS NOT NULL | 非空   | is not null | `备注 IS NOT NULL` |

### 逻辑运算符

| 符号    | 中文     | 英文  | 示例                      |
|-------|--------|-----|-------------------------|
| AND   | 并且、且、和 | and | `年龄>18 AND 城市='北京'`    |
| OR    | 或者、或   | or  | `状态='活跃' OR 状态='待审核'`  |
| NOT   | 非、不是   | not | `NOT 状态='已删除'`         |

### 括号优先级

```java
parser.parse("(年龄>18 AND 年龄<60) OR 状态='VIP'");
// → ParenthesisExpression 包裹子表达式，控制优先级
```

### 值类型

#### 字符串

```java
parser.parse("名称='张三'");
// → ValueNode(type=STRING, rawValue=张三, parsedValue=张三)
```

#### 数值

```java
// 整数
parser.parse("年龄=25");
// → ValueNode(type=INTEGER, rawValue=25, parsedValue=25L)

// 浮点数
parser.parse("价格=99.99");
// → ValueNode(type=DECIMAL, rawValue=99.99, parsedValue=99.99)
```

#### 布尔值

```java
// 英文
parser.parse("启用=true");   // true
parser.parse("启用=false");  // false

// 中文
parser.parse("启用='真'");   // true
parser.parse("启用='假'");   // false
parser.parse("启用='否'");   // false
```

#### 时间范围枚举

SDK 预定义了 30+ 种时间范围，**不计算具体时间**，只识别关键字并返回枚举值。业务层根据枚举值自行计算时间范围。

```java
parser.parse("时间='近1小时'");
// → ValueNode(type=TIME_RANGE, rawValue=近1小时, parsedValue=LAST_1_HOUR)

parser.parse("时间='近3个月'");
// → ValueNode(type=TIME_RANGE, rawValue=近3个月, parsedValue=LAST_3_MONTHS)

parser.parse("时间='今天'");
// → ValueNode(type=TIME_RANGE, rawValue=今天, parsedValue=TODAY)
```

**支持的时间范围：**

| 分类    | 关键字                                                      |
|-------|----------------------------------------------------------|
| 分钟级   | 近5分钟、近10分钟、近15分钟、近30分钟                                   |
| 小时级   | 近1小时、近6小时、近12小时、近24小时                                    |
| 天级    | 近1天、近3天、近7天                                              |
| 周级    | 近1周、近2周                                                  |
| 月级    | 近1个月、近2个月、近3个月、近三个月、近6个月、近半年、一个月、三个月、半年              |
| 年级    | 近1年、近2年、近3年、一年                                           |
| 相对时间点 | 今天、昨天、前天、本周、上周、本月、上月、本季度、上季度、今年、去年                     |

**扩展时间范围：**

```yaml
# application.yml
io:
  github:
    surezzzzzz:
      sdk:
        expression:
          condition:
            parser:
              custom-time-ranges:
                近2小时: LAST_2_HOURS
                近48小时: LAST_2_DAYS
```

## 🎯 完整示例

### 示例1：简单比较

```java
Expression expr = parser.parse("年龄=25");
ComparisonExpression comp = (ComparisonExpression) expr;

System.out.println(comp.getField());                    // 年龄
System.out.println(comp.getOperator());                 // EQ
System.out.println(comp.getValue().getParsedValue());   // 25L
System.out.println(comp.getValue().getType());          // INTEGER
```

### 示例2：IN 运算符

```java
Expression expr = parser.parse("城市 IN ('北京','上海','深圳')");
InExpression in = (InExpression) expr;

System.out.println(in.getField());        // 城市
System.out.println(in.isNotIn());         // false
System.out.println(in.getValues().size());// 3
System.out.println(in.getValues().get(0).getRawValue()); // 北京
```

### 示例3：逻辑组合

```java
Expression expr = parser.parse("年龄>18 AND 年龄<60");
BinaryExpression binary = (BinaryExpression) expr;

System.out.println(binary.getOperator());              // AND
System.out.println(binary.getLeft().getClass());       // ComparisonExpression
System.out.println(binary.getRight().getClass());      // ComparisonExpression
```

### 示例4：复杂表达式

```java
String expression = "类型='活跃' AND 分类 IN ('高','中') AND " +
                   "名称 LIKE '测试' AND 描述 PREFIX LIKE '用户' AND " +
                   "标签 SUFFIX LIKE '标记' AND 备注 NOT LIKE '删除' AND " +
                   "扩展字段 IS NULL AND 年龄>18 AND 年龄<=60 AND " +
                   "状态!='禁用' AND 时间='近1个月'";

Expression expr = parser.parse(expression);
// → 复杂的 BinaryExpression 树形结构
```

### 示例5：使用 Visitor 转换为 SQL

```java
// 自定义 Visitor 实现
public class SqlVisitor implements ExpressionVisitor<String> {

    @Override
    public String visitComparison(ComparisonExpression expr) {
        String field = mapFieldName(expr.getField());
        String op = mapOperator(expr.getOperator());
        Object value = expr.getValue().getParsedValue();

        return field + " " + op + " " + formatValue(value);
    }

    @Override
    public String visitIn(InExpression expr) {
        String field = mapFieldName(expr.getField());
        String op = expr.isNotIn() ? "NOT IN" : "IN";
        String values = expr.getValues().stream()
            .map(v -> formatValue(v.getParsedValue()))
            .collect(Collectors.joining(",", "(", ")"));

        return field + " " + op + " " + values;
    }

    @Override
    public String visitBinary(BinaryExpression expr) {
        String left = expr.getLeft().accept(this);
        String right = expr.getRight().accept(this);
        return "(" + left + " " + expr.getOperator() + " " + right + ")";
    }

    // ... 实现其他 visit 方法
}

// 使用
Expression expr = parser.parse("年龄>18 AND 城市='北京'");
String sql = expr.accept(new SqlVisitor());
System.out.println(sql);
// → (age > 18 AND city = '北京')
```

### 示例6：转换为 Elasticsearch DSL

```java
public class EsDslVisitor implements ExpressionVisitor<JsonNode> {

    @Override
    public JsonNode visitComparison(ComparisonExpression expr) {
        String field = mapFieldName(expr.getField());
        ComparisonOperator op = expr.getOperator();
        Object value = expr.getValue().getParsedValue();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();

        switch (op) {
            case EQ:
                node.putObject("term").put(field, value.toString());
                break;
            case GT:
                node.putObject("range").putObject(field).put("gt", value.toString());
                break;
            // ... 其他运算符
        }

        return node;
    }

    @Override
    public JsonNode visitBinary(BinaryExpression expr) {
        JsonNode left = expr.getLeft().accept(this);
        JsonNode right = expr.getRight().accept(this);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();

        String boolType = expr.getOperator() == LogicalOperator.AND ? "must" : "should";
        ArrayNode array = mapper.createArrayNode();
        array.add(left);
        array.add(right);

        node.putObject("bool").putArray(boolType).addAll(array);

        return node;
    }

    // ... 实现其他 visit 方法
}

// 使用
Expression expr = parser.parse("年龄>18 AND 城市='北京'");
JsonNode dsl = expr.accept(new EsDslVisitor());
System.out.println(dsl.toPrettyString());
```

## 🔧 配置

```yaml
# application.yml
io:
  github:
    surezzzzzz:
      sdk:
        expression:
          condition:
            parser:
              enabled: true                 # 是否启用（默认 true）
              custom-time-ranges:           # 自定义时间范围映射
                近2小时: LAST_2_HOURS
                近48小时: LAST_2_DAYS
```

## 🏗️ 工作原理

### 架构设计

```
用户输入 (条件表达式字符串)
    ↓
ConditionExpressionParser (解析器入口)
    ↓
ANTLR 4.10.1
    ├─ ConditionExprLexer (词法分析器)
    ├─ ConditionExprParser (语法分析器)
    └─ ParseTree (ANTLR 解析树)
    ↓
AstBuilder (Visitor实现)
    ├─ ValueParser (值解析策略管理器)
    │   ├─ BooleanValueParseStrategy (优先级1)
    │   ├─ TimeRangeValueParseStrategy (优先级2)
    │   ├─ NumberValueParseStrategy (优先级3)
    │   └─ StringValueParseStrategy (优先级4)
    └─ 构建自定义 AST
    ↓
Expression (AST 根节点)
    ├─ ComparisonExpression
    ├─ InExpression
    ├─ LikeExpression
    ├─ NullExpression
    ├─ BinaryExpression
    ├─ UnaryExpression
    └─ ParenthesisExpression
    ↓
业务层 Visitor 转换
    ├─ SqlVisitor → SQL
    ├─ EsDslVisitor → ES DSL
    ├─ MongoQueryVisitor → MongoDB Query
    └─ 自定义 Visitor
```

**关键设计模式：**

1. **Visitor 模式** - AST 遍历与转换
   - `ExpressionVisitor<R>` 接口定义访问方法
   - 业务层实现 Visitor 自由转换为目标格式
   - 解耦解析与业务逻辑

2. **策略模式** - 值解析
   - `ValueParseStrategy` 接口
   - 4个策略：布尔、时间范围、数字、字符串
   - 按优先级依次尝试，第一个匹配的策略生效

3. **建造者模式** - AST 构建 & 异常构建
   - Lombok `@Builder` 注解
   - `ConditionExpressionParseException.builder()`

4. **ANTLR 语法驱动** - 词法和语法分析
   - `.g4` 语法文件定义语言规则
   - 自动生成 Lexer/Parser/Visitor
   - 语法严谨、性能优异

### ANTLR 语法文件

核心语法定义（简化版）：

```antlr
grammar ConditionExpr;

parse: expression EOF;

expression: andExpression (OR andExpression)*;

andExpression: unaryExpression (AND unaryExpression)*;

unaryExpression
    : NOT unaryExpression
    | primaryExpression
    ;

primaryExpression
    : '(' expression ')'
    | condition
    ;

condition
    : field comparisonOp value                      // 比较
    | field IN valueList                            // IN
    | field NOT IN valueList                        // NOT IN
    | field LIKE value                              // LIKE
    | field PREFIX LIKE value                       // PREFIX LIKE
    | field SUFFIX LIKE value                       // SUFFIX LIKE
    | field NOT LIKE value                          // NOT LIKE
    | field IS NULL                                 // IS NULL
    | field IS NOT NULL                             // IS NOT NULL
    ;

field: IDENTIFIER;
value: STRING | NUMBER | BOOLEAN | TIME_RANGE_KEYWORD;
valueList: '(' value (',' value)* ')';
```

## ⚠️ 错误处理

### ConditionExpressionParseException

所有解析错误都会抛出自定义异常，包含详细信息：

```java
try {
    Expression expr = parser.parse("年龄> AND 状态='活跃'");
} catch (ConditionExpressionParseException e) {
    // 错误类型
    ErrorType type = e.getErrorType();  // SYNTAX_ERROR

    // 原始表达式
    String expression = e.getExpression();  // "年龄> AND 状态='活跃'"

    // 错误行号和列号
    Integer line = e.getLine();  // 1
    Integer column = e.getColumn();  // 4

    // 有问题的 token
    String offending = e.getOffendingToken();  // "AND"

    // 友好的错误消息
    String message = e.getMessage();
    // → "语法错误：不期望的输入 "AND""
}
```

**错误类型：**

| 错误类型           | 说明          | 示例                  |
|----------------|-------------|---------------------|
| `SYNTAX_ERROR` | 语法错误        | `年龄> AND 状态='活跃'`  |
| `EMPTY_EXPRESSION` | 空表达式或只有空格   | `""` 或 `"   "`     |
| `INVALID_VALUE` | 值格式错误       | -                   |

## 📚 核心类说明

### Expression（AST 基类）

```java
public abstract class Expression {
    public abstract <R> R accept(ExpressionVisitor<R> visitor);
}
```

**子类：**

- `ComparisonExpression` - 比较表达式
- `InExpression` - IN/NOT IN 表达式
- `LikeExpression` - LIKE 表达式
- `NullExpression` - NULL 检查表达式
- `BinaryExpression` - 二元逻辑表达式（AND/OR）
- `UnaryExpression` - 一元逻辑表达式（NOT）
- `ParenthesisExpression` - 括号表达式

### ValueNode（值节点）

```java
@Data
@Builder
public class ValueNode {
    private ValueType type;        // 值类型：STRING/INTEGER/DECIMAL/BOOLEAN/TIME_RANGE
    private String rawValue;       // 原始字符串
    private Object parsedValue;    // 解析后的值
}
```

### ExpressionVisitor（访问者接口）

```java
public interface ExpressionVisitor<R> {
    R visitComparison(ComparisonExpression expression);
    R visitIn(InExpression expression);
    R visitLike(LikeExpression expression);
    R visitNull(NullExpression expression);
    R visitBinary(BinaryExpression expression);
    R visitUnary(UnaryExpression expression);
    R visitParenthesis(ParenthesisExpression expression);
}
```

## ❓ 常见问题

### Q: 如何添加自定义时间范围？

配置文件中添加映射：

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        expression:
          condition:
            parser:
              custom-time-ranges:
                近2小时: LAST_2_HOURS
                最近一周: LAST_1_WEEK
```

### Q: 如何实现字段名映射？

在 Visitor 中实现 `mapFieldName` 方法：

```java
public class SqlVisitor implements ExpressionVisitor<String> {

    private static final Map<String, String> FIELD_MAPPING = Map.of(
        "年龄", "age",
        "城市", "city",
        "名称", "name"
    );

    private String mapFieldName(String hint) {
        return FIELD_MAPPING.getOrDefault(hint, hint);
    }

    @Override
    public String visitComparison(ComparisonExpression expr) {
        String field = mapFieldName(expr.getField());  // 映射字段名
        // ...
    }
}
```

### Q: 时间范围如何计算具体时间？

SDK 只返回枚举值，业务层根据枚举自行计算：

```java
public class TimeRangeCalculator {

    public static LocalDateTime[] calculate(TimeRange range) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start;

        switch (range) {
            case LAST_1_HOUR:
                start = now.minusHours(1);
                break;
            case LAST_3_MONTHS:
                start = now.minusMonths(3);
                break;
            case TODAY:
                start = now.toLocalDate().atStartOfDay();
                break;
            // ... 其他枚举
        }

        return new LocalDateTime[] { start, now };
    }
}
```

### Q: 支持哪些数据库？

解析器与数据库无关，生成的 AST 可用于任何查询引擎。通过实现不同的 Visitor 转换为目标格式：

- SQL（MySQL、PostgreSQL、Oracle等）
- Elasticsearch DSL
- MongoDB Query
- JPA Criteria API
- MyBatis Dynamic SQL

### Q: LIKE 运算符的通配符在哪里添加？

SDK 只识别运算符类型，业务层根据 `MatchOperator` 枚举决定通配符位置：

```java
@Override
public String visitLike(LikeExpression expr) {
    String field = mapFieldName(expr.getField());
    String value = expr.getValue().getRawValue();
    String pattern;

    switch (expr.getOperator()) {
        case LIKE:
            pattern = "%" + value + "%";  // 模糊匹配
            break;
        case PREFIX:
            pattern = value + "%";        // 前缀匹配
            break;
        case SUFFIX:
            pattern = "%" + value;        // 后缀匹配
            break;
        case NOT_LIKE:
            return field + " NOT LIKE '%" + value + "%'";
    }

    return field + " LIKE '" + pattern + "'";
}
```

## 📦 依赖

- Java 8+
- Spring Boot 2.x+
- ANTLR 4.10.1

## 📝 版本历史

### v1.0.0 (2026-01-04)

**✨ 核心功能**

- 🎯 支持 6 大类运算符：比较、集合、模糊匹配、空值检查、逻辑运算、括号
- 🚀 支持 5 种值类型：字符串、整数、浮点数、布尔、时间范围枚举
- 📊 基于 ANTLR 4.10.1，语法严谨，性能优异
- 🔧 Visitor 模式，灵活转换为任意目标格式
- 🌐 中英文关键字支持，大小写不敏感
- ⚠️ 自定义异常，详细错误信息

**🏗️ 架构设计**

- ✨ **ANTLR 驱动** - 词法/语法分析，自动生成 Lexer/Parser/Visitor
- 🔄 **Visitor 模式** - 业务层通过 Visitor 自由转换 AST
- 📦 **策略模式** - 值解析策略，按优先级依次匹配
- 🏛️ **建造者模式** - AST 和异常构建

**⚡ 性能优化**

- ANTLR 高性能解析
- 策略优先级排序，快速匹配
- 关键字映射预构建

## 📄 许可证

Apache License 2.0

## 👤 作者

**surezzzzzz**

- GitHub: [@Sure-Zzzzzz](https://github.com/Sure-Zzzzzz)

## 🙏 致谢

- [ANTLR](https://www.antlr.org/) - 强大的语法分析工具
- [Spring Boot](https://spring.io/projects/spring-boot) - 优秀的应用框架
