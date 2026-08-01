package io.github.surezzzzzz.sdk.mysql.route.test.cases;

import io.github.surezzzzzz.sdk.mysql.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.mysql.route.matcher.MySqlRoutePatternMatcher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL Route 规则匹配器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class MySqlRoutePatternMatcherTest {

    private final MySqlRoutePatternMatcher matcher = new MySqlRoutePatternMatcher();

    @BeforeEach
    public void logTestStart() {
        log.info("开始执行 MySQL Route 规则匹配测试");
    }

    @Test
    public void shouldMatchExactPrefixAndSuffix() {
        assertTrue(matcher.matches("test_order", RouteMatchType.EXACT, "test_order", null));
        assertFalse(matcher.matches("test_user", RouteMatchType.EXACT, "test_order", null));
        assertTrue(matcher.matches("test_order_detail", RouteMatchType.PREFIX, "test_order", null));
        assertTrue(matcher.matches("audit_test", RouteMatchType.SUFFIX, "test", null));
    }

    @Test
    public void shouldMatchWildcardWithoutTreatingLiteralCharactersAsRegex() {
        Pattern pattern = matcher.compile(RouteMatchType.WILDCARD, "test_*.?order");
        assertTrue(matcher.matches("test_extra.xorder", RouteMatchType.WILDCARD, "test_*.?order", pattern));
        assertFalse(matcher.matches("test_extraXorder", RouteMatchType.WILDCARD, "test_*.?order", pattern));
        assertEquals("test_.*\\..order", matcher.toWildcardRegex("test_*.?order"));
    }

    @Test
    public void shouldMatchRegex() {
        Pattern pattern = matcher.compile(RouteMatchType.REGEX, "^test_[a-z]+_[0-9]+$");
        assertTrue(matcher.matches("test_order_001", RouteMatchType.REGEX, "^test_[a-z]+_[0-9]+$", pattern));
        assertFalse(matcher.matches("test_order_extra", RouteMatchType.REGEX, "^test_[a-z]+_[0-9]+$", pattern));
    }
}
