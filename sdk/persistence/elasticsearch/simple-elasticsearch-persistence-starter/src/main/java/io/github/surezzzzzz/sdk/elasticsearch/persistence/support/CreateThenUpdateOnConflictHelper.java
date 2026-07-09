package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.UpdateOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.rest.RestStatus;

import java.util.*;

/**
 * create 冲突转 update helper。
 *
 * @author surezzzzzz
 */
public final class CreateThenUpdateOnConflictHelper {

    private CreateThenUpdateOnConflictHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static PersistenceResult createThenUpdateOnConflict(PersistenceEngine engine,
                                                               IndexRequest createRequest,
                                                               UpdateRequest updateRequest) {
        try {
            return engine.create(forceCreate(createRequest));
        } catch (RuntimeException e) {
            if (!isConflict(e)) {
                throw e;
            }
            fillUpdateTarget(updateRequest, createRequest);
            return engine.update(updateRequest);
        }
    }

    public static BulkResult bulkCreateThenUpdateOnConflict(PersistenceEngine engine,
                                                            BulkRequest createRequest,
                                                            ConflictUpdateResolver resolver) {
        BulkRequest firstRequest = forceCreate(createRequest);
        BulkResult firstResult = engine.bulk(firstRequest);
        List<BulkItemFailure> conflictFailureList = findConflictFailures(firstResult);
        if (conflictFailureList.isEmpty()) {
            return firstResult;
        }

        List<BulkItem> updateItemList = new ArrayList<>(conflictFailureList.size());
        List<BulkItemFailure> attemptedConflictFailureList = new ArrayList<>(conflictFailureList.size());
        for (BulkItemFailure failure : conflictFailureList) {
            BulkItem createItem = firstRequest.getItemList().get(failure.getItemIndex());
            BulkItem updateItem = resolver.resolve(createItem, failure);
            if (updateItem != null) {
                updateItem.setType(BulkItemType.UPDATE);
                fillUpdateItemTarget(updateItem, createItem, firstRequest);
                updateItemList.add(updateItem);
                attemptedConflictFailureList.add(failure);
            }
        }
        if (updateItemList.isEmpty()) {
            return firstResult;
        }

        BulkRequest updateRequest = BulkRequest.builder()
                .itemList(updateItemList)
                .defaultIndex(firstRequest.getDefaultIndex())
                .options(copyUpdateBulkOptions(firstRequest.getOptions()))
                .build();
        BulkResult updateResult = engine.bulk(updateRequest);
        return merge(firstResult, updateResult, attemptedConflictFailureList);
    }

    private static IndexRequest forceCreate(IndexRequest request) {
        IndexOptions options = request.getOptions();
        IndexOptions.IndexOptionsBuilder builder = IndexOptions.builder();
        if (options != null) {
            builder.pipeline(options.getPipeline())
                    .refresh(options.getRefresh())
                    .routing(options.getRouting())
                    .timeoutMs(options.getTimeoutMs())
                    .refreshPolicy(options.getRefreshPolicy());
        }
        request.setOptions(builder.operationType(IndexOperationType.CREATE).build());
        return request;
    }

    private static BulkRequest forceCreate(BulkRequest request) {
        if (request.getItemList() != null) {
            for (BulkItem item : request.getItemList()) {
                item.setType(BulkItemType.CREATE);
            }
        }
        request.setOptions(copyBulkOptionsWithContinue(request.getOptions()));
        return request;
    }

    private static void fillUpdateTarget(UpdateRequest updateRequest, IndexRequest createRequest) {
        if (updateRequest.getIndex() == null || updateRequest.getIndex().trim().isEmpty()) {
            updateRequest.setIndex(DocumentMetadataHelper.resolveIndex(createRequest.getDocument(), createRequest.getIndex()));
        }
        if (updateRequest.getId() == null || updateRequest.getId().trim().isEmpty()) {
            updateRequest.setId(DocumentMetadataHelper.resolveId(createRequest.getDocument(), createRequest.getId()));
        }
        if (updateRequest.getOptions() == null && createRequest.getOptions() != null
                && createRequest.getOptions().getRouting() != null) {
            updateRequest.setOptions(UpdateOptions.builder()
                    .routing(createRequest.getOptions().getRouting())
                    .build());
        }
    }

    private static void fillUpdateItemTarget(BulkItem updateItem, BulkItem createItem, BulkRequest createRequest) {
        if (isBlank(updateItem.getIndex())) {
            updateItem.setIndex(isBlank(createItem.getIndex()) ? createRequest.getDefaultIndex() : createItem.getIndex());
        }
        if (isBlank(updateItem.getId())) {
            updateItem.setId(DocumentMetadataHelper.resolveId(createItem.getDocument(), createItem.getId()));
        }
        if (isBlank(updateItem.getRouting())) {
            updateItem.setRouting(createItem.getRouting());
        }
    }

