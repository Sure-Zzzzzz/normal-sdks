package io.github.surezzzzzz.sdk.limiter.redis.smart.exception;

import org.springframework.http.ResponseEntity;

/**
 * @author: Sure.
 * @description 智能限流异常处理器接口
 * @Date: 2024/12/XX XX:XX
 */
public interface SmartRedisLimiterExceptionHandler {

    /**
     * 处理限流异常
     *
     * @param ex 限流异常
     * @return 响应实体
     */
    ResponseEntity<?> handle(SmartRedisLimitExceededException ex);
}
