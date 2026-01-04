package io.github.surezzzzzz.sdk.expression.condition.parser.test.cases;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.ConditionExpressionParser;
import io.github.surezzzzzz.sdk.expression.condition.parser.test.ConditionExpressionParserTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 条件表达式解析器端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = ConditionExpressionParserTestApplication.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConditionExprParserEndToEndTest {

    @Autowired
    private ConditionExpressionParser parser;

    // ==================== 辅助方法 ====================

    private ComparisonExpression asComparison(Expression expr) {
        assertTrue(expr instanceof ComparisonExpression, "Expression应该是ComparisonExpression类型");
        return (ComparisonExpression) expr;
    }

    private InExpression asIn(Expression expr) {
        assertTrue(expr instanceof InExpression, "Expression应该是InExpression类型");
        return (InExpression) expr;
    }

    private LikeExpression asLike(Expression expr) {
        assertTrue(expr instanceof LikeExpression, "Expression应该是LikeExpression类型");
        return (LikeExpression) expr;
    }

    private NullExpression asNull(Expression expr) {
        assertTrue(expr instanceof NullExpression, "Expression应该是NullExpression类型");
        return (NullExpression) expr;
    }

    private BinaryExpression asBinary(Expression expr) {
        assertTrue(expr instanceof BinaryExpression, "Expression应该是BinaryExpression类型");
        return (BinaryExpression) expr;
    }

    private UnaryExpression asUnary(Expression expr) {
        assertTrue(expr instanceof UnaryExpression, "Expression应该是UnaryExpression类型");
        return (UnaryExpression) expr;
    }

    private ParenthesisExpression asParen(Expression expr) {
        assertTrue(expr instanceof ParenthesisExpression, "Expression应该是ParenthesisExpression类型");
        return (ParenthesisExpression) expr;
    }

    @BeforeAll
    static void setupAll() {
        log.info("========== 开始条件表达式解析器测试 ==========");
    }

    @AfterAll
    static void cleanupAll() {
        log.info("========== 条件表达式解析器测试完成 ==========");
    }

    // ==================== 比较运算符测试 ====================

    @Test
    @Order(1)
    @DisplayName("比较运算符 - 等于")
    void testEqualOperator() {
        log.info("========== 测试：比较运算符 - 等于 ==========");

        // 中文
        String expr1 = "状态='活跃'";
        log.info("表达式: {}", expr1);
        ComparisonExpression result1 = asComparison(parser.parse(expr1));
        log.info("解析结果: {}", result1);
        assertEquals("状态", result1.getField());
        assertEquals(ComparisonOperator.EQ, result1.getOperator());
        assertEquals("活跃", result1.getValue().getRawValue());
        assertEquals(ValueType.STRING, result1.getValue().getType());

        // 英文
        String expr2 = "status='active'";
        log.info("表达式: {}", expr2);
        ComparisonExpression result2 = asComparison(parser.parse(expr2));
        log.info("解析结果: {}", result2);
        assertEquals("status", result2.getField());
        assertEquals(ComparisonOperator.EQ, result2.getOperator());

        log.info("✓ 等于运算符测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("比较运算符 - 大于/小于")
    void testComparisonOperators() {
        log.info("========== 测试：比较运算符 - 大于/小于 ==========");

        // 大于
        String expr1 = "年龄>18";
        log.info("表达式: {}", expr1);
        ComparisonExpression result1 = asComparison(parser.parse(expr1));
        log.info("解析结果: {}", result1);
        assertEquals("年龄", result1.getField());
        assertEquals(ComparisonOperator.GT, result1.getOperator());
        assertEquals(18L, result1.getValue().getParsedValue());
        assertEquals(ValueType.INTEGER, result1.getValue().getType());

        // 大于等于
        String expr2 = "年龄>=18";
        ComparisonExpression result2 = asComparison(parser.parse(expr2));
        assertEquals(ComparisonOperator.GTE, result2.getOperator());

        // 小于
        String expr3 = "年龄<60";
        ComparisonExpression result3 = asComparison(parser.parse(expr3));
        assertEquals(ComparisonOperator.LT, result3.getOperator());

        // 小于等于
        String expr4 = "年龄<=60";
        ComparisonExpression result4 = asComparison(parser.parse(expr4));
        assertEquals(ComparisonOperator.LTE, result4.getOperator());

        // 不等于
        String expr5 = "状态!='已删除'";
        ComparisonExpression result5 = asComparison(parser.parse(expr5));
        assertEquals(ComparisonOperator.NE, result5.getOperator());

        log.info("✓ 比较运算符测试通过");
    }

    // ==================== IN 运算符测试 ====================

    @Test
    @Order(3)
    @DisplayName("IN运算符 - 基本功能")
    void testInOperator() {
        log.info("========== 测试：IN运算符 ==========");

        // 中文
        String expr1 = "城市 IN ('北京','上海','深圳')";
        log.info("表达式: {}", expr1);
        InExpression result1 = asIn(parser.parse(expr1));
        log.info("解析结果: {}", result1);
        assertEquals("城市", result1.getField());
        assertFalse(result1.isNotIn());
        assertEquals(3, result1.getValues().size());
        assertEquals("北京", result1.getValues().get(0).getRawValue());
        assertEquals("上海", result1.getValues().get(1).getRawValue());
        assertEquals("深圳", result1.getValues().get(2).getRawValue());

        // 英文
        String expr2 = "city IN ('Beijing','Shanghai','Shenzhen')";
        log.info("表达式: {}", expr2);
        InExpression result2 = asIn(parser.parse(expr2));
        log.info("解析结果: {}", result2);
        assertEquals("city", result2.getField());
        assertEquals(3, result2.getValues().size());

        log.info("✓ IN运算符测试通过");
    }

    @Test
    @Order(4)
    @DisplayName("NOT IN运算符")
    void testNotInOperator() {
        log.info("========== 测试：NOT IN运算符 ==========");

        String expr = "状态 NOT IN ('已删除','已禁用')";
        log.info("表达式: {}", expr);
        InExpression result = asIn(parser.parse(expr));
        log.info("解析结果: {}", result);
        assertEquals("状态", result.getField());
        assertTrue(result.isNotIn());
        assertEquals(2, result.getValues().size());

        log.info("✓ NOT IN运算符测试通过");
    }

    // ==================== LIKE 运算符测试 ====================

    @Test
    @Order(5)
    @DisplayName("LIKE运算符 - 模糊匹配")
    void testLikeOperator() {
        log.info("========== 测试：LIKE运算符 ==========");

        // 中文
        String expr1 = "名称 LIKE '测试'";
        log.info("表达式: {}", expr1);
        LikeExpression result1 = asLike(parser.parse(expr1));
        log.info("解析结果: {}", result1);
        assertEquals("名称", result1.getField());
        assertEquals(MatchOperator.LIKE, result1.getOperator());
        assertEquals("测试", result1.getValue().getRawValue());

        // 英文
        String expr2 = "name LIKE 'test'";
        LikeExpression result2 = asLike(parser.parse(expr2));
        assertEquals(MatchOperator.LIKE, result2.getOperator());

        log.info("✓ LIKE运算符测试通过");
    }

    @Test
    @Order(6)
    @DisplayName("PREFIX LIKE运算符 - 前缀匹配")
    void testPrefixLikeOperator() {
        log.info("========== 测试：PREFIX LIKE运算符 ==========");

        String expr = "名称 PREFIX LIKE '测试'";
        log.info("表达式: {}", expr);
        LikeExpression result = asLike(parser.parse(expr));
        log.info("解析结果: {}", result);
        assertEquals("名称", result.getField());
        assertEquals(MatchOperator.PREFIX, result.getOperator());
        assertEquals("测试", result.getValue().getRawValue());

        log.info("✓ PREFIX LIKE运算符测试通过");
    }

    @Test
    @Order(7)
    @DisplayName("SUFFIX LIKE运算符 - 后缀匹配")
    void testSuffixLikeOperator() {
        log.info("========== 测试：SUFFIX LIKE运算符 ==========");

        String expr = "名称 SUFFIX LIKE '测试'";
        log.info("表达式: {}", expr);
        LikeExpression result = asLike(parser.parse(expr));
        log.info("解析结果: {}", result);
        assertEquals("名称", result.getField());
        assertEquals(MatchOperator.SUFFIX, result.getOperator());

        log.info("✓ SUFFIX LIKE运算符测试通过");
    }

    @Test
    @Order(8)
    @DisplayName("NOT LIKE运算符")
    void testNotLikeOperator() {
        log.info("========== 测试：NOT LIKE运算符 ==========");

        String expr = "名称 NOT LIKE '测试'";
        log.info("表达式: {}", expr);
        LikeExpression result = asLike(parser.parse(expr));
        log.info("解析结果: {}", result);
        assertEquals("名称", result.getField());
        assertEquals(MatchOperator.NOT_LIKE, result.getOperator());

        log.info("✓ NOT LIKE运算符测试通过");
    }

    // ==================== NULL 运算符测试 ====================

    @Test
    @Order(9)
    @DisplayName("IS NULL运算符")
    void testIsNullOperator() {
        log.info("========== 测试：IS NULL运算符 ==========");

        // 中文
        String expr1 = "备注 IS NULL";
        log.info("表达式: {}", expr1);
        NullExpression result1 = asNull(parser.parse(expr1));
        log.info("解析结果: {}", result1);
        assertEquals("备注", result1.getField());
        assertTrue(result1.isNull());

        // 英文
        String expr2 = "remark IS NULL";
        NullExpression result2 = asNull(parser.parse(expr2));
        assertEquals("remark", result2.getField());

        log.info("✓ IS NULL运算符测试通过");
    }

    @Test
    @Order(10)
    @DisplayName("IS NOT NULL运算符")
    void testIsNotNullOperator() {
        log.info("========== 测试：IS NOT NULL运算符 ==========");

        String expr = "备注 IS NOT NULL";
        log.info("表达式: {}", expr);
        NullExpression result = asNull(parser.parse(expr));
        log.info("解析结果: {}", result);
        assertEquals("备注", result.getField());
        assertFalse(result.isNull());

        log.info("✓ IS NOT NULL运算符测试通过");
    }

    // ==================== 值类型测试 ====================

    @Test
    @Order(11)
    @DisplayName("值类型 - 字符串")
    void testStringValue() {
        log.info("========== 测试：字符串值 ==========");

        String expr = "名称='张三'";
        log.info("表达式: {}", expr);
        ComparisonExpression result = asComparison(parser.parse(expr));
        log.info("解析结果: {}", result);
        assertEquals(ValueType.STRING, result.getValue().getType());
        assertEquals("张三", result.getValue().getRawValue());
        assertEquals("张三", result.getValue().getParsedValue());

        log.info("✓ 字符串值测试通过");
    }

    @Test
    @Order(12)
    @DisplayName("值类型 - 数值")
    void testNumberValue() {
        log.info("========== 测试：数值 ==========");

        // 整数
        String expr1 = "年龄=25";
        log.info("表达式: {}", expr1);
        ComparisonExpression result1 = asComparison(parser.parse(expr1));
        log.info("解析结果: {}", result1);
        assertEquals(ValueType.INTEGER, result1.getValue().getType());
        assertEquals(25L, result1.getValue().getParsedValue());

        // 浮点数
        String expr2 = "价格=99.99";
        log.info("表达式: {}", expr2);
        ComparisonExpression result2 = asComparison(parser.parse(expr2));
        log.info("解析结果: {}", result2);
        assertEquals(ValueType.DECIMAL, result2.getValue().getType());
        assertEquals(99.99, result2.getValue().getParsedValue());

        log.info("✓ 数值测试通过");
    }

    @Test
    @Order(13)
    @DisplayName("值类型 - 布尔值")
    void testBooleanValue() {
        log.info("========== 测试：布尔值 ==========");

        // true
        String expr1 = "启用=true";
        log.info("表达式: {}", expr1);
        ComparisonExpression result1 = asComparison(parser.parse(expr1));
        log.info("解析结果: {}", result1);
        assertEquals(ValueType.BOOLEAN, result1.getValue().getType());
        assertEquals(true, result1.getValue().getParsedValue());

        // false
        String expr2 = "启用=false";
        ComparisonExpression result2 = asComparison(parser.parse(expr2));
        assertEquals(false, result2.getValue().getParsedValue());

        // 中文 - 真
        String expr3 = "启用='真'";
        ComparisonExpression result3 = asComparison(parser.parse(expr3));
        assertEquals(ValueType.BOOLEAN, result3.getValue().getType());
        assertEquals(true, result3.getValue().getParsedValue());

        // 中文 - 假
        String expr4 = "启用='假'";
        ComparisonExpression result4 = asComparison(parser.parse(expr4));
        assertEquals(false, result4.getValue().getParsedValue());

        log.info("✓ 布尔值测试通过");
    }

    @Test
    @Order(14)
    @DisplayName("值类型 - 时间范围")
    void testTimeRangeValue() {
        log.info("========== 测试：时间范围 ==========");

        // 近1小时
        String expr1 = "时间='近1小时'";
        log.info("表达式: {}", expr1);
        ComparisonExpression result1 = asComparison(parser.parse(expr1));
        log.info("解析结果: {}", result1);
        assertEquals(ValueType.TIME_RANGE, result1.getValue().getType());
        assertEquals(TimeRange.LAST_1_HOUR, result1.getValue().getParsedValue());

        // 近3个月
        String expr2 = "时间='近3个月'";
        ComparisonExpression result2 = asComparison(parser.parse(expr2));
        assertEquals(TimeRange.LAST_3_MONTHS, result2.getValue().getParsedValue());

        // 今天
        String expr3 = "时间='今天'";
        ComparisonExpression result3 = asComparison(parser.parse(expr3));
        assertEquals(TimeRange.TODAY, result3.getValue().getParsedValue());

        log.info("✓ 时间范围测试通过");
    }

    // ==================== 逻辑运算符测试 ====================

    @Test
    @Order(15)
    @DisplayName("逻辑运算符 - AND")
    void testAndOperator() {
        log.info("========== 测试：AND运算符 ==========");

        // 中文
        String expr1 = "年龄>18 AND 年龄<60";
        log.info("表达式: {}", expr1);
        BinaryExpression result1 = asBinary(parser.parse(expr1));
        log.info("解析结果: {}", result1);
        assertEquals(LogicalOperator.AND, result1.getOperator());
        assertTrue(result1.getLeft() instanceof ComparisonExpression);
        assertTrue(result1.getRight() instanceof ComparisonExpression);

        // 英文
        String expr2 = "age>18 AND age<60";
        BinaryExpression result2 = asBinary(parser.parse(expr2));
        assertEquals(LogicalOperator.AND, result2.getOperator());

        log.info("✓ AND运算符测试通过");
    }

    @Test
    @Order(16)
    @DisplayName("逻辑运算符 - OR")
    void testOrOperator() {
        log.info("========== 测试：OR运算符 ==========");

        String expr = "状态='活跃' OR 状态='待审核'";
        log.info("表达式: {}", expr);
        BinaryExpression result = asBinary(parser.parse(expr));
        log.info("解析结果: {}", result);
        assertEquals(LogicalOperator.OR, result.getOperator());

        log.info("✓ OR运算符测试通过");
    }

    @Test
    @Order(17)
    @DisplayName("逻辑运算符 - NOT")
    void testNotOperator() {
        log.info("========== 测试：NOT运算符 ==========");

        String expr = "NOT 状态='已删除'";
        log.info("表达式: {}", expr);
        UnaryExpression result = asUnary(parser.parse(expr));
        log.info("解析结果: {}", result);
        assertEquals(UnaryOperator.NOT, result.getOperator());
        assertTrue(result.getOperand() instanceof ComparisonExpression);

        log.info("✓ NOT运算符测试通过");
    }

    // ==================== 括号测试 ====================

    @Test
    @Order(18)
    @DisplayName("括号 - 优先级控制")
    void testParentheses() {
        log.info("========== 测试：括号优先级 ==========");

        String expr = "(年龄>18 AND 年龄<60) OR 状态='VIP'";
        log.info("表达式: {}", expr);
        BinaryExpression result = asBinary(parser.parse(expr));
        log.info("解析结果: {}", result);
        assertEquals(LogicalOperator.OR, result.getOperator());
        assertTrue(result.getLeft() instanceof ParenthesisExpression);

        ParenthesisExpression paren = asParen(result.getLeft());
        assertTrue(paren.getExpression() instanceof BinaryExpression);

        log.info("✓ 括号优先级测试通过");
    }

    // ==================== 复杂场景测试 ====================

    @Test
    @Order(20)
    @DisplayName("复杂场景 - 多条件组合")
    void testComplexConditions() {
        log.info("========== 测试：复杂多条件 ==========");

        String expr = "类型='活跃' AND 分类 IN ('高','中') AND 名称 LIKE '测试' AND 备注 IS NOT NULL";
        log.info("表达式: {}", expr);
        Expression result = parser.parse(expr);
        log.info("解析结果: {}", result);
        assertNotNull(result);
        assertTrue(result instanceof BinaryExpression);

        log.info("✓ 复杂多条件测试通过");
    }

    @Test
    @Order(21)
    @DisplayName("复杂场景 - 嵌套括号")
    void testNestedParentheses() {
        log.info("========== 测试：嵌套括号 ==========");

        String expr = "((年龄>18 AND 年龄<60) OR 状态='VIP') AND 城市='北京'";
        log.info("表达式: {}", expr);
        Expression result = parser.parse(expr);
        log.info("解析结果: {}", result);
        assertNotNull(result);
        assertTrue(result instanceof BinaryExpression);

        log.info("✓ 嵌套括号测试通过");
    }

    @Test
    @Order(22)
    @DisplayName("复杂场景 - 全运算符组合")
    void testAllOperatorsCombined() {
        log.info("========== 测试：全运算符组合 ==========");

        String expr = "类型='活跃' AND 分类 IN ('高','中') AND " +
                "名称 LIKE '测试' AND 描述 PREFIX LIKE '用户' AND " +
                "标签 SUFFIX LIKE '标记' AND 备注 NOT LIKE '删除' AND " +
                "扩展字段 IS NULL AND 年龄>18 AND 年龄<=60 AND " +
                "状态!='禁用' AND 时间='近1个月'";
        log.info("表达式: {}", expr);
        Expression result = parser.parse(expr);
        log.info("解析结果: {}", result);
        assertNotNull(result);

        log.info("✓ 全运算符组合测试通过");
    }

    // ==================== 错误处理测试 ====================

    @Test
    @Order(30)
    @DisplayName("错误处理 - 空表达式")
    void testEmptyExpression() {
        log.info("========== 测试：空表达式 ==========");

        assertThrows(ConditionExpressionParseException.class, () -> {
            parser.parse("");
        });

        log.info("✓ 空表达式异常处理正确");
    }

    @Test
    @Order(31)
    @DisplayName("错误处理 - null表达式")
    void testNullExpression() {
        log.info("========== 测试：null表达式 ==========");

        assertThrows(ConditionExpressionParseException.class, () -> {
            parser.parse(null);
        });

        log.info("✓ null表达式异常处理正确");
    }

    @Test
    @Order(32)
    @DisplayName("错误处理 - 语法错误")
    void testSyntaxError() {
        log.info("========== 测试：语法错误 ==========");

        assertThrows(ConditionExpressionParseException.class, () -> {
            parser.parse("年龄> AND 状态='活跃'");
        });

        log.info("✓ 语法错误异常处理正确");
    }

    @Test
    @Order(33)
    @DisplayName("错误处理 - 不匹配的括号")
    void testUnmatchedParentheses() {
        log.info("========== 测试：不匹配的括号 ==========");

        assertThrows(ConditionExpressionParseException.class, () -> {
            parser.parse("(年龄>18 AND 状态='活跃'");
        });

        log.info("✓ 不匹配括号异常处理正确");
    }

    // ==================== 中英文混合测试 ====================

    @Test
    @Order(40)
    @DisplayName("中英文混合 - 基本运算符")
    void testMixedLanguageBasic() {
        log.info("========== 测试：中英文混合 ==========");

        // 中文关键字 + 英文字段
        String expr1 = "status='活跃' AND age>18";
        log.info("表达式: {}", expr1);
        Expression result1 = parser.parse(expr1);
        log.info("解析结果: {}", result1);
        assertNotNull(result1);

        // 英文关键字 + 中文值
        String expr2 = "name LIKE '测试' OR city IN ('北京','上海')";
        log.info("表达式: {}", expr2);
        Expression result2 = parser.parse(expr2);
        log.info("解析结果: {}", result2);
        assertNotNull(result2);

        log.info("✓ 中英文混合测试通过");
    }

    @Test
    @Order(100)
    @DisplayName("超级端到端测试 - 全场景覆盖")
    void testSuperEndToEnd() {
        log.info("========== 超级端到端测试 - 全场景覆盖 ==========");

        // 场景1: 基本比较
        log.info("\n【场景1】基本比较测试");
        String q1 = "年龄=25";
        log.info("表达式: {}", q1);
        Expression r1 = parser.parse(q1);
        log.info("解析结果: {}", r1);
        assertTrue(r1 instanceof ComparisonExpression);
        log.info("✓ 场景1通过：基本比较解析正确");

        // 场景2: IN运算符
        log.info("\n【场景2】IN运算符测试");
        String q2 = "城市 IN ('北京','上海','深圳')";
        log.info("表达式: {}", q2);
        Expression r2 = parser.parse(q2);
        log.info("解析结果: {}", r2);
        assertTrue(r2 instanceof InExpression);
        assertEquals(3, ((InExpression) r2).getValues().size());
        log.info("✓ 场景2通过：IN运算符解析正确");

        // 场景3: LIKE运算符
        log.info("\n【场景3】LIKE运算符测试");
        String q3 = "名称 LIKE '测试'";
        log.info("表达式: {}", q3);
        Expression r3 = parser.parse(q3);
        log.info("解析结果: {}", r3);
        assertTrue(r3 instanceof LikeExpression);
        log.info("✓ 场景3通过：LIKE运算符解析正确");

        // 场景4: AND逻辑
        log.info("\n【场景4】AND逻辑测试");
        String q4 = "年龄>18 AND 年龄<60";
        log.info("表达式: {}", q4);
        Expression r4 = parser.parse(q4);
        log.info("解析结果: {}", r4);
        assertTrue(r4 instanceof BinaryExpression);
        assertEquals(LogicalOperator.AND, ((BinaryExpression) r4).getOperator());
        log.info("✓ 场景4通过：AND逻辑解析正确");

        // 场景5: 括号优先级
        log.info("\n【场景5】括号优先级测试");
        String q5 = "(年龄>18 AND 年龄<60) OR 状态='VIP'";
        log.info("表达式: {}", q5);
        Expression r5 = parser.parse(q5);
        log.info("解析结果: {}", r5);
        assertTrue(r5 instanceof BinaryExpression);
        assertTrue(((BinaryExpression) r5).getLeft() instanceof ParenthesisExpression);
        log.info("✓ 场景5通过：括号优先级解析正确");

        // 场景6: 复杂组合
        log.info("\n【场景6】复杂组合测试");
        String q6 = "类型='活跃' AND 分类 IN ('高','中') AND 名称 LIKE '测试' AND 备注 IS NOT NULL AND 年龄>18";
        log.info("表达式: {}", q6);
        Expression r6 = parser.parse(q6);
        log.info("解析结果: {}", r6);
        assertNotNull(r6);
        log.info("✓ 场景6通过：复杂组合解析正确");

        // 场景7: NOT运算符
        log.info("\n【场景7】NOT运算符测试");
        String q7 = "NOT 状态='已删除'";
        log.info("表达式: {}", q7);
        Expression r7 = parser.parse(q7);
        log.info("解析结果: {}", r7);
        assertTrue(r7 instanceof UnaryExpression);
        log.info("✓ 场景7通过：NOT运算符解析正确");

        // 场景8: 时间范围
        log.info("\n【场景8】时间范围测试");
        String q8 = "时间='近1个月'";
        log.info("表达式: {}", q8);
        Expression r8 = parser.parse(q8);
        log.info("解析结果: {}", r8);
        assertTrue(r8 instanceof ComparisonExpression);
        assertEquals(ValueType.TIME_RANGE, ((ComparisonExpression) r8).getValue().getType());
        log.info("✓ 场景8通过：时间范围解析正确");

        log.info("\n========== ✓ 超级端到端测试全部通过 (8个场景) ==========");
    }
}
