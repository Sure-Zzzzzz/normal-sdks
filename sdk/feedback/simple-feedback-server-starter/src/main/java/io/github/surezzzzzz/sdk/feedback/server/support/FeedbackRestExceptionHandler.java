package io.github.surezzzzzz.sdk.feedback.server.support;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.controller.ApplicationController;
import io.github.surezzzzzz.sdk.feedback.server.controller.SubmissionController;
import io.github.surezzzzzz.sdk.feedback.server.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * REST 错误映射（规范 §3.5：状态只由 HTTP status 表达；错误体只含安全消息、时间与请求标识，
 * 不暴露内部错误码、异常类型、SQL 与认证材料）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
@RestControllerAdvice(assignableTypes = {SubmissionController.class, ApplicationController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeedbackRestExceptionHandler {

    private static final int MAX_ERROR_CAUSE_DEPTH = 3;
    private static final int MAX_ERROR_STACK_FRAMES = 4;
    private static final int MAX_STACK_NAME_LENGTH = 128;
    private static final String CORRELATION_ID_ATTRIBUTE =
            FeedbackRestExceptionHandler.class.getName() + ".correlationId";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static HttpStatus mapStatus(SimpleFeedbackServerException exception) {
        String code = exception.getErrorCode();
        if (ErrorCode.COLLABORATION_ASSEMBLY_INCOMPLETE.equals(code)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (ErrorCode.SUBMISSION_NOT_FOUND.equals(code) || ErrorCode.APPLICATION_NOT_FOUND.equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if (ErrorCode.SCORE_OUT_OF_RANGE.equals(code) || ErrorCode.CONTENT_INVALID.equals(code)
                || ErrorCode.EMAIL_INVALID.equals(code) || ErrorCode.FIELD_TOO_LONG.equals(code)
                || ErrorCode.ENVIRONMENT_KEY_REJECTED.equals(code) || ErrorCode.APPLICATION_CODE_INVALID.equals(code)
                || ErrorCode.ENUM_VALUE_INVALID.equals(code) || ErrorCode.REQUIRED_FIELD_MISSING.equals(code)
                || ErrorCode.INVALID_REQUEST.equals(code)
                || ErrorCode.ATTACHMENT_NOT_ENABLED.equals(code) || ErrorCode.ATTACHMENT_COUNT_EXCEEDED.equals(code)
                || ErrorCode.APPLICATION_NOT_AVAILABLE.equals(code)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ErrorCode.ATTACHMENT_MEDIA_TYPE_REJECTED.equals(code)) {
            return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        }
        if (ErrorCode.ATTACHMENT_SIZE_EXCEEDED.equals(code)
                || ErrorCode.ATTACHMENT_TOTAL_SIZE_EXCEEDED.equals(code)) {
            return HttpStatus.PAYLOAD_TOO_LARGE;
        }
        if (ErrorCode.ILLEGAL_STATUS_TRANSITION.equals(code) || ErrorCode.ROW_VERSION_CONFLICT.equals(code)
                || ErrorCode.IDEMPOTENCY_DIGEST_MISMATCH.equals(code)
                || ErrorCode.APPLICATION_ALREADY_REGISTERED.equals(code)
                || ErrorCode.IAM_SOURCED_FIELD_LOCKED.equals(code)) {
            return HttpStatus.CONFLICT;
        }
        if (ErrorCode.RATE_LIMITED.equals(code)) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (ErrorCode.DEPENDENCY_UNAVAILABLE.equals(code)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    static ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        String correlationId = currentCorrelationId();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", message);
        body.put("timestamp", ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toString());
        body.put("correlationId", correlationId);
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", correlationId);
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    /**
     * 关联 ID：缺头由服务端生成；响应回显由控制器/过滤器协作完成。
     *
     * @return 关联 ID
     */
    public static String currentCorrelationId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            Object cached = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
            if (cached instanceof String && SAFE_CORRELATION_ID.matcher((String) cached).matches()) {
                return (String) cached;
            }
            String existing = request.getHeader("X-Correlation-Id");
            String correlationId = existing != null && SAFE_CORRELATION_ID.matcher(existing).matches()
                    ? existing : UUID.randomUUID().toString();
            request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
            return correlationId;
        }
        return UUID.randomUUID().toString();
    }

    // 仅保留定位用的类型与栈帧位置；异常消息和 Throwable 对象都不能进入日志。
    static String safeStackSummary(Throwable failure) {
        StringJoiner chain = new StringJoiner(" <- ", "[", "]");
        Throwable current = failure;
        for (int depth = 0; depth < MAX_ERROR_CAUSE_DEPTH && current != null; depth++) {
            StringJoiner frames = new StringJoiner(", ", "[", "]");
            StackTraceElement[] stack = current.getStackTrace();
            for (int index = 0; index < Math.min(stack.length, MAX_ERROR_STACK_FRAMES); index++) {
                StackTraceElement frame = stack[index];
                frames.add(safeStackName(frame.getClassName()) + "#"
                        + safeStackName(frame.getMethodName()) + ":" + frame.getLineNumber());
            }
            chain.add(safeStackName(current.getClass().getName()) + frames);
            current = current.getCause();
        }
        return chain.toString();
    }

    private static String safeStackName(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_STACK_NAME_LENGTH) {
            return "?";
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9') || character == '_' || character == '$'
                    || character == '.' || character == '<' || character == '>')) {
                return "?";
            }
        }
        return name;
    }

    /**
     * 领域异常统一映射。
     *
     * @param exception 领域异常
     * @return 错误响应
     */
    @ExceptionHandler(SimpleFeedbackServerException.class)
    public ResponseEntity<Map<String, Object>> handleDomain(SimpleFeedbackServerException exception) {
        HttpStatus status = mapStatus(exception);
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error("反馈服务内部异常 errorCode={} exceptionType={} httpStatus={} correlationId={} stack={}",
                    exception.getErrorCode(), exception.getClass().getSimpleName(), status.value(),
                    currentCorrelationId(), safeStackSummary(exception));
            return build(status, ErrorMessage.INTERNAL_ERROR);
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            log.warn("反馈服务依赖故障 fail-close errorCode={} httpStatus={} correlationId={}",
                    exception.getErrorCode(), status.value(), currentCorrelationId());
        } else {
            log.info("反馈业务拒绝 errorCode={} httpStatus={}", exception.getErrorCode(), status.value());
        }
        return build(status, exception.getMessage());
    }

}
