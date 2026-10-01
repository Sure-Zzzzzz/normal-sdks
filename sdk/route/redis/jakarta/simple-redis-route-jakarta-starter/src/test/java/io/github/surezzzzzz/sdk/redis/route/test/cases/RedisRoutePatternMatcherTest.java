package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.redis.route.matcher.RedisRoutePatternMatcher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 路由匹配器测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class RedisRoutePatternMatcherTest {

    private final RedisRoutePatternMatcher matcher = new RedisRoutePatternMatcher();

    @Test
    public void testExactPrefixSuffix() {
        assertTrue(matcher.matches("cache:user:001", RouteMatchType.EXACT, "cache:user:001", null));
        assertFalse(matcher.matches("cache:user:002", RouteMatchType.EXACT, "cache:user:001", null));

        assertTrue(matcher.matches("cache:user:001", RouteMatchType.PREFIX, "cache:", null));
        assertFalse(matcher.matches("lock:user:001", RouteMatchType.PREFIX, "cache:", null));

        assertTrue(matcher.matches("order:001:lock", RouteMatchType.SUFFIX, ":lock", null));
        assertFalse(matcher.matches("order:001:cache", RouteMatchType.SUFFIX, ":lock", null));
    }

    @Test
    public void testWildcardEscapesRegexCharacters() {
        Pattern pattern = matcher.compile(RouteMatchType.WILDCARD, "cache:user.?*");
        assertTrue(matcher.matches("cache:user.a001", RouteMatchType.WILDCARD, "cache:user.?*", pattern));
        assertFalse(matcher.matches("cache:userxa001", RouteMatchType.WILDCARD, "cache:user.?*", pattern));
        assertEquals("cache:user\\...*", matcher.toWildcardRegex("cache:user.?*"));
    }

    @Test
    public void testRegex() {
        Pattern pattern = matcher.compile(RouteMatchType.REGEX, "^rate:[a-z]+:\\d+$");
        assertTrue(matcher.matches("rate:api:001", RouteMatchType.REGEX, "^rate:[a-z]+:\\d+$", pattern));
        assertFalse(matcher.matches("rate:api:abc", RouteMatchType.REGEX, "^rate:[a-z]+:\\d+$", pattern));
    }
}
