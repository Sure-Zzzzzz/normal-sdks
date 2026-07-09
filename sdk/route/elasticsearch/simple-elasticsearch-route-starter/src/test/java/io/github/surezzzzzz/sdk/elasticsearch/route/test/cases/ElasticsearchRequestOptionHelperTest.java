package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchRequestOptionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.client.Request;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

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
    public void testByQueryBodyOptions() {
        log.info("=== testByQueryBodyOptions ===");
        Map<String, Object> body = new LinkedHashMap<>();
        ElasticsearchRequestOptionHelper.applyByQueryBodyOptions(body, true, 30000L, 2, "proceed", 500);

        assertEquals(Boolean.TRUE, body.get("refresh"));
        assertEquals("30000ms", body.get("timeout"));
        assertEquals(2, body.get("slices"));
        assertEquals("proceed", body.get("conflicts"));
        assertEquals(500, body.get("scroll_size"));
    }
}
