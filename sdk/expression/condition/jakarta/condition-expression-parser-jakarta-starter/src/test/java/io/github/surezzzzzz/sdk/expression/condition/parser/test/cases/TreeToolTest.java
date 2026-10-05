package io.github.surezzzzzz.sdk.expression.condition.parser.test.cases;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ExpressionValidationException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.support.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.visitor.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 每类节点、树工具及手工无效树的契约测试。 */
@Slf4j
class TreeToolTest {
    private final ConditionExpressionParser parser = new ConditionExpressionParser(new ValueParser(Arrays.asList(
            new BooleanValueParseStrategy(), new TimeRangeValueParseStrategy(), new NumberValueParseStrategy(), new StringValueParseStrategy())));

    @Test
    void everyNodeWorksInCollectorsMetricsAndPrinter() {
        log.info("验证七类表达式及存在性节点");
        Expression root = parser.parse("(a=1 OR b IN (2,2)) AND NOT c NOT LIKE 'sample' AND d IS NULL AND e EXISTS AND e NOT EXISTS");
        assertEquals(new LinkedHashSet<>(Arrays.asList("a", "b", "c", "d", "e")), ExpressionCollectors.collectFields(root));
        assertEquals(Arrays.asList(1L, 2L, 2L, "sample"), ExpressionCollectors.collectValues(root).stream()
                .map(ValueNode::getParsedValue).collect(java.util.stream.Collectors.toList()));
        assertEquals(6, ExpressionMetrics.countConditions(root));
        assertTrue(ExpressionPrinter.toCompactString(root).contains("e EXISTS"));
        assertTrue(ExpressionPrinter.toTreeString(root).contains("e NOT_EXISTS"));
        assertTrue(BaseExpressionVisitor.containsField(root, "d"));
        assertFalse(BaseExpressionVisitor.containsField(root, "missing"));
        assertTrue(BaseExpressionVisitor.findFieldCondition(root, "e") instanceof LikeExpression);
        assertEquals(1, BaseExpressionVisitor.findConditions(root, e -> e instanceof InExpression).size());
        assertFalse(BaseExpressionVisitor.isAllAnd(root));
        assertFalse(BaseExpressionVisitor.isAllOr(root));
        assertThrows(UnsupportedOperationException.class, () -> ExpressionCollectors.collectFields(root).clear());
        assertThrows(UnsupportedOperationException.class, () -> ExpressionCollectors.collectValues(root).clear());
    }

    @Test
    void logicalDepthPreservesTransparentParentheses() {
        log.info("验证逻辑深度与实际树深度区别");
        Expression root = parser.parse("((f=1))");
        assertEquals(1, ExpressionMetrics.calculateDepth(root));
        assertEquals(3, ExpressionTreeHelper.walk(root, null).getAstDepth());
        assertEquals(3, ExpressionTreeHelper.walk(root, null).getNodes());
        Expression binary = parser.parse("NOT (f=1 AND g=2)");
        assertEquals(3, ExpressionMetrics.calculateDepth(binary));
        assertEquals(4, ExpressionTreeHelper.walk(binary, null).getAstDepth());
        ExpressionValidationException depth = assertThrows(ExpressionValidationException.class, () -> ExpressionMetrics.validateDepth(binary, 2));
        assertEquals(ExpressionValidationException.MetricType.DEPTH, depth.getMetricType());
        assertEquals(3, depth.getActualValue());
        assertEquals(2, depth.getMaxValue());
        assertThrows(ExpressionValidationException.class, () -> ExpressionMetrics.validateConditionCount(binary, 1));
        assertThrows(IllegalArgumentException.class, () -> ExpressionMetrics.validateDepth(binary, 0));
    }

    @Test
    void sharedSubtreeIsCountedByOccurrenceNotMistakenForCycle() {
        log.info("验证共享子树和首次字段顺序");
        Expression leaf = parser.parse("f=1");
        Expression shared = new BinaryExpression(leaf, LogicalOperator.AND, leaf);
        assertEquals(3, ExpressionTreeHelper.walk(shared, null).getNodes());
        assertEquals(2, ExpressionMetrics.countConditions(shared));
        assertEquals(2, ExpressionCollectors.collectValues(shared).size());
        assertEquals(Collections.singleton("f"), ExpressionCollectors.collectFields(shared));
        assertSame(leaf, BaseExpressionVisitor.findFieldCondition(shared, "f"));
    }

