package io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller;

import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.annotation.SmartRedisLimiterManagementComponent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterManagementErrorResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.*;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterManagementTimeHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Management API 统一异常处理器
 *
 * @author surezzzzzz
 */
@Slf4j
@RestControllerAdvice(assignableTypes = {SmartRedisLimiterPolicyController.class,
        SmartRedisLimiterPolicyAdminController.class, SmartRedisLimiterPolicyPortalController.class})
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
        log.debug("SmartRedisLimiter Management 请求校验失败 category={}", exception.getErrorCode());
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * 处理 management 服务端异常
     */
    @ExceptionHandler(SmartRedisLimiterTypedServiceUnavailableException.class)
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleTypedServiceUnavailable(
            SmartRedisLimiterTypedServiceUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

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
        log.debug("SmartRedisLimiter Management 协议校验失败 category={}", exception.getErrorCode());
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

    /**
     * 权限拒绝保持 403，不被兜底异常包装为 500。
     */
    @ExceptionHandler(io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException.class)
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleDataPermissionDenied(
            io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException exception) {
        // DATA 注解链拒绝（数据权限不足/评估失败）：与业务拒绝同为 403，不落入兜底 500
        return response(HttpStatus.FORBIDDEN, ErrorMessage.ACCESS_DENIED);
    }

    @ExceptionHandler({SmartRedisLimiterManagementAccessDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleDenied(Exception exception) {
        log.debug("SmartRedisLimiter Management 权限拒绝");
        return response(HttpStatus.FORBIDDEN, ErrorMessage.ACCESS_DENIED);
    }

    /**
     * 格式与缺少参数返回安全的 400。
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleBadRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, ErrorMessage.REQUEST_INVALID);
    }

    /**
     * 保留 HTTP 方法错误及 Allow 响应头。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleMethod(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(exception.getSupportedHttpMethods() == null ? new org.springframework.http.HttpMethod[0]
                        : exception.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]))
                .body(SmartRedisLimiterManagementErrorResponse.builder().message(ErrorMessage.METHOD_NOT_ALLOWED)
                        .timestamp(SmartRedisLimiterManagementTimeHelper.nowMillis()).build());
    }

    /**
     * 保留媒体类型错误的 415。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<SmartRedisLimiterManagementErrorResponse> handleMediaType(Exception exception) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorMessage.MEDIA_TYPE_NOT_SUPPORTED);
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
