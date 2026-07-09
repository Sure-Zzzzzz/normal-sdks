package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ServerVersion;
import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchReflectionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchSearchContextHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ElasticsearchSearchContextHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ElasticsearchSearchContextHelperTest {

    @Test
    public void testBuildPitRequests() {
        log.info("=== testBuildPitRequests ===");
        Request openPit = ElasticsearchSearchContextHelper.buildOpenPitRequest("test_index", "1m");
        Request closePit = ElasticsearchSearchContextHelper.buildClosePitRequest("pit-1");
        ClusterInfo es79 = ClusterInfo.initial("primary", ServerVersion.parse("7.9.3"));
        ClusterInfo es710 = ClusterInfo.initial("primary", ServerVersion.parse("7.10.0"));

        assertEquals("POST", openPit.getMethod());
        assertEquals("/test_index/_pit?keep_alive=1m", openPit.getEndpoint());
        assertEquals("DELETE", closePit.getMethod());
        assertEquals("/_pit", closePit.getEndpoint());
        assertFalse(ElasticsearchSearchContextHelper.supportsPointInTime(es79));
        assertEquals(ElasticsearchSearchContextHelper.isPointInTimeBuilderAvailable(),
                ElasticsearchSearchContextHelper.supportsPointInTime(es710));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        boolean applied = ElasticsearchSearchContextHelper.applyPointInTime(sourceBuilder, "pit-1", "1m");
        assertEquals(ElasticsearchSearchContextHelper.isPointInTimeBuilderAvailable(), applied);
        if (applied) {
            Object pitBuilder = ElasticsearchReflectionHelper.invoke(ElasticsearchReflectionHelper.loadMethod(
                    SearchSourceBuilder.class, SimpleElasticsearchRouteConstant.METHOD_POINT_IN_TIME_BUILDER), sourceBuilder);
            assertEquals("pit-1", ElasticsearchReflectionHelper.invoke(ElasticsearchReflectionHelper.loadMethod(
                    pitBuilder.getClass(), "getEncodedId"), pitBuilder));
            assertEquals("1m", String.valueOf(ElasticsearchReflectionHelper.invoke(ElasticsearchReflectionHelper.loadMethod(
                    pitBuilder.getClass(), "getKeepAlive"), pitBuilder)));
        }
    }

    @Test
    public void testBuildScrollRequests() {
        log.info("=== testBuildScrollRequests ===");
        Request initial = ElasticsearchSearchContextHelper.buildInitialScrollSearchRequest(
                new String[]{"test_index"}, "{\"query\":{}}", "1m");
        Request next = ElasticsearchSearchContextHelper.buildScrollContinueRequest("scroll-1", "1m");
        Request clear = ElasticsearchSearchContextHelper.buildScrollClearRequest("scroll-1");

        assertEquals("POST", initial.getMethod());
        assertEquals("/test_index/_search?scroll=1m", initial.getEndpoint());
        assertEquals("POST", next.getMethod());
        assertEquals("/_search/scroll", next.getEndpoint());
        assertEquals("DELETE", clear.getMethod());
        assertEquals("/_search/scroll", clear.getEndpoint());
    }

    @Test
    public void testExtractPitId() {
        log.info("=== testExtractPitId ===");
        assertEquals("pit-1", ElasticsearchSearchContextHelper.extractPitId("{\"id\":\"pit-1\"}"));
        assertNull(ElasticsearchSearchContextHelper.extractPitId("{}"));
    }
}
