package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.TaskQueryRequest;

/**
 * Task Query Executor
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class TaskQueryExecutor extends AbstractPersistenceExecutor<TaskQueryRequest, ByQueryTaskResult> {

    @Override
    protected PersistenceOperationType getOperationType() {
        return PersistenceOperationType.GET_TASK;
    }

    @Override
    protected void validate(TaskQueryRequest request) {
    }

    @Override
    protected String getIndex(TaskQueryRequest request) {
        return null;
    }

    @Override
    protected String resolveDatasource(TaskQueryRequest request, String index) {
        return request.getDatasource();
    }

    @Override
    protected ByQueryTaskResult doExecute(TaskQueryRequest request, String datasource, PersistenceExecutionContext context) throws Exception {
        context.setTaskId(request.getTaskId());
        return writeApiHelper.getTask(datasource, request.getTaskId());
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
