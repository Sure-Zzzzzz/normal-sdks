package io.github.surezzzzzz.sdk.auth.aksk.resource.core.test.aspect;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.annotation.*;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.aspect.SimpleAkskSecurityAspect;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.exception.SimpleAkskExpressionException;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.exception.SimpleAkskSecurityException;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.provider.SimpleAkskSecurityContextProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleAkskSecurityAspect 集成测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@SpringBootTest(classes = SimpleAkskSecurityAspectTest.TestConfiguration.class)
class SimpleAkskSecurityAspectTest {

    @Autowired
    private TestController testController;

    @Autowired
    private ClassLevelAnnotatedController classLevelController;

    @Autowired
    private TestSimpleAkskSecurityContextProvider contextProvider;

    // ==================== @RequireContext 测试 ====================

    @Test
    void testRequireContextSuccess() {
        Map<String, String> context = new HashMap<>();
        context.put("userId", "user-123");
        contextProvider.setContext(context);
        assertDoesNotThrow(() -> testController.requireContextMethod());
    }

    @Test
    void testRequireContextFailureEmptyContext() {
        contextProvider.setContext(Collections.<String, String>emptyMap());
        SimpleAkskSecurityException exception = assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireContextMethod());
        assertEquals("Security context is required", exception.getMessage());
    }

    @Test
    void testRequireContextFailureNullContext() {
        contextProvider.setContext(null);
        SimpleAkskSecurityException exception = assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireContextMethod());
        assertEquals("Security context is required", exception.getMessage());
    }

    @Test
    void testRequireContextClassLevelSuccess() {
        Map<String, String> context = new HashMap<>();
        context.put("userId", "user-123");
        contextProvider.setContext(context);
        assertDoesNotThrow(() -> classLevelController.classLevelRequireContextMethod());
    }

    @Test
    void testRequireContextClassLevelFailure() {
        contextProvider.setContext(Collections.<String, String>emptyMap());
        assertThrows(SimpleAkskSecurityException.class,
                () -> classLevelController.classLevelRequireContextMethod());
    }

    // ==================== @RequireField 测试 ====================

    @Test
    void testRequireFieldSuccess() {
        Map<String, String> context = new HashMap<>();
        context.put("userId", "user-123");
        contextProvider.setContext(context);
        assertDoesNotThrow(() -> testController.requireFieldMethod());
    }

    @Test
    void testRequireFieldFailureFieldMissing() {
        Map<String, String> context = new HashMap<>();
        context.put("username", "testuser");
        contextProvider.setContext(context);
        SimpleAkskSecurityException exception = assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireFieldMethod());
        assertTrue(exception.getMessage().contains("userId"));
        assertTrue(exception.getMessage().contains("missing"));
    }

    @Test
    void testRequireFieldFailureFieldEmpty() {
        Map<String, String> context = new HashMap<>();
        context.put("userId", "");
        contextProvider.setContext(context);
        SimpleAkskSecurityException exception = assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireFieldMethod());
        assertTrue(exception.getMessage().contains("userId"));
    }

    @Test
    void testRequireFieldCustomMessage() {
        contextProvider.setContext(Collections.<String, String>emptyMap());
        SimpleAkskSecurityException exception = assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireFieldWithCustomMessage());
        assertEquals("User ID is required for this operation", exception.getMessage());
    }

    // ==================== @RequireFieldValue 测试 ====================

    @Test
    void testRequireFieldValueSuccess() {
        Map<String, String> context = new HashMap<>();
        context.put("role", "admin");
        contextProvider.setContext(context);
        assertDoesNotThrow(() -> testController.requireFieldValueMethod());
    }

    @Test
    void testRequireFieldValueFailureValueMismatch() {
        Map<String, String> context = new HashMap<>();
        context.put("role", "user");
        contextProvider.setContext(context);
        SimpleAkskSecurityException exception = assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireFieldValueMethod());
        assertTrue(exception.getMessage().contains("role"));
        assertTrue(exception.getMessage().contains("admin"));
        assertTrue(exception.getMessage().contains("user"));
    }

    @Test
    void testRequireFieldValueFailureFieldMissing() {
        contextProvider.setContext(Collections.<String, String>emptyMap());
        SimpleAkskSecurityException exception = assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireFieldValueMethod());
        assertTrue(exception.getMessage().contains("role"));
    }

    @Test
    void testRequireFieldValueCustomMessage() {
        Map<String, String> context = new HashMap<>();
        context.put("role", "user");
        contextProvider.setContext(context);
        SimpleAkskSecurityException exception = assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireFieldValueWithCustomMessage());
        assertEquals("Admin role required", exception.getMessage());
    }

    // ==================== @RequireExpression 测试 ====================

    @Test
    void testRequireExpressionSuccessSimpleExpression() {
        Map<String, String> context = new HashMap<>();
        context.put("userId", "user-123");
        contextProvider.setContext(context);
        assertDoesNotThrow(() -> testController.requireExpressionSimple());
    }

    @Test
    void testRequireExpressionFailureSimpleExpression() {
        contextProvider.setContext(Collections.<String, String>emptyMap());
        SimpleAkskSecurityException exception = assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireExpressionSimple());
        assertTrue(exception.getMessage().contains("Expression check failed"));
    }

    @Test
    void testRequireExpressionSuccessComplexExpression() {
        Map<String, String> context = new HashMap<>();
        context.put("clientType", "user");
        context.put("userId", "user-123");
        contextProvider.setContext(context);
        assertDoesNotThrow(() -> testController.requireExpressionComplex());
    }

    @Test
    void testRequireExpressionFailureComplexExpressionWrongClientType() {
        Map<String, String> context = new HashMap<>();
        context.put("clientType", "service");
        context.put("userId", "user-123");
        contextProvider.setContext(context);
        assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireExpressionComplex());
    }

    @Test
    void testRequireExpressionFailureComplexExpressionMissingUserId() {
        Map<String, String> context = new HashMap<>();
        context.put("clientType", "user");
        contextProvider.setContext(context);
        assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireExpressionComplex());
    }

    @Test
    void testRequireExpressionSuccessStringOperations() {
        Map<String, String> context = new HashMap<>();
        context.put("tenantId", "tenant-123");
        contextProvider.setContext(context);
        assertDoesNotThrow(() -> testController.requireExpressionStringOps());
    }

    @Test
    void testRequireExpressionFailureStringOperations() {
        Map<String, String> context = new HashMap<>();
        context.put("tenantId", "org-123");
        contextProvider.setContext(context);
        assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireExpressionStringOps());
    }

    @Test
    void testRequireExpressionCustomMessage() {
        contextProvider.setContext(Collections.<String, String>emptyMap());
        SimpleAkskSecurityException exception = assertThrows(SimpleAkskSecurityException.class,
                () -> testController.requireExpressionWithCustomMessage());
        assertEquals("Invalid tenant ID format", exception.getMessage());
    }

    @Test
    void testRequireExpressionInvalidExpression() {
        Map<String, String> context = new HashMap<>();
        context.put("userId", "user-123");
        contextProvider.setContext(context);
        SimpleAkskExpressionException exception = assertThrows(SimpleAkskExpressionException.class,
                () -> testController.requireExpressionInvalid());
        // 验证异常消息和表达式
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("Expression evaluation failed"));
        assertEquals("#context['userId'].nonExistentMethod()", exception.getExpression());
        assertNotNull(exception.getCause());
    }

    // ==================== 方法优先级测试 ====================

    @Test
    void testMethodAnnotationOverridesClassAnnotation() {
        // 类级别要求 role=admin，但方法级别要求 role=user
        Map<String, String> context = new HashMap<>();
        context.put("role", "user");
        contextProvider.setContext(context);
        assertDoesNotThrow(() -> classLevelController.methodOverridesClass());
    }

    // ==================== 测试配置 ====================

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfiguration {

        @Bean
        public TestSimpleAkskSecurityContextProvider testSimpleAkskSecurityContextProvider() {
            return new TestSimpleAkskSecurityContextProvider();
        }

        @Bean
        public SimpleAkskSecurityAspect akskSecurityAspect(TestSimpleAkskSecurityContextProvider provider) {
            return new SimpleAkskSecurityAspect(provider);
        }

        @Bean
        public TestController testController() {
            return new TestController();
        }

        @Bean
        public ClassLevelAnnotatedController classLevelAnnotatedController() {
            return new ClassLevelAnnotatedController();
        }
    }

    // ==================== 测试用的 SimpleAkskSecurityContextProvider ====================

    static class TestSimpleAkskSecurityContextProvider implements SimpleAkskSecurityContextProvider {
        private Map<String, String> context = new HashMap<>();

        public void setContext(Map<String, String> context) {
            this.context = context != null ? context : new HashMap<String, String>();
        }

        @Override
        public Map<String, String> getAll() {
            return context;
        }

        @Override
        public String get(String key) {
            return context.get(key);
        }
    }

    // ==================== 测试用的 Controller ====================

    @Component
    static class TestController {

        @RequireContext
        public void requireContextMethod() {
        }

        @RequireField("userId")
        public void requireFieldMethod() {
        }

        @RequireField(value = "userId", message = "User ID is required for this operation")
        public void requireFieldWithCustomMessage() {
        }

        @RequireFieldValue(field = "role", value = "admin")
        public void requireFieldValueMethod() {
        }

        @RequireFieldValue(field = "role", value = "admin", message = "Admin role required")
        public void requireFieldValueWithCustomMessage() {
        }

        @RequireExpression("#context['userId'] != null")
        public void requireExpressionSimple() {
        }

        @RequireExpression("#context['clientType'] == 'user' && #context['userId'] != null")
        public void requireExpressionComplex() {
        }

        @RequireExpression("#context['tenantId'] != null && #context['tenantId'].startsWith('tenant-')")
        public void requireExpressionStringOps() {
        }

        @RequireExpression(value = "#context['tenantId'] != null && #context['tenantId'].startsWith('tenant-')",
                message = "Invalid tenant ID format")
        public void requireExpressionWithCustomMessage() {
        }

        @RequireExpression("#context['userId'].nonExistentMethod()")
        public void requireExpressionInvalid() {
        }

        public void classLevelRequireContextMethod() {
        }
    }

    @Component
    @RequireContext
    static class ClassLevelAnnotatedController extends TestController {
        @Override
        public void classLevelRequireContextMethod() {
        }

        @RequireFieldValue(field = "role", value = "user")
        public void methodOverridesClass() {
        }
    }
}
