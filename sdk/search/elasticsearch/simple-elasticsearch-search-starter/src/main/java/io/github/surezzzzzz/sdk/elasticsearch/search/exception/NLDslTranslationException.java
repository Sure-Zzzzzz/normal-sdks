package io.github.surezzzzzz.sdk.elasticsearch.search.exception;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;

/**
 * 自然语言DSL翻译异常
 *
 * @author surezzzzzz
 */
public class NLDslTranslationException extends SimpleElasticsearchSearchException {

    public NLDslTranslationException(String message) {
        super(ErrorCode.NL_TRANSLATION_FAILED, message);
    }

    public NLDslTranslationException(String message, Throwable cause) {
        super(ErrorCode.NL_TRANSLATION_FAILED, message, cause);
    }
}
