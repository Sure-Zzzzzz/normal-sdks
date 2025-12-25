package io.github.surezzzzzz.sdk.elasticsearch.route.test.exception;

/**
 * 模拟 Elasticsearch 版本兼容性异常
 *
 * @author Sure
 * @since 1.0.3
 */
public class MockElasticsearchException extends RuntimeException {

    public MockElasticsearchException(String message) {
        super(message);
    }

    public MockElasticsearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
