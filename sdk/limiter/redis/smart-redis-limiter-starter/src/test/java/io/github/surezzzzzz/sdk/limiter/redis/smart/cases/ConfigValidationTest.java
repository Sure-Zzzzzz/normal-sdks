package io.github.surezzzzzz.sdk.limiter.redis.smart.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.SmartRedisLimiterApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置验证功能单元测试
 * 测试 SmartRedisLimiterProperties 的配置验证逻辑
 *
 * @author Sure
 * @since 1.0.2
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterApplication.class)
public class ConfigValidationTest {

    /**
     * 测试1：正常配置应该验证通过
     */
    @Test
    public void testValidConfig() {
        log.info("=== 测试正常配置验证 ===");

        SmartRedisLimiterProperties properties = createValidProperties();

        // 应该不抛异常
        assertDoesNotThrow(() -> properties.init());

        log.info("=== 正常配置验证通过 ===");
    }

    /**
     * 测试2：me 为空时应该抛异常
     */
    @Test
    public void testMeEmpty() {
        log.info("=== 测试 me 为空时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();
        properties.setMe("");  // 空字符串

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        // 原始错误信息在cause中
        Throwable cause = exception.getCause();
        assertNotNull(cause, "应该有cause异常");
        String message = cause.getMessage();
        assertTrue(message.contains("me"), "错误信息应该包含 'me'，实际: " + message);
        log.info("异常信息: {}", message);

        log.info("=== me 为空验证测试通过 ===");
    }

    /**
     * 测试3：me 长度超过50时应该抛异常
     */
    @Test
    public void testMeTooLong() {
        log.info("=== 测试 me 长度超过50时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();
        // Java 8 兼容：生成51个字符的字符串
        properties.setMe(new String(new char[51]).replace('\0', 'a'));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        // 原始错误信息在cause中
        Throwable cause = exception.getCause();
        assertNotNull(cause, "应该有cause异常");
        String message = cause.getMessage();
        assertTrue(message.contains("50"), "错误信息应该包含长度限制，实际: " + message);
        log.info("异常信息: {}", message);

        log.info("=== me 长度验证测试通过 ===");
    }

    /**
     * 测试4：count <= 0 时应该抛异常
     */
    @Test
    public void testCountInvalid() {
        log.info("=== 测试 count <= 0 时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();

        // 设置非法的count
        SmartRedisLimiterProperties.SmartLimitRule rule = new SmartRedisLimiterProperties.SmartLimitRule();
        rule.setCount(0);  // 非法值
        rule.setWindow(60);
        rule.setUnit(TimeUnit.SECONDS);

        properties.getAnnotation().getDefaultLimits().clear();
        properties.getAnnotation().getDefaultLimits().add(rule);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        // 原始错误信息在cause中
        Throwable cause = exception.getCause();
        assertNotNull(cause, "应该有cause异常");
        String message = cause.getMessage();
        assertTrue(message.contains("count"), "错误信息应该包含 'count'，实际: " + message);
        log.info("异常信息: {}", message);

        log.info("=== count 验证测试通过 ===");
    }

    /**
     * 测试5：window <= 0 时应该抛异常
     */
    @Test
    public void testWindowInvalid() {
        log.info("=== 测试 window <= 0 时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();

        // 设置非法的window
        SmartRedisLimiterProperties.SmartLimitRule rule = new SmartRedisLimiterProperties.SmartLimitRule();
        rule.setCount(100);
        rule.setWindow(-1);  // 非法值
        rule.setUnit(TimeUnit.SECONDS);

        properties.getAnnotation().getDefaultLimits().clear();
        properties.getAnnotation().getDefaultLimits().add(rule);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        // 原始错误信息在cause中
        Throwable cause = exception.getCause();
        assertNotNull(cause, "应该有cause异常");
        String message = cause.getMessage();
        assertTrue(message.contains("window"), "错误信息应该包含 'window'，实际: " + message);
        log.info("异常信息: {}", message);

        log.info("=== window 验证测试通过 ===");
    }

    /**
     * 测试6：非法的 mode 值时应该抛异常
     */
    @Test
    public void testModeInvalid() {
        log.info("=== 测试非法 mode 值时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();
        properties.setMode("invalid-mode");  // 非法值

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        // 原始错误信息在cause中
        Throwable cause = exception.getCause();
        assertNotNull(cause, "应该有cause异常");
        String message = cause.getMessage();
        assertTrue(message.contains("mode"), "错误信息应该包含 'mode'，实际: " + message);
        log.info("异常信息: {}", message);

        log.info("=== mode 验证测试通过 ===");
    }

    /**
     * 测试7：非法的 keyStrategy 值时应该抛异常
     */
    @Test
    public void testKeyStrategyInvalid() {
        log.info("=== 测试非法 keyStrategy 值时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();
        properties.getAnnotation().setDefaultKeyStrategy("invalid-strategy");  // 非法值

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        // 原始错误信息在cause中
        Throwable cause = exception.getCause();
        assertNotNull(cause, "应该有cause异常");
        String message = cause.getMessage();
        assertTrue(message.toLowerCase().contains("keystrategy"), "错误信息应该包含 'keyStrategy'，实际: " + message);
        log.info("异常信息: {}", message);

        log.info("=== keyStrategy 验证测试通过 ===");
    }

    /**
     * 测试8：commandTimeout <= 0 时应该抛异常
     */
    @Test
    public void testCommandTimeoutInvalid() {
        log.info("=== 测试 commandTimeout <= 0 时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();
        properties.getRedis().setCommandTimeout(0L);  // 非法值

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        // 原始错误信息在cause中
        Throwable cause = exception.getCause();
        assertNotNull(cause, "应该有cause异常");
        String message = cause.getMessage();
        assertTrue(message.contains("commandTimeout"), "错误信息应该包含 'commandTimeout'，实际: " + message);
        log.info("异常信息: {}", message);

        log.info("=== commandTimeout 验证测试通过 ===");
    }

    /**
     * 测试9：非法的 fallback 值时应该抛异常
     */
    @Test
    public void testFallbackInvalid() {
        log.info("=== 测试非法 fallback 值时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();
        properties.getFallback().setOnRedisError("invalid-fallback");  // 非法值

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        // 原始错误信息在cause中
        Throwable cause = exception.getCause();
        assertNotNull(cause, "应该有cause异常");
        String message = cause.getMessage();
        assertTrue(message.contains("fallback"), "错误信息应该包含 'fallback'，实际: " + message);
        log.info("异常信息: {}", message);

        log.info("=== fallback 验证测试通过 ===");
    }

    /**
     * 测试10：拦截器规则的 pathPattern 为空时应该抛异常
     */
    @Test
    public void testPathPatternEmpty() {
        log.info("=== 测试 pathPattern 为空时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();
        properties.setMode("interceptor");  // 启用拦截器模式

        // 添加一个pathPattern为空的规则
        SmartRedisLimiterProperties.SmartInterceptorRule rule = new SmartRedisLimiterProperties.SmartInterceptorRule();
        rule.setPathPattern("");  // 空字符串
        rule.setKeyStrategy("path");

        properties.getInterceptor().getRules().add(rule);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            properties.init();
        });

        // 原始错误信息在cause中
        Throwable cause = exception.getCause();
        assertNotNull(cause, "应该有cause异常");
        String message = cause.getMessage();
        assertTrue(message.contains("pathPattern"), "错误信息应该包含 'pathPattern'，实际: " + message);
        log.info("异常信息: {}", message);

        log.info("=== pathPattern 验证测试通过 ===");
    }

    /**
     * 创建一个合法的配置对象
     */
    private SmartRedisLimiterProperties createValidProperties() {
        SmartRedisLimiterProperties properties = new SmartRedisLimiterProperties();
        properties.setEnable(true);
        properties.setMe("test-service");
        properties.setMode("annotation");

        // 设置有效的限流规则
        SmartRedisLimiterProperties.SmartLimitRule rule = new SmartRedisLimiterProperties.SmartLimitRule();
        rule.setCount(100);
        rule.setWindow(60);
        rule.setUnit(TimeUnit.SECONDS);

        properties.getAnnotation().setDefaultLimits(new ArrayList<>());
        properties.getAnnotation().getDefaultLimits().add(rule);
        properties.getAnnotation().setDefaultKeyStrategy("method");

        // 设置有效的Redis配置
        properties.getRedis().setCommandTimeout(3000L);

        // 设置有效的降级配置
        properties.getFallback().setOnRedisError("allow");

        return properties;
    }
}
