package io.github.surezzzzzz.sdk.kms.server.controller;

import io.github.surezzzzzz.sdk.kms.core.exception.*;
import io.github.surezzzzzz.sdk.kms.server.exception.KmsPayloadTooLargeException;
import io.github.surezzzzzz.sdk.kms.server.exception.KmsUnauthenticatedException;
import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalResolver;
import io.github.surezzzzzz.sdk.kms.server.service.KmsRequestContext;
import io.github.surezzzzzz.sdk.kms.server.support.KmsHttpJson;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KMS REST 安全错误响应处理器。
 *
 * @author surezzzzzz
 */
@RestControllerAdvice
public class KmsHttpExceptionHandler {

    private static final String JSON_UTF8 = "application/json;charset=UTF-8";
    private static final String REQUEST_ID_UNAVAILABLE = "request-id-unavailable";
    private static final String MESSAGE_INVALID_REQUEST = "请求参数不合法";
    private static final String MESSAGE_AUTHENTICATION_REQUIRED = "需要有效认证";
    private static final String MESSAGE_ACCESS_DENIED = "无权访问该资源";
    private static final String MESSAGE_RESOURCE_NOT_FOUND = "资源不存在";
    private static final String MESSAGE_STATE_CONFLICT = "资源状态冲突";
    private static final String MESSAGE_UNPROCESSABLE = "请求无法安全处理";
    private static final String MESSAGE_PAYLOAD_TOO_LARGE = "请求内容超过允许范围";
    private static final String MESSAGE_SERVICE_UNAVAILABLE = "服务暂不可用";

    private final KmsPrincipalResolver principalResolver;

    /**
     * 创建 REST 安全错误响应处理器。
     */
    public KmsHttpExceptionHandler(KmsPrincipalResolver principalResolver) {
        this.principalResolver = principalResolver;
    }

    /**
     * 返回参数格式错误。
     */
    @ExceptionHandler({KmsValidationException.class, MissingRequestHeaderException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<String> handleValidation(Exception exception, HttpServletRequest request) {
        return response(400, MESSAGE_INVALID_REQUEST, request);
    }

    /**
     * 返回携带认证挑战的未认证响应。
     */
    @ExceptionHandler(KmsUnauthenticatedException.class)
    public ResponseEntity<String> handleUnauthenticated(KmsUnauthenticatedException exception,
                                                        HttpServletRequest request) {
        return response(401, MESSAGE_AUTHENTICATION_REQUIRED, request, "WWW-Authenticate",
                exception.getWwwAuthenticate());
    }

    /**
     * 返回认证或资源级授权拒绝。
     */
    @ExceptionHandler(KmsAuthorizationException.class)
    public ResponseEntity<String> handleAuthorization(KmsAuthorizationException exception, HttpServletRequest request) {
        return response(403, MESSAGE_ACCESS_DENIED, request);
    }

    /**
     * 返回当前 tenant 可见管理资源不存在。
     */
    @ExceptionHandler(KmsNotFoundException.class)
    public ResponseEntity<String> handleNotFound(KmsNotFoundException exception, HttpServletRequest request) {
        return response(404, MESSAGE_RESOURCE_NOT_FOUND, request);
    }

    /**
     * 返回乐观锁、状态机、策略或幂等冲突。
     */
    @ExceptionHandler({KmsStateConflictException.class, KmsPolicyConflictException.class,
            KmsIdempotencyConflictException.class})
    public ResponseEntity<String> handleConflict(RuntimeException exception, HttpServletRequest request) {
        return response(409, MESSAGE_STATE_CONFLICT, request);
    }

    /**
     * 返回不支持的请求媒体类型。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<String> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception,
                                                             HttpServletRequest request) {
        return response(415, MESSAGE_INVALID_REQUEST, request);
    }

    /**
     * 返回不支持的资源方法。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<String> handleUnsupportedMethod(HttpRequestMethodNotSupportedException exception,
                                                          HttpServletRequest request) {
        String allow = exception.getSupportedMethods() == null ? null
                : org.springframework.util.StringUtils.arrayToDelimitedString(exception.getSupportedMethods(), ", ");
        return response(405, MESSAGE_INVALID_REQUEST, request, "Allow", allow);
    }

    /**
     * 返回超出二进制字段上限的请求。
     */
    @ExceptionHandler(KmsPayloadTooLargeException.class)
    public ResponseEntity<String> handlePayloadTooLarge(KmsPayloadTooLargeException exception,
                                                        HttpServletRequest request) {
        return response(413, MESSAGE_PAYLOAD_TOO_LARGE, request);
    }

    /**
     * 将密码学细节统一归并为不可处理语义。
     */
    @ExceptionHandler(KmsCryptoException.class)
    public ResponseEntity<String> handleCrypto(KmsCryptoException exception, HttpServletRequest request) {
        return response(422, MESSAGE_UNPROCESSABLE, request);
    }

    /**
     * 返回受控的持久化不可用语义。
     */
    @ExceptionHandler({KmsPersistenceException.class, KmsServiceUnavailableException.class})
    public ResponseEntity<String> handleUnavailable(RuntimeException exception, HttpServletRequest request) {
        return response(503, MESSAGE_SERVICE_UNAVAILABLE, request);
    }

    /**
     * 对未分类运行时异常执行安全归并，禁止暴露实现细节。
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleUnexpected(RuntimeException exception, HttpServletRequest request) {
        return response(503, MESSAGE_SERVICE_UNAVAILABLE, request);
    }

    /**
     * 构造固定安全错误体。
     */
    private ResponseEntity<String> response(int status, String message, HttpServletRequest request) {
        return response(status, message, request, null, null);
    }

    /**
     * 构造附带协议要求响应头的固定安全错误体。
     */
    private ResponseEntity<String> response(int status, String message, HttpServletRequest request, String headerName,
                                            String headerValue) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", message);
        body.put("timestamp", KmsHttpJson.utcMillis(Instant.now()));
        body.put("requestId", requestId(request));
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(JSON_UTF8));
        if (headerName != null && headerValue != null) {
            builder.header(headerName, headerValue);
        }
        return builder.body(KmsHttpJson.write(body));
    }

    /**
     * 尝试使用认证上下文的请求标识，失败时只返回固定安全占位值。
     */
    private String requestId(HttpServletRequest request) {
        Object cached = request.getAttribute(KmsHttpControllerSupport.REQUEST_CONTEXT_ATTRIBUTE);
        if (cached instanceof KmsRequestContext) {
            String requestId = ((KmsRequestContext) cached).getRequestId();
            return requestId == null ? REQUEST_ID_UNAVAILABLE : requestId;
        }
        try {
            KmsRequestContext context = principalResolver.resolve(request);
            return context == null || context.getRequestId() == null ? REQUEST_ID_UNAVAILABLE : context.getRequestId();
        } catch (RuntimeException exception) {
            return REQUEST_ID_UNAVAILABLE;
        }
    }
}
