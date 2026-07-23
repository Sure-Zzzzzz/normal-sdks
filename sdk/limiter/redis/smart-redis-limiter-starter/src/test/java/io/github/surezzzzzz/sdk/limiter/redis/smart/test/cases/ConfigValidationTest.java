package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterStarterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置验证功能单元测试
 * 测试 SmartRedisLimiterProperties 的配置验证逻辑
 *
 * @author Sure
 * @since 1.0.2
 */
@Slf4j
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

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        String message = exception.getMessage();
        assertTrue(message.contains("me"), "错误信息应该包含 'me'，实际: " + message);
        log.info("异常信息: {}", message);

        log.info("=== me 为空验证测试通过 ===");
    }

    /**
     * 测试3：me 长度超过128时应该抛异常
     */
    @Test
    public void testMeTooLong() {
        log.info("=== 测试 me 长度超过128时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();
        // Java 8 兼容：生成129个字符的字符串
        properties.setMe(new String(new char[129]).replace('\0', 'a'));

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        String message = exception.getMessage();
        assertTrue(message.contains("128"), "错误信息应该包含长度限制，实际: " + message);
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
        rule.setCount(0L);  // 非法值
        rule.setWindow(60L);
        rule.setUnit(SmartRedisLimiterTimeUnit.SECONDS);

        properties.getAnnotation().getDefaultLimits().clear();
        properties.getAnnotation().getDefaultLimits().add(rule);

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        String message = exception.getMessage();
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
        rule.setCount(100L);
        rule.setWindow(-1L);  // 非法值
        rule.setUnit(SmartRedisLimiterTimeUnit.SECONDS);

        properties.getAnnotation().getDefaultLimits().clear();
        properties.getAnnotation().getDefaultLimits().add(rule);

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        String message = exception.getMessage();
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

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        String message = exception.getMessage();
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

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        String message = exception.getMessage();
        assertTrue(message.contains("default-key-strategy"), "错误信息应该包含 'default-key-strategy'，实际: " + message);
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

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        String message = exception.getMessage();
        assertTrue(message.contains("command-timeout"), "错误信息应该包含 'command-timeout'，实际: " + message);
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

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        String message = exception.getMessage();
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

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        String message = exception.getMessage();
        assertTrue(message.contains("path-pattern"), "错误信息应该包含 'path-pattern'，实际: " + message);
        log.info("异常信息: {}", message);

        log.info("=== pathPattern 验证测试通过 ===");
    }

    /**
     * 测试11：timeoutExecutorThreads <= 0 时应该抛异常
     */
    @Test
    public void testTimeoutExecutorThreadsInvalid() {
        log.info("=== 测试 timeoutExecutorThreads <= 0 时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();
        properties.getRedis().setTimeoutExecutorThreads(0);

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        assertTrue(exception.getMessage().contains("timeout-executor-threads"),
                "错误信息应该包含 'timeout-executor-threads'，实际: " + exception.getMessage());
        log.info("异常信息: {}", exception.getMessage());

        log.info("=== timeoutExecutorThreads 验证测试通过 ===");
    }

    /**
     * 测试12：timeoutExecutorQueueCapacity <= 0 时应该抛异常
     */
    @Test
    public void testTimeoutExecutorQueueCapacityInvalid() {
        log.info("=== 测试 timeoutExecutorQueueCapacity <= 0 时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();
        properties.getRedis().setTimeoutExecutorQueueCapacity(-1);

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        assertTrue(exception.getMessage().contains("timeout-executor-queue-capacity"),
                "错误信息应该包含 'timeout-executor-queue-capacity'，实际: " + exception.getMessage());
        log.info("异常信息: {}", exception.getMessage());

        log.info("=== timeoutExecutorQueueCapacity 验证测试通过 ===");
    }

    /**
     * 测试13：全局 fallback.onRedisError 非法值时应该抛异常
     */
    @Test
    public void testGlobalFallbackInvalid() {
        log.info("=== 测试全局 fallback.onRedisError 非法值时验证失败 ===");

        SmartRedisLimiterProperties properties = createValidProperties();
        properties.getFallback().setOnRedisError("invalid-global-fallback");

        SmartRedisLimiterConfigurationException exception =
                assertThrows(SmartRedisLimiterConfigurationException.class, properties::init);
        assertEquals(ErrorCode.CONFIG_VALIDATION_FAILED,
                exception.getErrorCode(), "错误码应为配置验证失败");
        assertTrue(exception.getMessage().contains("fallback"),
                "错误信息应该包含 'fallback'，实际: " + exception.getMessage());
        log.info("异常信息: {}", exception.getMessage());

        log.info("=== 全局 fallback 验证测试通过 ===");
    }

    /**
     * 测试远程策略默认关闭且默认值完整
     */
    @Test
    public void testRemotePolicyDefaults() {
        SmartRedisLimiterProperties properties = createValidProperties();

        log.info("远程策略默认配置: {}", properties.getRemotePolicy());
        assertFalse(properties.getRemotePolicy().getEnable(), "远程策略应默认关闭");
        assertNull(properties.getRemotePolicy().getSnapshotUrl(), "默认快照地址应为空");
        assertEquals(SmartRedisLimiterStarterConstant.DEFAULT_REMOTE_POLICY_REFRESH_INTERVAL_MILLIS,
                properties.getRemotePolicy().getRefreshIntervalMillis(), "刷新间隔默认值应一致");
        assertEquals(SmartRedisLimiterStarterConstant.DEFAULT_REMOTE_POLICY_MAX_LIMITS_PER_POLICY,
                properties.getRemotePolicy().getMaxLimitsPerPolicy(), "窗口上限默认值应一致");
    }

    /**
     * 测试远程策略关闭时不校验 URL
     */
    @Test
    public void testRemotePolicyDisabledIgnoresUrl() {
        SmartRedisLimiterProperties properties = createValidProperties();
        properties.getRemotePolicy().setEnable(false);
        properties.getRemotePolicy().setSnapshotUrl("not-a-url");
        properties.getRemotePolicy().setRefreshIntervalMillis(0L);

        log.info("远程策略关闭时配置: {}", properties.getRemotePolicy());
        assertDoesNotThrow(properties::init, "远程策略关闭时不应校验远程字段");
    }

    /**
     * 测试远程策略开启时接受合法绝对 URL
     */
    @Test
    public void testRemotePolicyEnabledAcceptsValidUrl() {
        SmartRedisLimiterProperties properties = createValidProperties();
        properties.getRemotePolicy().setEnable(true);
        properties.getRemotePolicy().setSnapshotUrl("https://management.internal:8443/custom/policy-snapshot");

        log.info("远程策略合法 URL: {}", properties.getRemotePolicy().getSnapshotUrl());
        assertDoesNotThrow(properties::init, "合法远程策略配置应通过验证");
    }

    /**
     * 测试远程策略开启时拒绝 URL 查询串
     */
    @Test
    public void testRemotePolicyRejectsUrlQuery() {
        SmartRedisLimiterProperties properties = createValidProperties();
        properties.getRemotePolicy().setEnable(true);
        properties.getRemotePolicy().setSnapshotUrl("https://management.internal/policy-snapshot?serviceCode=test");

        SmartRedisLimiterConfigurationException exception = assertThrows(
                SmartRedisLimiterConfigurationException.class, properties::init);
        log.info("非法 URL 异常: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("snapshot-url"), "错误信息应包含完整配置路径");
    }

    /**
     * 测试远程策略正数配置和 core 窗口上限
     */
    @Test
    public void testRemotePolicyPositiveValuesAndCoreLimit() {
        SmartRedisLimiterProperties properties = createValidProperties();
        properties.getRemotePolicy().setEnable(true);
        properties.getRemotePolicy().setSnapshotUrl("http://management.internal/policy-snapshot");
        properties.getRemotePolicy().setMaxLimitsPerPolicy(
                SmartRedisLimiterConstant.MAX_LIMITS_PER_POLICY + 1);

        SmartRedisLimiterConfigurationException exception = assertThrows(
                SmartRedisLimiterConfigurationException.class, properties::init);
        log.info("窗口上限异常: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("max-limits-per-policy"),
                "错误信息应包含完整配置路径");
    }

    /**
     * 测试 long 限流值和 Lua 安全整数边界
     */
    @Test
    public void testLongCountAndLuaSafeIntegerBoundary() {
        SmartRedisLimiterProperties properties = createValidProperties();
        SmartRedisLimiterProperties.SmartLimitRule rule =
                properties.getAnnotation().getDefaultLimits().get(0);
        rule.setCount(SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER);

        log.info("Lua 安全整数边界 count: {}", rule.getCount());
        assertDoesNotThrow(properties::init, "2^53-1 应通过验证");

        rule.setCount(SmartRedisLimiterStarterConstant.LUA_MAX_SAFE_INTEGER + 1L);
        SmartRedisLimiterConfigurationException exception = assertThrows(
                SmartRedisLimiterConfigurationException.class, properties::init);
        log.info("Lua 安全整数越界异常: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("count"), "错误信息应包含 count 配置路径");
    }

    /**
     * 测试拒绝等价重复时间窗口
     */
    @Test
    public void testEquivalentDuplicateWindowsRejected() {
        SmartRedisLimiterProperties properties = createValidProperties();
        SmartRedisLimiterProperties.SmartLimitRule equivalent = new SmartRedisLimiterProperties.SmartLimitRule();
        equivalent.setCount(200L);
        equivalent.setWindow(1L);
        equivalent.setUnit(SmartRedisLimiterTimeUnit.MINUTES);
        properties.getAnnotation().getDefaultLimits().add(equivalent);

        SmartRedisLimiterConfigurationException exception = assertThrows(
                SmartRedisLimiterConfigurationException.class, properties::init);
        log.info("等价窗口异常: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("60"), "错误信息应包含标准化窗口秒数");
    }

    /**
     * 测试时间单位为空时拒绝配置
     */
    @Test
    public void testNullTimeUnitRejected() {
        SmartRedisLimiterProperties properties = createValidProperties();
        properties.getAnnotation().getDefaultLimits().get(0).setUnit(null);

        SmartRedisLimiterConfigurationException exception = assertThrows(
                SmartRedisLimiterConfigurationException.class, properties::init);
        log.info("空时间单位异常: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("unit"), "错误信息应包含 unit 配置路径");
    }

    /**
     * 测试 me 和 resourceCode 复用 core 稳定编码校验
     */
    @Test
    public void testCoreStableCodeValidation() {
        SmartRedisLimiterProperties properties = createValidProperties();
        properties.setMe("invalid:service");

        SmartRedisLimiterConfigurationException meException = assertThrows(
                SmartRedisLimiterConfigurationException.class, properties::init);
        log.info("非法 me 异常: {}", meException.getMessage());
        assertTrue(meException.getMessage().contains(".me"), "错误信息应包含 me 完整配置路径");

        properties = createValidProperties();
        properties.setMode("interceptor");
        SmartRedisLimiterProperties.SmartInterceptorRule rule =
                new SmartRedisLimiterProperties.SmartInterceptorRule();
        rule.setPathPattern("/api/test/**");
        rule.setResourceCode("invalid:resource");
        properties.getInterceptor().getRules().add(rule);
        SmartRedisLimiterProperties finalProperties = properties;

        SmartRedisLimiterConfigurationException resourceException = assertThrows(
                SmartRedisLimiterConfigurationException.class, finalProperties::init);
        log.info("非法 resourceCode 异常: {}", resourceException.getMessage());
        assertTrue(resourceException.getMessage().contains("resource-code"),
                "错误信息应包含 resource-code 完整配置路径");
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
        rule.setCount(100L);
        rule.setWindow(60L);
        rule.setUnit(SmartRedisLimiterTimeUnit.SECONDS);

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
