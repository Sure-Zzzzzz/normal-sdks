package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ServerVersion;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchDslCompatibilityHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ElasticsearchDslCompatibilityHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ElasticsearchDslCompatibilityHelperTest {

    @Test
    public void testRemoveFieldRecursively() {
        log.info("=== testRemoveFieldRecursively ===");
        String json = "{\"aggs\":{\"a\":{\"composite\":{\"sources\":[{\"f\":{\"terms\":{\"field\":\"extraField\",\"missing_bucket\":true}}}]}}}}";
        String cleaned = ElasticsearchDslCompatibilityHelper.removeFieldRecursively(json, "missing_bucket");

        assertFalse(cleaned.contains("missing_bucket"));
        assertTrue(cleaned.contains("extraField"));
    }

    @Test
    public void testRemoveCompositeUnsupportedFieldsForEs6() {
        log.info("=== testRemoveCompositeUnsupportedFieldsForEs6 ===");
        ClusterInfo es6 = ClusterInfo.initial("primary", ServerVersion.parse("6.2.2"));
        String json = "{\"missing_bucket\":true,\"missing_order\":\"last\",\"field\":\"extraField\"}";
        String cleaned = ElasticsearchDslCompatibilityHelper.removeCompositeUnsupportedFields(json, es6);

        assertFalse(cleaned.contains("missing_bucket"));
        assertFalse(cleaned.contains("missing_order"));
        assertTrue(cleaned.contains("extraField"));
    }

    @Test
    public void testKeepCompositeFieldsForEs7() {
        log.info("=== testKeepCompositeFieldsForEs7 ===");
        ClusterInfo es7 = ClusterInfo.initial("primary", ServerVersion.parse("7.17.9"));
        String json = "{\"missing_bucket\":true,\"missing_order\":\"last\"}";

        assertEquals(json, ElasticsearchDslCompatibilityHelper.removeCompositeUnsupportedFields(json, es7));
    }
}
