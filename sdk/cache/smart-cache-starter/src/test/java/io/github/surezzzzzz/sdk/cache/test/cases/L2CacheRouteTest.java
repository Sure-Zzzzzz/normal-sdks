package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.exception.CacheRouteException;
import io.github.surezzzzzz.sdk.cache.exception.SmartCacheException;
import io.github.surezzzzzz.sdk.cache.layer.L2Cache;
import io.github.surezzzzzz.sdk.cache.serializer.JacksonSmartCacheSerializer;
import io.github.surezzzzzz.sdk.cache.serializer.PackageSmartCacheTypeValidator;
import io.github.surezzzzzz.sdk.cache.serializer.SmartCacheSerializer;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import io.github.surezzzzzz.sdk.redis.route.exception.SimpleRedisRouteException;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * L2 route 缓存测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
class L2CacheRouteTest {

    private SmartCacheProperties properties;
    private RedisRouteTemplate redisRouteTemplate;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private L2Cache l2Cache;

    @BeforeEach
    void setUp() {
        properties = new SmartCacheProperties();
        properties.getL2().setTtlRandomOffsetRatio(0D);
        properties.validate();
        redisRouteTemplate = mock(RedisRouteTemplate.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        SmartCacheSerializer serializer = new JacksonSmartCacheSerializer(new com.fasterxml.jackson.databind.ObjectMapper(),
                new PackageSmartCacheTypeValidator(Collections.singletonList("java.lang")));
        l2Cache = new L2Cache(properties, redisRouteTemplate, serializer);
    }

    @Test
    @DisplayName("测试 L2 get 通过 RedisRouteTemplate 读取字符串 payload")
    void shouldGetByRedisRouteTemplate() {
        String redisKey = buildRedisKey("user", "001");
        String payload = "{\"type\":\"java.lang.String\",\"data\":\"cache-value\"}";
        when(redisRouteTemplate.execute(eq(redisKey), any())).thenAnswer(invocation -> applyCallback(invocation.getArgument(1)));
        when(valueOperations.get(redisKey)).thenReturn(payload);

        String value = l2Cache.get("user", "001", String.class);

        log.info("L2 读取结果: {}", value);
        assertEquals("cache-value", value, "L2 应通过路由模板读取并反序列化字符串");
    }

    @Test
    @DisplayName("测试 L2 put 通过 RedisRouteTemplate 写入字符串 payload")
    void shouldPutByRedisRouteTemplate() {
        String redisKey = buildRedisKey("user", "001");
        when(redisRouteTemplate.execute(eq(redisKey), any())).thenAnswer(invocation -> applyCallback(invocation.getArgument(1)));

        l2Cache.put("user", "001", "cache-value", 60);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(redisKey), payloadCaptor.capture(), eq(60L), eq(java.util.concurrent.TimeUnit.SECONDS));
        log.info("L2 写入 payload: {}", payloadCaptor.getValue());
        assertTrue(payloadCaptor.getValue().contains("cache-value"), "写入 payload 应包含原始缓存值");
    }

    @Test
    @DisplayName("测试 L2 getAll 使用批量 route 执行")
    void shouldGetAllBySameRoute() {
        List<String> keys = Arrays.asList("001", "002");
        List<String> redisKeys = Arrays.asList(buildRedisKey("user", "001"), buildRedisKey("user", "002"));
        when(redisRouteTemplate.execute(eq(redisKeys), any())).thenAnswer(invocation -> applyCallback(invocation.getArgument(1)));
        when(valueOperations.multiGet(redisKeys)).thenReturn(Arrays.asList(
                "{\"type\":\"java.lang.String\",\"data\":\"v1\"}",
                "{\"type\":\"java.lang.String\",\"data\":\"v2\"}"
        ));

        Map<String, String> result = l2Cache.getAll("user", keys, String.class);

        log.info("L2 批量读取结果: {}", result);
        assertEquals("v1", result.get("001"), "001 对应值应正确");
        assertEquals("v2", result.get("002"), "002 对应值应正确");
    }

