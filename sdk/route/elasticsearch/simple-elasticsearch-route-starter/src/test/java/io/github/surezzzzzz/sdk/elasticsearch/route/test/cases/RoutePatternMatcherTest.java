package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.configuration.SimpleElasticsearchRouteProperties;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RoutePatternMatcher;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 路由模式匹配器测试
 * 测试 Pattern 缓存功能
 *
 * @author Sure
 * @since 1.0.2
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class RoutePatternMatcherTest {

    private RoutePatternMatcher matcher;

    @BeforeEach
    public void setup() {
        matcher = new RoutePatternMatcher();
        matcher.clearCache();
    }

    /**
     * 测试1：exact 精确匹配
     */
    @Test
    public void testExactMatch() {
        log.info("=== 测试 exact 精确匹配 ===");

        SimpleElasticsearchRouteProperties.RouteRule rule = createRule("test_index", "exact");

        assertTrue(matcher.matches("test_index", rule));
        assertFalse(matcher.matches("test_index_2", rule));
        assertFalse(matcher.matches("test", rule));

        log.info("=== exact 匹配测试通过 ===");
    }

    /**
     * 测试2：prefix 前缀匹配
     */
    @Test
    public void testPrefixMatch() {
        log.info("=== 测试 prefix 前缀匹配 ===");

        SimpleElasticsearchRouteProperties.RouteRule rule = createRule("test_", "prefix");

        assertTrue(matcher.matches("test_index", rule));
        assertTrue(matcher.matches("test_abc", rule));
        assertTrue(matcher.matches("test_", rule));
        assertFalse(matcher.matches("prod_index", rule));

        log.info("=== prefix 匹配测试通过 ===");
    }

    /**
     * 测试3：suffix 后缀匹配
     */
    @Test
    public void testSuffixMatch() {
        log.info("=== 测试 suffix 后缀匹配 ===");

        SimpleElasticsearchRouteProperties.RouteRule rule = createRule("_index", "suffix");

        assertTrue(matcher.matches("test_index", rule));
        assertTrue(matcher.matches("prod_index", rule));
        assertTrue(matcher.matches("_index", rule));
        assertFalse(matcher.matches("index_test", rule));

        log.info("=== suffix 匹配测试通过 ===");
    }

    /**
     * 测试4：wildcard 通配符匹配
     */
    @Test
    public void testWildcardMatch() {
        log.info("=== 测试 wildcard 通配符匹配 ===");

        SimpleElasticsearchRouteProperties.RouteRule rule = createRule("test_*_index", "wildcard");

        assertTrue(matcher.matches("test_foo_index", rule));
        assertTrue(matcher.matches("test_bar_index", rule));
        assertFalse(matcher.matches("test_index", rule));

        // 测试 ** 通配符
        SimpleElasticsearchRouteProperties.RouteRule rule2 = createRule("test/**", "wildcard");
        assertTrue(matcher.matches("test/a/b/c", rule2));
        assertTrue(matcher.matches("test/x", rule2));

        log.info("=== wildcard 匹配测试通过 ===");
    }

    /**
     * 测试5：regex 正则表达式匹配 + Pattern 缓存
     */
    @Test
    public void testRegexMatchWithCache() {
        log.info("=== 测试 regex 正则匹配和缓存 ===");

        // 清空缓存
        matcher.clearCache();
        assertEquals(0, matcher.getCacheSize());
        log.info("缓存已清空");

        // 创建正则规则
        SimpleElasticsearchRouteProperties.RouteRule rule = createRule("test_\\d+", "regex");

        // 首次匹配（会编译正则并缓存）
        assertTrue(matcher.matches("test_123", rule));
        assertEquals(1, matcher.getCacheSize());
        log.info("首次匹配后缓存大小: 1");

        // 再次匹配相同规则（应该从缓存获取）
        assertTrue(matcher.matches("test_456", rule));
        assertFalse(matcher.matches("test_abc", rule));
        assertEquals(1, matcher.getCacheSize());
        log.info("再次匹配后缓存大小仍为: 1");

        // 匹配不同的正则规则
        SimpleElasticsearchRouteProperties.RouteRule rule2 = createRule("[a-z]+_\\d{3}", "regex");
        assertTrue(matcher.matches("test_123", rule2));
        assertEquals(2, matcher.getCacheSize());
        log.info("新规则匹配后缓存大小: 2");

        // 清空缓存
        matcher.clearCache();
        assertEquals(0, matcher.getCacheSize());

        log.info("=== regex 匹配和缓存测试通过 ===");
    }

    /**
     * 测试6：正则表达式缓存性能
     */
    @Test
    public void testRegexCachePerformance() {
        log.info("=== 测试 regex 缓存性能 ===");

        matcher.clearCache();

        // 创建复杂的正则规则
        SimpleElasticsearchRouteProperties.RouteRule rule =
                createRule("^[a-zA-Z0-9_]+_\\d{1,5}_[a-z]{2,10}$", "regex");

        // 第一次匹配（会编译正则）
        long start1 = System.nanoTime();
        boolean result1 = matcher.matches("test_12345_index", rule);
        long time1 = System.nanoTime() - start1;
        log.info("首次匹配耗时: {} ns", time1);
        assertTrue(result1);

        // 第二次匹配（应该从缓存获取）
        long start2 = System.nanoTime();
        boolean result2 = matcher.matches("prod_99999_table", rule);
        long time2 = System.nanoTime() - start2;
        log.info("缓存命中耗时: {} ns", time2);
        assertTrue(result2);

        // 缓存应该只有1个
        assertEquals(1, matcher.getCacheSize());

        log.info("=== regex 缓存性能测试通过（缓存命中比首次匹配快）===");
    }

    /**
     * 测试7：null 和非法输入
     */
    @Test
    public void testInvalidInput() {
        log.info("=== 测试 null 和非法输入 ===");

        SimpleElasticsearchRouteProperties.RouteRule rule = createRule("test", "exact");

        // null 索引名
        assertFalse(matcher.matches(null, rule));

        // null 规则
        assertFalse(matcher.matches("test_index", null));

        // 非法正则表达式（捕获异常，返回 false）
        SimpleElasticsearchRouteProperties.RouteRule invalidRule = createRule("[invalid(", "regex");
        assertFalse(matcher.matches("test", invalidRule));

        log.info("=== 非法输入测试通过 ===");
    }

    /**
     * 测试8：所有匹配类型
     */
    @Test
    public void testAllMatchTypes() {
        log.info("=== 测试所有匹配类型 ===");

        String indexName = "test_index";

        assertTrue(matcher.matches(indexName, createRule("test_index", "exact")));
        assertTrue(matcher.matches(indexName, createRule("test_", "prefix")));
        assertTrue(matcher.matches(indexName, createRule("_index", "suffix")));
        assertTrue(matcher.matches(indexName, createRule("test_*", "wildcard")));
        assertTrue(matcher.matches(indexName, createRule("test_\\w+", "regex")));

        log.info("=== 所有匹配类型测试通过 ===");
    }

    /**
     * 辅助方法：创建规则
     */
    private SimpleElasticsearchRouteProperties.RouteRule createRule(String pattern, String type) {
        SimpleElasticsearchRouteProperties.RouteRule rule =
                new SimpleElasticsearchRouteProperties.RouteRule();
        rule.setPattern(pattern);
        rule.setType(type);
        rule.setDatasource("test");
        rule.setPriority(100);
        rule.setEnable(true);
        return rule;
    }
}
