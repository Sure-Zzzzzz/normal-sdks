package io.github.surezzzzzz.sdk.auth.iam.server.controller;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IAM 全局异常处理
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RestControllerAdvice
public class IamExceptionHandler {

    /**
     * IAM 业务异常统一出口（401/403/404/409 等按错误码映射）
     */
    @ExceptionHandler(SimpleIamServerException.class)
    public ResponseEntity<Map<String, Object>> handleIamException(SimpleIamServerException e) {
        log.warn("IAM business exception: {}", e.getMessage());
        return ResponseEntity.status(resolveStatus(e.getErrorCode())).body(errorBody(e.getMessage()));
    }

    private HttpStatus resolveStatus(String errorCode) {
        if (ErrorCode.USER_NOT_FOUND.equals(errorCode)
                || ErrorCode.DEPARTMENT_NOT_FOUND.equals(errorCode)
                || ErrorCode.USER_GROUP_NOT_FOUND.equals(errorCode)
                || ErrorCode.MESSAGE_NOT_FOUND.equals(errorCode)
                || ErrorCode.TRUSTED_APPLICATION_NOT_FOUND.equals(errorCode)
                || ErrorCode.TRUSTED_APPLICATION_CLIENT_NOT_BELONG.equals(errorCode)
                || ErrorCode.RESOURCE_VERIFICATION_CLIENT_NOT_FOUND.equals(errorCode)
                || ErrorCode.RESOURCE_VERIFICATION_CLIENT_NOT_BELONG.equals(errorCode)
                || ErrorCode.APPLICATION_AUTHORIZATION_NOT_FOUND.equals(errorCode)
                || ErrorCode.APPLICATION_MANIFEST_NOT_FOUND.equals(errorCode)
                || io.github.surezzzzzz.sdk.auth.iam.core.constant.ErrorCode.EXTERNAL_PROVIDER_NOT_FOUND
                .equals(errorCode)) {
            return HttpStatus.NOT_FOUND;
        }
        if (ErrorCode.USER_ALREADY_EXISTS.equals(errorCode)
                || ErrorCode.DEPARTMENT_ALREADY_EXISTS.equals(errorCode)
                || ErrorCode.DEPARTMENT_DELETE_BLOCKED.equals(errorCode)
                || ErrorCode.LAST_ADMIN_PROTECTED.equals(errorCode)
                || ErrorCode.USER_GROUP_ALREADY_EXISTS.equals(errorCode)
                || ErrorCode.TRUSTED_APPLICATION_ID_EXISTS.equals(errorCode)
                || ErrorCode.TRUSTED_APPLICATION_CODE_EXISTS.equals(errorCode)
                || ErrorCode.RESOURCE_VERIFICATION_CLIENT_ID_EXISTS.equals(errorCode)
                || ErrorCode.RESOURCE_VERIFICATION_CLIENT_REVOKED.equals(errorCode)
                || ErrorCode.TRUSTED_APPLICATION_DELETE_BLOCKED.equals(errorCode)
                || ErrorCode.APPLICATION_AUTHORIZATION_CONFLICT.equals(errorCode)
                || ErrorCode.APPLICATION_MANIFEST_CONFLICT.equals(errorCode)) {
            return HttpStatus.CONFLICT;
        }
        if (ErrorCode.MESSAGE_FORBIDDEN.equals(errorCode)
                || ErrorCode.AUTHORIZE_CONTEXT_FORBIDDEN.equals(errorCode)
                || ErrorCode.PERMISSION_DENIED.equals(errorCode)) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 请求体不可读（JSON 解析失败）出口
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody("请求体格式错误"));
    }

    /**
     * AccessDenied 出口
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(Exception e) {
        log.warn("API 访问拒绝（权限不足）：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody(ServerErrorMessage.PERMISSION_DENIED));
    }

    /**
     * 开放 API DATA 消费失败关闭出口（评估失败 / 范围不足 / 计划不可执行）
     */
    @ExceptionHandler(io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDataPermissionDenied(
            io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception.DataPermissionAccessDeniedException e) {
        log.warn("DATA 访问拒绝（失败关闭）：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody(ServerErrorMessage.PERMISSION_DENIED));
    }

    /**
     * 兜底异常出口
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("IAM unexpected exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("服务器内部错误"));
    }

    /**
     * 限流 429 的唯一出口：组件默认 handler 已显式关闭
     * （limiter.management.enable-default-exception-handler=false，与兜底 advice 平序不可控），
     * 由本类统一接管并保持与 IAM 错误体一致的 code/message/retryAfter 结构。
     */
    @ExceptionHandler(SmartRedisLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimited(SmartRedisLimitExceededException e) {
        log.warn("IAM 限流触发: key={}, retryAfter={}s", e.getKey(), e.getRetryAfter());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", SmartRedisLimiterConstant.HTTP_STATUS_TOO_MANY_REQUESTS);
        body.put("message", SmartRedisLimiterConstant.HTTP_MESSAGE_TOO_MANY_REQUESTS);
        body.put("retryAfter", e.getRetryAfter());
        ResponseEntity.BodyBuilder builder = ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header(SmartRedisLimiterConstant.HEADER_RETRY_AFTER, String.valueOf(e.getRetryAfter()));
        if (e.getLimit() > 0) {
            builder.header(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT, String.valueOf(e.getLimit()));
        }
        if (e.getRemaining() >= 0 && e.getLimit() > 0) {
            builder.header(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_REMAINING, String.valueOf(e.getRemaining()));
        }
        if (e.getResetAt() > 0) {
            builder.header(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_RESET, String.valueOf(e.getResetAt()));
        }
        return builder.body(body);
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("message", message);
        return body;
    }
}
