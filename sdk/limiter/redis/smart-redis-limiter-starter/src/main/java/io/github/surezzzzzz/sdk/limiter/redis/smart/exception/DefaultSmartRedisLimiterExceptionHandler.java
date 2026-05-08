package io.github.surezzzzzz.sdk.limiter.redis.smart.exception;

import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * @author: Sure.
 * @description 默认限流异常处理器（可选，需要显式开启）
 * @Date: 2024/12/XX XX:XX
 */
@RestControllerAdvice
@SmartRedisLimiterComponent
@ConditionalOnWebApplication
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.limiter.redis.smart.management",
        name = "enable-default-exception-handler",
        havingValue = "true",
        matchIfMissing = true
)
@Slf4j
public class DefaultSmartRedisLimiterExceptionHandler implements SmartRedisLimiterExceptionHandler {

    @ExceptionHandler(SmartRedisLimitExceededException.class)
    @Override
    public ResponseEntity<?> handle(SmartRedisLimitExceededException ex) {
        log.warn("SmartRedisLimiter 限流触发: key={}, retryAfter={}", ex.getKey(), ex.getRetryAfter());

        Map<String, Object> body = new HashMap<>();
        body.put("code", SmartRedisLimiterConstant.HTTP_STATUS_TOO_MANY_REQUESTS);
        body.put("message", SmartRedisLimiterConstant.HTTP_MESSAGE_TOO_MANY_REQUESTS);
        body.put("retryAfter", ex.getRetryAfter());

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header(SmartRedisLimiterConstant.HEADER_RETRY_AFTER, String.valueOf(ex.getRetryAfter()))
                .body(body);
    }
}