    @Test
    @DisplayName("测试 L2 putAll 使用物理 Redis Key 和带 TTL 的管道写入")
    void shouldPutAllBySameRouteWithTtlPipeline() {
        Map<String, Object> entries = new LinkedHashMap<>();
        entries.put("001", "v1");
        entries.put("002", null);
        entries.put("003", "v3");
        List<String> expectedRedisKeys = Arrays.asList(buildRedisKey("user", "001"), buildRedisKey("user", "003"));
        ArgumentCaptor<Collection<String>> redisKeysCaptor = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<SessionCallback> pipelineCaptor = ArgumentCaptor.forClass(SessionCallback.class);
        when(redisRouteTemplate.execute(any(Collection.class), any())).thenAnswer(invocation -> applyCallback(invocation.getArgument(1)));
        when(stringRedisTemplate.executePipelined(pipelineCaptor.capture())).thenAnswer(invocation -> {
            applyPipeline(invocation.getArgument(0));
            return Collections.emptyList();
        });

        l2Cache.putAll("user", entries);

        verify(redisRouteTemplate).execute(redisKeysCaptor.capture(), any());
        log.info("L2 批量写入路由物理 Key: {}", redisKeysCaptor.getValue());
        assertEquals(new HashSet<>(expectedRedisKeys), new HashSet<>(redisKeysCaptor.getValue()),
                "批量路由必须使用全部非空条目的最终物理 Redis Key");
        verify(stringRedisTemplate).executePipelined(pipelineCaptor.getValue());
        long expectedTtl = properties.getL2().getExpireSeconds();
        verify(valueOperations, times(2)).set(anyString(), anyString(), eq(expectedTtl), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq(buildRedisKey("user", "001")), anyString(), eq(expectedTtl), eq(TimeUnit.SECONDS));
        verify(valueOperations).set(eq(buildRedisKey("user", "003")), anyString(), eq(expectedTtl), eq(TimeUnit.SECONDS));
        verify(valueOperations, never()).set(eq(buildRedisKey("user", "002")), anyString(), eq(expectedTtl), eq(TimeUnit.SECONDS));
        log.info("L2 批量写入已通过选定模板执行带 TTL 的管道");
    }

