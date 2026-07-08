package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.BulkFailureClassifier;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceResultHelper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.rest.RestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PersistenceResultHelper 单元测试
 *
 * <p>验证 result 字段填充、delete notFoundAsSuccess 解释、bulk failure 明细与全局 itemIndex。
 *
 * @author surezzzzzz
 */
@Slf4j
class PersistenceResultHelperTest {

    private static final String DATASOURCE = "primary";

    private PersistenceExecutionContext context() {
        return PersistenceExecutionContext.builder()
                .operationType(PersistenceOperationType.INDEX)
                .startTimeMs(0L)
                .build();
    }

    @Test
    @DisplayName("fromDocWriteResponse：CREATED 映射为 created 且 success=true")
    void fromDocWriteResponseCreated() {
        DocWriteResponse response = mock(DocWriteResponse.class);
        when(response.getResult()).thenReturn(DocWriteResponse.Result.CREATED);
        when(response.getId()).thenReturn("id-1");
        when(response.getIndex()).thenReturn("idx-1");

        PersistenceResult result = PersistenceResultHelper.fromDocWriteResponse(
                response, DATASOURCE, PersistenceOperationType.INDEX, context());

        assertTrue(result.isSuccess(), "CREATED 应成功");
        assertEquals(SimpleElasticsearchPersistenceCoreConstant.ES_RESULT_CREATED, result.getResult(),
                "result 应为 created");
        assertEquals("id-1", result.getId(), "id 应透传");
    }

    @Test
    @DisplayName("fromDeleteResponse：NOT_FOUND + notFoundAsSuccess=null 视为成功")
    void fromDeleteResponseNotFoundAsSuccessNull() {
        DocWriteResponse response = mock(DocWriteResponse.class);
        when(response.getResult()).thenReturn(DocWriteResponse.Result.NOT_FOUND);
        when(response.getId()).thenReturn("id-2");
        when(response.getIndex()).thenReturn("idx-2");

        PersistenceResult result = PersistenceResultHelper.fromDeleteResponse(
                response, DATASOURCE, context(), null);

        assertTrue(result.isSuccess(), "notFoundAsSuccess=null 时 NOT_FOUND 应视为成功");
        assertEquals(SimpleElasticsearchPersistenceCoreConstant.ES_RESULT_NOT_FOUND, result.getResult(),
                "result 应为 not_found");
    }

    @Test
    @DisplayName("fromDeleteResponse：NOT_FOUND + notFoundAsSuccess=false 视为失败")
    void fromDeleteResponseNotFoundAsFailure() {
        DocWriteResponse response = mock(DocWriteResponse.class);
        when(response.getResult()).thenReturn(DocWriteResponse.Result.NOT_FOUND);
        when(response.getId()).thenReturn("id-3");
        when(response.getIndex()).thenReturn("idx-3");

        PersistenceResult result = PersistenceResultHelper.fromDeleteResponse(
                response, DATASOURCE, context(), Boolean.FALSE);

        assertFalse(result.isSuccess(), "notFoundAsSuccess=false 时 NOT_FOUND 应视为失败");
    }

    @Test
    @DisplayName("fromDeleteResponse：DELETED 无论 notFoundAsSuccess 均成功")
    void fromDeleteResponseDeleted() {
        DocWriteResponse response = mock(DocWriteResponse.class);
        when(response.getResult()).thenReturn(DocWriteResponse.Result.DELETED);
        when(response.getId()).thenReturn("id-4");
        when(response.getIndex()).thenReturn("idx-4");

        PersistenceResult result = PersistenceResultHelper.fromDeleteResponse(
                response, DATASOURCE, context(), Boolean.FALSE);
        assertTrue(result.isSuccess(), "DELETED 应成功");
        assertEquals(SimpleElasticsearchPersistenceCoreConstant.ES_RESULT_DELETED, result.getResult(),
                "result 应为 deleted");
    }

    @Test
    @DisplayName("fromBulkResponse：失败 item 填充 status/errorType/reason，itemIndex 含 offset")
    void fromBulkResponseFailureDetail() {
        BulkItemResponse item = mock(BulkItemResponse.class);
        when(item.isFailed()).thenReturn(true);
        when(item.getItemId()).thenReturn(1);
        when(item.getOpType()).thenReturn(org.elasticsearch.action.DocWriteRequest.OpType.INDEX);
        when(item.getId()).thenReturn("id-f");
        when(item.getIndex()).thenReturn("idx-f");
        when(item.getFailureMessage()).thenReturn("msg");
        BulkItemResponse.Failure failure = mock(BulkItemResponse.Failure.class);
        when(failure.getStatus()).thenReturn(RestStatus.TOO_MANY_REQUESTS);
        when(failure.getType()).thenReturn("type-1");
        when(failure.getCause()).thenReturn(new RuntimeException("reason-1"));
        when(item.getFailure()).thenReturn(failure);

        BulkResponse response = mock(BulkResponse.class);
        when(response.getItems()).thenReturn(new BulkItemResponse[]{item});
        when(response.hasFailures()).thenReturn(true);

        BulkFailureClassifier classifier = (status, type, reason) ->
                Integer.valueOf(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_TOO_MANY_REQUESTS).equals(status);

        BulkResult result = PersistenceResultHelper.fromBulkResponse(
                response, DATASOURCE, context(), 10, classifier);

        assertFalse(result.isSuccess(), "有失败应整体失败");
        assertEquals(1, result.getFailed(), "1 个失败");
        assertEquals(1, result.getFailureList().size(), "1 个失败明细");
        io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure f =
                result.getFailureList().get(0);
        assertEquals(Integer.valueOf(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_TOO_MANY_REQUESTS),
                f.getStatus(), "status 应为 429");
        assertEquals("type-1", f.getErrorType(), "errorType 应来自 getType");
        assertEquals("reason-1", f.getErrorReason(), "errorReason 应来自 getCause().getMessage");
        assertTrue(f.getRetryable(), "classifier 应判可重试");
        assertEquals(11, f.getItemIndex(), "itemIndex = offset(10) + itemId(1)");
    }

    @Test
    @DisplayName("fromBulkResponse：无失败时 success=true，failureList 为空")
    void fromBulkResponseAllSuccess() {
        BulkItemResponse ok = mock(BulkItemResponse.class);
        when(ok.isFailed()).thenReturn(false);
        when(ok.getItemId()).thenReturn(0);

        BulkResponse response = mock(BulkResponse.class);
        when(response.getItems()).thenReturn(new BulkItemResponse[]{ok});
        when(response.hasFailures()).thenReturn(false);

        BulkResult result = PersistenceResultHelper.fromBulkResponse(
                response, DATASOURCE, context(), 0, null);

        assertTrue(result.isSuccess(), "无失败应成功");
        assertEquals(0, result.getFailureList().size(), "failureList 应为空");
        assertEquals(1, result.getSucceeded(), "succeeded 应为 1");
    }
}
