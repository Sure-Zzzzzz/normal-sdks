package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.test.SmartRedisLimiterTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.test.service.TestService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 注解（Aspect）模式降级端到端测试
 *
 * <p>application.yaml 通过 Redis Route 将专用 fallback key 路由到坏端口
 * datasource（localhost:16399 无人监听），让所有请求真实走生产执行器的 fallback 路径，
 * 验证 @SmartRedisLimiter 注解方法在 Redis/route 异常时按 allow/deny 策略处理。
 * 不通过桩执行器或停 Redis 模拟故障，统一靠死端口真实复现 Redis 不可用。
 *
 * @author Sure.
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterTestApplication.class)
public class SmartLimiterAnnotationFallbackTest {

    @Autowired
    private TestService testService;

    /**
     * 测试1：查询方法（fallback=allow）- Redis异常时放行
     */
    @Test
    public void testQueryMethodFallbackAllow() {
        log.info("=== 测试查询方法降级策略ALLOW ===");

        String result = testService.fallbackAllowMethod("fallback-allow");
        log.info("注解显式 allow 降级结果: {}", result);
        assertEquals("fallback_allow_success", result);

        log.info("=== 查询方法降级ALLOW测试通过 ===");
    }

    /**
     * 测试2：创建订单方法（fallback=deny）- Redis异常时拒绝
     */
    @Test
    public void testCreateOrderFallbackDeny() {
        log.info("=== 测试创建订单降级策略DENY ===");

        log.info("调用注解显式 deny 降级方法");
        assertThrows(SmartRedisLimitExceededException.class, () ->
                testService.fallbackDenyMethod("fallback-deny"));

        log.info("=== 创建订单降级DENY测试通过 ===");
    }

    /**
     * 测试3：未显式配置 fallback 时使用注解模式默认 allow
     */
    @Test
    public void testAnnotationDefaultFallbackAllow() {
        log.info("=== 测试注解模式默认降级策略ALLOW ===");

        String result = testService.fallbackDefaultMethod("fallback-default");
        log.info("注解默认 allow 降级结果: {}", result);
        assertEquals("fallback_default_success", result);

        log.info("=== 注解模式默认降级策略测试通过 ===");
    }
}
