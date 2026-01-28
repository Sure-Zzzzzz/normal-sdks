package io.github.surezzzzzz.sdk.auth.aksk.resource.core.test.provider;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.provider.SimpleAkskSecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.support.SimpleAkskSecurityContextHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleAkskSecurityContextProvider 单元测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
class SimpleAkskSecurityContextProviderTest {

    /**
     * 测试用的 SimpleAkskSecurityContextProvider 实现
     * <p>
     * 委托给 SimpleAkskSecurityContextHelper 来读取数据
     */
    static class TestSimpleAkskSecurityContextProvider implements SimpleAkskSecurityContextProvider {

        @Override
        public Map<String, String> getAll() {
            return SimpleAkskSecurityContextHelper.getAll();
        }

        @Override
        public String get(String key) {
            return SimpleAkskSecurityContextHelper.get(key);
        }
    }

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        // 设置 Request Context
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        // 清理 Request Context
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 设置测试数据到 Request Attribute
     */
    private void setContextData(Map<String, String> context) {
        request.setAttribute(SimpleAkskResourceConstant.CONTEXT_ATTRIBUTE, context);
    }

    @Test
    void testGetAll() {
        Map<String, String> context = new HashMap<>();
        context.put("userId", "user-123");
        context.put("username", "testuser");
        setContextData(context);

        SimpleAkskSecurityContextProvider provider = new TestSimpleAkskSecurityContextProvider();
        Map<String, String> result = provider.getAll();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("user-123", result.get("userId"));
        assertEquals("testuser", result.get("username"));
    }

    @Test
    void testGet() {
        Map<String, String> context = new HashMap<>();
        context.put("userId", "user-123");
        context.put("username", "testuser");
        setContextData(context);

        SimpleAkskSecurityContextProvider provider = new TestSimpleAkskSecurityContextProvider();

        assertEquals("user-123", provider.get("userId"));
        assertEquals("testuser", provider.get("username"));
        assertNull(provider.get("nonexistent"));
    }

    @Test
    void testGetUserId() {
        Map<String, String> context = new HashMap<>();
        context.put("userId", "user-123");
        setContextData(context);

        SimpleAkskSecurityContextProvider provider = new TestSimpleAkskSecurityContextProvider();
        assertEquals("user-123", provider.getUserId());
    }

    @Test
    void testGetUsername() {
        Map<String, String> context = new HashMap<>();
        context.put("username", "testuser");
        setContextData(context);

        SimpleAkskSecurityContextProvider provider = new TestSimpleAkskSecurityContextProvider();
        assertEquals("testuser", provider.getUsername());
    }

    @Test
    void testGetClientId() {
        Map<String, String> context = new HashMap<>();
        context.put("clientId", "client-123");
        setContextData(context);

        SimpleAkskSecurityContextProvider provider = new TestSimpleAkskSecurityContextProvider();
        assertEquals("client-123", provider.getClientId());
    }

    @Test
    void testGetClientType() {
        Map<String, String> context = new HashMap<>();
        context.put("clientType", "user");
        setContextData(context);

        SimpleAkskSecurityContextProvider provider = new TestSimpleAkskSecurityContextProvider();
        assertEquals("user", provider.getClientType());
    }

    @Test
    void testGetSecurityContext() {
        Map<String, String> context = new HashMap<>();
        context.put("securityContext", "role:admin,level:5");
        setContextData(context);

        SimpleAkskSecurityContextProvider provider = new TestSimpleAkskSecurityContextProvider();
        assertEquals("role:admin,level:5", provider.getSecurityContext());
    }

    @Test
    void testGetRoles() {
        Map<String, String> context = new HashMap<>();
        context.put("roles", "admin,operator");
        setContextData(context);

        SimpleAkskSecurityContextProvider provider = new TestSimpleAkskSecurityContextProvider();
        assertEquals("admin,operator", provider.getRoles());
    }

    @Test
    void testGetScope() {
        Map<String, String> context = new HashMap<>();
        context.put("scope", "read write");
        setContextData(context);

        SimpleAkskSecurityContextProvider provider = new TestSimpleAkskSecurityContextProvider();
        assertEquals("read write", provider.getScope());
    }

    @Test
    void testDefaultMethodsWithNullValues() {
        Map<String, String> context = new HashMap<>();
        setContextData(context);

        SimpleAkskSecurityContextProvider provider = new TestSimpleAkskSecurityContextProvider();

        assertNull(provider.getUserId());
        assertNull(provider.getUsername());
        assertNull(provider.getClientId());
        assertNull(provider.getClientType());
        assertNull(provider.getSecurityContext());
        assertNull(provider.getRoles());
        assertNull(provider.getScope());
    }

    @Test
    void testEmptyContext() {
        Map<String, String> context = new HashMap<>();
        setContextData(context);

        SimpleAkskSecurityContextProvider provider = new TestSimpleAkskSecurityContextProvider();

        Map<String, String> result = provider.getAll();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
