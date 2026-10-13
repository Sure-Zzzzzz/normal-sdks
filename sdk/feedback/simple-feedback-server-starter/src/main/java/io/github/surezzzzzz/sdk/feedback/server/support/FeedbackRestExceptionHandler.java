package io.github.surezzzzzz.sdk.feedback.server.support;

import io.github.surezzzzzz.sdk.feedback.server.annotation.SimpleFeedbackServerComponent;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.feedback.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.feedback.server.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST 错误映射（规范 §3.5：状态只由 HTTP status 表达；错误体只含安全消息、时间与请求标识，
 * 不暴露内部错误码、异常类型、SQL 与认证材料）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleFeedbackServerComponent
@RestControllerAdvice
public class FeedbackRestExceptionHandler {

    private static HttpStatus mapStatus(SimpleFeedbackServerException exception) {
        String code = exception.getErrorCode();
        // 资源不存在按错误码先判（走查纠错：裸 SimpleFeedbackServerException 抛出的 NOT_FOUND 此前落到默认 400）
        if (ErrorCode.SUBMISSION_NOT_FOUND.equals(code) || ErrorCode.APPLICATION_NOT_FOUND.equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if (exception instanceof SubmissionValidationException || exception instanceof ApplicationNotAvailableException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (exception instanceof AttachmentRejectException) {
            if (ErrorCode.ATTACHMENT_MEDIA_TYPE_REJECTED.equals(code)) {
                return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            }
            if (ErrorCode.ATTACHMENT_SIZE_EXCEEDED.equals(code)
                    || ErrorCode.ATTACHMENT_TOTAL_SIZE_EXCEEDED.equals(code)) {
                return HttpStatus.PAYLOAD_TOO_LARGE;
            }
            return HttpStatus.BAD_REQUEST;
        }
        if (exception instanceof FeedbackStateException || exception instanceof IdempotencyConflictException) {
            return HttpStatus.CONFLICT;
        }
        if (exception instanceof SubmissionRateLimitException) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (exception instanceof FeedbackDependencyException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", message);
        body.put("timestamp", ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toString());
        body.put("correlationId", currentCorrelationId());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 关联 ID：缺头由服务端生成（修正⑱；响应回显由控制器/过滤器协作完成）。
     *
     * @return 关联 ID
     */
    public static String currentCorrelationId() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String existing = request.getHeader("X-Correlation-Id");
            if (existing != null && !existing.trim().isEmpty()) {
                return existing;
            }
        }
        return UUID.randomUUID().toString();
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
        if (status.is5xxServerError()) {
            // 外部依赖故障已按失败关闭处理：WARN 记类别不打堆栈（规范 §12.5 级别语义）
            log.warn("反馈服务依赖故障 fail-close errorCode={} httpStatus={}", exception.getErrorCode(),
                    status.value());
        } else {
            log.info("反馈业务拒绝 errorCode={} httpStatus={}", exception.getErrorCode(), status.value());
        }
        return build(status, exception.getMessage());
    }

    /**
     * multipart 层超限（总量超限 413，由 Spring multipart 配置承载）。
     *
     * @param exception 超限异常
     * @return 错误响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUpload(MaxUploadSizeExceededException exception) {
        log.info("反馈附件请求超限 httpStatus=413");
        return build(HttpStatus.PAYLOAD_TOO_LARGE, ErrorMessage.REQUEST_TOO_LARGE);
    }
}
