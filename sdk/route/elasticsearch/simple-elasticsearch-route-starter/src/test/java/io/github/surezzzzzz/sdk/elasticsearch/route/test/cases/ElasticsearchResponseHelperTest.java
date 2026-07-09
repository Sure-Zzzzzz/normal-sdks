package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.exception.ElasticsearchCompatibilityException;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchResponseHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.rest.RestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ElasticsearchResponseHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ElasticsearchResponseHelperTest {

    @Test
    public void testExtractTotalHitsFromMap() {
        log.info("=== testExtractTotalHitsFromMap ===");
        Map<String, Object> hits = new LinkedHashMap<>();
        hits.put("total", 12L);
        assertEquals(12L, ElasticsearchResponseHelper.extractTotalHits(hits));

        hits.put("total", Collections.singletonMap("value", 34));
        assertEquals(34L, ElasticsearchResponseHelper.extractTotalHits(hits));
    }

    @Test
    public void testStatusAndRetryable() {
        log.info("=== testStatusAndRetryable ===");
        BulkItemResponse.Failure failure = new BulkItemResponse.Failure("test_index", "_doc", "1",
                new RuntimeException("version conflict"), RestStatus.CONFLICT);

        assertEquals("version conflict", ElasticsearchResponseHelper.extractFailureReason(failure));
        assertEquals("CONFLICT", ElasticsearchResponseHelper.extractFailureType(failure));
        assertEquals(409, ElasticsearchResponseHelper.extractStatusCode(failure));
        assertEquals("created", ElasticsearchResponseHelper.toDocWriteResultCode(DocWriteResponse.Result.CREATED));
        assertEquals(404, ElasticsearchResponseHelper.toStatusCode(RestStatus.NOT_FOUND));
        assertTrue(ElasticsearchResponseHelper.isRetryableStatus(408));
        assertTrue(ElasticsearchResponseHelper.isRetryableStatus(429));
        assertTrue(ElasticsearchResponseHelper.isRetryableStatus(503));
        assertFalse(ElasticsearchResponseHelper.isRetryableStatus(400));
    }

    @Test
    public void testMapExtraction() {
        log.info("=== testMapExtraction ===");
        Map<String, Object> parent = new LinkedHashMap<>();
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("node", "node-1");
        child.put("id", 1);
        parent.put("task", child);
        parent.put("completed", Boolean.TRUE);
        parent.put("response", Collections.singletonMap("updated", 1));
        parent.put("failures", Collections.singletonList("failure-1"));

        assertSame(child, ElasticsearchResponseHelper.extractMap(parent, "task"));
        assertSame(parent.get("response"), ElasticsearchResponseHelper.extractMap(parent, "response"));
        assertEquals("node-1:1", ElasticsearchResponseHelper.extractTaskId("fallback", child));
        assertEquals(10L, ElasticsearchResponseHelper.toLong("10"));
        assertEquals(0L, ElasticsearchResponseHelper.toLong("bad"));
        assertEquals("value", ElasticsearchResponseHelper.getAsString("value"));
        assertEquals("reason-1", ElasticsearchResponseHelper.extractFailureCause(
                Collections.singletonMap("reason", "reason-1")));
    }

    @Test
    public void testVersionCompatibilityException() {
        log.info("=== testVersionCompatibilityException ===");
        RuntimeException ex = new RuntimeException("request [/test/_search] contains unrecognized parameter: [ignore_throttled]");
        assertTrue(ElasticsearchResponseHelper.isUnrecognizedParameter(ex));
        assertTrue(ElasticsearchResponseHelper.isUnsupportedParameter(ex, "ignore_throttled"));
        assertTrue(ElasticsearchResponseHelper.isVersionCompatibilityException(ex));
        assertTrue(ElasticsearchResponseHelper.shouldFallbackToLowLevel(ex));
    }

    @Test
    public void testMappingSourceExtractFailureUsesRouteException() {
        log.info("=== testMappingSourceExtractFailureUsesRouteException ===");
        assertThrows(ElasticsearchCompatibilityException.class,
                () -> ElasticsearchResponseHelper.extractMappingSourceAsMap(new Object()));
    }
}
