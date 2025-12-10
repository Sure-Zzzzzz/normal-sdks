package io.github.surezzzzzz.sdk.limiter.redis.smart.service;

import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimitRule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.annotation.SmartRedisLimiter;
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
     * 限流方法：1秒10次
     */
    @SmartRedisLimiter(rules = {
            @SmartRedisLimitRule(count = 10, window = 1, unit = TimeUnit.SECONDS)
    })
    public String limitedMethod(String param) {
        log.debug("执行限流方法，参数: {}", param);
        return "success";
    }

    /**
     * 多时间窗口限流：1秒10次 + 1分钟100次
     */
    @SmartRedisLimiter(rules = {
            @SmartRedisLimitRule(count = 10, window = 1, unit = TimeUnit.SECONDS),
            @SmartRedisLimitRule(count = 100, window = 1, unit = TimeUnit.MINUTES)
    })
    public String multiWindowMethod(String param) {
        log.debug("执行多窗口限流方法，参数: {}", param);
        return "success";
    }
}
