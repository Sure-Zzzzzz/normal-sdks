package io.github.surezzzzzz.sdk.naturallanguage.parser.parser;

import io.github.surezzzzzz.sdk.naturallanguage.parser.configuration.NLParserProperties;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.keyword.KeywordRegistry;
import io.github.surezzzzzz.sdk.naturallanguage.parser.tokenizer.Token;

import java.util.List;

/**
 * 解析器插件接口
 * 所有内置和自定义解析器统一实现此接口
 * 通过 @Order 控制执行顺序，数字越小越先执行
 *
 * @author surezzzzzz
 */
public interface NLParserPlugin {

    /**
     * 解析 token 流，将结果写入 ParseResult
     *
     * @param tokens     当前 token 列表（可修改，如移除已消费的 token）
     * @param registry   关键字注册表
     * @param properties 解析器配置
     * @param result     解析结果收集器
     */
    void parse(List<Token> tokens, KeywordRegistry registry, NLParserProperties properties, ParseResult result);

    /**
     * 本 Parser 是否适用于当前意图类型
     *
     * @param intentType 当前意图类型
     * @return true 表示本 Parser 应参与本次解析
     */
    boolean supports(IntentType intentType);
}
