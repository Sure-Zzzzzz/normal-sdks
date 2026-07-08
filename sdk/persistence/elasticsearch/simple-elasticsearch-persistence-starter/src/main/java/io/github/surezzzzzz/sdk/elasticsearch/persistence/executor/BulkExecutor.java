package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.BulkFailureClassifier;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.BulkPersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentProcessContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.DocumentMetadataHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceEsRequestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceResultHelper;
import org.elasticsearch.action.bulk.BulkResponse;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    protected BulkFailureClassifier bulkFailureClassifier;

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
        List<BulkItem> itemList = request.getItemList();
        List<String> renderedIndices = renderAndPreProcess(request, datasource);
        BulkOptions options = request.getOptions();
        int batchSize = options == null || options.getBatchSize() == null || options.getBatchSize() <= 0
                ? itemList.size() : options.getBatchSize();
        boolean continueOnFailure = options == null || !Boolean.FALSE.equals(options.getContinueOnFailure());

        int batchTotal = 0;
        int batchSucceeded = 0;
        int batchFailed = 0;
        int totalSucceeded = 0;
        int totalFailed = 0;
        List<BulkItemFailure> failureList = new ArrayList<>();
        boolean stoppedOnFailure = false;

        int offset = 0;
        while (offset < itemList.size()) {
            int end = Math.min(offset + batchSize, itemList.size());
            List<String> batchRenderedIndices = renderedIndices.subList(offset, end);
            BulkRequest batchRequest = buildBatchRequest(request, itemList, offset, end, batchRenderedIndices);
            BulkResponse response;
            try {
                response = writeApiHelper.bulk(datasource,
                        PersistenceEsRequestHelper.buildBulkRequest(batchRequest, batchRenderedIndices));
            } catch (Exception e) {
                if (batchTotal > 0) {
                    BulkResult partial = aggregate(itemList.size(), totalSucceeded, totalFailed, failureList,
                            batchTotal, batchSucceeded, batchFailed, stoppedOnFailure, datasource, context, true);
                    throw new BulkPersistenceExecutionException(partial, e);
                }
                throw e;
            }
            BulkResult batchResult = PersistenceResultHelper.fromBulkResponse(response, datasource, context,
                    offset, bulkFailureClassifier);
            batchTotal++;
            totalSucceeded += batchResult.getSucceeded();
            totalFailed += batchResult.getFailed();
            if (batchResult.getFailed() > 0) {
                batchFailed++;
            } else {
                batchSucceeded++;
            }
            if (!CollectionUtils.isEmpty(batchResult.getFailureList())) {
                failureList.addAll(batchResult.getFailureList());
            }
            offset = end;
            if (!continueOnFailure && batchResult.getFailed() > 0) {
                stoppedOnFailure = true;
                break;
            }
        }
        return aggregate(itemList.size(), totalSucceeded, totalFailed, failureList,
                batchTotal, batchSucceeded, batchFailed, stoppedOnFailure, datasource, context, false);
    }

    @Override
    protected SimpleElasticsearchPersistenceException wrap(Exception e) {
        if (e instanceof SimpleElasticsearchPersistenceException) {
            return (SimpleElasticsearchPersistenceException) e;
        }
        return new PersistenceExecutionException(ErrorCode.EXECUTION_FAILED,
                String.format(ErrorMessage.EXECUTION_FAILED, e.getMessage()), e);
    }

    private List<String> renderAndPreProcess(BulkRequest request, String datasource) {
        List<String> renderedIndices = new ArrayList<>(request.getItemList().size());
        for (int i = 0; i < request.getItemList().size(); i++) {
            BulkItem item = request.getItemList().get(i);
            String rawIndex = resolveItemIndex(request, item);
            String renderedIndex = resolveWriteIndex(rawIndex);
            renderedIndices.add(renderedIndex);
            if (isDocumentWrite(item)) {
                Object processedDocument = documentPreProcessorChain.process(item.getDocument(), DocumentProcessContext.builder()
                        .operationType(toOperationType(item.getType()))
                        .rawIndex(rawIndex)
                        .renderedIndex(renderedIndex)
                        .datasource(datasource)
                        .bulk(true)
                        .bulkItemIndex(i)
                        .build());
                item.setDocument(processedDocument);
            }
        }
        return renderedIndices;
    }

    private BulkRequest buildBatchRequest(BulkRequest original, List<BulkItem> itemList,
                                          int from, int to, List<String> batchRenderedIndices) {
        List<BulkItem> batchItems = new ArrayList<>(itemList.subList(from, to));
        return BulkRequest.builder()
                .itemList(batchItems)
                .defaultIndex(original.getDefaultIndex())
                .options(original.getOptions())
                .build();
    }

    private BulkResult aggregate(int total, int succeeded, int failed, List<BulkItemFailure> failureList,
                                 int batchTotal, int batchSucceeded, int batchFailed,
                                 boolean stoppedOnFailure, String datasource,
                                 PersistenceExecutionContext context, boolean partial) {
        boolean success = failed == 0 && !stoppedOnFailure && !partial;
        boolean hasFailure = failed > 0 || stoppedOnFailure || partial;
        return BulkResult.builder()
                .success(success)
                .hasFailure(hasFailure)
                .total(total)
                .succeeded(succeeded)
                .failed(failed)
                .datasource(datasource)
                .tookMs(context.getTookMs())
                .failureList(failureList)
                .batchTotal(batchTotal)
                .batchSucceeded(batchSucceeded)
                .batchFailed(batchFailed)
                .stoppedOnFailure(stoppedOnFailure)
                .partial(partial)
                .build();
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

    private boolean isDocumentWrite(BulkItem item) {
        return item != null && item.getDocument() != null
                && (BulkItemType.INDEX == item.getType() || BulkItemType.CREATE == item.getType());
    }

    private PersistenceOperationType toOperationType(BulkItemType type) {
        return BulkItemType.CREATE == type ? PersistenceOperationType.CREATE : PersistenceOperationType.INDEX;
    }
}
