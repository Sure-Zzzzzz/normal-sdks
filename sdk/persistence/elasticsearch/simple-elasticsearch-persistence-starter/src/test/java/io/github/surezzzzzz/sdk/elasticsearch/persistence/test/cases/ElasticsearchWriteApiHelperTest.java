package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.ByQueryOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.ElasticsearchWriteApiHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * by-query request 构造单元测试。
 * <p>覆盖 DESIGN 7.2：URL 参数映射、options=null 安全、scrollSize 优先级。
 * body 收敛（仅 query/script）由真实 ES 集成测试覆盖（HTTP 不再 400）。
 * 纯静态方法单测，不挂 Spring 上下文，与本模块 PersistenceEsRequestHelperTest 惯例一致。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
class ElasticsearchWriteApiHelperTest {

    private static final String ENDPOINT = "/test_index/_update_by_query";
    private static final String BODY = "{\"query\":{\"match_none\":{}}}";

    @Test
    @DisplayName("完整 options：10 个执行参数全部映射到 URL query")
    void buildByQueryRequestFullOptions() {
        log.info("=== buildByQueryRequestFullOptions ===");
        ByQueryOptions options = ByQueryOptions.builder()
                .conflicts("proceed")
                .refresh(false)
                .timeoutMs(30000L)
                .slices(2)
                .scrollSize(500)
                .requestsPerSecond(500f)
                .maxDocs(1000L)
                .waitForActiveShards(1)
                .routing("r1")
                .build();
        Request request = ElasticsearchWriteApiHelper.buildByQueryRequest(ENDPOINT, BODY, options, false);

        Map<String, String> params = request.getParameters();
        log.info("URL 参数映射: {}", params);
        assertEquals(10, params.size());
        assertEquals("false", params.get(SimpleElasticsearchRouteConstant.PARAM_WAIT_FOR_COMPLETION));
        assertEquals("false", params.get(SimpleElasticsearchRouteConstant.PARAM_REFRESH));
        assertEquals("30000ms", params.get(SimpleElasticsearchRouteConstant.PARAM_TIMEOUT));
        assertEquals("2", params.get(SimpleElasticsearchRouteConstant.PARAM_SLICES));
        assertEquals("proceed", params.get(SimpleElasticsearchRouteConstant.PARAM_CONFLICTS));
        assertEquals("500", params.get(SimpleElasticsearchRouteConstant.PARAM_SCROLL_SIZE));
        assertEquals("500.0", params.get(SimpleElasticsearchRouteConstant.PARAM_REQUESTS_PER_SECOND));
        assertEquals("1000", params.get(SimpleElasticsearchRouteConstant.PARAM_MAX_DOCS));
        assertEquals("1", params.get(SimpleElasticsearchRouteConstant.PARAM_WAIT_FOR_ACTIVE_SHARDS));
        assertEquals("r1", params.get(SimpleElasticsearchRouteConstant.PARAM_ROUTING));
        assertEquals(SimpleElasticsearchRouteConstant.HTTP_METHOD_POST, request.getMethod());
        assertEquals(ENDPOINT, request.getEndpoint());
    }

    @Test
    @DisplayName("options=null：仅 wait_for_completion 透传，其余执行参数不出现")
    void buildByQueryRequestNullOptions() {
        log.info("=== buildByQueryRequestNullOptions ===");
        Request request = ElasticsearchWriteApiHelper.buildByQueryRequest(ENDPOINT, BODY, null, true);
        Map<String, String> params = request.getParameters();
        log.info("URL 参数映射: {}", params);
        assertEquals(1, params.size());
        assertEquals("true", params.get(SimpleElasticsearchRouteConstant.PARAM_WAIT_FOR_COMPLETION));
        assertNull(params.get(SimpleElasticsearchRouteConstant.PARAM_REFRESH));
        assertNull(params.get(SimpleElasticsearchRouteConstant.PARAM_TIMEOUT));
        assertNull(params.get(SimpleElasticsearchRouteConstant.PARAM_SLICES));
        assertNull(params.get(SimpleElasticsearchRouteConstant.PARAM_CONFLICTS));
        assertNull(params.get(SimpleElasticsearchRouteConstant.PARAM_SCROLL_SIZE));
        assertNull(params.get(SimpleElasticsearchRouteConstant.PARAM_ROUTING));
    }

    @Test
    @DisplayName("scrollSize 优先级：scrollSize 覆盖 batchSize")
    void resolveScrollSizeScrollSizeOverridesBatchSize() {
        log.info("=== resolveScrollSizeScrollSizeOverridesBatchSize ===");
        ByQueryOptions options = ByQueryOptions.builder().scrollSize(500).batchSize(100).build();
        log.info("scrollSize=500, batchSize=100 -> {}", ElasticsearchWriteApiHelper.resolveScrollSize(options));
        assertEquals(Integer.valueOf(500), ElasticsearchWriteApiHelper.resolveScrollSize(options));
    }

    @Test
    @DisplayName("scrollSize 优先级：仅 batchSize 时 fallback 为 scroll_size")
    void resolveScrollSizeBatchSizeFallback() {
        log.info("=== resolveScrollSizeBatchSizeFallback ===");
        ByQueryOptions options = ByQueryOptions.builder().batchSize(100).build();
        log.info("scrollSize=null, batchSize=100 -> {}", ElasticsearchWriteApiHelper.resolveScrollSize(options));
        assertEquals(Integer.valueOf(100), ElasticsearchWriteApiHelper.resolveScrollSize(options));
    }

    @Test
    @DisplayName("scrollSize 优先级：仅 scrollSize 时取 scrollSize")
    void resolveScrollSizeOnlyScrollSize() {
        log.info("=== resolveScrollSizeOnlyScrollSize ===");
        ByQueryOptions options = ByQueryOptions.builder().scrollSize(500).build();
        log.info("scrollSize=500, batchSize=null -> {}", ElasticsearchWriteApiHelper.resolveScrollSize(options));
        assertEquals(Integer.valueOf(500), ElasticsearchWriteApiHelper.resolveScrollSize(options));
    }

    @Test
    @DisplayName("scrollSize 优先级：两者都 null 时返回 null")
    void resolveScrollSizeBothNull() {
        log.info("=== resolveScrollSizeBothNull ===");
        ByQueryOptions options = ByQueryOptions.builder().build();
        log.info("scrollSize=null, batchSize=null -> {}", ElasticsearchWriteApiHelper.resolveScrollSize(options));
        assertNull(ElasticsearchWriteApiHelper.resolveScrollSize(options));
    }

    @Test
    @DisplayName("resolveScrollSize(null) 返回 null")
    void resolveScrollSizeNullOptions() {
        log.info("=== resolveScrollSizeNullOptions ===");
        log.info("options=null -> {}", ElasticsearchWriteApiHelper.resolveScrollSize(null));
        assertNull(ElasticsearchWriteApiHelper.resolveScrollSize(null));
    }
}
