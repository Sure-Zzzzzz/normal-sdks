package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionHintsResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionValidationResult;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.ExpressionParseException;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.service.ExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor.ExpressionToQueryConditionVisitor;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor.ExpressionVisitorRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.TimeRange;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AdvancedExpressionService 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchSearchTestApplication.class)
class ExpressionTest {

    @Autowired
    private ExpressionService expressionService;

    @Autowired
    private ExpressionVisitorRegistry visitorRegistry;

    // ==================== ExpressionVisitorRegistry ====================

    @Test
    @DisplayName("registry - 有 field-mapping 的索引预建了 visitor")
    void testRegistryWithMapping() {
        ExpressionToQueryConditionVisitor visitor = visitorRegistry.resolve("test_order_index");
        log.info("visitor for test_order_index: {}", visitor);
        assertNotNull(visitor);
    }

    @Test
    @DisplayName("registry - 无 field-mapping 的索引返回默认 visitor")
    void testRegistryDefaultVisitor() {
        ExpressionToQueryConditionVisitor v1 = visitorRegistry.resolve("test_log_*");
        ExpressionToQueryConditionVisitor v2 = visitorRegistry.resolve(null);
        ExpressionToQueryConditionVisitor v3 = visitorRegistry.resolve("不存在的索引");
        log.info("default visitor: {}", v1);
        // 三个都应该是同一个默认实例
        assertSame(v1, v2);
        assertSame(v1, v3);
    }

    // ==================== translate - 基本操作符 ====================

    @Test
    @DisplayName("translate - 等于")
    void testTranslateEq() {
        QueryCondition result = expressionService.translate("威胁类型 = \"木马\"", null);
        log.info("eq: {}", result);
        assertNotNull(result);
        assertEquals("威胁类型", result.getField());
        assertEquals("eq", result.getOp());
        assertEquals("木马", result.getValue());
    }

    @Test
    @DisplayName("translate - 不等于")
    void testTranslateNe() {
        QueryCondition result = expressionService.translate("处置状态 != \"已忽略\"", null);
        log.info("ne: {}", result);
        assertEquals("ne", result.getOp());
        assertEquals("已忽略", result.getValue());
    }

    @Test
    @DisplayName("translate - 数值大于等于")
    void testTranslateGte() {
        QueryCondition result = expressionService.translate("攻击次数 >= 10", null);
        log.info("gte: {}", result);
        assertEquals("gte", result.getOp());
        assertEquals("10", result.getValue());
    }

    @Test
    @DisplayName("translate - IN 多值")
    void testTranslateIn() {
        QueryCondition result = expressionService.translate("威胁等级 IN (\"高危\", \"中危\")", null);
        log.info("in: {}", result);
        assertEquals("in", result.getOp());
        assertNotNull(result.getValues());
        assertEquals(2, result.getValues().size());
        assertTrue(result.getValues().contains("高危"));
        assertTrue(result.getValues().contains("中危"));
    }

    @Test
    @DisplayName("translate - NOT IN")
    void testTranslateNotIn() {
        QueryCondition result = expressionService.translate("协议 NOT IN (\"HTTP\", \"HTTPS\")", null);
        log.info("not_in: {}", result);
        assertEquals("not_in", result.getOp());
        assertEquals(2, result.getValues().size());
    }

    @Test
    @DisplayName("translate - LIKE 模糊匹配")
    void testTranslateLike() {
        QueryCondition result = expressionService.translate("外联目标 LIKE \"evil\"", null);
        log.info("like: {}", result);
        assertEquals("like", result.getOp());
        assertEquals("evil", result.getValue());
    }

    @Test
    @DisplayName("translate - NOT LIKE")
    void testTranslateNotLike() {
        QueryCondition result = expressionService.translate("外联目标 NOT LIKE \"cdn\"", null);
        log.info("not_like: {}", result);
        assertEquals("not_like", result.getOp());
        assertEquals("cdn", result.getValue());
    }

    @Test
    @DisplayName("translate - IS NULL")
    void testTranslateIsNull() {
        QueryCondition result = expressionService.translate("威胁标签 IS NULL", null);
        log.info("is_null: {}", result);
        assertEquals("is_null", result.getOp());
        assertNull(result.getValue());
    }