    @Test
    void allPublicTreeToolsRejectCyclesAndMalformedTrees() {
        log.info("验证循环、空孩子、未知节点和无效叶子");
        ParenthesisExpression cycle = new ParenthesisExpression();
        cycle.setExpression(cycle);
        Expression unknown = new Expression() { public <R> R accept(ExpressionVisitor<R> visitor) { return null; } };
        List<Expression> invalid = Arrays.asList(cycle, unknown, new ParenthesisExpression(),
                new BinaryExpression(parser.parse("f=1"), LogicalOperator.AND, null),
                new UnaryExpression(null, parser.parse("f=1")), new ComparisonExpression(),
                new InExpression("f", false, new ArrayList<>()),
                new LikeExpression("f", MatchOperator.LIKE, null),
                new LikeExpression("f", MatchOperator.EXISTS, new ValueNode(ValueType.STRING, "x", "x")),
                new ComparisonExpression("f", ComparisonOperator.EQ, new ValueNode(ValueType.INTEGER, "1", 1)));
        for (Expression root : invalid) {
            ExpressionValidationException error = assertThrows(ExpressionValidationException.class, () -> ExpressionMetrics.calculateDepth(root));
            assertEquals(ExpressionValidationException.MetricType.INVALID_TREE, error.getMetricType());
            assertEquals(ErrorMessage.INVALID_TREE, error.getMessage());
            assertThrows(ExpressionValidationException.class, () -> ExpressionMetrics.countConditions(root));
            assertThrows(ExpressionValidationException.class, () -> ExpressionCollectors.collectFields(root));
            assertThrows(ExpressionValidationException.class, () -> ExpressionCollectors.collectValues(root));
            assertThrows(ExpressionValidationException.class, () -> ExpressionPrinter.toCompactString(root));
            assertThrows(ExpressionValidationException.class, () -> ExpressionPrinter.toTreeString(root));
            assertThrows(ExpressionValidationException.class, () -> BaseExpressionVisitor.containsField(root, "f"));
            assertThrows(ExpressionValidationException.class, () -> BaseExpressionVisitor.findConditions(root, e -> true));
            assertThrows(ExpressionValidationException.class, () -> BaseExpressionVisitor.isAllAnd(root));
        }
    }

    @Test
    void toolsAreBoundedBeforeAnyConsumerSideEffect() {
        log.info("验证深手工树、出现次数容量及消费时机");
        Expression root = parser.parse("f=1");
        for (int i = 0; i < 64; i++) root = new ParenthesisExpression(root);
        Expression deep = root;
        assertEquals(ExpressionValidationException.MetricType.AST_DEPTH,
                assertThrows(ExpressionValidationException.class, () -> ExpressionMetrics.calculateDepth(deep)).getMetricType());
        assertEquals(65, ExpressionTreeHelper.walk(deep, ParseOptions.builder().maxAstDepth(65).build(), null).getAstDepth());
        Expression dag = parser.parse("f=1");
        for (int i = 0; i < 10; i++) dag = new BinaryExpression(dag, LogicalOperator.AND, dag);
        Expression many = dag;
        assertThrows(ExpressionValidationException.class, () -> ExpressionTreeHelper.walk(many,
                ParseOptions.builder().maxConditions(1000).maxAstNodes(1000).build(), null));
        List<Expression> consumed = new ArrayList<>();
        assertThrows(ExpressionValidationException.class, () -> ExpressionTreeHelper.walk(
                new BinaryExpression(parser.parse("f=1"), LogicalOperator.AND, null), (node, depth) -> consumed.add(node)));
        assertTrue(consumed.isEmpty());
    }

    @Test
    void emptyTreesAndTopologyHelpersHaveExplicitSemantics() {
        log.info("验证空树、NOT 拓扑和默认访问者");
        assertEquals(0, ExpressionMetrics.calculateDepth(null));
        assertEquals(0, ExpressionMetrics.countConditions(null));
        assertTrue(ExpressionCollectors.collectFields(null).isEmpty());
        assertTrue(ExpressionCollectors.collectValues(null).isEmpty());
        assertEquals("", ExpressionPrinter.toCompactString(null));
        assertEquals("", ExpressionPrinter.toTreeString(null));
        assertNull(BaseExpressionVisitor.findFieldCondition(null, "f"));
        assertTrue(BaseExpressionVisitor.isAllAnd(parser.parse("NOT (f=1 AND g=2)")));
        assertTrue(BaseExpressionVisitor.isAllOr(parser.parse("NOT (f=1 OR g=2)")));
        int count = parser.parse("f=1 AND NOT (g=2 OR h=3)").accept(new BaseExpressionVisitor<Integer>() {
            public Integer visitComparison(ComparisonExpression expr) { return 1; }
            protected Integer combineBinaryResults(Integer left, Integer right, LogicalOperator op) { return left + right; }
            protected Integer combineUnaryResult(Integer operand, UnaryOperator op) { return operand; }
            protected Integer getDefaultResult() { return 0; }
        });
        assertEquals(3, count);
    }

    @Test
    void modelToStringNeverPrintsFieldsValuesOrChildren() {
        log.info("验证所有模型的默认输出不携带内容");
        Expression root = parser.parse("privateField='privateValue' AND NOT (other IN (1,2))");
        ExpressionTreeHelper.walk(root, (node, depth) -> {
            assertFalse(node.toString().contains("privateField"));
            assertFalse(node.toString().contains("privateValue"));
            assertFalse(node.toString().contains("other"));
        });
        ValueNode value = ((ComparisonExpression) ((BinaryExpression) root).getLeft()).getValue();
        assertFalse(value.toString().contains("privateValue"));
        assertTrue(ExpressionPrinter.toCompactString(root).contains("privateValue"));
    }
}
