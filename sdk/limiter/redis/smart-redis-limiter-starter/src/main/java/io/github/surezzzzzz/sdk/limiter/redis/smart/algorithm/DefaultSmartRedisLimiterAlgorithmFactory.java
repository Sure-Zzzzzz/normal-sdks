package io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认算法工厂实现
 *
 * @author Sure.
 * @Date: 2026-05-08
 */
@SmartRedisLimiterComponent
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart", name = "enable", havingValue = "true")
@Slf4j
public class DefaultSmartRedisLimiterAlgorithmFactory implements SmartRedisLimiterAlgorithmFactory {

    @Autowired
    private SmartRedisLimiterFixedWindowAlgorithm fixedWindowAlgorithm;

    @Autowired
    private SmartRedisLimiterSlidingWindowAlgorithm slidingWindowAlgorithm;

    @Override
    public SmartRedisLimiterAlgorithm getAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isEmpty()) {
            algorithm = SmartRedisLimiterConstant.ALGORITHM_FIXED;
        }

        switch (algorithm) {
            case SmartRedisLimiterConstant.ALGORITHM_SLIDING:
                log.debug("使用滑动窗口算法");
                return slidingWindowAlgorithm;
            case SmartRedisLimiterConstant.ALGORITHM_FIXED:
            default:
                log.debug("使用固定窗口算法");
                return fixedWindowAlgorithm;
        }
    }
}
