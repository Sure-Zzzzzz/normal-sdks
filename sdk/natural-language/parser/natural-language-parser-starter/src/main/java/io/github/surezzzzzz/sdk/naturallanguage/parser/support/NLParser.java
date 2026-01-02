package io.github.surezzzzzz.sdk.naturallanguage.parser.support;

import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.*;
import io.github.surezzzzzz.sdk.naturallanguage.parser.parser.*;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.NLTokenizer;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 自然语言解析器（门面/协调者）
 * <p>
 * 职责：
 * 1. 协调各个专门的parser
 * 2. 分词（委托给tokenizer）
 * 3. 组装最终的Intent对象
 * <p>
 * 架构模式：
 * - 门面模式（Facade Pattern）：为子系统提供统一接口
 * - 组合模式（Composition Pattern）：组合多个parser
 * - 策略模式（Strategy Pattern）：各parser独立实现解析策略
 * <p>
 * 线程安全：
 * 本类是线程安全的，因为：
 * 1. tokenizer是final的不可变引用
 * 2. 各个parser都是无状态的线程安全实例
 * 3. parse方法中所有状态都是方法局部变量
 * 4. 无共享可变状态
 *
 * @author surezzzzzz
 */
@Slf4j
@NaturalLanguageParserComponent
public class NLParser {

    // ========== 依赖组件 ==========

    /**
     * 分词器
     */
    private final NLTokenizer tokenizer;

    /**
     * 索引提取器
     */
    private final IndexExtractor indexExtractor;

    /**
     * 条件解析器
     */
    private final ConditionParser conditionParser;

    /**
     * 聚合解析器
     */
    private final AggregationParser aggregationParser;

    /**
     * 排序解析器
     */
    private final SortParser sortParser;

    /**
     * 分页解析器
     */
    private final PaginationParser paginationParser;

    /**
     * 构造函数
     *
     * @param tokenizer 分词器实例（必须非null）
     * @throws IllegalArgumentException 如果tokenizer为null
     */
    public NLParser(NLTokenizer tokenizer) {
        if (tokenizer == null) {
            throw new IllegalArgumentException("tokenizer不能为null");
        }
        this.tokenizer = tokenizer;

        // 初始化各个parser（无状态，可以复用）
        this.indexExtractor = new IndexExtractor();
        this.conditionParser = new ConditionParser();
        this.aggregationParser = new AggregationParser();
        this.sortParser = new SortParser();
        this.paginationParser = new PaginationParser();
    }

    /**
     * 解析自然语言为意图
     *
     * @param naturalLanguage 自然语言查询
     * @return 解析后的意图
     * @throws IllegalArgumentException 如果输入为空或只包含停用词
     * @throws NLParseException         如果解析过程中遇到语法错误
     */
    public Intent parse(String naturalLanguage) {
        try {
            // 1. 分词
            List<Token> tokens = tokenizer.tokenize(naturalLanguage);

            if (tokens == null || tokens.isEmpty()) {
                throw NLParseException.emptyQuery(naturalLanguage);
            }

            // 打印token化结果（用于调试）
            logTokenizationResult(tokens);

            // 2. 提取索引/表名（会修改tokens列表，移除已识别的索引相关token）
            String indexHint = indexExtractor.extractAndRemove(tokens);

            // 3. 解析各个组件（顺序无关，各parser独立工作）
            List<AggregationIntent> aggregations = aggregationParser.parse(tokens);
            ConditionIntent condition = conditionParser.parse(tokens);
            List<SortIntent> sorts = sortParser.parse(tokens);
            PaginationIntent pagination = paginationParser.parse(tokens);

            // 4. 根据是否有聚合决定返回类型
            Intent intent;
            if (!aggregations.isEmpty()) {
                // 有聚合 → 返回 AnalyticsIntent
                intent = AnalyticsIntent.builder()
                        .condition(condition)
                        .aggregations(aggregations)
                        .build();
            } else {
                // 普通查询 → 返回 QueryIntent
                intent = QueryIntent.builder()
                        .condition(condition)
                        .sorts(sorts)
                        .pagination(pagination)
                        .build();
            }

            // 5. 设置索引提示
            if (indexHint != null) {
                intent.setIndexHint(indexHint);
            }

            return intent;
        } catch (NLParseException e) {
            // 如果异常中没有查询文本，添加上去
            if (e.getQuery() == null) {
                throw enrichExceptionWithQuery(e, naturalLanguage);
            }
            throw e;
        }
    }

    /**
     * 为异常添加原始查询文本上下文
     */
    private NLParseException enrichExceptionWithQuery(NLParseException e, String query) {
        return NLParseException.builder(e.getErrorType())
                .query(query)
                .position(e.getPosition())
                .parseState(e.getParseState())
                .suggestion(e.getSuggestion())
                .relatedToken(e.getRelatedToken())
                .message(e.getMessage().split("\n")[0])  // 只保留第一行消息
                .build();
    }

    /**
     * 打印Token化结果（用于调试和查看）
     *
     * @param tokens token列表
     */
    private void logTokenizationResult(List<Token> tokens) {
        if (!log.isInfoEnabled()) {
            return;
        }

        log.info("\n【Token化结果】共{}个token:", tokens.size());
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            StringBuilder tokenInfo = new StringBuilder();
            tokenInfo.append(String.format("  [%d] 类型=%-15s 文本='%s'", i, token.getType(), token.getText()));

            // 根据类型打印额外信息
            switch (token.getType()) {
                case OPERATOR:
                    tokenInfo.append(String.format(" → 操作符=%s", token.getOperatorType()));
                    break;
                case LOGIC:
                    tokenInfo.append(String.format(" → 逻辑=%s", token.getLogicType()));
                    break;
                case AGGREGATION:
                    tokenInfo.append(String.format(" → 聚合=%s", token.getAggType()));
                    break;
                case SORT:
                    tokenInfo.append(String.format(" → 排序=%s", token.getSortOrder()));
                    break;
                case NUMBER:
                    tokenInfo.append(String.format(" → 数值=%s", token.getValue()));
                    break;
                default:
                    // 其他类型不打印额外信息
                    break;
            }
            log.info(tokenInfo.toString());
        }
        log.info("");
    }
}
