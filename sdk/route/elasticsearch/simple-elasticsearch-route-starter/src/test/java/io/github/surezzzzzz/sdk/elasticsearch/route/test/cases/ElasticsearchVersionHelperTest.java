package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ServerVersion;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchVersionHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ElasticsearchVersionHelper 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ElasticsearchVersionHelperTest {

    @Test
    public void testServerVersionCompareMethods() {
        log.info("=== testServerVersionCompareMethods ===");
        ServerVersion es622 = ServerVersion.parse("6.2.2");
        ServerVersion es710 = ServerVersion.parse("7.10.0");
        ServerVersion es7 = ServerVersion.parse("7");

        assertTrue(es622.isEs6());
        assertFalse(es622.isEs7());
        assertTrue(es710.isEs7());
        assertTrue(es710.isAtLeast(7, 10));
        assertFalse(es622.isAtLeast(6, 3));
        assertTrue(es622.isBefore(7));
        assertTrue(es7.isBefore(7, 10));
        assertFalse(es7.isAtLeast(7, 10));
    }

    @Test
    public void testClusterInfoVersionMethods() {
        log.info("=== testClusterInfoVersionMethods ===");
        ClusterInfo unknown = ClusterInfo.initial("primary", null);
        ClusterInfo es622 = ClusterInfo.initial("primary", ServerVersion.parse("6.2.2"));
        ClusterInfo es717 = ClusterInfo.initial("primary", ServerVersion.parse("7.17.9"));

        assertTrue(ElasticsearchVersionHelper.isUnknown(null));
        assertTrue(ElasticsearchVersionHelper.isUnknown(unknown));
        assertFalse(ElasticsearchVersionHelper.isKnown(unknown));
        assertTrue(ElasticsearchVersionHelper.isEs6(es622));
        assertTrue(ElasticsearchVersionHelper.isEs7(es717));
        assertTrue(ElasticsearchVersionHelper.isAtLeast(es717, 7, 10));
        assertTrue(ElasticsearchVersionHelper.isBefore(es622, 7));
    }

    @Test
    public void testCompositeCapabilityMethods() {
        log.info("=== testCompositeCapabilityMethods ===");
        ClusterInfo es600 = ClusterInfo.initial("primary", ServerVersion.parse("6.0.0"));
        ClusterInfo es622 = ClusterInfo.initial("primary", ServerVersion.parse("6.2.2"));
        ClusterInfo es717 = ClusterInfo.initial("primary", ServerVersion.parse("7.17.9"));

        assertFalse(ElasticsearchVersionHelper.supportsCompositeAggregation(null));
        assertFalse(ElasticsearchVersionHelper.supportsCompositeAggregation(es600));
        assertTrue(ElasticsearchVersionHelper.supportsCompositeAggregation(es622));
        assertFalse(ElasticsearchVersionHelper.supportsStableCompositeAfterKey(es622));
        assertFalse(ElasticsearchVersionHelper.supportsCompositeMissingBucket(es622));
        assertTrue(ElasticsearchVersionHelper.supportsStableCompositeAfterKey(es717));
        assertTrue(ElasticsearchVersionHelper.supportsCompositeMissingBucket(es717));
    }
}
