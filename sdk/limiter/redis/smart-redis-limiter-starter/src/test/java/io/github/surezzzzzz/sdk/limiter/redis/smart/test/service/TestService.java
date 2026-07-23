package io.github.surezzzzzz.sdk.limiter.redis.smart.test.service;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimitRule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterFallbackStrategy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
                    @SmartRedisLimitRule(count = 10, window = 1, unit = SmartRedisLimiterTimeUnit.SECONDS)
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
                    @SmartRedisLimitRule(count = 10, window = 1, unit = SmartRedisLimiterTimeUnit.SECONDS),
                    @SmartRedisLimitRule(count = 100, window = 1, unit = SmartRedisLimiterTimeUnit.MINUTES)
            }
    )
    public String multiWindowMethod(String param) {
        log.debug("执行多窗口限流方法，参数: {}", param);
        return "success";
    }

    /**
     * 降级测试方法（fallback=allow）
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 5, window = 10, unit = SmartRedisLimiterTimeUnit.SECONDS)
            },
            fallback = SmartRedisLimiterFallbackStrategy.ALLOW_CODE
    )
    public String fallbackAllowMethod(String param) {
        log.info("执行 fallbackAllowMethod: {}", param);
        return "fallback_allow_success";
    }

    /**
     * 降级测试方法（fallback=deny）
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 3, window = 10, unit = SmartRedisLimiterTimeUnit.SECONDS)
            },
            fallback = SmartRedisLimiterFallbackStrategy.DENY_CODE
    )
    public String fallbackDenyMethod(String param) {
        log.info("执行 fallbackDenyMethod: {}", param);
        return "fallback_deny_success";
    }

    /**
     * 降级测试方法（使用注解模式默认策略）
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 10, window = 10, unit = SmartRedisLimiterTimeUnit.SECONDS)
            }
    )
    public String fallbackDefaultMethod(String param) {
        log.info("执行 fallbackDefaultMethod: {}", param);
        return "fallback_default_success";
    }

    /**
     * 滑动窗口多时间窗口限流（3次/1秒 + 10次/1分钟）
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 3, window = 1, unit = SmartRedisLimiterTimeUnit.SECONDS),
                    @SmartRedisLimitRule(count = 10, window = 1, unit = SmartRedisLimiterTimeUnit.MINUTES)
            },
            algorithm = "sliding"
    )
    public String slidingWindowMultiWindowMethod(String param) {
        log.debug("执行滑动窗口多窗口限流方法，参数: {}", param);
        return "sliding_multi_success";
    }

    /**
     * 固定窗口限流（与滑动窗口对比用，相同参数：5次/1秒）
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 5, window = 1, unit = SmartRedisLimiterTimeUnit.SECONDS)
            },
            algorithm = "fixed"
    )
    public String fixedWindowMethod(String param) {
        log.debug("执行固定窗口限流方法，参数: {}", param);
        return "fixed_success";
    }

    /**
     * 滑动窗口限流
     */
    @SmartRedisLimiter(
            rules = {
                    @SmartRedisLimitRule(count = 5, window = 1, unit = SmartRedisLimiterTimeUnit.SECONDS)
            },
            algorithm = "sliding"
    )
    public String slidingWindowMethod(String param) {
        log.debug("执行滑动窗口限流方法，参数: {}", param);
        return "sliding_success";
    }
}
