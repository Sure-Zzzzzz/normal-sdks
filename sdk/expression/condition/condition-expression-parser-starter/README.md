# Condition Expression Parser Starter

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8+-green.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.x+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![ANTLR](https://img.shields.io/badge/ANTLR-4.10.1-orange.svg)](https://www.antlr.org/)

> 一个基于 ANTLR 的条件表达式解析器，将结构化条件表达式解析为 AST（抽象语法树），支持比较、集合、模糊匹配、空值检查等多种运算符，配合
> Visitor 模式灵活转换为任意目标格式（SQL、ES DSL、MongoDB Query等）。

## 特性

- **ANTLR 驱动** - 基于 ANTLR 4.10.1，语法严谨，性能优异
- **功能完善** - 8大类运算符：比较、集合、模糊（LIKE/PREFIX/SUFFIX + NOT_LIKE/NOT_PREFIX/NOT_SUFFIX）、空值、存在性（EXISTS/NOT EXISTS）、逻辑、括号优先级
- **中文友好** - 运算符支持中文写法（`等于`/`且`/`包含于`…），中文字段名无需空格分隔
- **大小写不敏感** - 英文关键字 `AND`/`And`/`and` 均可
- **多值类型** - 字符串、整数、浮点数、布尔值、时间范围枚举（30+种预定义范围）
- **开箱即用** - Spring Boot Starter 自动配置，零配置启动
- **Visitor 模式** - AST 输出，业务层通过 Visitor 自由转换为目标格式
- **友好错误** - 自定义异常，详细错误信息、位置提示，不暴露内部 token

## 快速开始

### 添加依赖

```gradle
dependencies {
    implementation 'io.github.sure-zzzzzz:condition-expression-parser-starter:1.0.5'
}
```

### 基础使用

```java
@Autowired
private ConditionExpressionParser parser;

// 简单比较
Expression expr = parser.parse("年龄>18");
// → ComparisonExpression(field=年龄, operator=GT, value=18)

// 中文运算符
Expression expr = parser.parse("年龄等于25");
// → ComparisonExpression(field=年龄, operator=EQ, value=25)

// 复杂表达式
Expression expr = parser.parse(
        "类型='活跃' AND 分类 IN ('高','中') AND 名称 LIKE '测试' AND 备注 IS NOT NULL"
);
// → BinaryExpression(AND) 树形结构

// 使用 Visitor 转换为 SQL
String sql = expr.accept(new SqlVisitor());
```

## 支持的语法

### 比较运算符

| 符号 | 中文     | 示例             |
|----|--------|----------------|
| =  | 等于     | `年龄=25` 或 `年龄等于25` |
| != | 不等于    | `状态!='已删除'` 或 `状态不等于'已删除'` |
| >  | 大于、晚于  | `年龄>18` 或 `年龄大于18` 或 `时间晚于'2025-01-01'` |
| >= | 大于等于   | `年龄>=18` |
| <  | 小于、早于  | `年龄<60` 或 `时间早于'2025-12-31'` |
| <= | 小于等于   | `年龄<=60` |

### 集合运算符

| 关键字    | 中文   | 示例                        |
|--------|------|---------------------------|
| IN     | 包含于  | `城市 IN ('北京','上海','深圳')`  |
| NOT IN |      | `状态 NOT IN ('已删除','已禁用')` |

### 模糊匹配运算符

| 关键字           | 中文     | 说明      | 示例                         |
|---------------|--------|---------|----------------------------|
| LIKE          | 包含     | 模糊匹配   | `名称 LIKE '测试'`             |
| PREFIX LIKE   | 前缀     | 前缀匹配   | `名称 PREFIX LIKE '测试'`        |
| SUFFIX LIKE   | 后缀     | 后缀匹配   | `名称 SUFFIX LIKE '测试'`        |
| NOT LIKE      | 不包含    | 模糊不匹配  | `名称 NOT LIKE '删除'`            |
| NOT PREFIX LIKE | 前缀不匹配 | 排除前缀匹配 | `名称 NOT PREFIX LIKE '张'`     |
| NOT SUFFIX LIKE | 后缀不匹配 | 排除后缀匹配 | `邮箱 NOT SUFFIX LIKE '@spam.com'` |

> SDK 只识别运算符类型，业务层根据类型自行决定通配符位置（`%test%`、`test%`、`%test`）。

### 空值检查

| 关键字         | 中文  | 示例               |
|-------------|-----|------------------|
| IS NULL     | 空   | `备注 IS NULL`     |
| IS NOT NULL | 非空  | `备注 IS NOT NULL` |

### 存在性检查

| 关键字      | 中文     | 示例               |
|----------|--------|------------------|
| EXISTS   | 存在     | `备注 EXISTS`      |
| NOT EXISTS | 不存在 | `备注 NOT EXISTS`  |

### 逻辑运算符

| 符号  | 中文   | 示例                    |
|-----|------|-----------------------|
| AND | 并且、且 | `年龄>18 AND 城市='北京'`   |
| OR  | 或者、或 | `状态='活跃' OR 状态='待审核'` |
| NOT | 非    | `NOT 状态='已删除'`        |

> 所有英文关键字大小写不敏感：`AND` / `And` / `and` 均可。

### 括号优先级

```java
parser.parse("(年龄>18 AND 年龄<60) OR 状态='VIP'");
```

### 值类型与引号规则

| 值类型 | 引号 | 示例 |
|-------|------|------|
| 数字 | 不加 | `年龄=25`、`价格=99.9` |
| 布尔值 | 不加 | `启用=true`、`启用='真'` |
| 时间范围 | 加引号 | `时间='近1小时'` |
| 字符串/中文值 | **必须加** | `名称='张三'`、`状态="活跃"` |

> 引号支持 ASCII 单引号 `'` 和双引号 `"`，不支持中文引号 `""`。

#### 时间范围

SDK 预定义了 30+ 种时间范围，只识别关键字并返回枚举值，不计算具体时间。业务层根据枚举的 `amount` 和 `unit` 自行计算。

```java
parser.parse("时间='近1小时'");
// → ValueNode(type=TIME_RANGE, parsedValue=LAST_1_HOUR)

parser.parse("时间='近3个月'");
// → ValueNode(type=TIME_RANGE, parsedValue=LAST_3_MONTHS)

parser.parse("时间='今天'");
// → ValueNode(type=TIME_RANGE, parsedValue=TODAY)
```

| 分类    | 关键字                                  |
|-------|--------------------------------------|
| 分钟级   | 近5分钟、近10分钟、近15分钟、近30分钟               |
| 小时级   | 近1小时、近6小时、近12小时、近24小时                |
| 天级    | 近1天、近3天、近7天                          |
| 周级    | 近1周、近2周                              |
| 月级    | 近1个月、近2个月、近3个月、近半年、一个月、三个月、半年        |
| 年级    | 近1年、近2年、近3年、一年                       |
| 相对时间点 | 今天、昨天、前天、本周、上周、本月、上月、本季度、上季度、今年、去年   |

> 每个时间范围均支持"近X"和"最近X"两种写法，数字支持阿拉伯数字和中文数字（如 `近七天` = `近7天`）。
> 完整关键字列表可通过 `TimeRange.getAllKeywords()` 获取。

## 配置

```yaml
io:
  github:
    surezzzzzz:
      sdk:
        expression:
          condition:
            parser:
              enabled: true    # 是否启用（默认 true）
```

## 错误处理

所有解析错误抛出 `ConditionExpressionParseException`，包含详细的错误类型、位置和建议：

```java
try {
    parser.parse("年龄> AND 状态='活跃'");
} catch (ConditionExpressionParseException e) {
    e.getErrorType();        // SYNTAX_ERROR
    e.getExpression();       // "年龄> AND 状态='活跃'"
    e.getLine();             // 1
    e.getColumn();           // 4
    e.getOffendingToken();   // "AND"（不暴露 <EOF> 等内部 token）
    e.getMessage();          // 友好的中文错误消息
}
```

**错误类型：**

| 错误类型               | 说明        |
|--------------------|-----------|
| `SYNTAX_ERROR`     | 语法错误      |
| `EMPTY_EXPRESSION` | 空表达式或只有空格 |
| `INVALID_VALUE`    | 值格式错误     |
| `MISMATCHED_PARENTHESIS` | 括号不匹配 |
| `EMPTY_IN_LIST`    | IN 值列表为空  |

## 工作原理

```
条件表达式字符串
    ↓
ANTLR 4 (Lexer + Parser)
    ↓
ParseTree
    ↓
AstBuilder (Visitor) + ValueParser (策略模式)
    ↓
Expression (AST)
    ├─ ComparisonExpression
    ├─ InExpression
    ├─ LikeExpression
    ├─ NullExpression
    ├─ BinaryExpression (AND/OR)
    ├─ UnaryExpression (NOT)
    └─ ParenthesisExpression
    ↓
业务层 Visitor → SQL / ES DSL / MongoDB Query / ...
```

## TimeRange API

```java
// 关键字查找
TimeRange range = TimeRange.fromKeyword("近7天");    // LAST_7_DAYS
TimeRange range = TimeRange.fromKeyword("最近一小时"); // LAST_1_HOUR

// 关键字判断
boolean is = TimeRange.isKeyword("今天");            // true

// 获取所有关键字映射（不可变 Map，可用于前端提示）
Map<String, TimeRange> keywords = TimeRange.getAllKeywords();

// 枚举元数据（业务层用于计算时间）
int amount = range.getAmount();     // 7
ChronoUnit unit = range.getUnit();  // DAYS
```

## 常见问题

### Q: 如何实现字段名映射？

在 Visitor 中实现映射即可：

```java
public class SqlVisitor implements ExpressionVisitor<String> {
    private String mapFieldName(String hint) {
        return FIELD_MAPPING.getOrDefault(hint, hint);
    }
}
```

### Q: 时间范围如何计算具体时间？

SDK 只返回枚举值，业务层根据 `amount` 和 `unit` 计算：

```java
TimeRange range = valueNode.getParsedValue(); // 如 LAST_3_MONTHS
LocalDateTime start = LocalDateTime.now().minus(range.getAmount(), range.getUnit());
```

### Q: 中文运算符和中文字段名之间需要空格吗？

不需要。`年龄等于25` 可以直接解析。ANTLR 会优先匹配更长的运算符关键字（`等于` 2字符 > 单个中文字符），自动正确拆分。

唯一例外：如果字段名本身以运算符关键字开头（如 `包含规则`，`包含` 是 LIKE 别名），需要加空格：`` 包含 `规则 ` LIKE 'test' ``。但实际业务中极少出现这种情况。

### Q: 支持哪些数据库？

解析器与数据库无关。通过实现不同的 Visitor 转换为目标格式：SQL、Elasticsearch DSL、MongoDB Query、JPA Criteria API 等。

## 依赖

- Java 8+
- Spring Boot 2.x+
- ANTLR 4.10.1

## 许可证

Apache License 2.0

## 作者

**surezzzzzz**

- GitHub: [@Sure-Zzzzzz](https://github.com/Sure-Zzzzzz)
