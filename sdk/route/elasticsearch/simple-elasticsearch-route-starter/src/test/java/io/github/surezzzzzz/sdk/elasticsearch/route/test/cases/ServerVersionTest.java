package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.exception.VersionException;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.ServerVersion;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServerVersion 单元测试
 *
 * @author Sure
 * @since 1.0.3
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class ServerVersionTest {

    @Test
    public void testParseFullVersion() {
        log.info("=== testParseFullVersion ===");
        ServerVersion v = ServerVersion.parse("6.2.2");
        assertEquals("6.2.2", v.getRaw());
        assertEquals(6, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(2, v.getPatch());
    }

    @Test
    public void testParseWithSuffix() {
        log.info("=== testParseWithSuffix ===");
        ServerVersion v = ServerVersion.parse("7.17.9-SNAPSHOT");
        assertEquals("7.17.9-SNAPSHOT", v.getRaw());
        assertEquals(7, v.getMajor());
        assertEquals(17, v.getMinor());
        assertEquals(9, v.getPatch());
    }

    @Test
    public void testTryParseEmpty() {
        log.info("=== testTryParseEmpty ===");
        assertNull(ServerVersion.tryParse(null));
        assertNull(ServerVersion.tryParse(""));
        assertNull(ServerVersion.tryParse("   "));
    }

    @Test
    public void testInvalidFormat() {
        log.info("=== testInvalidFormat ===");
        assertThrows(VersionException.class, () -> ServerVersion.parse("x.y.z"));
    }

    @Test
    public void testEquals() {
        log.info("=== testEquals ===");
        assertEquals(ServerVersion.parse("6.2.2"), ServerVersion.parse("6.2.2"));
        assertNotEquals(ServerVersion.parse("6.2.2"), ServerVersion.parse("6.2.3"));
    }
}

