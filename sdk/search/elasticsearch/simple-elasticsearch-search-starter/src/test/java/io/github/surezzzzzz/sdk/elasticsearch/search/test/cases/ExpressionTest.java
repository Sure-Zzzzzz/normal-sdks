package io.github.surezzzzzz.sdk.elasticsearch.search.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionValidationResult;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.ExpressionParseException;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.service.ExpressionService;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor.ExpressionToQueryConditionVisitor;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor.ExpressionVisitorRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.elasticsearch.search.test.SimpleElasticsearchSearchTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
                "威胁类型 = \"木马\" AND 攻击次数 >= 10", "test_order_index");
        log.info("field mapping: {}", result);

        QueryCondition left = result.getConditions().get(0);
        QueryCondition right = result.getConditions().get(1);
        assertEquals("threat_type", left.getField());
        assertEquals("attack_count", right.getField());
    }

    @Test
    @DisplayName("translate - 字段名映射找不到时原样透传")
    void testTranslateFieldMappingFallback() {
        QueryCondition result = expressionService.translate("未知字段 = \"值\"", "test_order_index");
        log.info("field mapping fallback: {}", result);
        assertEquals("未知字段", result.getField());
    }

    // ==================== translate - 时间范围 ====================

    @Test
    @DisplayName("translate - 时间范围 = 最近7天 转为 between")
    void testTranslateTimeRange() {
        QueryCondition result = expressionService.translate("首次告警时间 = 最近7天", "test_order_index");
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
        ExpressionValidationResult result = expressionService.validate(
                "威胁类型 = \"木马\" AND 攻击次数 >= 10");
        log.info("validate valid: {}", result);
        assertTrue(result.isValid());
        assertNull(result.getErrorMessage());
        assertEquals(-1, result.getErrorPosition());
    }

    @Test
    @DisplayName("validate - 语法错误")
    void testValidateInvalid() {
        ExpressionValidationResult result = expressionService.validate(
                "威胁类型 = \"木马\" AND 攻击次数 >=");
        log.info("validate invalid: {}", result);
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
        log.info("error: {}", result.getErrorMessage());
    }

    @Test
    @DisplayName("validate - 空表达式")
    void testValidateEmpty() {
        ExpressionValidationResult result = expressionService.validate("");
        log.info("validate empty: {}", result);
        assertFalse(result.isValid());
        assertNotNull(result.getErrorMessage());
    }

    // ==================== translate - 异常 ====================

    @Test
    @DisplayName("translate - 语法错误抛出 ExpressionParseException")
    void testTranslateThrowsOnSyntaxError() {
        assertThrows(ExpressionParseException.class, () ->
                expressionService.translate("威胁类型 = ", null));
    }
}
