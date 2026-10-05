package io.github.surezzzzzz.sdk.expression.condition.parser.test.cases;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.aop.framework.ProxyFactory;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException.ErrorType.*;

/** 主入口、值策略和每项容量的边界验收。 */
@Slf4j
class ParserBoundaryTest {
    private static ValueParser values() {
        return new ValueParser(Arrays.asList(new StringValueParseStrategy(), new NumberValueParseStrategy(),
                new TimeRangeValueParseStrategy(), new BooleanValueParseStrategy()));
    }
    private final ConditionExpressionParser parser = new ConditionExpressionParser(values());

    static Stream<Arguments> limitCases() {
        List<Arguments> cases = new ArrayList<>();
        for (String name : Arrays.asList("length", "tokens", "parseDepth", "astDepth", "nodes", "conditions", "in")) {
            String expression;
            int required;
            ConditionExpressionParseException.ErrorType error;
            switch (name) {
                case "length": expression = "f=1"; required = 3; error = LENGTH_LIMIT; break;
                case "tokens": expression = "f=1"; required = 3; error = TOKEN_LIMIT; break;
                case "parseDepth": expression = "NOT (NOT f=1)"; required = 3; error = PARSE_DEPTH_LIMIT; break;
                case "astDepth": expression = "(f=1)"; required = 2; error = AST_DEPTH_LIMIT; break;
                case "nodes": expression = "f=1 AND g=2"; required = 3; error = AST_NODE_LIMIT; break;
                case "conditions": expression = "f=1 AND g=2"; required = 2; error = CONDITION_LIMIT; break;
                default: expression = "f IN (1,2,3)"; required = 3; error = IN_VALUE_LIMIT;
            }
            for (int delta = -1; delta <= 1; delta++) cases.add(Arguments.of(name, expression, required + delta, delta < 0, error));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} capacity={2} rejected={3}")
    @MethodSource("limitCases")
    void eachLimitHasThreeDirectBoundaryAssertions(String name, String expression, int limit, boolean rejected,
            ConditionExpressionParseException.ErrorType type) {
        ParseOptions options = new ParseOptions();
        switch (name) {
            case "length": options.setMaxLength(limit); break;
            case "tokens": options.setMaxTokens(limit); break;
            case "parseDepth": options.setMaxParseDepth(limit); break;
            case "astDepth": options.setMaxAstDepth(limit); break;
            case "nodes": options.setMaxAstNodes(limit); break;
            case "conditions": options.setMaxConditions(limit); break;
            default: options.setMaxInValues(limit);
        }
        log.info("容量边界 name={} limit={} rejected={}", name, limit, rejected);
        if (rejected) assertEquals(type, assertThrows(ConditionExpressionParseException.class,
                () -> parser.parse(expression, options)).getErrorType());
        else {
            Expression result = parser.parse(expression, options);
            assertEquals(1L, io.github.surezzzzzz.sdk.expression.condition.parser.support.ExpressionCollectors
                    .collectValues(result).get(0).asInteger());
        }
    }

    static Stream<Arguments> timeKeywords() {
        return TimeRange.getAllKeywords().entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }
    @ParameterizedTest
    @MethodSource("timeKeywords")
    void everyTimeKeywordIsAcceptedByLexerAndQuotedValue(String keyword, TimeRange expected) {
        log.info("时间词对账 code={}", expected.getCode());
        for (String text : Arrays.asList("stamp=" + keyword, "stamp='" + keyword + "'")) {
            ValueNode value = ((ComparisonExpression) parser.parse(text)).getValue();
            assertEquals(ValueType.TIME_RANGE, value.getType());
            assertSame(expected, value.asTimeRange());
            assertEquals(keyword, value.getRawValue());
        }
    }

    @ParameterizedTest
    @CsvSource({"True,true", "tRuE,true", "FaLsE,false", "fAlSe,false", "真,true", "假,false", "否,false"})
    void mixedCaseAndChineseBooleans(String text, boolean expected) {
        log.info("布尔值转换 expected={}", expected);
        for (String input : Arrays.asList(text, "'" + text + "'"))
            assertEquals(expected, ((ComparisonExpression) parser.parse("f=" + input)).getValue().asBoolean());
    }

    @Test
    void numbersRetainTypesAndRejectOverflow() {
        log.info("验证 Long 边界和有限 Double");
        assertEquals(Long.MAX_VALUE, ((ComparisonExpression) parser.parse("f=9223372036854775807")).getValue().asInteger());
        assertEquals(Long.MIN_VALUE, ((ComparisonExpression) parser.parse("f=-9223372036854775808")).getValue().asInteger());
        assertEquals(18L, ((ComparisonExpression) parser.parse("f='18'")).getValue().asInteger());
        assertEquals(-1.25, ((ComparisonExpression) parser.parse("f='-1.25'")).getValue().asDecimal());
        for (String text : Arrays.asList("9223372036854775808", "-9223372036854775809", "9".repeat(310) + ".0")) {
            ConditionExpressionParseException ex = assertThrows(ConditionExpressionParseException.class, () -> parser.parse("f=" + text));
            assertEquals(INVALID_VALUE, ex.getErrorType());
            assertNull(ex.getCause());
            assertEquals(1, ex.getLine());
            assertEquals(2, ex.getColumn());
        }
        assertEquals(ValueType.NULL, values().parse(null).getType());
    }

    @Test
    void runtimeAndWhitespaceLimitsAreVerified() {
        log.info("实际测试运行时 Java={} Boot={}", System.getProperty("java.version"),
                org.springframework.boot.SpringBootVersion.getVersion());
        assertEquals("3.4.2", org.springframework.boot.SpringBootVersion.getVersion());
        assertTrue(Runtime.version().feature() == 17 || Runtime.version().feature() == 21);
        assertEquals(LENGTH_LIMIT, assertThrows(ConditionExpressionParseException.class,
                () -> parser.parse(" ".repeat(4), ParseOptions.builder().maxLength(3).build())).getErrorType());
    }

    @Test
    void fieldsAndStringsPreserveText() {
        log.info("验证字段路径、保留字与引号内容");
        assertEquals("record.extraField", ((ComparisonExpression) parser.parse("record.extraField=1")).getField());
        assertEquals("记录.状态", ((ComparisonExpression) parser.parse("记录.状态=1")).getField());
        assertEquals("AND", ((ComparisonExpression) parser.parse("`AND`=1")).getField());
        assertEquals("字段 1", ((ComparisonExpression) parser.parse("`字段 1`='a'")).getField());
        assertEquals("", ((ComparisonExpression) parser.parse("f=''")).getValue().asString());
        String raw = "(NOT field) \\n \\t";
        assertEquals(raw, ((ComparisonExpression) parser.parse("f='" + raw + "'")).getValue().asString());
        InExpression in = (InExpression) parser.parse("f IN ('18',18,'sample',18)");
        assertEquals(Arrays.asList(18L, 18L, "sample", 18L),
                in.getValues().stream().map(ValueNode::getParsedValue).collect(java.util.stream.Collectors.toList()));
        for (String invalid : Arrays.asList("``=1", "`a\nb`=1", "f='a\nb'", "f=1 remaining", "f=1e3"))
            assertThrows(ConditionExpressionParseException.class, () -> parser.parse(invalid));
    }

    @Test
    void deterministicErrorCategoriesAndPositions() {
        log.info("验证词法、语法及位置信息");
        assertEquals(EMPTY_EXPRESSION, assertThrows(ConditionExpressionParseException.class, () -> parser.parse(" \n ")).getErrorType());
        assertEquals(EMPTY_EXPRESSION, assertThrows(ConditionExpressionParseException.class, () -> parser.parse(null)).getErrorType());
        assertEquals(UNCLOSED_STRING, assertThrows(ConditionExpressionParseException.class, () -> parser.parse("f='sample")).getErrorType());
        assertEquals(EMPTY_IN_LIST, assertThrows(ConditionExpressionParseException.class, () -> parser.parse("f NOT IN ()")).getErrorType());
        assertEquals(MISMATCHED_PARENTHESIS, assertThrows(ConditionExpressionParseException.class, () -> parser.parse("(f=1")).getErrorType());
        assertEquals(MISMATCHED_PARENTHESIS, assertThrows(ConditionExpressionParseException.class, () -> parser.parse("f=1)")).getErrorType());
        assertEquals(SYNTAX_ERROR, assertThrows(ConditionExpressionParseException.class, () -> parser.parse("f=")).getErrorType());
        ConditionExpressionParseException unicode = assertThrows(ConditionExpressionParseException.class,
                () -> parser.parse("名称='😀'\nAND @"));
        assertEquals(LEXICAL_ERROR, unicode.getErrorType());
        assertEquals(2, unicode.getLine());
        assertEquals(4, unicode.getColumn());
        ConditionExpressionParseException sameLine = assertThrows(ConditionExpressionParseException.class,
                () -> parser.parse("f='😀' @"));
        assertEquals(6, sameLine.getColumn());
    }

    @Test
    void exceptionBuilderPreservesPrimitiveApiAndUnknownPositions() throws Exception {
        log.info("验证异常 builder 的旧方法签名和默认位置");
        assertEquals(ConditionExpressionParseException.Builder.class,
                ConditionExpressionParseException.Builder.class.getMethod("line", int.class).getReturnType());
        assertEquals(ConditionExpressionParseException.Builder.class,
                ConditionExpressionParseException.Builder.class.getMethod("column", int.class).getReturnType());
        ConditionExpressionParseException unknown = ConditionExpressionParseException.builder(SYNTAX_ERROR).build();
        assertEquals(-1, unknown.getLine());
        assertEquals(-1, unknown.getColumn());
        ConditionExpressionParseException located = ConditionExpressionParseException.builder(SYNTAX_ERROR).line(2).column(3).build();
        assertEquals(2, located.getLine());
        assertEquals(3, located.getColumn());
    }

    @Test
    void preflightNeverCallsStrategiesForResourceRejection() {
        AtomicInteger calls = new AtomicInteger();
        ValueParseStrategy strategy = new ValueParseStrategy() {
            public boolean canParse(String raw) { calls.incrementAndGet(); return true; }
            public ValueNode parse(String raw) { return ValueNode.builder().type(ValueType.STRING).rawValue(raw).parsedValue(raw).build(); }
            public int getPriority() { return 0; }
        };
        ConditionExpressionParser guarded = new ConditionExpressionParser(new ValueParser(Collections.singletonList(strategy)));
        log.info("验证词法和递归预检拒绝时值策略未执行");
        assertThrows(ConditionExpressionParseException.class, () -> guarded.parse("f=1", ParseOptions.builder().maxLength(2).build()));
        assertThrows(ConditionExpressionParseException.class, () -> guarded.parse("f=1", ParseOptions.builder().maxTokens(2).build()));
        assertThrows(ConditionExpressionParseException.class, () -> guarded.parse("NOT (NOT f=1)",
                ParseOptions.builder().maxParseDepth(2).build()));
        assertEquals(0, calls.get());
    }

    @Test
    void notInAndQuotedPunctuationDoNotConsumePrefixBudget() {
        ParseOptions limits = ParseOptions.builder().maxParseDepth(1).build();
        log.info("验证操作符 NOT 和引号内符号不计递归容量");
        assertTrue(((InExpression) parser.parse("f NOT IN (1,2)", limits)).isNotIn());
        assertEquals("(NOT NOT)", ((ComparisonExpression) parser.parse("f='(NOT NOT)'", limits)).getValue().asString());
        assertNotNull(parser.parse("f NOT PREFIX LIKE 'sample'", limits));
        assertNotNull(parser.parse("f NOT EXISTS", limits));
        assertNotNull(parser.parse("(f=1) AND (g=2)", limits));
        assertEquals(PARSE_DEPTH_LIMIT, assertThrows(ConditionExpressionParseException.class,
                () -> parser.parse("NOT ((f=1))", ParseOptions.builder().maxParseDepth(2).build())).getErrorType());
    }

    @Test
    void deepAndLongChainsRejectWithoutStackOverflow() {
        log.info("验证深括号、前缀链和平链均有界");
        assertEquals(PARSE_DEPTH_LIMIT, assertThrows(ConditionExpressionParseException.class,
                () -> parser.parse("(".repeat(33) + "f=1" + ")".repeat(33))).getErrorType());
        assertEquals(PARSE_DEPTH_LIMIT, assertThrows(ConditionExpressionParseException.class,
                () -> parser.parse("NOT ".repeat(65) + "f=1", ParseOptions.builder().maxParseDepth(64).build())).getErrorType());
        assertEquals(AST_DEPTH_LIMIT, assertThrows(ConditionExpressionParseException.class,
                () -> parser.parse("f=1 AND ".repeat(99) + "f=1")).getErrorType());
        assertEquals(64, io.github.surezzzzzz.sdk.expression.condition.parser.support.ExpressionMetrics.calculateDepth(
                parser.parse("f=1 AND ".repeat(63) + "f=1")));
    }

    @Test
    void configurationOwnershipAndValidation() {
        log.info("验证容量快照和非法配置");
        ParseOptions options = ParseOptions.builder().maxLength(3).build();
        ConditionExpressionParser snapshot = new ConditionExpressionParser(values(), options);
        options.setMaxLength(100);
        assertEquals(LENGTH_LIMIT, assertThrows(ConditionExpressionParseException.class, () -> snapshot.parse("field=1")).getErrorType());
        assertNotNull(snapshot.parse("field=1", options));
        assertThrows(IllegalArgumentException.class, () -> snapshot.parse("f=1", null));
        assertThrows(IllegalArgumentException.class, () -> new ConditionExpressionParser(values(), null));
        assertThrows(IllegalArgumentException.class, () -> new ConditionExpressionParser(null));
        List<ParseOptions> invalid = Arrays.asList(ParseOptions.builder().maxLength(0).build(),
                ParseOptions.builder().maxTokens(-1).build(), ParseOptions.builder().maxParseDepth(65).build(),
                ParseOptions.builder().maxParseDepth(0).build(), ParseOptions.builder().maxAstDepth(0).build(),
                ParseOptions.builder().maxAstNodes(0).build(), ParseOptions.builder().maxConditions(0).build(),
                ParseOptions.builder().maxInValues(0).build());
        for (ParseOptions limit : invalid) assertThrows(IllegalArgumentException.class, () -> parser.parse("f=1", limit));
    }

    @Test
    void strategiesOwnListAndUsePriorityNotSpringOrder() {
        log.info("验证列表所有权、排序确定性和重复拒绝");
        List<ValueParseStrategy> mutable = new ArrayList<>(Arrays.asList(new StrategyB(), new StrategyA()));
        ValueParser owner = new ValueParser(Collections.unmodifiableList(mutable));
        assertTrue(mutable.get(0) instanceof StrategyB);
        mutable.clear();
        assertEquals("A", owner.parse("sample").asString());
        assertThrows(IllegalArgumentException.class, () -> new ValueParser(Arrays.asList(new StrategyA(), new StrategyA())));
        assertThrows(IllegalArgumentException.class, () -> new ValueParser(null));
        assertThrows(IllegalArgumentException.class, () -> new ValueParser(Collections.singletonList(null)));
        ProxyFactory factory = new ProxyFactory(new StrategyA());
        factory.setInterfaces(ValueParseStrategy.class);
        ValueParseStrategy proxy = (ValueParseStrategy) factory.getProxy();
        assertThrows(IllegalArgumentException.class, () -> new ValueParser(Arrays.asList(proxy, new StrategyA())));
        MutablePriority high = new MutablePriority();
        ValueParser frozen = new ValueParser(Arrays.asList(new StrategyA(), high));
        high.priority = 100;
        assertEquals("M", frozen.parse("sample").asString());
    }

    @Test
    void failedOrInvalidExtensionCannotLeakOrFallback() {
        log.info("验证扩展失败不回退且去除原因");
        ValueParseStrategy broken = new StrategyA() {
            public ValueNode parse(String raw) { throw new IllegalStateException("private-value-" + raw); }
            public int getPriority() { return -100; }
        };
        ValueParser tested = new ValueParser(Arrays.asList(broken, new StringValueParseStrategy()));
        ConditionExpressionParseException ex = assertThrows(ConditionExpressionParseException.class, () -> tested.parse("sample-secret"));
        assertEquals(INVALID_VALUE, ex.getErrorType());
        assertNull(ex.getCause());
        assertFalse(ex.toString().contains("sample-secret"));
        ValueParseStrategy invalid = new StrategyA() {
            public ValueNode parse(String raw) { return null; }
        };
        assertThrows(ConditionExpressionParseException.class, () -> new ValueParser(Collections.singletonList(invalid)).parse("x"));
        ValueParseStrategy wrongType = new StrategyA() {
            public ValueNode parse(String raw) { return new ValueNode(ValueType.INTEGER, raw, 1); }
        };
        assertEquals(INVALID_VALUE, assertThrows(ConditionExpressionParseException.class,
                () -> new ValueParser(Collections.singletonList(wrongType)).parse("x")).getErrorType());
        assertThrows(ConditionExpressionParseException.class, () -> new ValueParser(Collections.emptyList()).parse("x"));
    }

    @Test
    void keywordOwnershipAndEnumCodes() {
        log.info("验证时间表和枚举代码");
        String alias = TimeRange.LAST_7_DAYS.getAliases()[0];
        TimeRange.LAST_7_DAYS.getAliases()[0] = "modified";
        assertSame(TimeRange.LAST_7_DAYS, TimeRange.fromKeyword(alias));
        assertThrows(UnsupportedOperationException.class, () -> TimeRange.getAllKeywords().clear());
        assertEquals(14, TimeRange.LAST_14_DAYS.getAmount());
        assertEquals(java.time.temporal.ChronoUnit.DAYS, TimeRange.LAST_30_DAYS.getUnit());
        assertEquals(java.time.temporal.ChronoUnit.MONTHS, TimeRange.LAST_1_MONTH.getUnit());
        assertTrue(TimeRange.LAST_90_DAYS.isLastType());
        assertTrue(TimeRange.LAST_YEAR.isRelativeType());
        assertFalse(TimeRange.LAST_YEAR.isLastType());
        for (ComparisonOperator op : ComparisonOperator.values()) assertSame(op, ComparisonOperator.fromCode(op.getCode()));
        for (LogicalOperator op : LogicalOperator.values()) assertSame(op, LogicalOperator.fromCode(op.getCode()));
        for (MatchOperator op : MatchOperator.values()) assertSame(op, MatchOperator.fromCode(op.getCode()));
        for (UnaryOperator op : UnaryOperator.values()) assertSame(op, UnaryOperator.fromCode(op.getCode()));
        for (ValueType op : ValueType.values()) assertSame(op, ValueType.fromCode(op.getCode()));
        for (TimeRange op : TimeRange.values()) assertSame(op, TimeRange.fromCode(op.getCode()));
        assertNull(TimeRange.fromCode(null));
        assertFalse(ComparisonOperator.isValid("unknown"));
        assertEquals(ComparisonOperator.values().length, ComparisonOperator.getAllCodes().length);
    }

    @Test
    void parserHasNoCrossRequestState() throws Exception {
        log.info("验证同一解析器的并发隔离");
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (long i = 0; i < 200; i++) {
                long expected = i;
                results.add(executor.submit(() -> ((ComparisonExpression) parser.parse("f=" + expected)).getValue().asInteger()));
            }
            for (int i = 0; i < results.size(); i++) assertEquals((long) i, results.get(i).get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }

    @org.springframework.core.annotation.Order(100)
    static class StrategyA implements ValueParseStrategy {
        public boolean canParse(String raw) { return true; }
        public ValueNode parse(String raw) { return ValueNode.builder().type(ValueType.STRING).parsedValue("A").rawValue(raw).build(); }
        public int getPriority() { return 0; }
    }
    @org.springframework.core.annotation.Order(-100)
    static class StrategyB extends StrategyA {
        public ValueNode parse(String raw) { return ValueNode.builder().type(ValueType.STRING).parsedValue("B").rawValue(raw).build(); }
    }
    static class MutablePriority extends StrategyA {
        int priority = -1;
        public int getPriority() { return priority; }
        public ValueNode parse(String raw) { return ValueNode.builder().type(ValueType.STRING).parsedValue("M").rawValue(raw).build(); }
    }
}
