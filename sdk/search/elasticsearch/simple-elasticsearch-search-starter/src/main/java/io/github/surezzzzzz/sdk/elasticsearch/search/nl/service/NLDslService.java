package io.github.surezzzzzz.sdk.elasticsearch.search.nl.service;

import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.NLDslTranslationException;
import io.github.surezzzzzz.sdk.elasticsearch.search.nl.translator.SimpleElasticsearchIntentTranslator;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.AnalyticsIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.Intent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.model.QueryIntent;
import io.github.surezzzzzz.sdk.naturallanguage.parser.support.NLParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/**
 * 自然语言DSL服务
 * <p>
 * 职责：
 * 1. 调用NLParser解析自然语言
 * 2. 调用Translator转换Intent为Request
 * 3. 处理索引参数的优先级逻辑
 * 4. 为第二阶段的直接查询功能做准备
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class NLDslService {

    @Autowired
    private NLParser nlParser;

    @Autowired
    private SimpleElasticsearchIntentTranslator translator;

    /**
     * 将自然语言转换为DSL Request对象
     *
     * @param text          自然语言查询文本
     * @param indexOverride 索引覆盖参数（可选，优先级高于NL中的提示）
     * @return QueryRequest或AggRequest
     * @throws io.github.surezzzzzz.sdk.naturallanguage.parser.exception.NLParseException 解析失败
     * @throws NLDslTranslationException                                                  翻译失败
     */
    public Object translateToRequest(String text, String indexOverride) {
        log.debug("Translating natural language to DSL: text='{}', indexOverride='{}'", text, indexOverride);

        // 1. 解析自然语言
        Intent intent = nlParser.parse(text);
        log.debug("Parsed intent: type={}, indexHint={}", intent.getType(), intent.getIndexHint());

        // 2. 确定最终使用的索引
        String finalIndex = determineFinalIndex(intent, indexOverride);

        // 3. 根据Intent类型转换为对应的Request
        Object request;
        if (intent instanceof QueryIntent) {
            request = translator.translate((QueryIntent) intent, finalIndex);
        } else if (intent instanceof AnalyticsIntent) {
            request = translator.translate((AnalyticsIntent) intent, finalIndex);
        } else {
            throw new NLDslTranslationException("不支持的Intent类型: " + intent.getClass().getSimpleName());
        }

        log.debug("Translated to DSL Request successfully");
        return request;
    }

    /**
     * 确定最终使用的索引
     * <p>
     * 优先级：indexOverride参数 > Intent.indexHint > 抛出异常
     */
    private String determineFinalIndex(Intent intent, String indexOverride) {
        // 参数优先级更高
        if (StringUtils.hasText(indexOverride)) {
            return indexOverride;
        }

        // 否则使用Intent中的提示
        if (StringUtils.hasText(intent.getIndexHint())) {
            return intent.getIndexHint();
        }

        throw new NLDslTranslationException("未指定索引，请在自然语言中指定（如：'查询user_profile这个索引'）或通过index参数传入");
    }
}
