package io.github.surezzzzzz.sdk.limiter.redis.smart.service;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimitRule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterFallbackStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author: Sure.
 * @description 测试服务
 * @Date: 2024/12/XX XX:XX
 */
@Service
@Slf4j
public class TestService {

    /**
     * 基础限流方法（不配置降级，使用默认）
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 10, window = 1, unit = TimeUnit.SECONDS)
            }
    )
    public String limitedMethod(String param) {
        log.debug("执行限流方法，参数: {}", param);
        return "success";
    }

    /**
     * 多时间窗口限流：1秒10次 + 1分钟100次
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 10, window = 1, unit = TimeUnit.SECONDS),
                    @SmartRedisLimitRule(count = 100, window = 1, unit = TimeUnit.MINUTES)
            }
    )
    public String multiWindowMethod(String param) {
        log.debug("执行多窗口限流方法，参数: {}", param);
        return "success";
    }

    /**
     * ✅ 查询方法（fallback=allow）
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 5, window = 10, unit = TimeUnit.SECONDS)
            },
            fallback = SmartRedisLimiterFallbackStrategy.ALLOW_CODE
    )
    public String queryMethod(String param) {
        log.info("执行 queryMethod: {}", param);
        return "query_success";
    }

    /**
     * ✅ 创建订单（fallback=deny）
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 3, window = 10, unit = TimeUnit.SECONDS)
            },
            fallback = SmartRedisLimiterFallbackStrategy.DENY_CODE
    )
    public String createOrder(String orderId) {
        log.info("执行 createOrder: {}", orderId);
        return "order_created";
    }

    /**
     * ✅ 支付方法（fallback=deny）
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 2, window = 10, unit = TimeUnit.SECONDS)
            },
            fallback = SmartRedisLimiterFallbackStrategy.DENY_CODE
    )
    public String payment(String paymentId) {
        log.info("执行 payment: {}", paymentId);
        return "payment_success";
    }

    /**
     * ✅ 不配置降级策略（使用注解模式默认值）
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 10, window = 10, unit = TimeUnit.SECONDS)
            }
            // 不配置fallback，使用annotation.default-fallback
    )
    public String defaultFallbackMethod(String param) {
        log.info("执行 defaultFallbackMethod: {}", param);
        return "default_success";
    }
}
