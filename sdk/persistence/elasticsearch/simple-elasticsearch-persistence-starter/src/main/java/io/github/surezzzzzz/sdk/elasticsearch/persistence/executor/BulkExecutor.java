package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.DocumentMetadataHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceEsRequestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceResultHelper;
import org.elasticsearch.action.bulk.BulkResponse;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Bulk Executor
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class BulkExecutor extends AbstractPersistenceExecutor<BulkRequest, BulkResult> {

    @Override
    protected PersistenceOperationType getOperationType() {
        return PersistenceOperationType.BULK;
    }

    @Override
    protected void validate(BulkRequest request) {
    }

    @Override
    protected String getIndex(BulkRequest request) {
        BulkItem first = request.getItemList().get(0);
        if (StringUtils.hasText(first.getIndex())) {
            return first.getIndex();
        }
        if (StringUtils.hasText(request.getDefaultIndex())) {
            return request.getDefaultIndex();
        }
        if (first.getDocument() != null) {
            return DocumentMetadataHelper.resolveIndex(first.getDocument(), null);
        }
        throw new PersistenceExecutionException(ErrorCode.REQUEST_VALIDATION_FAILED,
                String.format(ErrorMessage.REQUEST_VALIDATION_FAILED, "bulk item index/defaultIndex 不能为空"));
    }

    @Override
    protected String resolveDatasource(BulkRequest request, String index) {
        String[] indices = new String[request.getItemList().size()];
        for (int i = 0; i < request.getItemList().size(); i++) {
            BulkItem item = request.getItemList().get(i);
            indices[i] = resolveItemIndex(request, item);
        }
        return registry.resolveDataSourceOrThrow(indices);
    }

    @Override
    protected BulkResult doExecute(BulkRequest request, String datasource, PersistenceExecutionContext context) throws Exception {
        List<String> renderedIndices = new ArrayList<>(request.getItemList().size());
        if (!CollectionUtils.isEmpty(request.getItemList())) {
            for (BulkItem item : request.getItemList()) {
                String rawIndex = StringUtils.hasText(item.getIndex()) ? item.getIndex() : request.getDefaultIndex();
                renderedIndices.add(resolveWriteIndex(rawIndex));
            }
        }
        BulkResponse response = writeApiHelper.bulk(datasource,
                PersistenceEsRequestHelper.buildBulkRequest(request, renderedIndices));
        return PersistenceResultHelper.fromBulkResponse(response, datasource, context);
    }

    @Override
    protected SimpleElasticsearchPersistenceException wrap(Exception e) {
        if (e instanceof SimpleElasticsearchPersistenceException) {
            return (SimpleElasticsearchPersistenceException) e;
        }
        return new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED,
                String.format(ErrorMessage.EXECUTION_FAILED, e.getMessage()), e);
    }

    private String resolveItemIndex(BulkRequest request, BulkItem item) {
        if (StringUtils.hasText(item.getIndex())) {
            return item.getIndex();
        }
        if (StringUtils.hasText(request.getDefaultIndex())) {
            return request.getDefaultIndex();
        }
        if (item.getDocument() != null) {
            return DocumentMetadataHelper.resolveIndex(item.getDocument(), null);
        }
        return null;
    }
}
