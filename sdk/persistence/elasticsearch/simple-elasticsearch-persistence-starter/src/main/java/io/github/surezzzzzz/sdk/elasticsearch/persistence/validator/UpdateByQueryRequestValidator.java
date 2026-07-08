package io.github.surezzzzzz.sdk.elasticsearch.persistence.validator;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import org.springframework.util.StringUtils;

/**
 * Update By Query Request Validator
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class UpdateByQueryRequestValidator implements PersistenceRequestValidator<UpdateByQueryRequest> {

    @Override
    public Class<UpdateByQueryRequest> getRequestType() {
        return UpdateByQueryRequest.class;
    }

    @Override
    public void validate(UpdateByQueryRequest request) {
        if (request == null || !StringUtils.hasText(request.getIndex()) || !StringUtils.hasText(request.getScriptSource())) {
            throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "index/scriptSource 不能为空"));
        }
    }
}
