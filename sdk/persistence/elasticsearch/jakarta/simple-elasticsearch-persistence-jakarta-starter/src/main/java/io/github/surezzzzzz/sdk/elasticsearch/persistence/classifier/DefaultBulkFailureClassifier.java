package io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier;

import static io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.PersistenceProtocolConstant.HTTP_INTERNAL_SERVER_ERROR;
import static io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.PersistenceProtocolConstant.HTTP_TOO_MANY_REQUESTS;


/**
 * 默认批量失败重试分类器
 *
 * @author surezzzzzz
 */
public class DefaultBulkFailureClassifier implements BulkFailureClassifier {

    /**
     * 按脱敏失败信息判断是否适合业务侧重试。
     */
    @Override
    public boolean retryable(Integer status, String errorType, String errorReason) {
        return status != null && (status == HTTP_TOO_MANY_REQUESTS || status >= HTTP_INTERNAL_SERVER_ERROR);
    }
}

