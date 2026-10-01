package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.support.RedisServerInfoProbeHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RedisServerInfoProbeHelper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisServerInfoProbeHelperTest {

    @Test
    public void testProbeDisabledReturnsKnownFalseWithDisabledMessage() {
        RedisConnectionFactory factory = mockConnectionFactory();
        RedisServerInfo info = RedisServerInfoProbeHelper.probe("ds1", factory, false);
        log.info("known={}, errorMessage={}", info.isKnown(), info.getErrorMessage());
        assertNotNull(info, "不应返回 null");
        assertFalse(info.isKnown(), "probe 禁用时 known 应为 false");
        assertTrue(info.getErrorMessage().contains("probe.server-info=false"),
                "errorMessage 应说明禁用原因");
        assertNull(info.getVersion(), "禁用时 version 应为 null");
        assertNull(info.getRedisMode(), "禁用时 redisMode 应为 null");
        verifyNoInteractions(factory);
    }

    @Test
    public void testProbeParsesVersionAndModeFromInfoOutput() {
        RedisConnectionFactory factory = mockConnectionFactoryReturning(infoProps("7.2.6", "standalone"));
        RedisServerInfo info = RedisServerInfoProbeHelper.probe("ds7", factory, true);
        log.info("known={}, version={}, mode={}, datasourceKey={}",
                info.isKnown(), info.getVersion().getRaw(), info.getRedisMode(), info.getDatasourceKey());
        assertTrue(info.isKnown(), "正常 INFO 应探测成功 known=true");
        assertEquals("7.2.6", info.getVersion().getRaw(), "版本应为 7.2.6");
        assertEquals(7, info.getVersion().getMajor(), "major 应为 7");
        assertEquals(2, info.getVersion().getMinor(), "minor 应为 2");
        assertEquals(6, info.getVersion().getPatch(), "patch 应为 6");
        assertEquals("standalone", info.getRedisMode(), "mode 应为 standalone");
        assertEquals("ds7", info.getDatasourceKey(), "datasourceKey 应为 ds7");
        assertNull(info.getErrorMessage(), "成功时 errorMessage 应为 null");
    }

    @Test
    public void testProbeParsesClusterMode() {
        RedisConnectionFactory factory = mockConnectionFactoryReturning(infoProps("5.0.14", "cluster"));
        RedisServerInfo info = RedisServerInfoProbeHelper.probe("ds5c", factory, true);
        log.info("version={}, mode={}", info.getVersion().getRaw(), info.getRedisMode());
        assertTrue(info.isKnown(), "cluster 探测应成功");
        assertEquals("5.0.14", info.getVersion().getRaw(), "版本应为 5.0.14");
        assertEquals("cluster", info.getRedisMode(), "mode 应为 cluster");
    }

    @Test
    public void testProbeParsesClusterPrefixedPropertiesInfoOutput() {
        Properties props = new Properties();
        props.setProperty("127.0.0.1:17010.redis_version", "5.0.14");
        props.setProperty("127.0.0.1:17010.redis_mode", "cluster");
        RedisConnectionFactory factory = mockConnectionFactoryReturning(props);
        RedisServerInfo info = RedisServerInfoProbeHelper.probe("ds5c", factory, true);
        log.info("cluster Properties 前缀输出: known={}, version={}, mode={}",
                info.isKnown(), info.getVersion().getRaw(), info.getRedisMode());
        assertTrue(info.isKnown(), "cluster 前缀 Properties INFO 应能解析");
        assertEquals("5.0.14", info.getVersion().getRaw(), "版本应为 5.0.14");
        assertEquals("cluster", info.getRedisMode(), "mode 应为 cluster");
    }

    @Test
    public void testProbeReturnsKnownFalseWhenInfoEmpty() {
        RedisConnectionFactory factory = mockConnectionFactoryReturning("");
        RedisServerInfo info = RedisServerInfoProbeHelper.probe("ds1", factory, true);
        log.info("known={}, errorMessage={}", info.isKnown(), info.getErrorMessage());
        assertFalse(info.isKnown(), "空 INFO 应 known=false");
        assertNull(info.getVersion(), "空 INFO 时 version 应为 null");
        assertNotNull(info.getErrorMessage(), "空 INFO 应有 errorMessage");
    }

    @Test
    public void testProbeReturnsKnownFalseWhenVersionLineMissing() {
        // 只有 redis_mode，没有 redis_version
        Properties props = new Properties();
        props.setProperty("redis_mode", "standalone");
        RedisConnectionFactory factory = mockConnectionFactoryReturning(props);
        RedisServerInfo info = RedisServerInfoProbeHelper.probe("ds1", factory, true);
        log.info("缺 redis_version 时 known={}, errorMessage={}", info.isKnown(), info.getErrorMessage());
        assertFalse(info.isKnown(), "缺 redis_version 应 known=false");
        assertNull(info.getVersion(), "缺版本时 version 应为 null");
    }

    @Test
    public void testProbeReturnsKnownFalseWhenVersionInvalid() {
        RedisConnectionFactory factory = mockConnectionFactoryReturning(infoProps("not.a.version", "standalone"));
        RedisServerInfo info = RedisServerInfoProbeHelper.probe("ds1", factory, true);
        log.info("非法版本号时 known={}, errorMessage={}", info.isKnown(), info.getErrorMessage());
        assertFalse(info.isKnown(), "非法版本号应 known=false");
        assertNull(info.getVersion(), "非法版本号时 version 应为 null");
    }

    @Test
    public void testProbeReturnsKnownFalseWhenConnectionThrows() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RuntimeException("OPAQUE-ERROR-CONTENT"));
        RedisServerInfo info = RedisServerInfoProbeHelper.probe("ds1", factory, true);
        log.info("连接异常时 known={}, errorMessage={}", info.isKnown(), info.getErrorMessage());
        assertFalse(info.isKnown(), "连接异常应 known=false");
        assertNotNull(info.getErrorMessage(), "应有 errorMessage");
        assertFalse(info.getErrorMessage().contains("OPAQUE-ERROR-CONTENT"),
                "errorMessage 不得包含原始异常消息");
    }

    @Test
    public void testProbeDoesNotLeakOpaqueContentOnFailure() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RuntimeException("OPAQUE-DETAIL-CONTENT"));
        RedisServerInfo info = RedisServerInfoProbeHelper.probe("ds1", factory, true);
        log.info("errorMessage=[{}]", info.getErrorMessage());
        assertFalse(info.getErrorMessage().contains("OPAQUE-DETAIL-CONTENT"),
                "errorMessage 不得包含原始异常详情");
    }

    @Test
    public void testProbeHandlesMultilinePropertiesInfoOutput() {
        // info("server") 返回 Properties，包含多条记录
        Properties props = new Properties();
        props.setProperty("redis_version", "6.2.0");
        props.setProperty("redis_mode", "standalone");
        props.setProperty("os", "Linux");
        props.setProperty("uptime_in_seconds", "12345");
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.serverCommands()).thenReturn(connection);
        when(connection.info("server")).thenReturn(props);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(connection);

        RedisServerInfo info = RedisServerInfoProbeHelper.probe("ds6", factory, true);
        log.info("多行 Properties INFO 输出: known={}, version={}, mode={}",
                info.isKnown(), info.getVersion().getRaw(), info.getRedisMode());
        assertTrue(info.isKnown(), "多行 Properties INFO 应能解析");
        assertEquals("6.2.0", info.getVersion().getRaw(), "版本应为 6.2.0");
        assertEquals("standalone", info.getRedisMode(), "mode 应为 standalone");
    }

    private RedisConnectionFactory mockConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    private RedisConnectionFactory mockConnectionFactoryReturning(Properties props) {
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.serverCommands()).thenReturn(connection);
        when(connection.info("server")).thenReturn(props);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenReturn(connection);
        return factory;
    }

    private RedisConnectionFactory mockConnectionFactoryReturning(String infoText) {
        // info("server") 返回类型为 Properties，这里构造一个只含 redis_version/redis_mode 的 Properties
        Properties props = new Properties();
        String[] lines = infoText.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon > 0) {
                props.setProperty(trimmed.substring(0, colon), trimmed.substring(colon + 1));
            }
        }
        return mockConnectionFactoryReturning(props);
    }

    private Properties infoProps(String version, String mode) {
        Properties props = new Properties();
        props.setProperty("redis_version", version);
        props.setProperty("redis_mode", mode);
        return props;
    }
}
