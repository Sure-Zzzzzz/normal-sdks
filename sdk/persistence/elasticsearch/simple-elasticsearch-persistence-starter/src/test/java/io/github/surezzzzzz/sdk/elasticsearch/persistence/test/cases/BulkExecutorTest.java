package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.BulkFailureClassifier;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.BulkPersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.executor.BulkExecutor;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessorChain;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.ElasticsearchWriteApiHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.validator.PersistenceRequestValidatorRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.WriteIndexResolver;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.rest.RestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BulkExecutor 单元测试
 *
 * <p>验证 batchSize 分批、continueOnFailure 停止、partial 异常、failure 明细聚合。
 * 不连真实 ES，writeApiHelper / registry / resolver 全部 mock。
 *
 * @author surezzzzzz
 */
@Slf4j
class BulkExecutorTest {

    private BulkExecutor executor;
    private ElasticsearchWriteApiHelper writeApiHelper;
    private SimpleElasticsearchRouteRegistry registry;
    private WriteIndexResolver writeIndexResolver;
    private DocumentPreProcessorChain documentPreProcessorChain;
    private BulkFailureClassifier classifier;

    @BeforeEach
    void setUp() {
        executor = new BulkExecutor();
        writeApiHelper = mock(ElasticsearchWriteApiHelper.class);
        registry = mock(SimpleElasticsearchRouteRegistry.class);
        writeIndexResolver = mock(WriteIndexResolver.class);
        documentPreProcessorChain = mock(DocumentPreProcessorChain.class);
        classifier = mock(BulkFailureClassifier.class);
        RouteResolver routeResolver = mock(RouteResolver.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        PersistenceRequestValidatorRegistry validatorRegistry = new PersistenceRequestValidatorRegistry(new ArrayList<>());

        ReflectionTestUtils.setField(executor, "writeApiHelper", writeApiHelper);
        ReflectionTestUtils.setField(executor, "registry", registry);
        ReflectionTestUtils.setField(executor, "writeIndexResolver", writeIndexResolver);
        ReflectionTestUtils.setField(executor, "routeResolver", routeResolver);
        ReflectionTestUtils.setField(executor, "documentPreProcessorChain", documentPreProcessorChain);
        ReflectionTestUtils.setField(executor, "validatorRegistry", validatorRegistry);
        ReflectionTestUtils.setField(executor, "eventPublisher", publisher);
        ReflectionTestUtils.setField(executor, "bulkFailureClassifier", classifier);

        when(writeIndexResolver.resolveWriteIndex(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(documentPreProcessorChain.process(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(registry.resolveDataSourceOrThrow(any(String[].class))).thenReturn("primary");
        when(classifier.retryable(any(), any(), any())).thenReturn(false);
    }

    @Test
    @DisplayName("batchSize=2 把 5 个 item 分成 3 批，全部成功聚合")
    void batchSizeSplitting() throws Exception {
        when(writeApiHelper.bulk(anyString(), any())).thenAnswer(inv -> {
            org.elasticsearch.action.bulk.BulkRequest esReq = inv.getArgument(1);
            return mockBulkResponse(false, esReq.requests().size());
        });

        BulkRequest request = buildRequest(5, BulkOptions.builder().batchSize(2).build());
        BulkResult result = executor.execute(request);

        assertTrue(result.isSuccess(), "全部成功应整体成功");
        assertEquals(5, result.getTotal(), "total 应为 5");
        assertEquals(5, result.getSucceeded(), "succeeded 应为 5");
        assertEquals(0, result.getFailed(), "failed 应为 0");
        assertEquals(Integer.valueOf(3), result.getBatchTotal(), "应分 3 批");
        assertEquals(Integer.valueOf(3), result.getBatchSucceeded(), "3 批均成功");
        assertFalse(result.getPartial(), "不应为 partial");
    }

    @Test
    @DisplayName("continueOnFailure=false：第一批有 item failure 后停止后续批次")
    void continueOnFailureFalseStops() throws Exception {
        BulkResponse failResp = mockBulkResponseWithFailure(2, 0, RestStatus.CONFLICT);
        BulkResponse okResp = mockBulkResponse(false, 2);
        when(writeApiHelper.bulk(anyString(), any())).thenReturn(failResp, okResp);

        BulkRequest request = buildRequest(4, BulkOptions.builder().batchSize(2).continueOnFailure(false).build());
        BulkResult result = executor.execute(request);

        assertFalse(result.isSuccess(), "应整体失败");
        assertTrue(result.getStoppedOnFailure(), "应因失败停止");
        assertEquals(1, result.getFailed(), "第一批 1 个失败 item");
        assertEquals(Integer.valueOf(1), result.getBatchTotal(), "只提交 1 批");
        assertEquals(Integer.valueOf(1), result.getBatchFailed(), "1 批有失败");
    }

    @Test
    @DisplayName("continueOnFailure=true：出现失败仍提交后续批次，聚合所有失败")
    void continueOnFailureTrueContinues() throws Exception {
        BulkResponse failResp = mockBulkResponseWithFailure(2, 0, RestStatus.CONFLICT);
        BulkResponse okResp = mockBulkResponse(false, 2);
        when(writeApiHelper.bulk(anyString(), any())).thenReturn(failResp, okResp);

        BulkRequest request = buildRequest(4, BulkOptions.builder().batchSize(2).continueOnFailure(true).build());
        BulkResult result = executor.execute(request);

        assertFalse(result.isSuccess(), "有失败 item 应整体失败");
        assertFalse(result.getStoppedOnFailure(), "不应停止");
        assertEquals(1, result.getFailed(), "仅第一批 1 个失败");
        assertEquals(Integer.valueOf(2), result.getBatchTotal(), "提交 2 批");
        assertEquals(1, result.getFailureList().size(), "聚合 1 个失败明细");
    }

    @Test
    @DisplayName("第一批异常直接抛出（batchTotal=0 不包 partial）")
    void firstBatchExceptionRethrown() throws Exception {
        when(writeApiHelper.bulk(anyString(), any())).thenThrow(new RuntimeException("es down"));

        BulkRequest request = buildRequest(2, BulkOptions.builder().batchSize(2).build());
        assertThrows(RuntimeException.class, () -> executor.execute(request),
                "第一批异常应直接抛出，不包 BulkPersistenceExecutionException");
    }

    @Test
    @DisplayName("后续批次异常包 BulkPersistenceExecutionException，承载 partialResult")
    void laterBatchExceptionWrappedAsPartial() throws Exception {
        BulkResponse okResp = mockBulkResponse(false, 2);
        when(writeApiHelper.bulk(anyString(), any())).thenReturn(okResp)
                .thenThrow(new RuntimeException("es down mid"));

        BulkRequest request = buildRequest(4, BulkOptions.builder().batchSize(2).build());
        BulkPersistenceExecutionException ex = assertThrows(BulkPersistenceExecutionException.class,
                () -> executor.execute(request), "后续批次异常应包 partial 异常");
        BulkResult partial = ex.getPartialResult();
        assertEquals(2, partial.getSucceeded(), "partial 应含已成功批次");
        assertTrue(partial.getPartial(), "partial 标记应为 true");
        assertEquals(Integer.valueOf(1), partial.getBatchTotal(), "已提交 1 批");
    }

    @Test
    @DisplayName("failure 明细：status/errorType/reason/retryable 与全局 itemIndex")
    void failureDetailFields() throws Exception {
        when(classifier.retryable(any(), any(), any())).thenReturn(true);
        BulkResponse failResp = mockBulkResponseWithFailure(2, 1, RestStatus.TOO_MANY_REQUESTS);
        when(writeApiHelper.bulk(anyString(), any())).thenReturn(failResp);

        BulkRequest request = buildRequest(2, BulkOptions.builder().batchSize(2).build());
        BulkResult result = executor.execute(request);

        assertEquals(1, result.getFailureList().size(), "应有 1 个失败明细");
        io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure failure =
                result.getFailureList().get(0);
        assertEquals(Integer.valueOf(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_TOO_MANY_REQUESTS),
                failure.getStatus(), "status 应为 429");
        assertEquals("type-conflict", failure.getErrorType(), "errorType 应来自 Failure.getType()");
        assertTrue(failure.getRetryable(), "retryable 应来自 classifier");
        assertEquals(1, failure.getItemIndex(), "itemIndex 应为该 item 在批次内的全局下标");
    }

    private BulkRequest buildRequest(int count, BulkOptions options) {
        List<BulkItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(BulkItem.builder().type(BulkItemType.INDEX).id("id-" + i).index("test_bulk_unit").build());
        }
        return BulkRequest.builder().itemList(items).defaultIndex("test_bulk_unit").options(options).build();
    }

    private BulkResponse mockBulkResponse(boolean hasFailure, int itemCount) {
        BulkItemResponse[] items = new BulkItemResponse[itemCount];
        for (int i = 0; i < itemCount; i++) {
            items[i] = mockSuccessItem(i);
        }
        BulkResponse response = mock(BulkResponse.class);
        when(response.getItems()).thenReturn(items);
        when(response.hasFailures()).thenReturn(hasFailure);
        return response;
    }

    private BulkResponse mockBulkResponseWithFailure(int itemCount, int failIndex, RestStatus status) {
        BulkItemResponse[] items = new BulkItemResponse[itemCount];
        for (int i = 0; i < itemCount; i++) {
            items[i] = i == failIndex ? mockFailedItem(i, status) : mockSuccessItem(i);
        }
        BulkResponse response = mock(BulkResponse.class);
        when(response.getItems()).thenReturn(items);
        when(response.hasFailures()).thenReturn(true);
        return response;
    }

    private BulkItemResponse mockSuccessItem(int itemId) {
        BulkItemResponse item = mock(BulkItemResponse.class);
        when(item.isFailed()).thenReturn(false);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getOpType()).thenReturn(DocWriteRequest.OpType.INDEX);
        when(item.getId()).thenReturn("id-" + itemId);
        when(item.getIndex()).thenReturn("test_bulk_unit");
        return item;
    }

    private BulkItemResponse mockFailedItem(int itemId, RestStatus status) {
        BulkItemResponse item = mock(BulkItemResponse.class);
        when(item.isFailed()).thenReturn(true);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getOpType()).thenReturn(DocWriteRequest.OpType.INDEX);
        when(item.getId()).thenReturn("id-" + itemId);
        when(item.getIndex()).thenReturn("test_bulk_unit");
        when(item.getFailureMessage()).thenReturn("bulk item failed");

        BulkItemResponse.Failure failure = mock(BulkItemResponse.Failure.class);
        when(failure.getStatus()).thenReturn(status);
        when(failure.getType()).thenReturn("type-conflict");
        when(failure.getCause()).thenReturn(new RuntimeException("reason-" + itemId));
        when(item.getFailure()).thenReturn(failure);
        return item;
    }
}
