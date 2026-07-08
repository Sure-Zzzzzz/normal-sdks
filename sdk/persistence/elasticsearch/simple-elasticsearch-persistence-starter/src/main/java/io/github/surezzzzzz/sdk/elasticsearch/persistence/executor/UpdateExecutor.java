package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceEsRequestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceResultHelper;
import org.elasticsearch.action.DocWriteResponse;

/**
 * Update Executor
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class UpdateExecutor extends AbstractPersistenceExecutor<UpdateRequest, PersistenceResult> {

    @Override
    protected PersistenceOperationType getOperationType() {
        return PersistenceOperationType.UPDATE;
    }

    @Override
    protected void validate(UpdateRequest request) {
    }

    @Override
    protected String getIndex(UpdateRequest request) {
        return request.getIndex();
    }

    @Override
    protected PersistenceResult doExecute(UpdateRequest request, String datasource, PersistenceExecutionContext context) throws Exception {
        String renderedIndex = resolveWriteIndex(context.getIndex());
        context.setIndex(renderedIndex);
        DocWriteResponse response = writeApiHelper.update(datasource,
                PersistenceEsRequestHelper.buildUpdateRequest(request, renderedIndex));
        return PersistenceResultHelper.fromDocWriteResponse(response, datasource, PersistenceOperationType.UPDATE, context);
    }

    @Override
    protected SimpleElasticsearchPersistenceException wrap(Exception e) {
        if (e instanceof SimpleElasticsearchPersistenceException) {
            return (SimpleElasticsearchPersistenceException) e;
        }
        return new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED,
                String.format(ErrorMessage.EXECUTION_FAILED, e.getMessage()), e);
    }
}
