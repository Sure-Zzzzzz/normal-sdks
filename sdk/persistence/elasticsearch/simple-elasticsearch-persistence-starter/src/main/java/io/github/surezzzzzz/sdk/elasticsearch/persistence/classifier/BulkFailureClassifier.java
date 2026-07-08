package io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier;

/**
 * Bulk Failure Classifier
 *
 * @author surezzzzzz
 */
public interface BulkFailureClassifier {

    /**
     * 判断 bulk item 失败是否适合后续重试。
     *
     * @param status ES 状态码
     * @param errorType ES 错误类型
     * @param errorReason ES 错误原因
     * @return true 可重试，false 不建议重试
     */
    boolean retryable(Integer status, String errorType, String errorReason);
}
