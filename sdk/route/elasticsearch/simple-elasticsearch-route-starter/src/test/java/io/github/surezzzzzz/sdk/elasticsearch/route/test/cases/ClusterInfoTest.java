package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ServerVersion;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClusterInfo 单元测试
 *
 * @author Sure
 * @since 1.0.3
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ClusterInfoTest {

    @Test
    public void testEffectiveVersionConfiguredFirst() {
        log.info("=== testEffectiveVersionConfiguredFirst ===");
        ClusterInfo info = ClusterInfo.initial("primary", ServerVersion.parse("6.2.2"))
                .withDetected(ServerVersion.parse("6.2.3"), System.currentTimeMillis());

        assertNotNull(info.getConfiguredVersion());
        assertNotNull(info.getDetectedVersion());
        assertEquals(6, info.getEffectiveVersion().getMajor());
        assertTrue(info.isVersionMismatch());
    }

    @Test
    public void testEffectiveVersionFallbackToDetected() {
        log.info("=== testEffectiveVersionFallbackToDetected ===");
        ClusterInfo info = ClusterInfo.initial("primary", null)
                .withDetected(ServerVersion.parse("7.17.9"), System.currentTimeMillis());

        assertNull(info.getConfiguredVersion());
        assertNotNull(info.getDetectedVersion());
        assertEquals(7, info.getEffectiveVersion().getMajor());
        assertFalse(info.isVersionMismatch());
    }
}

