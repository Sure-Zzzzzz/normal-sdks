package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchEndpointHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ElasticsearchEndpointHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ElasticsearchEndpointHelperTest {

    @Test
    public void testBuildSearchAndCountEndpoint() {
        log.info("=== testBuildSearchAndCountEndpoint ===");
        assertEquals("/test_index", ElasticsearchEndpointHelper.buildIndexEndpoint("test_index"));
        assertEquals("/_search", ElasticsearchEndpointHelper.buildSearchEndpoint(null));
        assertEquals("/_count", ElasticsearchEndpointHelper.buildCountEndpoint(new String[0]));
        assertEquals("/test_index/_search", ElasticsearchEndpointHelper.buildSearchEndpoint(new String[]{"test_index"}));
        assertEquals("/test_a,test_b/_count", ElasticsearchEndpointHelper.buildCountEndpoint(new String[]{"test_a", "test_b"}));
        assertEquals("/test_index/_search?scroll=1m",
                ElasticsearchEndpointHelper.buildSearchEndpoint(new String[]{"test_index"}, "1m"));
    }

    @Test
    public void testBuildProtocolEndpoints() {
        log.info("=== testBuildProtocolEndpoints ===");
        assertEquals("/test_index/_mapping", ElasticsearchEndpointHelper.buildMappingEndpoint("test_index"));
        assertEquals("/test_index/_doc/1", ElasticsearchEndpointHelper.buildDocEndpoint("test_index", "1"));
        assertEquals("/test_index/_refresh", ElasticsearchEndpointHelper.buildRefreshEndpoint("test_index"));
        assertEquals("/_refresh", ElasticsearchEndpointHelper.buildRefreshEndpoint(null));
        assertEquals("/test_index/_update_by_query", ElasticsearchEndpointHelper.buildUpdateByQueryEndpoint("test_index"));
        assertEquals("/test_index/_delete_by_query", ElasticsearchEndpointHelper.buildDeleteByQueryEndpoint("test_index"));
        assertEquals("/_tasks/node-1:1", ElasticsearchEndpointHelper.buildTaskEndpoint("node-1:1"));
        assertEquals("/test_index/_pit?keep_alive=1m", ElasticsearchEndpointHelper.buildOpenPitEndpoint("test_index", "1m"));
        assertEquals("/_pit", ElasticsearchEndpointHelper.buildClosePitEndpoint());
        assertEquals("/_search/scroll", ElasticsearchEndpointHelper.buildScrollEndpoint());
    }

    @Test
    public void testBuildBody() {
        log.info("=== testBuildBody ===");
        assertEquals("{\"scroll\":\"1m\",\"scroll_id\":\"scroll-1\"}",
                ElasticsearchEndpointHelper.buildScrollContinueBody("scroll-1", "1m"));
        assertEquals("{\"scroll_id\":\"scroll-1\"}", ElasticsearchEndpointHelper.buildScrollClearBody("scroll-1"));
        assertEquals("{\"id\":\"pit-1\"}", ElasticsearchEndpointHelper.buildClosePitBody("pit-1"));
    }
}
