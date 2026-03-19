package io.github.surezzzzzz.sdk.cache.test.cases;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheEvict;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCachePut;
import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheable;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.cache.test.SmartCacheTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Annotation API Test
 * <p>
 * 测试注解式 API：@SmartCacheable, @SmartCachePut, @SmartCacheEvict
 * </p>
 *
 * @author Sure
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SmartCacheTestApplication.class)
public class AnnotationApiTest {

    @Autowired
    private SmartCacheManager cacheManager;

    @Autowired
    private TestService testService;

    @BeforeEach
    public void setUp() {
        log.info("========== 初始化测试环境 ==========");
        cacheManager.clear("userCache");
        TestService.callCount = 0;
        log.info("测试环境初始化完成");
    }

    @Test
    public void testCacheableShouldCacheResult() {
        log.info("========== 测试：@SmartCacheable 应该缓存结果 ==========");

        // Given
        Long userId = 1L;
        log.info("用户 ID: {}", userId);

        // When - 第一次调用
        String result1 = testService.getUser(userId);
        log.info("第一次调用结果: {}, 调用次数: {}", result1, TestService.callCount);

        // Then
        assertNotNull(result1);
        assertEquals("User-1", result1);
        assertEquals(1, TestService.callCount);
        log.info("验证通过：第一次调用成功");

        // When - 第二次调用（应该从缓存获取）
        String result2 = testService.getUser(userId);
        log.info("第二次调用结果: {}, 调用次数: {}", result2, TestService.callCount);

        // Then
        assertNotNull(result2);
        assertEquals("User-1", result2);
        assertEquals(1, TestService.callCount); // 调用次数不变，说明从缓存获取
        log.info("验证通过：第二次从缓存获取");
        log.info("测试通过");
    }

    @Test
    public void testCachePutShouldUpdateCache() {
        log.info("========== 测试：@SmartCachePut 应该更新缓存 ==========");

        // Given
        Long userId = 2L;
        String newName = "UpdatedUser-2";
        log.info("用户 ID: {}, 新名称: {}", userId, newName);

        // When - 先获取（缓存）
        String result1 = testService.getUser(userId);
        log.info("初始值: {}", result1);

        // 更新缓存
        String result2 = testService.updateUser(userId, newName);
        log.info("更新后返回值: {}", result2);

        // Then
        assertEquals(newName, result2);
        log.info("验证通过：更新成功");

        // 再次获取（应该是更新后的值）
        String result3 = testService.getUser(userId);
        log.info("再次获取: {}, 调用次数: {}", result3, TestService.callCount);
        assertEquals(newName, result3);
        assertEquals(1, TestService.callCount); // 只调用了一次 getUser，说明第二次从缓存获取
        log.info("验证通过：缓存已更新");
        log.info("测试通过");
    }

    @Test
    public void testCacheEvictShouldRemoveCache() {
        log.info("========== 测试：@SmartCacheEvict 应该删除缓存 ==========");

        // Given
        Long userId = 3L;
        log.info("用户 ID: {}", userId);

        // When - 先获取（缓存）
        String result1 = testService.getUser(userId);
        log.info("初始值: {}, 调用次数: {}", result1, TestService.callCount);
        assertEquals(1, TestService.callCount);

        // 删除缓存
        testService.deleteUser(userId);
        log.info("已删除缓存");

        // 再次获取（应该重新加载）
        String result2 = testService.getUser(userId);
        log.info("删除后再次获取: {}, 调用次数: {}", result2, TestService.callCount);

        // Then
        assertNotNull(result2);
        assertEquals(2, TestService.callCount); // 调用次数增加，说明重新加载了
        log.info("验证通过：缓存已删除并重新加载");
        log.info("测试通过");
    }

    @Test
    public void testCacheEvictAllEntriesShouldClearAll() {
        log.info("========== 测试：@SmartCacheEvict(allEntries=true) 应该清空所有缓存 ==========");

        // Given
        testService.getUser(1L);
        testService.getUser(2L);
        testService.getUser(3L);
        log.info("已缓存 3 个用户，调用次数: {}", TestService.callCount);
        assertEquals(3, TestService.callCount);

        // When - 清空所有缓存
        testService.clearAllUsers();
        log.info("已清空所有缓存");

        // 再次获取（应该重新加载）
        testService.getUser(1L);
        testService.getUser(2L);
        testService.getUser(3L);
        log.info("清空后再次获取，调用次数: {}", TestService.callCount);

        // Then
        assertEquals(6, TestService.callCount); // 调用次数翻倍，说明重新加载了
        log.info("验证通过：所有缓存已清空并重新加载");
        log.info("测试通过");
    }