    private static BulkOptions copyUpdateBulkOptions(BulkOptions options) {
        if (options == null) {
            return BulkOptions.builder().continueOnFailure(true).build();
        }
        return BulkOptions.builder()
                .batchSize(options.getBatchSize())
                .continueOnFailure(true)
                .refresh(options.getRefresh())
                .routing(options.getRouting())
                .timeoutMs(options.getTimeoutMs())
                .refreshPolicy(options.getRefreshPolicy())
                .build();
    }

    private static BulkOptions copyBulkOptionsWithContinue(BulkOptions options) {
        if (options == null) {
            return BulkOptions.builder().continueOnFailure(true).build();
        }
        return BulkOptions.builder()
                .batchSize(options.getBatchSize())
                .continueOnFailure(true)
                .pipeline(options.getPipeline())
                .refresh(options.getRefresh())
                .routing(options.getRouting())
                .timeoutMs(options.getTimeoutMs())
                .refreshPolicy(options.getRefreshPolicy())
                .build();
    }

    private static List<BulkItemFailure> findConflictFailures(BulkResult result) {
        if (result.getFailureList() == null || result.getFailureList().isEmpty()) {
            return Collections.emptyList();
        }
        List<BulkItemFailure> conflictList = new ArrayList<>();
        for (BulkItemFailure failure : result.getFailureList()) {
            if (isConflict(failure)) {
                conflictList.add(failure);
            }
        }
        return conflictList;
    }

    private static boolean isConflict(BulkItemFailure failure) {
        return failure != null
                && SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_CONFLICT == toInt(failure.getStatus())
                && BulkItemType.CREATE == failure.getType();
    }

    private static boolean isConflict(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof ElasticsearchStatusException) {
                RestStatus status = ((ElasticsearchStatusException) current).status();
                if (status != null && SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_CONFLICT == status.getStatus()) {
                    return true;
                }
            }
            if (current instanceof ResponseException) {
                if (SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_CONFLICT
                        == ((ResponseException) current).getResponse().getStatusLine().getStatusCode()) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static BulkResult merge(BulkResult firstResult, BulkResult updateResult,
                                    List<BulkItemFailure> attemptedConflictFailureList) {
        Map<Integer, BulkItemFailure> updateFailureMap = new HashMap<>();
        if (updateResult.getFailureList() != null) {
            for (BulkItemFailure failure : updateResult.getFailureList()) {
                updateFailureMap.put(failure.getItemIndex(), failure);
            }
        }
        Map<Integer, Integer> attemptedIndexMap = new HashMap<>();
        for (int i = 0; i < attemptedConflictFailureList.size(); i++) {
            attemptedIndexMap.put(attemptedConflictFailureList.get(i).getItemIndex(), i);
        }

        List<BulkItemFailure> mergedFailureList = new ArrayList<>();
        for (BulkItemFailure failure : safeList(firstResult.getFailureList())) {
            if (!isConflict(failure)) {
                mergedFailureList.add(failure);
                continue;
            }
            Integer updateIndex = attemptedIndexMap.get(failure.getItemIndex());
            if (updateIndex == null) {
                mergedFailureList.add(failure);
                continue;
            }
            BulkItemFailure updateFailure = updateFailureMap.get(updateIndex);
            if (updateFailure != null) {
                mergedFailureList.add(toOriginalFailureIndex(updateFailure, failure));
            }
        }

        int total = firstResult.getTotal();
        int failed = mergedFailureList.size();
        return BulkResult.builder()
                .success(failed == 0)
                .hasFailure(failed > 0)
                .total(total)
                .succeeded(total - failed)
                .failed(failed)
                .datasource(firstResult.getDatasource())
                .tookMs(firstResult.getTookMs() + updateResult.getTookMs())
                .failureList(mergedFailureList)
                .batchTotal(sum(firstResult.getBatchTotal(), updateResult.getBatchTotal()))
                .batchSucceeded(sum(firstResult.getBatchSucceeded(), updateResult.getBatchSucceeded()))
                .batchFailed(sum(firstResult.getBatchFailed(), updateResult.getBatchFailed()))
                .stoppedOnFailure(Boolean.TRUE.equals(firstResult.getStoppedOnFailure())
                        || Boolean.TRUE.equals(updateResult.getStoppedOnFailure()))
                .partial(Boolean.TRUE.equals(firstResult.getPartial()) || Boolean.TRUE.equals(updateResult.getPartial()))
                .build();
    }

    private static BulkItemFailure toOriginalFailureIndex(BulkItemFailure updateFailure, BulkItemFailure originalFailure) {
        return BulkItemFailure.builder()
                .itemIndex(originalFailure.getItemIndex())
                .type(updateFailure.getType())
                .id(updateFailure.getId())
                .index(updateFailure.getIndex())
                .datasource(updateFailure.getDatasource())
                .errorCode(updateFailure.getErrorCode())
                .errorMessage(updateFailure.getErrorMessage())
                .status(updateFailure.getStatus())
                .errorType(updateFailure.getErrorType())
                .errorReason(updateFailure.getErrorReason())
                .retryable(updateFailure.getRetryable())
                .build();
    }

    private static List<BulkItemFailure> safeList(List<BulkItemFailure> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private static Integer sum(Integer left, Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return toInt(left) + toInt(right);
    }

    private static int toInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
