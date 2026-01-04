package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.annotation.ConditionExpressionParserComponent;
import io.github.surezzzzzz.sdk.expression.condition.parser.antlr.ConditionExprLexer;
import io.github.surezzzzzz.sdk.expression.condition.parser.antlr.ConditionExprParser;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.Expression;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.*;

/**
 * 条件表达式解析器（主入口）
 * <p>
 * 这是对外提供的主要 API，用于将条件表达式字符串解析为 AST
 * <p>
 * 使用示例：
 * <pre>{@code
 * @Autowired
 * private ConditionExpressionParser parser;
 *
 * // 解析表达式
 * Expression ast = parser.parse("类型='活跃' AND 分类 IN ('高','中')");
 *
 * // 使用 Visitor 转换成目标格式
 * SqlVisitor sqlVisitor = new SqlVisitor();
 * String sql = ast.accept(sqlVisitor);
 * }</pre>
 *
 * @author surezzzzzz
 */
@Slf4j
@ConditionExpressionParserComponent
public class ConditionExpressionParser {

    private final ValueParser valueParser;

    public ConditionExpressionParser(ValueParser valueParser) {
        this.valueParser = valueParser;
    }

    /**
     * 解析条件表达式
     *
     * @param expression 条件表达式字符串
     * @return AST 根节点
     * @throws ConditionExpressionParseException 解析失败时抛出
     */
    public Expression parse(String expression) {
        // 输入校验
        if (expression == null || expression.trim().isEmpty()) {
            throw ConditionExpressionParseException.emptyExpression(expression);
        }

        try {
            // 创建词法分析器
            CharStream input = CharStreams.fromString(expression);
            ConditionExprLexer lexer = new ConditionExprLexer(input);

            // 移除默认错误监听器，添加自定义错误监听器
            lexer.removeErrorListeners();
            lexer.addErrorListener(new CustomErrorListener(expression));

            // 创建语法分析器
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            ConditionExprParser parser = new ConditionExprParser(tokens);

            // 移除默认错误监听器，添加自定义错误监听器
            parser.removeErrorListeners();
            parser.addErrorListener(new CustomErrorListener(expression));

            // 解析并构建 AST
            ConditionExprParser.ParseContext parseTree = parser.parse();
            AstBuilder astBuilder = new AstBuilder(valueParser, expression);

            return astBuilder.visit(parseTree);

        } catch (ConditionExpressionParseException e) {
            // 直接重新抛出我们的自定义异常
            throw e;
        } catch (Exception e) {
            // 其他异常转换为我们的异常
            log.error("解析条件表达式失败: {}", expression, e);
            throw ConditionExpressionParseException.builder(ConditionExpressionParseException.ErrorType.SYNTAX_ERROR)
                    .expression(expression)
                    .message("解析失败: " + e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    /**
     * 自定义错误监听器
     * 将 ANTLR 的错误转换为我们的自定义异常
     */
    private static class CustomErrorListener extends BaseErrorListener {

        private final String expression;

        public CustomErrorListener(String expression) {
            this.expression = expression;
        }

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e) {

            // 提取有问题的 token
            String offendingText = null;
            if (offendingSymbol instanceof Token) {
                offendingText = ((Token) offendingSymbol).getText();
            }

            // 尝试提供更友好的错误消息
            String friendlyMessage = makeFriendlyMessage(msg, offendingText);

            throw ConditionExpressionParseException.syntaxError(
                    expression,
                    line,
                    charPositionInLine,
                    offendingText,
                    friendlyMessage
            );
        }

        /**
         * 将 ANTLR 的技术性错误消息转换为用户友好的消息
         */
        private String makeFriendlyMessage(String antlrMessage, String offendingToken) {
            if (antlrMessage.contains("mismatched input")) {
                return "语法错误：不期望的输入 \"" + offendingToken + "\"";
            } else if (antlrMessage.contains("missing")) {
                return "语法错误：缺少必要的元素";
            } else if (antlrMessage.contains("extraneous input")) {
                return "语法错误：多余的输入 \"" + offendingToken + "\"";
            } else if (antlrMessage.contains("no viable alternative")) {
                return "语法错误：无法识别的语法结构";
            } else {
                return "语法错误：" + antlrMessage;
            }
        }
    }
}
