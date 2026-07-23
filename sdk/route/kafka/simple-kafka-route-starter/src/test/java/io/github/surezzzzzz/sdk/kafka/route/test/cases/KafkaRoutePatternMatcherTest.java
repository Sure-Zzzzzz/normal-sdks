package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.constant.RouteMatchType;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kafka 路由模式匹配器测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaRoutePatternMatcherTest {

    private final KafkaRoutePatternMatcher matcher = new KafkaRoutePatternMatcher();

    @Test
    public void testExactPrefixSuffixMatch() {
        assertTrue(matcher.matches("mock.topic", RouteMatchType.EXACT, "mock.topic", null));
        assertFalse(matcher.matches("mock.topic.extra", RouteMatchType.EXACT, "mock.topic", null));
        assertTrue(matcher.matches("event.order.created", RouteMatchType.PREFIX, "event.", null));
        assertTrue(matcher.matches("audit.order.event", RouteMatchType.SUFFIX, ".event", null));
    }

    @Test
    public void testWildcardEscapesRegexMetaCharacters() {
        String pattern = "mock.topic[?]";
        assertTrue(matcher.matches("mock.topic[1]", RouteMatchType.WILDCARD, pattern,
                matcher.compile(RouteMatchType.WILDCARD, pattern)));
        assertFalse(matcher.matches("mock-topic[1]", RouteMatchType.WILDCARD, pattern,
                matcher.compile(RouteMatchType.WILDCARD, pattern)));
    }

    @Test
    public void testRegexMatch() {
        String pattern = "event\\.(order|pay)\\..*";
        assertTrue(matcher.matches("event.order.created", RouteMatchType.REGEX, pattern,
                matcher.compile(RouteMatchType.REGEX, pattern)));
        assertFalse(matcher.matches("audit.order.created", RouteMatchType.REGEX, pattern,
                matcher.compile(RouteMatchType.REGEX, pattern)));
    }
}
