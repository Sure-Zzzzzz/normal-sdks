package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchReflectionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchResponseHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.XContentCompatibilityHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XContentCompatibilityHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class XContentCompatibilityHelperTest {

    @Test
    public void testDetectXContentPackage() {
        log.info("=== testDetectXContentPackage ===");
        String xContentPackage = XContentCompatibilityHelper.detectXContentPackage();
        assertNotNull(xContentPackage);
        assertTrue(xContentPackage.equals("org.elasticsearch.xcontent.")
                || xContentPackage.equals("org.elasticsearch.common.xcontent."));
        assertNotNull(XContentCompatibilityHelper.getNamedXContentRegistry());
        Object parser = XContentCompatibilityHelper.createParser("{}".getBytes(StandardCharsets.UTF_8));
        assertEquals("START_OBJECT", String.valueOf(ElasticsearchReflectionHelper.invoke(
                ElasticsearchReflectionHelper.loadMethod(parser.getClass(), "nextToken"), parser)));
        XContentCompatibilityHelper.closeParser(parser);
    }

    @Test
    public void testParseCountResponse() throws Exception {
        log.info("=== testParseCountResponse ===");
        byte[] body = "{\"count\":123,\"_shards\":{}}".getBytes(StandardCharsets.UTF_8);
        long count = XContentCompatibilityHelper.parseCountResponse(new ByteArrayInputStream(body));
        assertEquals(123L, count);
    }

    @Test
    public void testParseSearchResponse() throws Exception {
        log.info("=== testParseSearchResponse ===");
        String totalHits = XContentCompatibilityHelper.useXContent7xPackage()
                ? "{\"value\":0,\"relation\":\"eq\"}"
                : "0";
        byte[] body = ("{\"took\":1,\"timed_out\":false,\"_shards\":{\"total\":1,\"successful\":1,"
                + "\"skipped\":0,\"failed\":0},\"hits\":{\"total\":" + totalHits + ","
                + "\"max_score\":null,\"hits\":[]}}").getBytes(StandardCharsets.UTF_8);
        SearchResponse response = XContentCompatibilityHelper.parseSearchResponse(body);
        SearchHits hits = response.getHits();
        assertEquals(0L, ElasticsearchResponseHelper.extractTotalHits(hits));
        assertEquals(1, response.getTook().millis());
    }
}
