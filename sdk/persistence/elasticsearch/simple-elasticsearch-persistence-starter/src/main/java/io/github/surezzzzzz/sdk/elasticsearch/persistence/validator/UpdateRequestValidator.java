package io.github.surezzzzzz.sdk.elasticsearch.persistence.validator;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Update Request Validator
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class UpdateRequestValidator implements PersistenceRequestValidator<UpdateRequest> {

    @Override
    public Class<UpdateRequest> getRequestType() {
        return UpdateRequest.class;
    }

    @Override
    public void validate(UpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.getIndex()) || !StringUtils.hasText(request.getId())) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "index/id 不能为空"));
        }
        if (CollectionUtils.isEmpty(request.getFieldMap()) && !StringUtils.hasText(request.getScriptSource())) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "fieldMap 和 scriptSource 不能同时为空"));
        }
        WriteOptionsValidator.validate(request.getOptions());
        if (request.getOptions() != null && request.getOptions().getRetryOnConflict() != null
                && request.getOptions().getRetryOnConflict() < 0) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "retryOnConflict 不能小于 0"));
        }
    }
}
