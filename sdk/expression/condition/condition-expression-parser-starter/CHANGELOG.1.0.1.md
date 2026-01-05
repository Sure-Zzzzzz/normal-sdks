# Changelog

## [1.0.1] - 2026-01-05

### ✨ 新特性

#### 1. 新增 `support` 工具包

提供3个静态工具类，简化业务层开发：

**ExpressionCollectors** - 信息收集
- `collectFields()` - 收集所有字段名（去重）
- `collectValues()` - 收集所有值节点
- 使用场景：字段白名单验证、参数提取

**ExpressionMetrics** - 复杂度度量
- `calculateDepth()` - 计算表达式树深度
- `countConditions()` - 统计条件总数
- `validateDepth()` / `validateConditionCount()` - 验证限制
- 使用场景：防止恶意构造的超级复杂表达式

**ExpressionPrinter** - 格式化输出
- `toCompactString()` - 单行紧凑格式
- `toTreeString()` - 多行树形格式
- 使用场景：调试日志、错误提示
- 内部使用 Visitor 模式实现，避免硬编码

#### 2. 增强 `BaseExpressionVisitor` 基类

新增**8个静态工具方法**，提供丰富的查询能力：

**类型检查**
- `isLeafExpression()` - 是否为叶子节点
- `isLogicalExpression()` - 是否为逻辑组合节点
- `isParenthesisExpression()` - 是否为括号表达式

**字段查询**
- `containsField(expr, fieldHint)` - 是否包含指定字段
- `findFieldCondition(expr, fieldHint)` - 查找字段的第一个条件

**逻辑分析**
- `isAllAnd(expr)` - 是否全部 AND 连接（筛选规则特征）
- `isAllOr(expr)` - 是否全部 OR 连接（排除规则特征）

**条件查找**
- `findConditions(expr, predicate)` - 查找所有满足条件的叶子表达式

#### 3. 新增 `exception` 包

**ExpressionValidationException** - 验证异常
- 当表达式不满足验证规则时抛出（深度超限、条件数超限）
- 提供 `MetricType` 枚举（DEPTH、CONDITION_COUNT）
- 包含实际值、最大值等详细信息

### 📝 使用示例

```java
Expression expr = parser.parse("威胁类型='恶意' AND 存活状态!='失活'");

// 1. 收集信息
Set<String> fields = ExpressionCollectors.collectFields(expr);
List<ValueNode> values = ExpressionCollectors.collectValues(expr);

// 2. 验证复杂度
try {
    ExpressionMetrics.validateDepth(expr, 10);
    ExpressionMetrics.validateConditionCount(expr, 20);
} catch (ExpressionValidationException e) {
    log.error("表达式过于复杂: {}, 实际值={}, 最大值={}",
        e.getMetricType(), e.getActualValue(), e.getMaxValue());
}

// 3. 格式化输出
String compact = ExpressionPrinter.toCompactString(expr);
String tree = ExpressionPrinter.toTreeString(expr);

// 4. 字段查询
boolean hasStatus = BaseExpressionVisitor.containsField(expr, "存活状态");
Expression statusCond = BaseExpressionVisitor.findFieldCondition(expr, "存活状态");

// 5. 逻辑分析（判断规则类型）
if (BaseExpressionVisitor.isAllAnd(expr)) {
    // 筛选规则：必须同时满足所有条件
}
if (BaseExpressionVisitor.isAllOr(expr)) {
    // 排除规则：满足任一即排除
}

// 6. 条件查找
List<Expression> comparisons = BaseExpressionVisitor.findConditions(expr,
    e -> e instanceof ComparisonExpression);
```

### ⚡ 升级说明

- ✅ 完全向后兼容，无破坏性变更
- ✅ 所有工具方法线程安全
- ✅ 0 依赖新增，无需修改现有代码
