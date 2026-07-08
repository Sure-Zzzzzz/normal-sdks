package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.ByQueryOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceEsRequestHelper;
import org.elasticsearch.index.reindex.BulkByScrollResponse;

/**
 * Update By Query Executor
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class UpdateByQueryExecutor extends AbstractPersistenceExecutor<UpdateByQueryRequest, ByQueryTaskResult> {

    @Override
    protected PersistenceOperationType getOperationType() {
        return PersistenceOperationType.UPDATE_BY_QUERY;
    }

    @Override
    protected void validate(UpdateByQueryRequest request) {
    }

    @Override
    protected String getIndex(UpdateByQueryRequest request) {
        return request.getIndex();
    }

    @Override
    protected ByQueryTaskResult doExecute(UpdateByQueryRequest request, String datasource, PersistenceExecutionContext context) throws Exception {
        String rawIndex = request.getIndex();
        String renderedIndex = resolveWriteIndex(rawIndex);
        if (!renderedIndex.equals(rawIndex)) {
            request.setIndex(renderedIndex);
        }
        ByQueryOptions options = request.getOptions();
        if (options != null && Boolean.FALSE.equals(options.getWaitForCompletion())) {
            String taskId = writeApiHelper.submitUpdateByQueryTask(datasource, request);
            context.setServerAsyncTask(true);
            context.setTaskId(taskId);
            return ByQueryTaskResult.builder().completed(false).taskId(taskId).datasource(datasource).index(request.getIndex()).build();
        }
        BulkByScrollResponse response = writeApiHelper.updateByQuery(datasource,
                PersistenceEsRequestHelper.buildUpdateByQueryRequest(request));
        return ByQueryTaskResult.builder()
                .completed(true)
                .datasource(datasource)
                .index(request.getIndex())
                .total(response.getTotal())
                .updated(response.getUpdated())
                .versionConflicts(response.getVersionConflicts())
                .tookMs(response.getTook().millis())
                .build();
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
