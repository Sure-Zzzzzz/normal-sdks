package io.github.surezzzzzz.sdk.elasticsearch.persistence.validator;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import org.springframework.util.CollectionUtils;

/**
 * Bulk Request Validator
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class BulkRequestValidator implements PersistenceRequestValidator<BulkRequest> {

    @Override
    public Class<BulkRequest> getRequestType() {
        return BulkRequest.class;
    }

    @Override
    public void validate(BulkRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.getItemList())) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "itemList 不能为空"));
        }
    }
}
