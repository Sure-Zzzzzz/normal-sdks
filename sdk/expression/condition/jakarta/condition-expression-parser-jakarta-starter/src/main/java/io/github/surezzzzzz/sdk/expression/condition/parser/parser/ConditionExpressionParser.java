package io.github.surezzzzzz.sdk.expression.condition.parser.parser;

import io.github.surezzzzzz.sdk.expression.condition.parser.antlr.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ConditionExpressionParserConstant;
import io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException;
import io.github.surezzzzzz.sdk.expression.condition.parser.model.*;
import io.github.surezzzzzz.sdk.expression.condition.parser.support.ExpressionTreeHelper;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.*;
import java.util.*;
import static io.github.surezzzzzz.sdk.expression.condition.parser.exception.ConditionExpressionParseException.ErrorType.*;

/** 主解析入口；每次调用独占词法、语法与构树状态，不保存结果或输入。 */
@Slf4j
public class ConditionExpressionParser {
    private final ValueParser valueParser;
    private final ParseOptions defaults;

    /** 使用 SDK 默认容量，可不启动 Spring。 */
    public ConditionExpressionParser(ValueParser valueParser) { this(valueParser, new ParseOptions()); }
    /** 复制完整默认容量；null 或无效配置拒绝。 */
    public ConditionExpressionParser(ValueParser valueParser, ParseOptions options) {
        if (valueParser == null) throw new IllegalArgumentException(ErrorMessage.INVALID_CONFIGURATION);
        this.valueParser = valueParser;
        this.defaults = ParseOptions.snapshot(options);
    }
    /** 使用构造时快照解析；引号不强制值为字符串类型。 */
    public Expression parse(String expression) { return parse(expression, defaults); }

    /** 显式选项完整使用且立即复制；拒绝时不返回部分树、不输出原文或原因。 */
    public Expression parse(String expression, ParseOptions options) {
        ParseOptions limits = ParseOptions.snapshot(options);
        long started = System.nanoTime();
        int length = expression == null ? 0 : expression.length();
        log.debug("Condition parse start length={}", length);
        try {
            if (expression == null)
                throw ConditionExpressionParseException.emptyExpression(expression);
            if (length > limits.getMaxLength()) throw failure(LENGTH_LIMIT, expression, null);
            if (expression.trim().isEmpty()) throw ConditionExpressionParseException.emptyExpression(expression);
            ConditionExprLexer lexer = new ConditionExprLexer(CharStreams.fromString(expression));
            lexer.removeErrorListeners();
            lexer.addErrorListener(new SafeErrorListener(expression));
            List<Token> boundedTokens = new ArrayList<>();
            while (true) {
                Token token = lexer.nextToken();
                if (token.getType() != Token.EOF && boundedTokens.size() >= limits.getMaxTokens())
                    throw failure(TOKEN_LIMIT, expression, token);
                boundedTokens.add(token);
                if (token.getType() == Token.EOF) break;
            }
            log.debug("Condition lexical complete length={} tokens={}", length, boundedTokens.size() - 1);
            preflight(boundedTokens, limits, expression);
            ConditionExprParser parser = new ConditionExprParser(new CommonTokenStream(new ListTokenSource(boundedTokens)));
            parser.removeErrorListeners();
            parser.addErrorListener(new SafeErrorListener(expression));
            Expression result = new AstBuilder(valueParser, expression, limits).visit(parser.parse());
            ExpressionTreeHelper.Statistics stats = ExpressionTreeHelper.walk(result, limits, null);
            log.debug("Condition parse complete tokens={} nodes={} conditions={} depth={} elapsedNanos={}",
                    boundedTokens.size() - 1, stats.getNodes(), stats.getConditions(),
                    stats.getAstDepth(), System.nanoTime() - started);
            return result;
        } catch (ConditionExpressionParseException ex) {
            log.debug("Condition parse rejected category={} line={} column={} elapsedNanos={}",
                    ex.getErrorType(), ex.getLine(), ex.getColumn(), System.nanoTime() - started);
            throw ex;
        } catch (RuntimeException ex) {
            log.debug("Condition parse rejected category={} elapsedNanos={}", SYNTAX_ERROR, System.nanoTime() - started);
            throw failure(SYNTAX_ERROR, expression, null);
        }
    }

    // 只统计表达式括号及前缀 NOT；IN 列表括号、字段后的 NOT 不增加递归负担。
    private void preflight(List<Token> tokens, ParseOptions limits, String expression) {
        Deque<Scope> stack = new ArrayDeque<>();
        int outer = 0;
        int pending = 0;
        boolean expectOperand = true;
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            int type = token.getType();
            if (type == ConditionExprLexer.NOT && expectOperand) {
                pending++;
            } else if (type == ConditionExprLexer.LPAREN) {
                boolean list = !expectOperand;
                stack.push(new Scope(outer, pending));
                if (!list) {
                    outer += pending + 1;
                    pending = 0;
                    expectOperand = true;
                } else if (i + 1 < tokens.size() && tokens.get(i + 1).getType() == ConditionExprLexer.RPAREN) {
                    throw failure(EMPTY_IN_LIST, expression, token);
                }
            } else if (type == ConditionExprLexer.RPAREN) {
                if (stack.isEmpty()) throw failure(MISMATCHED_PARENTHESIS, expression, token);
                Scope scope = stack.pop();
                outer = scope.outer;
                pending = scope.pending;
                expectOperand = false;
            } else if (type == ConditionExprLexer.AND || type == ConditionExprLexer.OR) {
                pending = 0;
                expectOperand = true;
            } else if (type != Token.EOF) {
                expectOperand = false;
            }
            if (outer + pending > limits.getMaxParseDepth())
                throw failure(PARSE_DEPTH_LIMIT, expression, token);
        }
        if (!stack.isEmpty()) throw failure(MISMATCHED_PARENTHESIS, expression, tokens.get(tokens.size() - 1));
    }

    private static ConditionExpressionParseException failure(ConditionExpressionParseException.ErrorType type,
            String expression, Token token) {
        ConditionExpressionParseException.Builder builder = ConditionExpressionParseException.builder(type).expression(expression);
        if (token != null) {
            builder.line(token.getLine()).column(token.getCharPositionInLine());
            if (token.getType() != Token.EOF) builder.offendingToken(token.getText());
        }
        return builder.build();
    }

    @Value
    private static class Scope {
        int outer;
        int pending;
    }

    private static class SafeErrorListener extends BaseErrorListener {
        private final String expression;
        SafeErrorListener(String expression) { this.expression = expression; }
        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                int line, int column, String message, RecognitionException cause) {
            ConditionExpressionParseException.ErrorType type = recognizer instanceof Lexer ? LEXICAL_ERROR : SYNTAX_ERROR;
            if (cause instanceof LexerNoViableAltException) {
                int index = ((LexerNoViableAltException) cause).getStartIndex();
                int utf16 = expression.offsetByCodePoints(0, index);
                char ch = expression.charAt(utf16);
                if (ch == ConditionExpressionParserConstant.SINGLE_QUOTE || ch == ConditionExpressionParserConstant.DOUBLE_QUOTE)
                    type = UNCLOSED_STRING;
            }
            ConditionExpressionParseException.Builder builder = ConditionExpressionParseException.builder(type)
                    .expression(expression).line(line).column(column);
            if (offendingSymbol instanceof Token && ((Token) offendingSymbol).getType() != Token.EOF)
                builder.offendingToken(((Token) offendingSymbol).getText());
            throw builder.build();
        }
    }
}
