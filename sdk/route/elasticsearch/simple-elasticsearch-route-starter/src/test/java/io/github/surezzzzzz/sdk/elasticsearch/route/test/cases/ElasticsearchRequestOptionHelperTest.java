package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.model.ByQueryRequestOptions;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchLowLevelRequestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchRequestOptionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Request;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ElasticsearchRequestOptionHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ElasticsearchRequestOptionHelperTest {

    @Test
    public void testExpandWildcards() {
        log.info("=== testExpandWildcards ===");
        assertEquals("open", ElasticsearchRequestOptionHelper.toExpandWildcards(IndicesOptions.lenientExpandOpen()));
        assertEquals("open,closed", ElasticsearchRequestOptionHelper.toExpandWildcards(
                IndicesOptions.fromOptions(true, true, true, true)));

        Request request = new Request("POST", "/test_index/_count");
        ElasticsearchRequestOptionHelper.applyIndicesOptions(request, IndicesOptions.lenientExpandOpen());
        assertEquals("true", request.getParameters().get("ignore_unavailable"));
        assertEquals("true", request.getParameters().get("allow_no_indices"));
        assertEquals("open", request.getParameters().get("expand_wildcards"));
    }

    @Test
    public void testRefreshPolicyAndTimeout() {
        log.info("=== testRefreshPolicyAndTimeout ===");
        assertEquals(WriteRequest.RefreshPolicy.IMMEDIATE, ElasticsearchRequestOptionHelper.toRefreshPolicy("true"));
        assertEquals(WriteRequest.RefreshPolicy.NONE, ElasticsearchRequestOptionHelper.toRefreshPolicy("false"));
        assertEquals(WriteRequest.RefreshPolicy.WAIT_UNTIL, ElasticsearchRequestOptionHelper.toRefreshPolicy("wait_for"));
        assertEquals(WriteRequest.RefreshPolicy.IMMEDIATE, ElasticsearchRequestOptionHelper.toRefreshPolicy(Boolean.TRUE));
        assertEquals("30000ms", ElasticsearchRequestOptionHelper.toTimeoutMs(30000L));
    }

    @Test
    public void testByQueryRequestOptions() {
        log.info("=== testByQueryRequestOptions ===");
        Request request = new Request("POST", "/test_index/_delete_by_query");
        ByQueryRequestOptions options = ByQueryRequestOptions.builder()
                .waitForCompletion(false)
                .refresh(false)
                .timeoutMs(30000L)
                .slices(2)
                .conflicts("proceed")
                .scrollSize(500)
                .requestsPerSecond(500f)
                .maxDocs(1000L)
                .waitForActiveShards(1)
                .routing("shard_key")
                .build();
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request, options);

        Map<String, String> parameters = request.getParameters();
        assertEquals(10, parameters.size());
        assertEquals("false", parameters.get("wait_for_completion"));
        assertEquals("false", parameters.get("refresh"));
        assertEquals("30000ms", parameters.get("timeout"));
        assertEquals("2", parameters.get("slices"));
        assertEquals("proceed", parameters.get("conflicts"));
        assertEquals("500", parameters.get("scroll_size"));
        assertEquals("500.0", parameters.get("requests_per_second"));
        assertEquals("1000", parameters.get("max_docs"));
        assertEquals("1", parameters.get("wait_for_active_shards"));
        assertEquals("shard_key", parameters.get("routing"));
    }

    @Test
    public void testByQueryRequestOptionsIgnoreNullAndBlank() {
        log.info("=== testByQueryRequestOptionsIgnoreNullAndBlank ===");
        Request request = new Request("POST", "/test_index/_delete_by_query");

        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request, null);
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(
                request, ByQueryRequestOptions.builder().build());
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(
                request, ByQueryRequestOptions.builder().conflicts("   ").build());
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(
                request, ByQueryRequestOptions.builder().conflicts("").build());

        assertTrue(request.getParameters().isEmpty());
        assertDoesNotThrow(() -> ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(
                null, ByQueryRequestOptions.builder().refresh(true).build()));
    }

    @Test
    public void testByQueryRequestOptionsPartialAndTrueValues() {
        log.info("=== testByQueryRequestOptionsPartialAndTrueValues ===");
        Request request = new Request("POST", "/test_index/_update_by_query");
        ByQueryRequestOptions options = ByQueryRequestOptions.builder()
                .waitForCompletion(true)
                .refresh(true)
                .conflicts("abort")
                .build();

        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request, options);

        Map<String, String> parameters = request.getParameters();
        assertEquals(3, parameters.size());
        assertEquals("true", parameters.get("wait_for_completion"));
        assertEquals("true", parameters.get("refresh"));
        assertEquals("abort", parameters.get("conflicts"));
        assertFalse(parameters.containsKey("timeout"));
        assertFalse(parameters.containsKey("slices"));
        assertFalse(parameters.containsKey("scroll_size"));
    }

    @Test
    public void testByQueryRequestOptionsWaitForCompletionNull() {
        log.info("=== testByQueryRequestOptionsWaitForCompletionNull ===");
        Request request = new Request("POST", "/test_index/_delete_by_query");
        ByQueryRequestOptions options = ByQueryRequestOptions.builder()
                .refresh(false)
                .timeoutMs(30000L)
                .slices(1)
                .build();

        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request, options);

        Map<String, String> parameters = request.getParameters();
        assertEquals(3, parameters.size());
        assertFalse(parameters.containsKey("wait_for_completion"));
        assertEquals("false", parameters.get("refresh"));
        assertEquals("30000ms", parameters.get("timeout"));
        assertEquals("1", parameters.get("slices"));
    }

    @Test
    public void testByQueryRequestOptionsConflictsTrimPassthrough() {
        log.info("=== testByQueryRequestOptionsConflictsTrimPassthrough ===");
        Request request = new Request("POST", "/test_index/_delete_by_query");
        ByQueryRequestOptions options = ByQueryRequestOptions.builder()
                .conflicts(" proceed ")
                .build();

        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request, options);

        Map<String, String> parameters = request.getParameters();
        assertEquals(1, parameters.size());
        assertEquals(" proceed ", parameters.get("conflicts"));
    }

    @Test
    public void testByQueryRequestOptionsZeroAndBoundaryValues() {
        log.info("=== testByQueryRequestOptionsZeroAndBoundaryValues ===");
        Request request = new Request("POST", "/test_index/_delete_by_query");
        ByQueryRequestOptions options = ByQueryRequestOptions.builder()
                .timeoutMs(0L)
                .slices(0)
                .scrollSize(0)
                .build();

        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request, options);

        Map<String, String> parameters = request.getParameters();
        assertEquals(3, parameters.size());
        assertEquals("0ms", parameters.get("timeout"));
        assertEquals("0", parameters.get("slices"));
        assertEquals("0", parameters.get("scroll_size"));
    }

    @Test
    public void testByQueryRequestOptionsRequestsPerSecond() {
        log.info("=== testByQueryRequestOptionsRequestsPerSecond ===");
        Request request = new Request("POST", "/test_index/_delete_by_query");

        // null 不透传
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request,
                ByQueryRequestOptions.builder().requestsPerSecond(null).build());
        assertTrue(request.getParameters().isEmpty());

        // 正常正数
        ByQueryRequestOptions options = ByQueryRequestOptions.builder()
                .requestsPerSecond(500f)
                .build();
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request, options);
        assertEquals("500.0", request.getParameters().get("requests_per_second"));

        // 0 暂停
        request = new Request("POST", "/test_index/_delete_by_query");
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request,
                ByQueryRequestOptions.builder().requestsPerSecond(0f).build());
        assertEquals("0.0", request.getParameters().get("requests_per_second"));

        // -1 不限
        request = new Request("POST", "/test_index/_delete_by_query");
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request,
                ByQueryRequestOptions.builder().requestsPerSecond(-1f).build());
        assertEquals("-1.0", request.getParameters().get("requests_per_second"));

        // 小数
        request = new Request("POST", "/test_index/_delete_by_query");
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request,
                ByQueryRequestOptions.builder().requestsPerSecond(10.5f).build());
        assertEquals("10.5", request.getParameters().get("requests_per_second"));
    }

    @Test
    public void testByQueryRequestOptionsRouting() {
        log.info("=== testByQueryRequestOptionsRouting ===");

        // null 不透传
        Request request = new Request("POST", "/test_index/_delete_by_query");
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request,
                ByQueryRequestOptions.builder().routing(null).build());
        assertTrue(request.getParameters().isEmpty());

        // 正常值
        request = new Request("POST", "/test_index/_delete_by_query");
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request,
                ByQueryRequestOptions.builder().routing("my_routing_key").build());
        assertEquals("my_routing_key", request.getParameters().get("routing"));

        // 空白不透传
        request = new Request("POST", "/test_index/_delete_by_query");
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request,
                ByQueryRequestOptions.builder().routing("   ").build());
        assertFalse(request.getParameters().containsKey("routing"));

        // 空串不透传
        request = new Request("POST", "/test_index/_delete_by_query");
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request,
                ByQueryRequestOptions.builder().routing("").build());
        assertFalse(request.getParameters().containsKey("routing"));
    }

    @Test
    public void testByQueryRequestOptionsMaxDocsAndWaitForActiveShards() {
        log.info("=== testByQueryRequestOptionsMaxDocsAndWaitForActiveShards ===");

        // null 不透传
        Request request = new Request("POST", "/test_index/_delete_by_query");
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request,
                ByQueryRequestOptions.builder().maxDocs(null).waitForActiveShards(null).build());
        assertTrue(request.getParameters().isEmpty());

        // 正常值
        request = new Request("POST", "/test_index/_delete_by_query");
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request,
                ByQueryRequestOptions.builder().maxDocs(5000L).waitForActiveShards(2).build());
        assertEquals("5000", request.getParameters().get("max_docs"));
        assertEquals("2", request.getParameters().get("wait_for_active_shards"));

        // 0 值
        request = new Request("POST", "/test_index/_delete_by_query");
        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request,
                ByQueryRequestOptions.builder().maxDocs(0L).waitForActiveShards(0).build());
        assertEquals("0", request.getParameters().get("max_docs"));
        assertEquals("0", request.getParameters().get("wait_for_active_shards"));
    }

    @Test
    public void testByQueryRequestOptionsEntityNotPolluted() {
        log.info("=== testByQueryRequestOptionsEntityNotPolluted ===");
        Request request = new Request("POST", "/test_index/_delete_by_query");
        request.setJsonEntity("{\"query\":{\"match_none\":{}}}");

        ByQueryRequestOptions options = ByQueryRequestOptions.builder()
                .waitForCompletion(false)
                .refresh(false)
                .timeoutMs(30000L)
                .slices(2)
                .conflicts("proceed")
                .scrollSize(500)
                .requestsPerSecond(500f)
                .maxDocs(1000L)
                .waitForActiveShards(1)
                .routing("shard_key")
                .build();

        ElasticsearchRequestOptionHelper.applyByQueryRequestOptions(request, options);

        try {
            assertEquals("{\"query\":{\"match_none\":{}}}",
                    ElasticsearchLowLevelRequestHelper.readEntity(request.getEntity()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Map<String, String> parameters = request.getParameters();
        assertEquals(10, parameters.size());
        assertEquals("false", parameters.get("wait_for_completion"));
        assertEquals("false", parameters.get("refresh"));
        assertEquals("30000ms", parameters.get("timeout"));
        assertEquals("2", parameters.get("slices"));
        assertEquals("proceed", parameters.get("conflicts"));
        assertEquals("500", parameters.get("scroll_size"));
        assertEquals("500.0", parameters.get("requests_per_second"));
        assertEquals("1000", parameters.get("max_docs"));
        assertEquals("1", parameters.get("wait_for_active_shards"));
        assertEquals("shard_key", parameters.get("routing"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testByQueryBodyOptionsCompatibility() throws Exception {
        log.info("=== testByQueryBodyOptionsCompatibility ===");
        Map<String, Object> body = new LinkedHashMap<>();
        ElasticsearchRequestOptionHelper.applyByQueryBodyOptions(body, false, 30000L, 2, "proceed", 500);

        assertEquals(Boolean.FALSE, body.get("refresh"));
        assertEquals("30000ms", body.get("timeout"));
        assertEquals(2, body.get("slices"));
        assertEquals("proceed", body.get("conflicts"));
        assertEquals(500, body.get("scroll_size"));
        assertDoesNotThrow(() -> ElasticsearchRequestOptionHelper.applyByQueryBodyOptions(
                null, null, null, null, null, null));

        Method method = ElasticsearchRequestOptionHelper.class.getDeclaredMethod(
                "applyByQueryBodyOptions",
                Map.class, Boolean.class, Long.class, Integer.class, String.class, Integer.class);
        assertEquals(void.class, method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertTrue(method.isAnnotationPresent(Deprecated.class));
    }

    @Test
    public void testByQueryRequestOptionsApiStructure() throws Exception {
        log.info("=== testByQueryRequestOptionsApiStructure ===");
        Method method = ElasticsearchRequestOptionHelper.class.getDeclaredMethod(
                "applyByQueryRequestOptions", Request.class, ByQueryRequestOptions.class);

        assertEquals(void.class, method.getReturnType());
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertFalse(method.isAnnotationPresent(Deprecated.class));
        assertEquals(1, java.util.Arrays.stream(ElasticsearchRequestOptionHelper.class.getDeclaredMethods())
                .filter(candidate -> "applyByQueryRequestOptions".equals(candidate.getName()))
                .count());
    }
}
