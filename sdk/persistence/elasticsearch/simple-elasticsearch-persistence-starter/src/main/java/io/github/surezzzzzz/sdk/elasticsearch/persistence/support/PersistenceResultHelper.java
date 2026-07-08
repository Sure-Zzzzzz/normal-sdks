package io.github.surezzzzzz.sdk.elasticsearch.persistence.support;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
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
                .build();
    }

    public static BulkResult fromBulkResponse(BulkResponse response, String datasource,
                                              PersistenceExecutionContext context) {
        List<BulkItemFailure> failureList = new ArrayList<>();
        int failed = 0;
        for (BulkItemResponse itemResponse : response.getItems()) {
            if (itemResponse.isFailed()) {
                failed++;
                failureList.add(BulkItemFailure.builder()
                        .itemIndex(itemResponse.getItemId())
                        .id(itemResponse.getId())
                        .index(itemResponse.getIndex())
                        .datasource(datasource)
                        .errorMessage(itemResponse.getFailureMessage())
                        .build());
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
}
