package io.github.surezzzzzz.sdk.expression.condition.parser.model;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ConditionExpressionParserConstant;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ErrorMessage;
import lombok.*;

/** 每次解析的完整限值；快照复制，禁止以零或负数关闭保护。 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ParseOptions {
    /** Java UTF-16 单元数，不是字节数。 */
    @Builder.Default
    private int maxLength = ConditionExpressionParserConstant.DEFAULT_MAX_LENGTH;
    /** 非 EOF 词法单元数。 */
    @Builder.Default
    private int maxTokens = ConditionExpressionParserConstant.DEFAULT_MAX_TOKENS;
    /** 括号和前缀 NOT 的合计递归负担。 */
    @Builder.Default
    private int maxParseDepth = ConditionExpressionParserConstant.DEFAULT_MAX_PARSE_DEPTH;
    /** 包括括号的实际树深度。 */
    @Builder.Default
    private int maxAstDepth = ConditionExpressionParserConstant.DEFAULT_MAX_AST_DEPTH;
    /** 包括容器及叶子的节点数。 */
    @Builder.Default
    private int maxAstNodes = ConditionExpressionParserConstant.DEFAULT_MAX_AST_NODES;
    /** 叶子条件数。 */
    @Builder.Default
    private int maxConditions = ConditionExpressionParserConstant.DEFAULT_MAX_CONDITIONS;
    /** 每个 IN 列表的值数。 */
    @Builder.Default
    private int maxInValues = ConditionExpressionParserConstant.DEFAULT_MAX_IN_VALUES;

    /** 校验并返回独立快照；不持有调用方配置。 */
    public static ParseOptions snapshot(ParseOptions options) {
        if (options == null) throw new IllegalArgumentException(ErrorMessage.INVALID_CONFIGURATION);
        ParseOptions copy = options.toBuilder().build();
        if (copy.maxLength <= 0 || copy.maxTokens <= 0 || copy.maxParseDepth <= 0
                || copy.maxParseDepth > ConditionExpressionParserConstant.MAX_PARSE_DEPTH
                || copy.maxAstDepth <= 0 || copy.maxAstNodes <= 0
                || copy.maxConditions <= 0 || copy.maxInValues <= 0) {
            throw new IllegalArgumentException(ErrorMessage.INVALID_CONFIGURATION);
        }
        return copy;
    }
}