    @Test
    public void testBeforeInvocationShouldEvictBeforeMethodExecution() {
        log.info("========== 测试：beforeInvocation=true 应该在方法执行前删除缓存 ==========");

        // Given - 先缓存一个值
        Long userId = 4L;
        String result1 = testService.getUser(userId);
        log.info("初始缓存值: {}, 调用次数: {}", result1, TestService.callCount);
        assertEquals("User-4", result1);
        assertEquals(1, TestService.callCount);

        // When - 调用 beforeInvocation=true 的删除方法（该方法会抛出异常）
        try {
            testService.deleteUserBeforeInvocation(userId);
            fail("应该抛出异常");
        } catch (RuntimeException e) {
            log.info("方法执行失败（预期行为）: {}", e.getMessage());
            assertEquals("模拟删除失败", e.getMessage());
        }

        // Then - 即使方法执行失败，缓存也应该被删除（因为 beforeInvocation=true）
        String result2 = testService.getUser(userId);
        log.info("删除后再次获取: {}, 调用次数: {}", result2, TestService.callCount);
        assertEquals(2, TestService.callCount); // 调用次数增加，说明缓存已被删除
        log.info("验证通过：beforeInvocation=true 在方法执行前删除了缓存");
        log.info("测试通过");
    }

    @Test
    public void testBeforeInvocationFalseShouldEvictAfterMethodExecution() {
        log.info("========== 测试：beforeInvocation=false 应该在方法执行后删除缓存 ==========");

        // Given - 先缓存一个值
        Long userId = 5L;
        String result1 = testService.getUser(userId);
        log.info("初始缓存值: {}, 调用次数: {}", result1, TestService.callCount);
        assertEquals("User-5", result1);
        assertEquals(1, TestService.callCount);

        // When - 调用 beforeInvocation=false（默认）的删除方法（该方法会抛出异常）
        try {
            testService.deleteUserAfterInvocation(userId);
            fail("应该抛出异常");
        } catch (RuntimeException e) {
            log.info("方法执行失败（预期行为）: {}", e.getMessage());
            assertEquals("模拟删除失败", e.getMessage());
        }

        // Then - 方法执行失败，缓存不应该被删除（因为 beforeInvocation=false）
        String result2 = testService.getUser(userId);
        log.info("删除失败后再次获取: {}, 调用次数: {}", result2, TestService.callCount);
        assertEquals(1, TestService.callCount); // 调用次数不变，说明缓存未被删除
        log.info("验证通过：beforeInvocation=false 在方法失败时保留了缓存");
        log.info("测试通过");
    }

    @Test
    public void testSpELInjectionPrevention() throws NoSuchMethodException {
        log.info("========== 测试：SpEL 注入防护 ==========");

        // 获取测试方法用于 SpEL 解析
        Method method = TestService.class.getMethod("getUser", Long.class);
        Long userId = 999L;
        Object[] args = new Object[]{userId};

        // 测试1：正常的 SpEL 表达式应该工作
        assertDoesNotThrow(() -> {
            String result = io.github.surezzzzzz.sdk.cache.support.SpELExpressionHelper
                    .parseExpression("#userId", method, args, null);
            assertEquals("999", result);
            log.info("正常 SpEL 表达式工作正常: {}", result);
        });

        // 测试2：尝试类型引用 - 应该抛出异常（SimpleEvaluationContext 不支持）
        assertThrows(Exception.class, () -> {
            io.github.surezzzzzz.sdk.cache.support.SpELExpressionHelper
                    .parseExpression("T(java.lang.Runtime).getRuntime().exec('calc')", method, args, null);
        });
        log.info("验证通过：类型引用被阻止");

        // 测试3：尝试构造函数调用 - 应该抛出异常
        assertThrows(Exception.class, () -> {
            io.github.surezzzzzz.sdk.cache.support.SpELExpressionHelper
                    .parseExpression("new java.lang.String('test')", method, args, null);
        });
        log.info("验证通过：构造函数调用被阻止");

        // 测试4：尝试反射操作 - 应该抛出异常
        assertThrows(Exception.class, () -> {
            io.github.surezzzzzz.sdk.cache.support.SpELExpressionHelper
                    .parseExpression("#userId.getClass().forName('java.lang.Runtime')", method, args, null);
        });
        log.info("验证通过：反射操作被阻止");

        log.info("验证通过：SimpleEvaluationContext 提供了完整的 SpEL 注入防护");
        log.info("测试通过");
    }

    /**
     * 测试服务
     */
    @Service
    public static class TestService {

        public static int callCount = 0;

        @SmartCacheable(cacheName = "userCache", key = "#userId")
        public String getUser(Long userId) {
            callCount++;
            log.info("从数据源加载用户: {}", userId);
            return "User-" + userId;
        }

        @SmartCachePut(cacheName = "userCache", key = "#userId")
        public String updateUser(Long userId, String newName) {
            log.info("更新用户: {}, 新名称: {}", userId, newName);
            return newName;
        }

        @SmartCacheEvict(cacheName = "userCache", key = "#userId")
        public void deleteUser(Long userId) {
            log.info("删除用户: {}", userId);
        }

        @SmartCacheEvict(cacheName = "userCache", key = "#userId", beforeInvocation = true)
        public void deleteUserBeforeInvocation(Long userId) {
            log.info("删除用户（beforeInvocation=true）: {}", userId);
            throw new RuntimeException("模拟删除失败");
        }

        @SmartCacheEvict(cacheName = "userCache", key = "#userId", beforeInvocation = false)
        public void deleteUserAfterInvocation(Long userId) {
            log.info("删除用户（beforeInvocation=false）: {}", userId);
            throw new RuntimeException("模拟删除失败");
        }

        @SmartCacheEvict(cacheName = "userCache", allEntries = true)
        public void clearAllUsers() {
            log.info("清空所有用户缓存");
        }
    }
}
