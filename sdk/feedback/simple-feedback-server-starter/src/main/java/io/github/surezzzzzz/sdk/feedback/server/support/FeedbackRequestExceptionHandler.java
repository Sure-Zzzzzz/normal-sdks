package io.github.surezzzzzz.sdk.feedback.server.support;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.controller.ApplicationController;
import io.github.surezzzzzz.sdk.feedback.server.controller.SubmissionController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

/**
 * 仅处理反馈端点的请求解析错误，避免改变宿主其他控制器的错误响应。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
@RestControllerAdvice(assignableTypes = {SubmissionController.class, ApplicationController.class})
public class FeedbackRequestExceptionHandler {

    /**
     * 身份解析失败与业务越权使用各自的 HTTP 状态，响应不回显认证异常细节。
     *
     * @param exception 认证异常
     * @return 401 错误响应
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException exception) {
        log.info("反馈身份解析失败 httpStatus=401");
        return FeedbackRestExceptionHandler.build(HttpStatus.UNAUTHORIZED, ErrorMessage.AUTHENTICATION_REQUIRED);
    }

    /**
     * 已认证主体类型不允许或业务越权时返回脱敏的禁止访问响应。
     *
     * @param exception 越权异常
     * @return 403 错误响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException exception) {
        log.info("反馈访问被拒 httpStatus=403");
        return FeedbackRestExceptionHandler.build(HttpStatus.FORBIDDEN, ErrorMessage.SUBJECT_NOT_ALLOWED);
    }

    /**
     * 请求解析失败不回传框架异常或原始请求内容。
     *
     * @param exception 请求解析异常
     * @return 400 错误响应
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class,
            MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(Exception exception) {
        log.info("反馈请求解析失败 httpStatus=400 exceptionType={}", exception.getClass().getSimpleName());
        return FeedbackRestExceptionHandler.build(HttpStatus.BAD_REQUEST, ErrorMessage.INVALID_REQUEST);
    }

    /**
     * 数据访问失败统一按依赖不可用返回，避免数据库故障被误报为内部错误。
     *
     * @param exception 数据访问异常
     * @return 503 错误响应
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessFailure(DataAccessException exception) {
        log.warn("反馈数据依赖故障 httpStatus=503 exceptionType={} correlationId={}",
                exception.getClass().getSimpleName(), FeedbackRestExceptionHandler.currentCorrelationId());
        return FeedbackRestExceptionHandler.build(HttpStatus.SERVICE_UNAVAILABLE,
                ErrorMessage.DEPENDENCY_UNAVAILABLE);
    }

    /**
     * 其他未预期故障只在反馈控制器范围内返回安全 500；领域异常由高优先级处理器先映射。
     *
     * @param exception 未预期故障
     * @return 500 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        log.error("反馈控制器内部异常 httpStatus=500 exceptionType={} correlationId={} stack={}",
                exception.getClass().getSimpleName(), FeedbackRestExceptionHandler.currentCorrelationId(),
                FeedbackRestExceptionHandler.safeStackSummary(exception));
        return FeedbackRestExceptionHandler.build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessage.INTERNAL_ERROR);
    }
}
