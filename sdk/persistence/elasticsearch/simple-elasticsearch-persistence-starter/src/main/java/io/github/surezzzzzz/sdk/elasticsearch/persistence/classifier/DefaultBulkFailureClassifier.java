package io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant;

/**
 * Default Bulk Failure Classifier
 *
 * @author surezzzzzz
 */
public class DefaultBulkFailureClassifier implements BulkFailureClassifier {

    @Override
    public boolean retryable(Integer status, String errorType, String errorReason) {
        if (status == null) {
            return false;
        }
        return SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_REQUEST_TIMEOUT == status
                || SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_TOO_MANY_REQUESTS == status
                || SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_INTERNAL_SERVER_ERROR == status
                || SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_BAD_GATEWAY == status
                || SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_SERVICE_UNAVAILABLE == status
                || SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_GATEWAY_TIMEOUT == status;
    }
}
