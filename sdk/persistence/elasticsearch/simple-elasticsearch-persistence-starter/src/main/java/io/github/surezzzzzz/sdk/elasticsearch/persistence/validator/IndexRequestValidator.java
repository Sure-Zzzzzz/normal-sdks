package io.github.surezzzzzz.sdk.elasticsearch.persistence.validator;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;

/**
 * Index Request Validator
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class IndexRequestValidator implements PersistenceRequestValidator<IndexRequest> {

    @Override
    public Class<IndexRequest> getRequestType() {
        return IndexRequest.class;
    }

    @Override
    public void validate(IndexRequest request) {
        if (request == null || request.getDocument() == null) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "document 不能为空"));
        }
        WriteOptionsValidator.validate(request.getOptions());
    }
}
