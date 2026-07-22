package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller;

import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.annotation.SmartRedisLimiterManagementComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterManagementErrorResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementValidationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterPolicyConflictException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterPolicyNotFoundException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterManagementTimeHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Management API 统一异常处理器
 *
 * @author surezzzzzz
 */
@Slf4j
@RestControllerAdvice
@SmartRedisLimiterManagementComponent
public class SmartRedisLimiterManagementExceptionHandler {

    /**
     * 处理策略不存在
     */
    @ExceptionHandler(SmartRedisLimiterPolicyNotFoundException.class)
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleNotFound(
            SmartRedisLimiterPolicyNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /**
     * 处理策略冲突
     */
    @ExceptionHandler(SmartRedisLimiterPolicyConflictException.class)
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleConflict(
            SmartRedisLimiterPolicyConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage());
    }

    /**
     * 处理请求校验异常
     */
    @ExceptionHandler(SmartRedisLimiterManagementValidationException.class)
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleValidation(
            SmartRedisLimiterManagementValidationException exception) {
        log.warn("SmartRedisLimiter Management 请求校验失败", exception);
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * 处理 management 服务端异常
     */
    @ExceptionHandler(SmartRedisLimiterManagementException.class)
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleManagement(
            SmartRedisLimiterManagementException exception) {
        log.error("SmartRedisLimiter Management 服务异常", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessage.PERSISTENCE_FAILED);
    }

    /**
     * 处理 core 协议校验异常
     */
    @ExceptionHandler(SmartRedisLimiterException.class)
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleCore(
            SmartRedisLimiterException exception) {
        log.warn("SmartRedisLimiter Management 协议校验失败", exception);
        return response(HttpStatus.BAD_REQUEST,
                String.format(ErrorMessage.POLICY_VALIDATION_FAILED, exception.getMessage()));
    }

    /**
     * 处理未分类异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleUnexpected(Exception exception) {
        log.error("SmartRedisLimiter Management 未分类异常", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessage.PERSISTENCE_FAILED);
    }

    private ResponseEntity<SmartRedisLimiterManagementErrorResponse> response(
            HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(SmartRedisLimiterManagementErrorResponse.builder()
                        .message(message)
                        .timestamp(SmartRedisLimiterManagementTimeHelper.nowMillis())
                        .build());
    }
}