    @Test
    @DisplayName("测试 L2 putAll 跨数据源路由异常映射为缓存路由异常")
    void shouldMapPutAllCrossDatasourceRouteException() {
        Map<String, Object> entries = Collections.<String, Object>singletonMap("001", "v1");
        SimpleRedisRouteException routeException = new SimpleRedisRouteException(
                io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode.REDIS_ROUTE_009, "批量 Key 跨数据源");
        when(redisRouteTemplate.execute(any(Collection.class), any())).thenThrow(routeException);

        CacheRouteException exception = assertThrows(CacheRouteException.class,
                () -> l2Cache.putAll("user", entries), "跨数据源路由异常必须转换为 CacheRouteException");

        log.info("L2 批量写入跨数据源异常码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.SMART_CACHE_ROUTE_CROSS_DATASOURCE, exception.getErrorCode(), "跨数据源错误码必须保持精确映射");
        assertSame(routeException, exception.getCause(), "缓存路由异常必须保留原始路由异常原因");
        verifyNoInteractions(stringRedisTemplate, valueOperations);
    }

    @Test
    @DisplayName("测试 L2 putAll 普通路由异常映射为 L2 操作异常")
    void shouldMapPutAllOrdinaryRouteExceptionToL2OperationFailed() {
        Map<String, Object> entries = Collections.<String, Object>singletonMap("001", "v1");
        SimpleRedisRouteException routeException = new SimpleRedisRouteException("REDIS_ROUTE_001", "路由失败");
        when(redisRouteTemplate.execute(any(Collection.class), any())).thenThrow(routeException);

        SmartCacheException exception = assertThrows(SmartCacheException.class,
                () -> l2Cache.putAll("user", entries), "普通路由异常必须转换为 SmartCacheException");

        log.info("L2 批量写入普通路由异常码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.SMART_CACHE_L2_OPERATION_FAILED, exception.getErrorCode(), "普通路由异常必须映射为 L2 操作失败");
        assertNotEquals(ErrorCode.SMART_CACHE_ROUTE_CROSS_DATASOURCE, exception.getErrorCode(), "普通路由异常不得映射为跨数据源错误");
        assertSame(routeException, exception.getCause(), "L2 操作异常必须保留原始路由异常原因");
        verifyNoInteractions(stringRedisTemplate, valueOperations);
    }

    @Test
    @DisplayName("测试 L2 putAll 管道异常映射为 L2 操作异常")
    void shouldMapPutAllPipelineExceptionToL2OperationFailed() {
        Map<String, Object> entries = Collections.<String, Object>singletonMap("001", "v1");
        RuntimeException pipelineException = new RuntimeException("管道执行失败");
        when(redisRouteTemplate.execute(any(Collection.class), any())).thenAnswer(invocation -> applyCallback(invocation.getArgument(1)));
        when(stringRedisTemplate.executePipelined(any(SessionCallback.class))).thenThrow(pipelineException);

        SmartCacheException exception = assertThrows(SmartCacheException.class,
                () -> l2Cache.putAll("user", entries), "管道异常必须转换为 SmartCacheException");

        log.info("L2 批量写入管道异常码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.SMART_CACHE_L2_OPERATION_FAILED, exception.getErrorCode(), "管道异常必须映射为 L2 操作失败");
        assertNotEquals(ErrorCode.SMART_CACHE_ROUTE_CROSS_DATASOURCE, exception.getErrorCode(), "管道异常不得映射为跨数据源错误");
        assertSame(pipelineException, exception.getCause(), "L2 操作异常必须保留原始管道异常原因");
        verify(stringRedisTemplate).executePipelined(any(SessionCallback.class));
        verifyNoInteractions(valueOperations);
    }

    @Test
    @DisplayName("测试 L2 getAll 跨数据源路由异常映射为缓存路由异常")
    void shouldMapGetAllCrossDatasourceRouteException() {
        List<String> keys = Arrays.asList("001", "002");
        SimpleRedisRouteException routeException = new SimpleRedisRouteException(
                io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode.REDIS_ROUTE_009, "批量 Key 跨数据源");
        when(redisRouteTemplate.execute(any(Collection.class), any())).thenThrow(routeException);

        CacheRouteException exception = assertThrows(CacheRouteException.class,
                () -> l2Cache.getAll("user", keys, String.class), "跨数据源路由异常必须转换为 CacheRouteException");

        log.info("L2 批量读取跨数据源异常码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.SMART_CACHE_ROUTE_CROSS_DATASOURCE, exception.getErrorCode(), "跨数据源错误码必须保持精确映射");
        assertSame(routeException, exception.getCause(), "缓存路由异常必须保留原始路由异常原因");
        verifyNoInteractions(stringRedisTemplate, valueOperations);
    }

    @Test
    @DisplayName("测试 L2 getAll 普通路由异常映射为 L2 操作异常")
    void shouldMapGetAllOrdinaryRouteExceptionToL2OperationFailed() {
        List<String> keys = Arrays.asList("001", "002");
        SimpleRedisRouteException routeException = new SimpleRedisRouteException("REDIS_ROUTE_001", "路由失败");
        when(redisRouteTemplate.execute(any(Collection.class), any())).thenThrow(routeException);

        SmartCacheException exception = assertThrows(SmartCacheException.class,
                () -> l2Cache.getAll("user", keys, String.class), "普通路由异常必须转换为 SmartCacheException");

        log.info("L2 批量读取普通路由异常码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.SMART_CACHE_L2_OPERATION_FAILED, exception.getErrorCode(), "普通路由异常必须映射为 L2 操作失败");
        assertNotEquals(ErrorCode.SMART_CACHE_ROUTE_CROSS_DATASOURCE, exception.getErrorCode(), "普通路由异常不得映射为跨数据源错误");
        assertSame(routeException, exception.getCause(), "L2 操作异常必须保留原始路由异常原因");
        verifyNoInteractions(stringRedisTemplate, valueOperations);
    }

    @Test
    @DisplayName("测试 L2 getAll Redis 读取异常映射为 L2 操作异常")
    void shouldMapGetAllRedisExceptionToL2OperationFailed() {
        List<String> keys = Arrays.asList("001", "002");
        RuntimeException redisException = new RuntimeException("Redis 读取失败");
        when(redisRouteTemplate.execute(any(Collection.class), any())).thenAnswer(invocation -> applyCallback(invocation.getArgument(1)));
        when(valueOperations.multiGet(any(Collection.class))).thenThrow(redisException);

        SmartCacheException exception = assertThrows(SmartCacheException.class,
                () -> l2Cache.getAll("user", keys, String.class), "Redis 读取异常必须转换为 SmartCacheException");

        log.info("L2 批量读取 Redis 异常码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.SMART_CACHE_L2_OPERATION_FAILED, exception.getErrorCode(), "Redis 读取异常必须映射为 L2 操作失败");
        assertNotEquals(ErrorCode.SMART_CACHE_ROUTE_CROSS_DATASOURCE, exception.getErrorCode(), "Redis 读取异常不得映射为跨数据源错误");
        assertSame(redisException, exception.getCause(), "L2 操作异常必须保留原始 Redis 异常原因");
    }

    private String buildRedisKey(String cacheName, String key) {
        return KeyHelper.buildCacheKey(properties.getL2().getKeyFormat(), properties.getKeyPrefix(), cacheName,
                properties.getMe(), key);
    }

    @SuppressWarnings("unchecked")
    private Object applyCallback(Object callback) {
        return ((Function<StringRedisTemplate, Object>) callback).apply(stringRedisTemplate);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyPipeline(SessionCallback callback) {
        callback.execute(stringRedisTemplate);
    }
}
