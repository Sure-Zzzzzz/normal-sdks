package io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier;

import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchResponseHelper;

/**
 * Default Bulk Failure Classifier
 *
 * @author surezzzzzz
 */
public class DefaultBulkFailureClassifier implements BulkFailureClassifier {

    @Override
    public boolean retryable(Integer status, String errorType, String errorReason) {
        return ElasticsearchResponseHelper.isRetryableStatus(status);
    }
}
