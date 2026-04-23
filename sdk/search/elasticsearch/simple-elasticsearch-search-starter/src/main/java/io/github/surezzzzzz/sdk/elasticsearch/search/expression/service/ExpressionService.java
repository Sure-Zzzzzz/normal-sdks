package io.github.surezzzzzz.sdk.elasticsearch.search.expression.service;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.endpoint.response.ExpressionValidationResult;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.ExpressionParseException;
import io.github.surezzzzzz.sdk.elasticsearch.search.expression.visitor.ExpressionVisitorRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryCondition;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.Expression;
import io.github.surezzzzzz.sdk.expression.condition.parser.parser.ConditionExpressionParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

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
