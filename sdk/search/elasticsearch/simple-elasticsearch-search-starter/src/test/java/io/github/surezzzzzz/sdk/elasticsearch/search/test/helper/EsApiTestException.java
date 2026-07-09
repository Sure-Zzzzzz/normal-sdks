package io.github.surezzzzzz.sdk.elasticsearch.search.test.helper;

/**
 * ES 测试 API 异常。
 *
 * @author surezzzzzz
 */
public class EsApiTestException extends RuntimeException {

    public EsApiTestException(String message) {
        super(message);
    }

    public EsApiTestException(String message, Throwable cause) {
        super(message, cause);
    }
}
