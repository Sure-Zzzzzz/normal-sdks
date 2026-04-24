package io.github.surezzzzzz.sdk.elasticsearch.search.expression.service;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionHintsResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionValidationResult;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.ExpressionParseException;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor.ExpressionVisitorRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.TimeRange;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.Expression;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.ConditionExpressionParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 条件表达式服务
 * 将条件表达式字符串转换为 QueryCondition，供查询执行器直接使用
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class ExpressionService {

    private final ConditionExpressionParser expressionParser;
    private final ExpressionVisitorRegistry visitorRegistry;
    private final SimpleElasticsearchSearchProperties properties;

    /**
     * 将条件表达式字符串转换为 QueryCondition
     * 字段名映射从索引配置中自动获取
     *
     * @param expression 表达式字符串
     * @param index      索引别名，用于查找字段名映射
     * @return QueryCondition，可直接赋值给 QueryRequest.query 或 AggRequest.query
     * @throws ExpressionParseException 语法错误或超长时抛出
     */
    public QueryCondition translate(String expression, String index) {
        validateLength(expression);
        log.debug("Translating expression: index={}", index);
        try {
            Expression ast = expressionParser.parse(expression);
            QueryCondition condition = ast.accept(visitorRegistry.resolve(index));
            log.debug("Expression translated successfully");
            return condition;
        } catch (ConditionExpressionParseException e) {
            log.warn("Expression parse failed: {}", e.getMessage());
            throw new ExpressionParseException(
                    String.format(ErrorMessage.EXPRESSION_PARSE_FAILED, e.getMessage()), e);
        }
    }

    /**
     * 校验表达式语法，不抛异常
     *
     * @param expression 表达式字符串
     * @return 校验结果
     */
    public ExpressionValidationResult validate(String expression) {
        try {
            expressionParser.parse(expression);
            return ExpressionValidationResult.builder()
                    .valid(true)
                    .errorPosition(-1)
                    .build();
        } catch (ConditionExpressionParseException e) {
            return ExpressionValidationResult.builder()
                    .valid(false)
                    .errorMessage(e.getMessage())
                    .errorPosition(e.getColumn() >= 0 ? e.getColumn() : -1)
                    .build();
        }
    }

    /**
     * 获取表达式提示信息（供前端自动补全）
     *
     * @param index 索引别名
     * @return 提示信息
     */
    public ExpressionHintsResponse getHints(String index) {
        // 1. 字段列表：searchable=true，排除 sensitive
        List<ExpressionHintsResponse.FieldHint> fields = buildFieldHints(index);

        // 2. 运算符列表
        List<ExpressionHintsResponse.OperatorHint> operators = buildOperatorHints();

        // 3. 时间范围主关键字
        List<String> timeRanges = Arrays.stream(TimeRange.values())
                .map(TimeRange::getKeyword)
                .collect(Collectors.toList());

        // 4. 值规则
        ExpressionHintsResponse.ValueRules valueRules = ExpressionHintsResponse.ValueRules.builder()
                .stringNeedsQuote(true)
                .supportedQuotes(Arrays.asList("'", "\""))
                .booleanKeywords(Arrays.asList("true", "false", "真", "假"))
                .numberNoQuote(true)
                .build();

        return ExpressionHintsResponse.builder()
                .fields(fields)
                .operators(operators)
                .timeRanges(timeRanges)
                .valueRules(valueRules)
                .build();
    }

    private List<ExpressionHintsResponse.FieldHint> buildFieldHints(String index) {
        Map<String, List<String>> labelMap = visitorRegistry.resolveLabelMap(index);
        if (labelMap.isEmpty()) {
            return Collections.emptyList();
        }
        return labelMap.entrySet().stream()
                .map(e -> ExpressionHintsResponse.FieldHint.builder()
                        .name(e.getKey())
                        .label(e.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private static final List<ExpressionHintsResponse.OperatorHint> OPERATOR_HINTS = Arrays.asList(
            ExpressionHintsResponse.OperatorHint.builder().op("=").description("等于").chinese("等于").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("!=").description("不等于").chinese("不等于").build(),
            ExpressionHintsResponse.OperatorHint.builder().op(">").description("大于").chinese("大于、晚于").build(),
            ExpressionHintsResponse.OperatorHint.builder().op(">=").description("大于等于").chinese("大于等于").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("<").description("小于").chinese("小于、早于").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("<=").description("小于等于").chinese("小于等于").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("IN").description("在列表中").chinese("包含于").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("NOT IN").description("不在列表中").chinese("不包含于").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("LIKE").description("模糊匹配").chinese("包含").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("PREFIX LIKE").description("前缀匹配").chinese("前缀").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("SUFFIX LIKE").description("后缀匹配").chinese("后缀").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("NOT LIKE").description("模糊不匹配").chinese("不包含").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("IS NULL").description("为空").chinese("空、为空").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("IS NOT NULL").description("不为空").chinese("非空").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("AND").description("逻辑与").chinese("且、并且").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("OR").description("逻辑或").chinese("或、或者").build(),
            ExpressionHintsResponse.OperatorHint.builder().op("NOT").description("逻辑非").chinese("非").build()
    );

    private List<ExpressionHintsResponse.OperatorHint> buildOperatorHints() {
        return OPERATOR_HINTS;
    }

    /**
     * 校验表达式长度，超限时抛出异常
     */
    private void validateLength(String expression) {
        int maxLength = properties.getApi().getExpression().getMaxLength();
        if (maxLength > 0 && StringUtils.hasText(expression) && expression.length() > maxLength) {
            throw new ExpressionParseException(
                    String.format(ErrorMessage.EXPRESSION_TOO_LONG, maxLength, expression.length()));
        }
    }
}
