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
}
