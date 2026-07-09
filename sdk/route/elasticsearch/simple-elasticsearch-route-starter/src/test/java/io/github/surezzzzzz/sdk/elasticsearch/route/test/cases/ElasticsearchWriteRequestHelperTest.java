package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchWriteRequestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.index.VersionType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ElasticsearchWriteRequestHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ElasticsearchWriteRequestHelperTest {

    @Test
    public void testTypedRequests() {
        log.info("=== testTypedRequests ===");
        IndexRequest indexRequest = ElasticsearchWriteRequestHelper.newTypedIndexRequest("test_index", "1");
        UpdateRequest updateRequest = ElasticsearchWriteRequestHelper.newTypedUpdateRequest("test_index", "1");
        DeleteRequest deleteRequest = ElasticsearchWriteRequestHelper.newTypedDeleteRequest("test_index", "1");

        assertEquals("test_index", indexRequest.index());
        assertEquals("1", indexRequest.id());
        assertEquals("test_index", updateRequest.index());
        assertEquals("1", updateRequest.id());
        assertEquals("test_index", deleteRequest.index());
        assertEquals("1", deleteRequest.id());
    }

    @Test
    public void testApplyWriteOptions() {
        log.info("=== testApplyWriteOptions ===");
        IndexRequest indexRequest = ElasticsearchWriteRequestHelper.newTypedIndexRequest("test_index");
        ElasticsearchWriteRequestHelper.applyRouting(indexRequest, "route-1");
        ElasticsearchWriteRequestHelper.applyPipeline(indexRequest, "pipeline-1");
        ElasticsearchWriteRequestHelper.applyCreateOpType(indexRequest, true);

        assertEquals("route-1", indexRequest.routing());
        assertEquals("pipeline-1", indexRequest.getPipeline());
        assertEquals(DocWriteRequest.OpType.CREATE, indexRequest.opType());

        UpdateRequest updateRequest = ElasticsearchWriteRequestHelper.newTypedUpdateRequest("test_index", "1");
        ElasticsearchWriteRequestHelper.applyRetryOnConflict(updateRequest, 3);
        ElasticsearchWriteRequestHelper.applyDetectNoop(updateRequest, false);
        assertEquals(3, updateRequest.retryOnConflict());
        assertFalse(updateRequest.detectNoop());

        DeleteRequest deleteRequest = ElasticsearchWriteRequestHelper.newTypedDeleteRequest("test_index", "1");
        ElasticsearchWriteRequestHelper.applyVersion(deleteRequest, 10L);
        ElasticsearchWriteRequestHelper.applyVersionType(deleteRequest, "external");
        assertEquals(10L, deleteRequest.version());
        assertEquals(VersionType.EXTERNAL, deleteRequest.versionType());
    }
}
