package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.DocumentMetadataHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceEsRequestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceResultHelper;
import org.elasticsearch.action.DocWriteResponse;
import org.springframework.util.StringUtils;

/**
 * Delete Executor
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class DeleteExecutor extends AbstractPersistenceExecutor<DeleteRequest, PersistenceResult> {

    @Override
    protected PersistenceOperationType getOperationType() {
        return PersistenceOperationType.DELETE;
    }

    @Override
    protected void validate(DeleteRequest request) {
    }

    @Override
    protected String getIndex(DeleteRequest request) {
        return StringUtils.hasText(request.getIndex()) ? request.getIndex()
                : DocumentMetadataHelper.resolveIndex(request.getDocumentClass());
    }

    @Override
    protected PersistenceResult doExecute(DeleteRequest request, String datasource, PersistenceExecutionContext context) throws Exception {
        String renderedIndex = resolveWriteIndex(context.getIndex());
        context.setIndex(renderedIndex);
        DocWriteResponse response = writeApiHelper.delete(datasource,
                PersistenceEsRequestHelper.buildDeleteRequest(request, renderedIndex));
        return PersistenceResultHelper.fromDocWriteResponse(response, datasource, PersistenceOperationType.DELETE, context);
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
