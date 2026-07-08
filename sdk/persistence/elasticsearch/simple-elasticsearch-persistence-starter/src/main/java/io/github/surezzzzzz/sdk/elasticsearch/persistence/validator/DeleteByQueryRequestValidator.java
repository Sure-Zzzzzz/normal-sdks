package io.github.surezzzzzz.sdk.elasticsearch.persistence.validator;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteByQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import org.springframework.util.StringUtils;

/**
 * Delete By Query Request Validator
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class DeleteByQueryRequestValidator implements PersistenceRequestValidator<DeleteByQueryRequest> {

    @Override
    public Class<DeleteByQueryRequest> getRequestType() {
        return DeleteByQueryRequest.class;
    }

    @Override
    public void validate(DeleteByQueryRequest request) {
        if (request == null || !StringUtils.hasText(request.getIndex())) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "index 不能为空"));
        }
    }
}
