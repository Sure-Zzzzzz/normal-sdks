package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.exception.RouteException;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerInfo;
import io.github.surezzzzzz.sdk.redis.route.model.RedisServerVersion;
import io.github.surezzzzzz.sdk.redis.route.support.RedisCommandCompatibilityHelper;
import io.github.surezzzzzz.sdk.redis.route.support.RedisCommandCapabilityHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RedisCommandCompatibilityHelper 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisCommandCompatibilityHelperTest {

    @Test
    public void testDeletePreferUnlinkUsesUnlinkWhenSupported() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisServerInfo info = knownInfo("7.2.6");
        when(template.unlink("k1")).thenReturn(true);

        RedisCommandCompatibilityHelper.deletePreferUnlink(template, info, "k1");

        log.info("Redis 7.x 应调用 unlink");
        verify(template, times(1)).unlink("k1");
        verify(template, never()).delete("k1");
    }

    @Test
    public void testDeletePreferUnlinkFallsBackToDeleteWhenUnlinkUnsupported() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisServerInfo info = knownInfo("3.2.12");
        when(template.delete("k1")).thenReturn(true);

        RedisCommandCompatibilityHelper.deletePreferUnlink(template, info, "k1");

        log.info("Redis 3.x 不支持 unlink，应降级到 delete");
        verify(template, times(1)).delete("k1");
        verify(template, never()).unlink(anyString());
    }

    @Test
    public void testDeletePreferUnlinkFallsBackToDeleteWhenInfoUnknown() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisServerInfo info = unknownInfo();
        when(template.delete("k1")).thenReturn(true);

        RedisCommandCompatibilityHelper.deletePreferUnlink(template, info, "k1");

        log.info("unknown info 保守降级到 delete");
        verify(template, times(1)).delete("k1");
        verify(template, never()).unlink(anyString());
    }

    @Test
    public void testRequireCapabilityPassesWhenSupported() {
        RedisServerInfo info = knownInfo("7.2.6");
        log.info("满足能力时 requireCapability 不应抛异常");
        assertDoesNotThrow(() ->
                RedisCommandCompatibilityHelper.requireCapability(info, RedisCommandCapabilityHelper.CAPABILITY_UNLINK));
    }

    @Test
    public void testRequireCapabilityThrowsWhenNotSupported() {
        RedisServerInfo info = knownInfo("3.2.12");
        RouteException ex = assertThrows(RouteException.class,
                () -> RedisCommandCompatibilityHelper.requireCapability(info, RedisCommandCapabilityHelper.CAPABILITY_UNLINK),
                "不满足能力时应抛 RouteException");
        log.info("errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.REDIS_ROUTE_013, ex.getErrorCode(), "errorCode 应为 REDIS_ROUTE_013");
        assertTrue(ex.getMessage().contains(info.getDatasourceKey()), "消息应包含 datasource key");
        assertTrue(ex.getMessage().contains(RedisCommandCapabilityHelper.CAPABILITY_UNLINK), "消息应包含能力名");
        assertTrue(ex.getMessage().contains("3.2.12"), "消息应包含 Server version");
    }

    @Test
    public void testRequireCapabilityThrowsWhenInfoUnknown() {
        RedisServerInfo info = unknownInfo();
        RouteException ex = assertThrows(RouteException.class,
                () -> RedisCommandCompatibilityHelper.requireCapability(info, RedisCommandCapabilityHelper.CAPABILITY_UNLINK),
                "unknown info 时应抛 RouteException");
        log.info("errorCode={}, message={}", ex.getErrorCode(), ex.getMessage());
        assertEquals(ErrorCode.REDIS_ROUTE_013, ex.getErrorCode(), "errorCode 应为 REDIS_ROUTE_013");
        assertTrue(ex.getMessage().contains("unknown"), "unknown 时 versionStr 应为 unknown");
    }

    @Test
    public void testRequireCapabilityMessageDoesNotLeakPasswordOrUsername() {
        RedisServerInfo info = unknownInfo();
        RouteException ex = assertThrows(RouteException.class,
                () -> RedisCommandCompatibilityHelper.requireCapability(info, RedisCommandCapabilityHelper.CAPABILITY_GETEX));
        log.info("errorMessage=[{}]", ex.getMessage());
        String lower = ex.getMessage().toLowerCase();
        assertFalse(lower.contains("pass" + "word"), "消息不得包含认证敏感字段");
        assertFalse(lower.contains("user" + "name"), "消息不得包含认证身份字段");
    }

    private RedisServerInfo knownInfo(String versionStr) {
        return RedisServerInfo.builder()
                .datasourceKey("test-ds")
                .known(true)
                .version(RedisServerVersion.parse(versionStr))
                .redisMode("standalone")
                .build();
    }

    private RedisServerInfo unknownInfo() {
        return RedisServerInfo.builder()
                .datasourceKey("test-ds")
                .known(false)
                .errorMessage("探测失败")
                .build();
    }
}
