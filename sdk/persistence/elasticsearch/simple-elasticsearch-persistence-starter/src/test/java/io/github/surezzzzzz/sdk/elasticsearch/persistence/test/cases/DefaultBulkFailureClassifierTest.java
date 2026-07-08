package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.DefaultBulkFailureClassifier;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DefaultBulkFailureClassifier 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultBulkFailureClassifierTest {

    private final DefaultBulkFailureClassifier classifier = new DefaultBulkFailureClassifier();

    @Test
    @DisplayName("可重试状态码：408/429/500/502/503/504 返回 true")
    void retryableStatus() {
        assertTrue(classifier.retryable(408, null, null), "408 应可重试");
        assertTrue(classifier.retryable(429, null, null), "429 应可重试");
        assertTrue(classifier.retryable(500, null, null), "500 应可重试");
        assertTrue(classifier.retryable(502, null, null), "502 应可重试");
        assertTrue(classifier.retryable(503, null, null), "503 应可重试");
        assertTrue(classifier.retryable(504, null, null), "504 应可重试");
    }

    @Test
    @DisplayName("不可重试状态码：400/404/409 返回 false")
    void nonRetryableStatus() {
        assertFalse(classifier.retryable(400, null, null), "400 不应可重试");
        assertFalse(classifier.retryable(404, null, null), "404 不应可重试");
        assertFalse(classifier.retryable(409, null, null), "409 不应可重试");
    }

    @Test
    @DisplayName("status 为 null 时返回 false")
    void nullStatus() {
        assertFalse(classifier.retryable(null, "some_type", "some_reason"), "null status 不应可重试");
    }
}
