package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.support.SpELResolver;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.DocumentIndexHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SpEL 表达式解析测试
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class SpELResolverTest {

    // 辅助类全名
    private static final String CLASS_NAME =
            "io.github.surezzzzzz.sdk.elasticsearch.route.test.DocumentIndexHelper";

    // RequestAttributes 中的标志位 key
    private static final String HISTORY_FLAG_KEY = CLASS_NAME + ".access.history";

    // SpEL 表达式
    private static final String SPEL_EXPRESSION =
            "#{T(" + CLASS_NAME + ").processIndexName('test_index')}";

    @AfterEach
    public void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 测试：判断是否为 SpEL 表达式
     */
    @Test
    public void testIsSpEL() {
        assertTrue(SpELResolver.isSpEL("#{expression}"));
        assertTrue(SpELResolver.isSpEL("#{T(java.lang.String).valueOf('test')}"));

        assertFalse(SpELResolver.isSpEL("plain_text"));
        assertFalse(SpELResolver.isSpEL(null));
        assertFalse(SpELResolver.isSpEL(""));

        log.info("✓ SpEL detection test passed");
    }

    /**
     * 测试：简单 SpEL 表达式解析
     */
    @Test
    public void testResolveSimpleExpression() {
        // 字符串拼接
        String result = SpELResolver.resolve("#{'prefix_' + 'suffix'}");
        assertEquals("prefix_suffix", result);

        // 静态方法调用
        result = SpELResolver.resolve("#{T(java.lang.String).valueOf(123)}");
        assertEquals("123", result);

        // 非 SpEL 表达式
        result = SpELResolver.resolve("plain_index");
        assertEquals("plain_index", result);

        log.info("✓ Simple expression test passed");
    }

    /**
     * 测试：不设置 RequestAttributes
     * 预期：返回 "test_index"（不带 .history 后缀）
     */
    @Test
    public void testSpELWithoutRequestContext() {
        String result = SpELResolver.resolve(SPEL_EXPRESSION);

        assertEquals("test_index", result);
        log.info("Without RequestContext: [{}]", result);
    }

    /**
     * 测试：设置 RequestAttributes，但标志位为 false
     * 预期：返回 "test_index"
     */
    @Test
    public void testSpELWithRequestContextFalse() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);

        // 设置标志位为 false
        attributes.setAttribute(HISTORY_FLAG_KEY, Boolean.FALSE, RequestAttributes.SCOPE_REQUEST);

        RequestContextHolder.setRequestAttributes(attributes);

        try {
            String result = SpELResolver.resolve(SPEL_EXPRESSION);

            assertEquals("test_index", result);
            log.info("With RequestContext (history=false): [{}]", result);

        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /**
     * 测试：设置 RequestAttributes，标志位为 true
     * 预期：返回 "test_index.history"
     */
    @Test
    public void testSpELWithRequestContextTrue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);

        // 设置标志位为 true
        attributes.setAttribute(HISTORY_FLAG_KEY, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);

        RequestContextHolder.setRequestAttributes(attributes);

        try {
            String result = SpELResolver.resolve(SPEL_EXPRESSION);

            // ✅ 应该返回带 .history 后缀的索引名
            assertEquals("test_index.history", result);
            log.info("With RequestContext (history=true): [{}]", result);

        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /**
     * 测试：直接调用方法（不用 SpEL）
     */
    @Test
    public void testDirectMethodCall() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);

        // 测试 1：标志位为 false
        attributes.setAttribute(HISTORY_FLAG_KEY, Boolean.FALSE, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.setRequestAttributes(attributes);

        try {
            String result = DocumentIndexHelper.processIndexName("test_index");
            assertEquals("test_index", result);
            log.info("Direct call (history=false): [{}]", result);

        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        // 测试 2：标志位为 true
        attributes.setAttribute(HISTORY_FLAG_KEY, Boolean.TRUE, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.setRequestAttributes(attributes);

        try {
            String result = DocumentIndexHelper.processIndexName("test_index");
            assertEquals("test_index.history", result);
            log.info("Direct call (history=true): [{}]", result);

        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /**
     * 测试：无效的 SpEL 表达式
     */
    @Test
    public void testResolveInvalidExpression() {
        // 格式错误的表达式
        String invalid = "#{invalid expression";
        String result = SpELResolver.resolve(invalid);
        assertEquals(invalid, result);

        // 不存在的类
        String nonExistent = "#{T(com.example.NonExistentClass).method()}";
        result = SpELResolver.resolve(nonExistent);
        assertEquals(nonExistent, result);

        log.info("✓ Invalid expression test passed");
    }

    /**
     * 测试：SpEL 缓存功能
     */
    @Test
    public void testSpELCache() {
        log.info("=== 测试 SpEL 缓存功能 ===");

        // 清空缓存
        SpELResolver.clearCache();
        assertEquals(0, SpELResolver.getCacheSize());
        log.info("缓存已清空");

        // 首次解析
        String expr1 = "#{'cached_value'}";
        String result1 = SpELResolver.resolve(expr1);
        assertEquals("cached_value", result1);
        assertEquals(1, SpELResolver.getCacheSize());
        log.info("首次解析后缓存大小: 1");

        // 再次解析相同表达式（应该命中缓存）
        String result2 = SpELResolver.resolve(expr1);
        assertEquals("cached_value", result2);
        assertEquals(1, SpELResolver.getCacheSize());
        log.info("再次解析相同表达式，缓存大小仍为: 1");

        // 解析不同表达式
        String expr2 = "#{'another_value'}";
        String result3 = SpELResolver.resolve(expr2);
        assertEquals("another_value", result3);
        assertEquals(2, SpELResolver.getCacheSize());
        log.info("解析不同表达式后缓存大小: 2");

        // 解析非 SpEL 表达式（不应进入缓存）
        String plain = "plain_text";
        String result4 = SpELResolver.resolve(plain);
        assertEquals("plain_text", result4);
        assertEquals(2, SpELResolver.getCacheSize());
        log.info("解析非 SpEL 表达式后缓存大小仍为: 2");

        // 清空缓存
        SpELResolver.clearCache();
        assertEquals(0, SpELResolver.getCacheSize());
        log.info("再次清空缓存后大小: 0");

        log.info("=== SpEL 缓存测试通过 ===");
    }

    /**
     * 测试:SpEL 缓存多次访问相同表达式
     * 注意:缓存的是编译后的 Expression 对象,而不是结果值
     * 所以每次求值都会重新计算,但编译过程被缓存了
     */
    @Test
    public void testSpELCachePerformance() {
        log.info("=== 测试 SpEL 缓存性能 ===");

        SpELResolver.clearCache();

        String expr = "#{T(java.lang.System).currentTimeMillis()}";

        // 第一次解析（会编译表达式并缓存）
        long start1 = System.nanoTime();
        String result1 = SpELResolver.resolve(expr);
        long time1 = System.nanoTime() - start1;
        log.info("首次解析耗时: {} ns, 结果: {}", time1, result1);

        // 等待一小段时间，确保时间戳会不同
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            // ignore
        }

        // 第二次解析（从缓存获取编译后的 Expression，但会重新求值）
        long start2 = System.nanoTime();
        String result2 = SpELResolver.resolve(expr);
        long time2 = System.nanoTime() - start2;
        log.info("缓存命中耗时: {} ns, 结果: {}", time2, result2);

        // 结果应该不同（因为是重新求值，currentTimeMillis 会返回不同的值）
        assertNotEquals(result1, result2, "重新求值应该返回不同的时间戳");

        // 缓存应该只有1个（缓存的是 Expression 对象）
        assertEquals(1, SpELResolver.getCacheSize());

        log.info("=== SpEL 缓存性能测试通过（缓存 Expression 对象，支持运行时上下文）===");
    }
}
