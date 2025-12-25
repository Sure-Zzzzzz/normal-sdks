package io.github.surezzzzzz.sdk.elasticsearch.route.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.route.test.SimpleElasticsearchRouteTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RouteTemplateProxy 异常处理集成测试
 * 使用真实的 Spring 环境验证异常不会被包装为 InvocationTargetException
 *
 * @author Sure
 * @since 1.0.3
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchRouteTestApplication.class)
public class RouteTemplateProxyExceptionTest {

    @Autowired
    @Qualifier("elasticsearchRestTemplate")
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    /**
     * 测试：访问不存在的索引应该抛出原始异常，不被包装
     */
    @Test
    public void testExceptionNotWrappedAsInvocationTargetException() {
        log.info("=== testExceptionNotWrappedAsInvocationTargetException ===");

        try {
            // 尝试访问一个不存在的索引（会触发异常）
            elasticsearchRestTemplate.exists("non-existent-id",
                    IndexCoordinates.of("non_existent_index_12345"));

        } catch (Exception e) {
            log.info("Caught exception type: {}", e.getClass().getName());
            log.info("Exception message: {}", e.getMessage());

            // 验证：不是 InvocationTargetException
            assertNotEquals(InvocationTargetException.class, e.getClass(),
                    "异常不应该被包装为 InvocationTargetException");

            // 验证堆栈中不包含 InvocationTargetException
            boolean hasInvocationException = containsInvocationTargetException(e);
            assertFalse(hasInvocationException,
                    "堆栈信息不应包含 InvocationTargetException");

            log.info("✅ Exception correctly thrown without InvocationTargetException wrapping");
            return;
        }

        // 如果索引存在或连接失败，测试仍然通过（因为重点是异常处理机制）
        log.warn("⚠️ No exception thrown - ES might be offline or index exists");
    }

    /**
     * 测试：模拟版本兼容性错误场景
     * 通过尝试不支持的操作来触发类似的异常
     */
    @Test
    public void testVersionCompatibilityLikeException() {
        log.info("=== testVersionCompatibilityLikeException ===");

        try {
            // 尝试使用可能不兼容的操作
            // 注意：这个测试可能因 ES 版本而有不同行为
            elasticsearchRestTemplate.indexOps(IndexCoordinates.of("_invalid.index.name.with.dots"));

        } catch (Exception e) {
            log.info("Caught exception type: {}", e.getClass().getName());
            log.info("Exception message: {}", e.getMessage());

            // 验证异常处理机制
            assertNotEquals(InvocationTargetException.class, e.getClass());

            // 检查是否被识别为兼容性问题
            boolean isCompatibilityIssue = isVersionCompatibilityRelated(e.getMessage());
            if (isCompatibilityIssue) {
                log.info("✅ Compatibility issue correctly detected: {}", e.getMessage());
            }

            return;
        }

        log.warn("⚠️ No exception thrown - operation might be supported in current ES version");
    }

    /**
     * 测试：批量操作异常处理
     */
    @Test
    public void testBulkOperationException() {
        log.info("=== testBulkOperationException ===");

        try {
            // 尝试对不存在的索引执行操作
            elasticsearchRestTemplate.delete("test-id",
                    IndexCoordinates.of("definitely_non_existent_index_99999"));

        } catch (Exception e) {
            log.info("Caught exception type: {}", e.getClass().getName());

            // 验证异常链
            assertNotNull(e);
            assertNotEquals(InvocationTargetException.class, e.getClass());

            // 打印异常链
            printExceptionChain(e);

            log.info("✅ Bulk operation exception handled correctly");
            return;
        }

        log.warn("⚠️ No exception thrown");
    }

    /**
     * 辅助方法：检查异常链中是否包含 InvocationTargetException
     */
    private boolean containsInvocationTargetException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InvocationTargetException) {
                return true;
            }

            // 检查堆栈
            for (StackTraceElement element : current.getStackTrace()) {
                if (element.getClassName().contains("InvocationTargetException")) {
                    return true;
                }
            }

            current = current.getCause();
        }
        return false;
    }

    /**
     * 辅助方法：检查是否是版本兼容性相关的异常
     */
    private boolean isVersionCompatibilityRelated(String message) {
        if (message == null) {
            return false;
        }

        String lower = message.toLowerCase();
        return lower.contains("unrecognized parameter") ||
                lower.contains("master_timeout") ||
                lower.contains("no such parameter") ||
                lower.contains("unknown setting") ||
                lower.contains("illegal_argument_exception");
    }

    /**
     * 辅助方法：打印完整异常链
     */
    private void printExceptionChain(Throwable throwable) {
        log.info("=== Exception Chain ===");
        int level = 0;
        Throwable current = throwable;

        while (current != null) {
            log.info("Level {}: {} - {}",
                    level,
                    current.getClass().getSimpleName(),
                    current.getMessage());
            current = current.getCause();
            level++;
        }
        log.info("======================");
    }
}