    @Test
    @DisplayName("translate - IS NOT NULL")
    void testTranslateIsNotNull() {
        QueryCondition result = expressionService.translate("威胁标签 IS NOT NULL", null);
        log.info("is_not_null: {}", result);
        assertEquals("is_not_null", result.getOp());
    }

    // ==================== translate - 逻辑组合 ====================

    @Test
    @DisplayName("translate - AND 组合")
    void testTranslateAnd() {
        QueryCondition result = expressionService.translate(
                "威胁类型 = \"木马\" AND 攻击次数 >= 10", null);
        log.info("and: {}", result);
        assertEquals("and", result.getLogic());
        assertNotNull(result.getConditions());
        assertEquals(2, result.getConditions().size());
    }

    @Test
    @DisplayName("translate - OR 组合")
    void testTranslateOr() {
        QueryCondition result = expressionService.translate(
                "威胁类型 = \"木马\" OR 威胁类型 = \"蠕虫\"", null);
        log.info("or: {}", result);
        assertEquals("or", result.getLogic());
        assertEquals(2, result.getConditions().size());
    }

    @Test
    @DisplayName("translate - 括号分组")
    void testTranslateParenthesis() {
        QueryCondition result = expressionService.translate(
                "(威胁类型 = \"木马\" OR 威胁类型 = \"蠕虫\") AND 攻击次数 > 5", null);
        log.info("parenthesis: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());
        // 左侧是 OR 组合
        QueryCondition left = result.getConditions().get(0);
        assertEquals("or", left.getLogic());
    }

    @Test
    @DisplayName("translate - NOT 叶子节点取反")
    void testTranslateNotLeaf() {
        QueryCondition result = expressionService.translate(
                "NOT 告警单位 = \"测试环境\"", null);
        log.info("not leaf: {}", result);
        // NOT eq → ne
        assertEquals("ne", result.getOp());
        assertEquals("告警单位", result.getField());
        assertEquals("测试环境", result.getValue());
    }

    @Test
    @DisplayName("translate - NOT 复合节点德摩根展开")
    void testTranslateNotComplex() {
        QueryCondition result = expressionService.translate(
                "NOT (威胁类型 = \"木马\" AND 攻击次数 >= 10)", null);
        log.info("not complex: {}", result);
        // NOT (A AND B) → (NOT A) OR (NOT B)
        assertEquals("or", result.getLogic());
        assertEquals(2, result.getConditions().size());
        assertEquals("ne", result.getConditions().get(0).getOp());
        assertEquals("lt", result.getConditions().get(1).getOp());
    }

    // ==================== translate - 字段名映射 ====================

    @Test
    @DisplayName("translate - 字段名映射（从索引配置读取）")
    void testTranslateFieldMapping() {
        // test_order_index 在 application.yaml 中配置了 field-mapping
        QueryCondition result = expressionService.translate(
                "状态 = \"已完成\" AND 金额 >= 10", "test_order_index");
        log.info("field mapping: {}", result);

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("status", left.getField());
        assertEquals("amount", right.getField());
    }

    @Test
    @DisplayName("translate - 字段名映射找不到时原样透传")
    void testTranslateFieldMappingFallback() {
        QueryCondition result = expressionService.translate("未知字段 = \"值\"", "test_order_index");
        log.info("field mapping fallback: {}", result);
        assertEquals("未知字段", result.getField());
    }

    @Test
    @DisplayName("translate - 多个中文标签都映射到同一 ES 字段")
    void testTranslateFieldMappingMultipleLabels() {
        // "订单状态" 和 "状态" 都映射到 status
        QueryCondition r1 = expressionService.translate("状态 = \"已完成\"", "test_order_index");
        QueryCondition r2 = expressionService.translate("订单状态 = \"已完成\"", "test_order_index");
        log.info("label 1: {}", r1);
        log.info("label 2: {}", r2);
        assertEquals("status", r1.getField());
        assertEquals("status", r2.getField());
    }

    // ==================== translate - 时间范围 ====================

    @Test
    @DisplayName("translate - 时间范围 = 最近7天 转为 between")
    void testTranslateTimeRange() {
        QueryCondition result = expressionService.translate("创建时间 = 最近7天", "test_order_index");
        log.info("time range: {}", result);
        assertEquals("between", result.getOp());
        assertNotNull(result.getValues());
        assertEquals(2, result.getValues().size());
        log.info("from={}, to={}", result.getValues().get(0), result.getValues().get(1));
    }

    // ==================== validate ====================

    @Test
    @DisplayName("validate - 合法表达式")
    void testValidateValid() {
        // 必须传 index，normalize 后解析成功
        ExpressionValidationResult result = expressionService.validate(
                "威胁类型 = \"木马\" AND 攻击次数 >= 10", "test_nl_user_index");
        log.info("validate valid: {}", result);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
        assertEquals(-1, result.getErrorPosition());
    }

    @Test
    @DisplayName("validate - 语法错误")
    void testValidateInvalid() {
        ExpressionValidationResult result = expressionService.validate(
                "威胁类型 = \"木马\" AND 攻击次数 >=", "test_nl_user_index");
        log.info("validate invalid: {}", result);
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        log.info("error: {}", result.getErrorMessage());
    }

    @Test
    @DisplayName("validate - 空表达式")
    void testValidateEmpty() {
        ExpressionValidationResult result = expressionService.validate("", "test_nl_user_index");
        log.info("validate empty: {}", result);
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("validate - 支持中文 label")
    void testValidateWithChineseLabel() {
        log.info("========== 测试：validate 支持中文 label ==========");

        // 订单ID 替换后解析成功
        ExpressionValidationResult result = expressionService.validate(
                "订单ID = 'xxx'", "test_order_index");
        log.info("result: {}", result);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());

        log.info("✓ validate 支持中文 label 测试通过");
    }

    @Test
    @DisplayName("validate - 中文 label + 中文运算符")
    void testValidateWithChineseLabelAndOperator() {
        log.info("========== 测试：validate 中文 label + 中文运算符 ==========");

        ExpressionValidationResult result = expressionService.validate(
                "订单ID等于'xxx' 且 城市大于'北京'", "test_nl_user_index");
        log.info("result: {}", result);
        assertTrue(result.isValid());

        log.info("✓ validate 中文 label + 运算符测试通过");
    }

    // ==================== translate - 异常 ====================

    @Test
    @DisplayName("translate - 语法错误抛出 ExpressionParseException")
    void testTranslateThrowsOnSyntaxError() {
        assertThrows(ExpressionParseException.class, () ->
                expressionService.translate("威胁类型 = ", null));
    }

    // ==================== getHints ====================

    @Test
    @DisplayName("getHints - 全局提示不依赖索引（运算符、时间范围、值规则）")
    void testGetHintsGlobalHints() {
        log.info("========== 测试：getHints 全局提示 ==========");

        ExpressionHintsResponse hints = expressionService.getHints(null);
        log.info("hints: {}", hints);

        assertNotNull(hints);
        assertNotNull(hints.getOperators());
        assertNotNull(hints.getTimeRanges());
        assertNotNull(hints.getValueRules());

        // 运算符不为空，且无重复
        assertFalse(hints.getOperators().isEmpty(), "运算符列表不应为空");
        long distinctOps = hints.getOperators().stream()
                .map(ExpressionHintsResponse.OperatorHint::getOp)
                .distinct().count();
        assertEquals(hints.getOperators().size(), distinctOps, "运算符列表不应有重复");

        // 包含核心运算符
        List<String> ops = hints.getOperators().stream()
                .map(ExpressionHintsResponse.OperatorHint::getOp)
                .collect(java.util.stream.Collectors.toList());
        assertTrue(ops.contains("="), "应包含 =");
        assertTrue(ops.contains("!="), "应包含 !=");
        assertTrue(ops.contains("IN"), "应包含 IN");
        assertTrue(ops.contains("NOT IN"), "应包含 NOT IN");
        assertTrue(ops.contains("LIKE"), "应包含 LIKE");
        assertTrue(ops.contains("IS NULL"), "应包含 IS NULL");
        assertTrue(ops.contains("AND"), "应包含 AND");
        assertTrue(ops.contains("OR"), "应包含 OR");
        assertTrue(ops.contains("NOT"), "应包含 NOT");

        // 时间范围数量与 TimeRange 枚举一致
        assertEquals(TimeRange.values().length, hints.getTimeRanges().size(),
                "时间范围数量应与 TimeRange 枚举一致");

        log.info("✓ getHints 全局提示测试通过");
    }

    @Test
    @DisplayName("getHints - 运算符包含中文别名")
    void testGetHintsOperatorsWithChinese() {
        log.info("========== 测试：getHints 运算符中文别名 ==========");

        ExpressionHintsResponse hints = expressionService.getHints(null);

        boolean hasEqWithChinese = hints.getOperators().stream()
                .anyMatch(op -> "=".equals(op.getOp()) && op.getChinese() != null && !op.getChinese().isEmpty());
        assertTrue(hasEqWithChinese, "等于运算符应有中文别名");

        boolean hasAndWithChinese = hints.getOperators().stream()
                .anyMatch(op -> "AND".equals(op.getOp()) && op.getChinese() != null && !op.getChinese().isEmpty());
        assertTrue(hasAndWithChinese, "AND 运算符应有中文别名");

        log.info("✓ 运算符中文别名测试通过");
    }

    @Test
    @DisplayName("getHints - 时间范围包含所有主关键字")
    void testGetHintsTimeRanges() {
        log.info("========== 测试：getHints 时间范围主关键字 ==========");

        ExpressionHintsResponse hints = expressionService.getHints(null);
        List<String> timeRanges = hints.getTimeRanges();
        log.info("timeRanges: {}", timeRanges);

        assertTrue(timeRanges.contains("近7天"), "应包含 '近7天'");
        assertTrue(timeRanges.contains("近1小时"), "应包含 '近1小时'");
        assertTrue(timeRanges.contains("近3个月"), "应包含 '近3个月'");
        assertTrue(timeRanges.contains("今天"), "应包含 '今天'");
        assertTrue(timeRanges.contains("近1个月"), "应包含 '近1个月'");

        for (TimeRange range : TimeRange.values()) {
            assertTrue(timeRanges.contains(range.getKeyword()),
                    "应包含主关键字: " + range.getKeyword());
        }

        log.info("✓ 时间范围主关键字测试通过");
    }

    @Test
    @DisplayName("getHints - 值规则正确")
    void testGetHintsValueRules() {
        log.info("========== 测试：getHints 值规则 ==========");

        ExpressionHintsResponse hints = expressionService.getHints(null);
        ExpressionHintsResponse.ValueRules rules = hints.getValueRules();
        log.info("valueRules: {}", rules);

        assertTrue(rules.isStringNeedsQuote(), "字符串值需要加引号");
        assertTrue(rules.isNumberNoQuote(), "数字不需要引号");
        assertTrue(rules.getSupportedQuotes().contains("'"), "支持单引号");
        assertTrue(rules.getSupportedQuotes().contains("\""), "支持双引号");
        assertTrue(rules.getBooleanKeywords().contains("true"), "布尔关键字包含 true");
        assertTrue(rules.getBooleanKeywords().contains("假"), "布尔关键字包含 假");

        log.info("✓ 值规则测试通过");
    }

    @Test
    @DisplayName("getHints - 所有运算符都有非空中文描述")
    void testGetHintsAllOperatorsHaveChinese() {
        ExpressionHintsResponse hints = expressionService.getHints(null);
        hints.getOperators().forEach(op ->
                assertNotNull(op.getChinese(),
                        "运算符 " + op.getOp() + " 的 chinese 不应为 null"));
        hints.getOperators().forEach(op ->
                assertFalse(op.getChinese().isEmpty(),
                        "运算符 " + op.getOp() + " 的 chinese 不应为空字符串"));
    }

    @Test
    @DisplayName("getHints - 不存在的索引字段为空但全局提示仍返回")
    void testGetHintsNonExistentIndex() {
        log.info("========== 测试：getHints 不存在的索引 ==========");

        ExpressionHintsResponse hints = expressionService.getHints("不存在的索引");
        log.info("hints for non-existent index: {}", hints);

        assertNotNull(hints);
        assertTrue(hints.getFields() == null || hints.getFields().isEmpty(),
                "不存在索引的字段列表应为空");
        assertNotNull(hints.getOperators());
        assertNotNull(hints.getTimeRanges());

        log.info("✓ 不存在索引测试通过");
    }

    @Test
    @DisplayName("getHints - 敏感字段被正确排除")
    void testGetHintsSensitiveFieldsExcluded() {
        log.info("========== 测试：getHints 敏感字段排除 ==========");

        ExpressionHintsResponse hints = expressionService.getHints("test_employee");
        log.info("hints for test_employee: {}", hints);

        assertNotNull(hints);
        List<ExpressionHintsResponse.FieldHint> fields = hints.getFields();
        assertNotNull(fields);
        log.info("fields returned: {}", fields.stream()
                .map(ExpressionHintsResponse.FieldHint::getName)
                .collect(java.util.stream.Collectors.toList()));

        // 验证非敏感字段存在
        assertTrue(fields.stream().anyMatch(f -> "emp_id".equals(f.getName())),
                "应包含 emp_id");
        assertTrue(fields.stream().anyMatch(f -> "emp_name".equals(f.getName())),
                "应包含 emp_name");
        assertTrue(fields.stream().anyMatch(f -> "department".equals(f.getName())),
                "应包含 department");
        assertTrue(fields.stream().anyMatch(f -> "join_date".equals(f.getName())),
                "应包含 join_date");
        assertTrue(fields.stream().anyMatch(f -> "phone".equals(f.getName())),
                "应包含 phone");

        // 验证敏感字段被排除
        assertFalse(fields.stream().anyMatch(f -> "salary".equals(f.getName())),
                "salary 是敏感字段应被排除");
        assertFalse(fields.stream().anyMatch(f -> "id_card".equals(f.getName())),
                "id_card 是敏感字段应被排除");

        // 验证标签列表正确
        fields.stream()
                .filter(f -> "emp_id".equals(f.getName()))
                .findFirst()
                .ifPresent(f -> {
                    assertNotNull(f.getLabel());
                    assertEquals(2, f.getLabel().size());
                    assertTrue(f.getLabel().contains("员工ID"));
                    assertTrue(f.getLabel().contains("工号"));
                });

        log.info("✓ 敏感字段排除测试通过");
    }

    @Test
    @DisplayName("translate - 同时有 field-mapping 和 sensitive-fields 的索引翻译正常")
    void testTranslateWithFieldMappingAndSensitiveFields() {
        log.info("========== 测试：translate 混合配置索引 ==========");

        // 使用中文标签翻译（非敏感字段）
        QueryCondition result1 = expressionService.translate(
                "员工姓名 = '张三' AND 部门 = '研发部'",
                "test_employee");
        log.info("result1: {}", result1);
        assertNotNull(result1);
        assertEquals("and", result1.getLogic());

        // 验证字段映射正确
        QueryCondition cond1 = result1.getConditions().get(0);
        QueryCondition cond2 = result1.getConditions().get(1);
        assertEquals("emp_name", cond1.getField());
        assertEquals("department", cond2.getField());

        // 使用工号标签
        QueryCondition result2 = expressionService.translate(
                "工号 = 'E001'",
                "test_employee");
        log.info("result2: {}", result2);
        assertEquals("emp_id", result2.getField());

        log.info("✓ 混合配置索引翻译测试通过");
    }

    // ==================== v1.6.4 label 预替换测试 ====================

    @Test
    @DisplayName("translate - 中英混合 label（订单ID）预替换为英文字段名")
    void testTranslateMixedChineseEnglishLabel() {
        log.info("========== 测试：中英混合 label 预替换 ==========");

        // test_order_index: 订单ID → order_id
        QueryCondition result = expressionService.translate(
                "订单ID = 'xxx'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("order_id", result.getField());
        assertEquals("eq", result.getOp());
        assertEquals("xxx", result.getValue());

        log.info("✓ 中英混合 label 预替换测试通过");
    }

    @Test
    @DisplayName("translate - 中英混合 label + 纯中文 label 组合")
    void testTranslateMixedAndPureChineseLabels() {
        log.info("========== 测试：中英混合 + 纯中文 label 组合 ==========");

        // test_nl_user_index 配置了 orderId→订单ID 和 status→状态
        // 订单ID（混合）AND 状态（纯中文）
        QueryCondition result = expressionService.translate(
                "订单ID = 'xxx' AND 状态 = '已完成'", "test_nl_user_index");
        log.info("result: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("orderId", left.getField());
        assertEquals("status", right.getField());

        log.info("✓ 中英混合 + 纯中文 label 组合测试通过");
    }

    @Test
    @DisplayName("translate - 多个中英混合 label 组合")
    void testTranslateMultipleMixedLabels() {
        log.info("========== 测试：多个中英混合 label 组合 ==========");

        // 订单ID = 'xxx' AND 订单号 = 'yyy'
        QueryCondition result = expressionService.translate(
                "订单ID = 'xxx' AND 订单号 = 'yyy'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("order_id", left.getField());
        assertEquals("order_id", right.getField());

        log.info("✓ 多个中英混合 label 组合测试通过");
    }

    @Test
    @DisplayName("translate - 同一字段多个 label 映射（订单号/订单ID → order_id）")
    void testTranslateSameFieldMultipleLabels() {
        log.info("========== 测试：同一字段多个 label 映射 ==========");

        // 订单号 → order_id
        QueryCondition r1 = expressionService.translate(
                "订单号 = 'xxx'", "test_order_index");
        log.info("订单号: {}", r1);
        assertEquals("order_id", r1.getField());

        // 订单ID → order_id
        QueryCondition r2 = expressionService.translate(
                "订单ID = 'xxx'", "test_order_index");
        log.info("订单ID: {}", r2);
        assertEquals("order_id", r2.getField());

        // 两个 label 混用在同一表达式中
        QueryCondition r3 = expressionService.translate(
                "订单号 = 'xxx' AND 订单ID = 'yyy'", "test_order_index");
        log.info("混用: {}", r3);
        assertEquals("and", r3.getLogic());

        log.info("✓ 同一字段多个 label 映射测试通过");
    }

    @Test
    @DisplayName("translate - 同一 label 在表达式中多次出现")
    void testTranslateLabelAppearsMultipleTimes() {
        log.info("========== 测试：同一 label 多次出现 ==========");

        // 订单ID 出现两次，两处都被替换
        QueryCondition result = expressionService.translate(
                "订单ID = 'xxx' AND 订单ID = 'yyy'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("order_id", left.getField());
        assertEquals("order_id", right.getField());

        log.info("✓ 同一 label 多次出现测试通过");
    }

    @Test
    @DisplayName("translate - 纯中文 label 不受影响")
    void testTranslatePureChineseLabelUnaffected() {
        log.info("========== 测试：纯中文 label 不受影响 ==========");

        // 状态 = '已完成' → status = '已完成'
        QueryCondition result = expressionService.translate(
                "状态 = '已完成'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("status", result.getField());
        assertEquals("eq", result.getOp());
        assertEquals("已完成", result.getValue());

        log.info("✓ 纯中文 label 不受影响测试通过");
    }

    @Test
    @DisplayName("translate - 纯英文字段名不替换（不在 labelMap 中）")
    void testTranslatePureEnglishFieldNoReplace() {
        log.info("========== 测试：纯英文字段名不替换 ==========");

        // order_id = 'xxx' → order_id = 'xxx'（不在 labelMap 中，不替换）
        QueryCondition result = expressionService.translate(
                "order_id = 'xxx'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("order_id", result.getField());
        assertEquals("eq", result.getOp());
        assertEquals("xxx", result.getValue());

        log.info("✓ 纯英文字段名不替换测试通过");
    }

    @Test
    @DisplayName("translate - 长短 label 同时存在时长 label 优先（订单ID vs 订单）")
    void testTranslateLabelPriority() {
        log.info("========== 测试：长短 label 优先级 ==========");

        // test_employee 配置了 员工ID → emp_id，姓名 → emp_name
        // 员工ID 是长 label（3字符），工号 → emp_id（2个label）
        // 替换后 visitor 用 reverse mapping 反查英文字段名
        QueryCondition result = expressionService.translate(
                "工号 = 'E001'", "test_employee");
        log.info("result: {}", result);
        assertEquals("emp_id", result.getField());

        log.info("✓ 长短 label 优先级测试通过");
    }

    @Test
    @DisplayName("translate - label 不在 fieldMapping 中则不替换")
    void testTranslateLabelNotInMapping() {
        log.info("========== 测试：label 不在 fieldMapping 中 ==========");

        // "未知标签" 不在 test_order_index 的 field-mapping 中，原样传递
        QueryCondition result = expressionService.translate(
                "未知标签 = 'xxx'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("未知标签", result.getField());

        log.info("✓ label 不在 fieldMapping 中测试通过");
    }

    @Test
    @DisplayName("translate - 无 field-mapping 的索引不替换")
    void testTranslateNoFieldMapping() {
        log.info("========== 测试：无 field-mapping 索引 ==========");

        // test_log_* 无 field-mapping，原样传递
        QueryCondition result = expressionService.translate(
                "城市 = '北京'", "test_log_*");
        log.info("result: {}", result);
        assertEquals("城市", result.getField());

        log.info("✓ 无 field-mapping 索引测试通过");
    }

    @Test
    @DisplayName("translate - STRING 值中的 label 不会被替换")
    void testTranslateStringValueNotReplaced() {
        log.info("========== 测试：STRING 值中 label 不被替换 ==========");

        // 订单号 = 'hello' → order_id = 'hello'
        // STRING 值 'hello' 不是任何 label，不会被 replace 误伤
        QueryCondition result = expressionService.translate(
                "订单号 = 'hello'", "test_order_index");
        log.info("result: {}", result);
        assertEquals("order_id", result.getField());
        assertEquals("hello", result.getValue());

        log.info("✓ STRING 值中 label 不被替换测试通过");
    }

    @Test
    @DisplayName("translate - 中文比较运算符（等于/大于）在 label 替换后仍正常解析")
    void testTranslateChineseOperatorAfterLabelReplace() {
        log.info("========== 测试：中文比较运算符在 label 替换后仍正常 ==========");

        // 订单ID 等于 'xxx' → orderId 等于 'xxx' → orderId = 'xxx'
        QueryCondition result = expressionService.translate(
                "订单ID 等于 'xxx'", "test_nl_user_index");
        log.info("result: {}", result);
        assertEquals("orderId", result.getField());
        assertEquals("eq", result.getOp());
        assertEquals("xxx", result.getValue());

        // 城市 大于 '上海' → city 大于 '上海' → city > '上海'
        QueryCondition result2 = expressionService.translate(
                "城市 大于 '上海'", "test_nl_user_index");
        log.info("result2: {}", result2);
        assertEquals("city", result2.getField());
        assertEquals("gt", result2.getOp());

        log.info("✓ 中文比较运算符正常解析测试通过");
    }

    @Test
    @DisplayName("translate - 中文逻辑运算符（且/或/非）在 label 替换后仍正常解析")
    void testTranslateChineseLogicalOperatorAfterLabelReplace() {
        log.info("========== 测试：中文逻辑运算符在 label 替换后仍正常 ==========");

        // 订单ID='xxx' 且 状态='已完成' → orderId='xxx' 且 status='已完成'
        QueryCondition result = expressionService.translate(
                "订单ID='xxx' 且 状态='已完成'", "test_nl_user_index");
        log.info("result: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("orderId", left.getField());
        assertEquals("status", right.getField());

        log.info("✓ 中文逻辑运算符正常解析测试通过");
    }

    @Test
    @DisplayName("translate - 复杂表达式（中英混合 label + 纯中文 label + 中文运算符）")
    void testTranslateComplexExpressionMixedLabels() {
        log.info("========== 测试：复杂表达式混合场景 ==========");

        // 订单ID='xxx' 且 城市='北京'
        // 解析为 (订单ID='xxx' 且 城市='北京')，顶层 getConditions()=2
        QueryCondition result = expressionService.translate(
                "订单ID='xxx' 且 城市='北京'", "test_nl_user_index");
        log.info("result: {}", result);
        assertEquals("and", result.getLogic());
        assertEquals(2, result.getConditions().size());

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("orderId", left.getField());
        assertEquals("city", right.getField());

        log.info("✓ 复杂表达式混合场景测试通过");
    }
}
