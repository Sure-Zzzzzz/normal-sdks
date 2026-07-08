package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentProcessContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.DocumentMetadataHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceEsRequestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceResultHelper;
import org.elasticsearch.action.DocWriteResponse;

/**
 * Index Executor
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class IndexExecutor extends AbstractPersistenceExecutor<IndexRequest, PersistenceResult> {

    @Override
    protected PersistenceOperationType getOperationType() {
        return PersistenceOperationType.INDEX;
    }

    @Override
    protected void validate(IndexRequest request) {
    }

    @Override
    protected String getIndex(IndexRequest request) {
        return DocumentMetadataHelper.resolveIndex(request.getDocument(), request.getIndex());
    }

    @Override
    protected PersistenceResult doExecute(IndexRequest request, String datasource, PersistenceExecutionContext context) throws Exception {
        String rawIndex = context.getIndex();
        String renderedIndex = resolveWriteIndex(rawIndex);
        context.setIndex(renderedIndex);
        IndexOperationType operationType = request.getOptions() == null ? IndexOperationType.INDEX : request.getOptions().getOperationType();
        if (operationType == null) {
            operationType = IndexOperationType.INDEX;
        }
        PersistenceOperationType persistenceOperationType = IndexOperationType.CREATE == operationType
                ? PersistenceOperationType.CREATE : PersistenceOperationType.INDEX;
        context.setOperationType(persistenceOperationType);
        Object processedDocument = documentPreProcessorChain.process(request.getDocument(), DocumentProcessContext.builder()
                .operationType(persistenceOperationType)
                .rawIndex(rawIndex)
                .renderedIndex(renderedIndex)
                .datasource(datasource)
                .bulk(false)
                .build());
        request.setDocument(processedDocument);
        DocWriteResponse response = writeApiHelper.index(datasource,
                PersistenceEsRequestHelper.buildIndexRequest(request, renderedIndex));
        return PersistenceResultHelper.fromDocWriteResponse(response, datasource, persistenceOperationType, context);
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
