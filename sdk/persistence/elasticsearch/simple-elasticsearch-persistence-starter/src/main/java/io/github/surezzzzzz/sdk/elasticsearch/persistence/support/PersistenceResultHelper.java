package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.BulkFailureClassifier;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistence Result Helper
 *
 * @author surezzzzzz
 */
public final class PersistenceResultHelper {

    private PersistenceResultHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 单条 index/update 写入结果。
     */
    public static PersistenceResult fromDocWriteResponse(DocWriteResponse response, String datasource,
                                                         PersistenceOperationType operationType,
                                                         PersistenceExecutionContext context) {
        return PersistenceResult.builder()
                .success(true)
                .id(response.getId())
                .index(response.getIndex())
                .datasource(datasource)
                .operationType(operationType)
                .asyncRouted(context.isRouteAsyncWrite())
                .tookMs(context.getTookMs())
                .result(toResult(response.getResult()))
                .build();
    }

    /**
     * 单条 delete 写入结果，按 notFoundAsSuccess 解释 not_found 成败。
     */
    public static PersistenceResult fromDeleteResponse(DocWriteResponse response, String datasource,
                                                       PersistenceExecutionContext context,
                                                       Boolean notFoundAsSuccess) {
        String result = toResult(response.getResult());
        boolean success = !isNotFound(response) || !Boolean.FALSE.equals(notFoundAsSuccess);
        return PersistenceResult.builder()
                .success(success)
                .id(response.getId())
                .index(response.getIndex())
                .datasource(datasource)
                .operationType(PersistenceOperationType.DELETE)
                .asyncRouted(context.isRouteAsyncWrite())
                .tookMs(context.getTookMs())
                .result(result)
                .build();
    }

    /**
     * 单批 bulk 响应转换。
     *
     * @param itemIndexOffset 当前批次首 item 在原始 itemList 中的全局下标
     * @param classifier      bulk 失败可重试分类器
     */
    public static BulkResult fromBulkResponse(BulkResponse response, String datasource,
                                              PersistenceExecutionContext context,
                                              int itemIndexOffset, BulkFailureClassifier classifier) {
        List<BulkItemFailure> failureList = new ArrayList<>();
        int failed = 0;
        for (BulkItemResponse itemResponse : response.getItems()) {
            if (itemResponse.isFailed()) {
                failed++;
                failureList.add(toFailure(itemResponse, itemIndexOffset, datasource, classifier));
            }
        }
        int total = response.getItems().length;
        return BulkResult.builder()
                .success(!response.hasFailures())
                .hasFailure(response.hasFailures())
                .total(total)
                .succeeded(total - failed)
                .failed(failed)
                .datasource(datasource)
                .tookMs(context.getTookMs())
                .failureList(failureList)
                .build();
    }

    private static BulkItemFailure toFailure(BulkItemResponse itemResponse, int itemIndexOffset,
                                             String datasource, BulkFailureClassifier classifier) {
        BulkItemResponse.Failure failure = itemResponse.getFailure();
        Integer status = failure == null ? null : toInt(failure.getStatus());
        String errorType = failure == null ? null : failure.getType();
        String errorReason = extractReason(failure);
        boolean retryable = classifier == null ? false : classifier.retryable(status, errorType, errorReason);
        return BulkItemFailure.builder()
                .itemIndex(itemIndexOffset + itemResponse.getItemId())
                .type(toItemType(itemResponse.getOpType()))
                .id(itemResponse.getId())
                .index(itemResponse.getIndex())
                .datasource(datasource)
                .errorMessage(itemResponse.getFailureMessage())
                .status(status)
                .errorType(errorType)
                .errorReason(errorReason)
                .retryable(retryable)
                .build();
    }

    private static String extractReason(BulkItemResponse.Failure failure) {
        if (failure == null || failure.getCause() == null) {
            return null;
        }
        return failure.getCause().getMessage();
    }

    private static BulkItemType toItemType(DocWriteRequest.OpType opType) {
        if (opType == null) {
            return null;
        }
        switch (opType) {
            case CREATE:
                return BulkItemType.CREATE;
            case UPDATE:
                return BulkItemType.UPDATE;
            case DELETE:
                return BulkItemType.DELETE;
            default:
                return BulkItemType.INDEX;
        }
    }

    private static boolean isNotFound(DocWriteResponse response) {
        return response != null && DocWriteResponse.Result.NOT_FOUND == response.getResult();
    }

    private static String toResult(DocWriteResponse.Result result) {
        if (result == null) {
            return null;
        }
        switch (result) {
            case CREATED:
                return SimpleElasticsearchPersistenceCoreConstant.ES_RESULT_CREATED;
            case UPDATED:
                return SimpleElasticsearchPersistenceCoreConstant.ES_RESULT_UPDATED;
            case DELETED:
                return SimpleElasticsearchPersistenceCoreConstant.ES_RESULT_DELETED;
            case NOT_FOUND:
                return SimpleElasticsearchPersistenceCoreConstant.ES_RESULT_NOT_FOUND;
            case NOOP:
                return SimpleElasticsearchPersistenceCoreConstant.ES_RESULT_NOOP;
            default:
                return result.name().toLowerCase();
        }
    }

    private static Integer toInt(org.elasticsearch.rest.RestStatus status) {
        return status == null ? null : status.getStatus();
    }
}
