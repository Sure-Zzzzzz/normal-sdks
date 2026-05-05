package io.github.surezzzzzz.sdk.naturallanguage.parser.support;

import io.github.surezzzzzz.sdk.naturallanguage.parser.annotation.NaturalLanguageParserComponent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.configuration.NLParserProperties;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.*;
import io.github.surezzzzzz.sdk.naturallanguage.parser.parser.*;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.NLTokenizer;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 自然语言解析器（门面）
 * <p>
 * 协调分词器、关键字注册表、解析器插件，将自然语言解析为 Intent 对象
 *
 * @author surezzzzzz
 */
@Slf4j
@NaturalLanguageParserComponent
public class NLParser {

    private final NLTokenizer tokenizer;
    private final KeywordRegistry keywordRegistry;
    private final NLParserProperties properties;
    private final List<NLParserPlugin> plugins;

    /**
     * 构造函数
     *
     * @param tokenizer       分词器
     * @param keywordRegistry 关键字注册表
     * @param properties      配置
     * @param plugins         解析器插件列表
     */
    public NLParser(NLTokenizer tokenizer, KeywordRegistry keywordRegistry,
                    NLParserProperties properties, List<NLParserPlugin> plugins) {
        this.tokenizer = tokenizer;
        this.keywordRegistry = keywordRegistry;
        this.properties = properties;
        this.plugins = plugins != null ? plugins : java.util.Collections.<NLParserPlugin>emptyList();
    }

    /**
     * 解析自然语言为意图
     *
     * @param naturalLanguage 自然语言查询
     * @return 解析后的意图
     */
    public Intent parse(String naturalLanguage) {
        if (naturalLanguage == null || naturalLanguage.trim().isEmpty()) {
            throw NLParseException.emptyQuery(naturalLanguage);
        }

        // 1. 分词
        List<Token> tokens = tokenizer.tokenize(naturalLanguage, keywordRegistry);

        if (tokens == null || tokens.isEmpty()) {
            throw NLParseException.emptyQuery(naturalLanguage);
        }

        // 2. 判断意图类型
        boolean hasAgg = false;
        for (Token token : tokens) {
            if (token.getType() == io.github.surezzzzzz.sdk.naturallanguage.parser.constant.TokenType.AGGREGATION) {
                hasAgg = true;
                break;
            }
        }
        IntentType intentType = hasAgg ? IntentType.ANALYTICS : IntentType.QUERY;

        // 3. 使用 Plugin 链解析
        Intent intent = parseWithPlugins(tokens, intentType);

        return intent;
    }

    /**
     * 使用 Plugin 链解析
     */
    private Intent parseWithPlugins(List<Token> tokens, IntentType intentType) {
        ParseResult result = new ParseResult();
        for (NLParserPlugin plugin : plugins) {
            if (plugin.supports(intentType)) {
                plugin.parse(tokens, keywordRegistry, properties, result);
            }
        }
        return buildIntent(intentType, result);
    }

    /**
     * 从 ParseResult 组装 Intent
     */
    private Intent buildIntent(IntentType intentType, ParseResult result) {
        Intent intent;
        if (intentType == IntentType.ANALYTICS) {
            intent = AnalyticsIntent.builder()
                    .condition(result.getCondition())
                    .aggregations(result.getAggregations())
                    .build();
        } else {
            intent = QueryIntent.builder()
                    .condition(result.getCondition())
                    .sorts(result.getSorts())
                    .pagination(result.getPagination())
                    .dateRange(result.getDateRange())
                    .collapse(result.getCollapse())
                    .fieldProjections(result.getFieldProjections())
                    .build();
        }

        // 设置索引提示
        if (result.getIndexHint() != null) {
            intent.setIndexHint(result.getIndexHint());
        }
        intent.setType(intentType);

        // 透传扩展数据
        if (result.getExt() != null && !result.getExt().isEmpty()) {
            intent.setExt(result.getExt());
        }

        return intent;
    }
}
