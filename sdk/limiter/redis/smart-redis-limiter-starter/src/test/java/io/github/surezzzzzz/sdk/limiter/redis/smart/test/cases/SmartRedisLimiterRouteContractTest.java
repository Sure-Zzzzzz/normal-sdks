package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterAutoConfiguration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterConfigurationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterRedisException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.RouteSmartRedisLimiterRedisExecutor;
import io.github.surezzzzzz.sdk.limiter.redis.smart.executor.SmartRedisLimiterRedisExecutionResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterKeyHelper;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SmartRedisLimiter Redis Route 契约测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterRouteContractTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testMissingRedisRouteTemplateFailsFast() {
        ObjectProvider<RedisRouteTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        SmartRedisLimiterConfigurationException exception = assertThrows(
                SmartRedisLimiterConfigurationException.class,
                () -> new SmartRedisLimiterAutoConfiguration.RedisRouteExecutorConfiguration()
                        .smartRedisLimiterRedisExecutor(provider, new SmartRedisLimiterProperties()));

        log.info("缺少 RedisRouteTemplate 时错误码: {}", exception.getErrorCode());
        assertEquals(ErrorCode.CONFIG_REDIS_ROUTE_TEMPLATE_MISSING, exception.getErrorCode(),
                "错误码应精确匹配 RedisRouteTemplate 缺失");
        assertTrue(exception.getMessage().contains("RedisRouteTemplate"),
                "错误消息应说明 RedisRouteTemplate 缺失");
    }

    @Test
    public void testKnownFalseStillResolvesRoute() {
        RedisRouteTemplate routeTemplate = mock(RedisRouteTemplate.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        RedisServerInfo serverInfo = RedisServerInfo.builder()
                .datasourceKey("default")
                .known(false)
                .redisMode(null)
                .errorMessage("探测未完成")
                .build();
        when(routeTemplate.serverInfoByKey("smart-limiter:test:key")).thenReturn(serverInfo);
        when(routeTemplate.execute(eq("smart-limiter:test:key"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<StringRedisTemplate, String> callback = invocation.getArgument(1);
            return callback.apply(stringRedisTemplate);
        });

        RouteSmartRedisLimiterRedisExecutor executor = new RouteSmartRedisLimiterRedisExecutor(routeTemplate);
        SmartRedisLimiterRedisExecutionResult<String> result = executor.execute(
                "smart-limiter:test:key", template -> "ok");

        log.info("known=false 路由结果: datasource={}, mode={}, resolved={}",
                result.getDatasourceKey(), result.getRedisMode(), result.isRouteResolved());
        assertEquals("ok", result.getValue(), "Redis 回调结果应透传");
        assertEquals("default", result.getDatasourceKey(), "datasourceKey 应正常记录");
        assertTrue(result.isRouteRequired(), "2.0.0 必须要求 Redis Route");
        assertTrue(result.isRouteResolved(), "known=false 不应被误判为路由失败");
        assertEquals(SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN, result.getRedisMode(),
                "探测未知时 Redis 模式应为 unknown");
    }

    @Test
    public void testRouteExceptionClassifiedAsRouteError() {
        RedisRouteTemplate routeTemplate = mock(RedisRouteTemplate.class);
        when(routeTemplate.serverInfoByKey("smart-limiter:test:key"))
                .thenThrow(new RouteException("ROUTE_TEST", "route unavailable"));
        when(routeTemplate.execute(eq("smart-limiter:test:key"), any()))
                .thenThrow(new RouteException("ROUTE_TEST", "route unavailable"));

        RouteSmartRedisLimiterRedisExecutor executor = new RouteSmartRedisLimiterRedisExecutor(routeTemplate);
        SmartRedisLimiterRedisException exception = assertThrows(
                SmartRedisLimiterRedisException.class,
                () -> executor.execute("smart-limiter:test:key", template -> "unused"));

        log.info("路由异常降级原因: {}", exception.getFallbackReason());
        assertEquals(SmartRedisLimiterConstant.FALLBACK_REASON_ROUTE_ERROR,
                exception.getFallbackReason(), "RouteException 必须分类为 route_error");
        assertFalse(exception.isRouteResolved(), "路由异常时 routeResolved 应为 false");
        assertNotNull(exception.getCause(), "应保留原始 RouteException");
    }

    @Test
    public void testRedisCallbackExceptionClassifiedAsRedisError() {
        RedisRouteTemplate routeTemplate = mock(RedisRouteTemplate.class);
        when(routeTemplate.serverInfoByKey("smart-limiter:test:key")).thenReturn(
                RedisServerInfo.builder()
                        .datasourceKey("default")
                        .known(true)
                        .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                        .build());
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(routeTemplate.execute(eq("smart-limiter:test:key"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<StringRedisTemplate, String> callback = invocation.getArgument(1);
            return callback.apply(stringRedisTemplate);
        });

        RouteSmartRedisLimiterRedisExecutor executor = new RouteSmartRedisLimiterRedisExecutor(routeTemplate);
        SmartRedisLimiterRedisException exception = assertThrows(
                SmartRedisLimiterRedisException.class,
                () -> executor.execute("smart-limiter:test:key", template -> {
                    throw new org.springframework.data.redis.RedisConnectionFailureException("redis unavailable");
                }));

        log.info("Redis 异常降级原因: {}, datasource={}",
                exception.getFallbackReason(), exception.getDatasourceKey());
        assertEquals(SmartRedisLimiterConstant.FALLBACK_REASON_REDIS_ERROR,
                exception.getFallbackReason(), "Redis 执行异常必须分类为 redis_error");
        assertTrue(exception.isRouteResolved(), "已获得 datasourceKey 时 routeResolved 应为 true");
        assertEquals("default", exception.getDatasourceKey(), "异常中应保留 datasourceKey");
    }

    @Test
    public void testRouteExceptionInsideCallbackClassifiedAsRedisError() {
        RedisRouteTemplate routeTemplate = mock(RedisRouteTemplate.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(routeTemplate.serverInfoByKey("smart-limiter:test:key")).thenReturn(
                RedisServerInfo.builder()
                        .datasourceKey("default")
                        .known(true)
                        .redisMode(SmartRedisLimiterConstant.REDIS_MODE_STANDALONE)
                        .build());
        when(routeTemplate.execute(eq("smart-limiter:test:key"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<StringRedisTemplate, String> callback = invocation.getArgument(1);
            return callback.apply(stringRedisTemplate);
        });

        RouteSmartRedisLimiterRedisExecutor executor = new RouteSmartRedisLimiterRedisExecutor(routeTemplate);
        SmartRedisLimiterRedisException exception = assertThrows(
                SmartRedisLimiterRedisException.class,
                () -> executor.execute("smart-limiter:test:key", template -> {
                    throw new RouteException("REDIS_CALLBACK_TEST", "callback failed");
                }));

        log.info("callback 内 RouteException 降级原因: {}", exception.getFallbackReason());
        assertEquals(SmartRedisLimiterConstant.FALLBACK_REASON_REDIS_ERROR,
                exception.getFallbackReason(), "callback 内异常必须按 redis_error 分类");
        assertTrue(exception.getCause() instanceof RouteException, "应保留 callback 原始异常");
    }

    @Test
    public void testServerInfoFailureDoesNotBlockExecution() {
        RedisRouteTemplate routeTemplate = mock(RedisRouteTemplate.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(routeTemplate.serverInfoByKey("smart-limiter:test:key"))
                .thenThrow(new RouteException("SERVER_INFO_TEST", "server info unavailable"));
        when(routeTemplate.execute(eq("smart-limiter:test:key"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<StringRedisTemplate, String> callback = invocation.getArgument(1);
            return callback.apply(stringRedisTemplate);
        });

        RouteSmartRedisLimiterRedisExecutor executor = new RouteSmartRedisLimiterRedisExecutor(routeTemplate);
        SmartRedisLimiterRedisExecutionResult<String> result = executor.execute(
                "smart-limiter:test:key", template -> "ok");

        log.info("serverInfo 失败但 execute 成功: value={}, datasource={}, resolved={}",
                result.getValue(), result.getDatasourceKey(), result.isRouteResolved());
        assertEquals("ok", result.getValue(), "serverInfo 失败不应阻断 Redis 执行");
        assertTrue(result.isRouteRequired(), "2.0.0 必须要求 Redis Route");
        assertFalse(result.isRouteResolved(), "serverInfo 失败时 routeResolved 应为 false");
        assertNull(result.getDatasourceKey(), "serverInfo 失败时 datasourceKey 应为空");
        assertEquals(SmartRedisLimiterConstant.REDIS_MODE_UNKNOWN, result.getRedisMode(),
                "serverInfo 失败时 redisMode 应为 unknown");
    }

    @Test
    public void testHashTagNormalizesBraces() {
        String baseKey = SmartRedisLimiterKeyHelper.buildBaseKey("test", "custom:{client}:value");
        String windowKey = SmartRedisLimiterKeyHelper.buildWindowKey(baseKey, 10L, "s", true);

        log.info("规范化后的窗口 Key: {}", windowKey);
        assertFalse(baseKey.contains("{"), "基础 Key 不应保留左大括号");
        assertFalse(baseKey.contains("}"), "基础 Key 不应保留右大括号");
        assertTrue(windowKey.startsWith("smart-limiter:{"), "启用 Hash Tag 时仍应保留 SDK 前缀");
        assertTrue(windowKey.contains("custom:_client_:value"), "调用方大括号应规范化为下划线");
        assertTrue(windowKey.endsWith(":10s"), "窗口 Key 应保留窗口后缀");
    }
}
