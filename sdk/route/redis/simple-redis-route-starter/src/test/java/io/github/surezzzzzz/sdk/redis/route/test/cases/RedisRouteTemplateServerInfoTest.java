package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerVersion;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.resolver.RedisRouteResolver;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RedisRouteTemplate 的 serverInfo 系列 API 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisRouteTemplateServerInfoTest {

    @Test
    public void testServerInfoReturnsDefaultDatasourceInfo() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisRouteResolver resolver = mock(RedisRouteResolver.class);
        RedisServerInfo expected = knownInfo("default", "7.2.6");
        when(registry.getDefaultDatasourceKey()).thenReturn("default");
        when(registry.getServerInfo("default")).thenReturn(expected);

        RedisRouteTemplate template = new RedisRouteTemplate(registry, resolver);
        RedisServerInfo info = template.serverInfo();
        log.info("serverInfo() datasourceKey={}, version={}",
                info.getDatasourceKey(), info.getVersion().getRaw());
        assertSame(expected, info, "应返回默认 datasource 的 serverInfo");
        verify(registry).getDefaultDatasourceKey();
        verify(registry).getServerInfo("default");
    }

    @Test
    public void testServerInfoByDatasourceKey() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisRouteResolver resolver = mock(RedisRouteResolver.class);
        RedisServerInfo expected = knownInfo("cache", "5.0.14");
        Set<String> keys = new HashSet<>();
        keys.add("default");
        keys.add("cache");
        when(registry.getDatasourceKeys()).thenReturn(keys);
        when(registry.getServerInfo("cache")).thenReturn(expected);

        RedisRouteTemplate template = new RedisRouteTemplate(registry, resolver);
        RedisServerInfo info = template.serverInfo("cache");
        log.info("serverInfo('cache') datasourceKey={}, version={}",
                info.getDatasourceKey(), info.getVersion().getRaw());
        assertSame(expected, info, "应返回 cache datasource 的 serverInfo");
        assertEquals("cache", info.getDatasourceKey(), "datasourceKey 应为 cache");
    }

    @Test
    public void testServerInfoByDatasourceKeyThrowsWhenBlank() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisRouteResolver resolver = mock(RedisRouteResolver.class);
        when(registry.getDatasourceKeys()).thenReturn(Collections.singleton("default"));

        RedisRouteTemplate template = new RedisRouteTemplate(registry, resolver);
        RouteException ex = assertThrows(RouteException.class,
                () -> template.serverInfo("  "),
                "空白 datasourceKey 应抛 RouteException");
        log.info("errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.REDIS_ROUTE_003, ex.getErrorCode(), "errorCode 应为 REDIS_ROUTE_003");
    }

    @Test
    public void testServerInfoByKeyResolvesThroughRouter() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisRouteResolver resolver = mock(RedisRouteResolver.class);
        RedisServerInfo expected = knownInfo("cache", "7.2.6");
        when(resolver.resolveDataSource("cache:user:001")).thenReturn("cache");
        when(registry.getServerInfo("cache")).thenReturn(expected);

        RedisRouteTemplate template = new RedisRouteTemplate(registry, resolver);
        RedisServerInfo info = template.serverInfoByKey("cache:user:001");
        log.info("serverInfoByKey('cache:user:001') datasourceKey={}, version={}",
                info.getDatasourceKey(), info.getVersion().getRaw());
        assertSame(expected, info, "应返回路由解析后 datasource 的 serverInfo");
        verify(resolver).resolveDataSource("cache:user:001");
        verify(registry).getServerInfo("cache");
    }

    @Test
    public void testServerInfoByKeyThrowsWhenKeyBlank() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisRouteResolver resolver = mock(RedisRouteResolver.class);

        RedisRouteTemplate template = new RedisRouteTemplate(registry, resolver);
        RouteException ex = assertThrows(RouteException.class,
                () -> template.serverInfoByKey(""),
                "空白 redisKey 应抛 RouteException");
        assertEquals(ErrorCode.REDIS_ROUTE_008, ex.getErrorCode(), "errorCode 应为 REDIS_ROUTE_008");
    }

    @Test
    public void testServerInfoByKeyThrowsWhenKeyNull() {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        RedisRouteResolver resolver = mock(RedisRouteResolver.class);

        RedisRouteTemplate template = new RedisRouteTemplate(registry, resolver);
        RouteException ex = assertThrows(RouteException.class,
                () -> template.serverInfoByKey(null),
                "null redisKey 应抛 RouteException");
        assertEquals(ErrorCode.REDIS_ROUTE_008, ex.getErrorCode(), "errorCode 应为 REDIS_ROUTE_008");
    }

    private RedisServerInfo knownInfo(String datasourceKey, String versionStr) {
        return RedisServerInfo.builder()
                .datasourceKey(datasourceKey)
                .known(true)
                .version(RedisServerVersion.parse(versionStr))
                .redisMode("standalone")
                .build();
    }
}
