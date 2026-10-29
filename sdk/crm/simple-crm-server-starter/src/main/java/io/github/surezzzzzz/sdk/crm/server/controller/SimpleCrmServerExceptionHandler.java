package io.github.surezzzzzz.sdk.crm.server.controller;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CRM REST 错误映射：错误响应只含安全消息、时间与关联 ID；
 * 401/403 由公共资源安全链统一给出，不经本映射。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleCrmServerComponent
@RestControllerAdvice
public class SimpleCrmServerExceptionHandler {

    /**
     * 领域异常映射。
     *
     * @param exception 领域异常
     * @return HTTP 响应
     */
    @ExceptionHandler(CrmException.class)
    public ResponseEntity<Map<String, Object>> handleCrm(CrmException exception) {
        HttpStatus status = statusOf(exception.getErrorCode());
        log.warn("CRM 领域拒绝：errorCode={}，httpStatus={}", exception.getErrorCode(), status.value());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", safeMessage(status));
        body.put("occurredAt", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }

    /** 将请求体、日期、数字和参数类型解析失败统一映射为 400。 */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            DateTimeParseException.class,
            NumberFormatException.class,
            ClassCastException.class})
    public ResponseEntity<Map<String, Object>> handleMalformedRequest(Exception exception) {
        log.warn("CRM 请求解析失败：type={}", exception.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(errorBody(HttpStatus.BAD_REQUEST));
    }

    private Map<String, Object> errorBody(HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("message", safeMessage(status));
        body.put("occurredAt", Instant.now().toString());
        return body;
    }

    private HttpStatus statusOf(CrmErrorCode errorCode) {
        switch (errorCode) {
            case VALIDATION_FAILED:
            case INVALID_FORMAT:
                return HttpStatus.BAD_REQUEST;
            case NOT_FOUND_OR_NOT_ACCESSIBLE:
                return HttpStatus.NOT_FOUND;
            case AGGREGATE_VERSION_CONFLICT:
            case IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD:
            case INVALID_STATE_TRANSITION:
            case QUOTATION_EXPIRED:
            case QUOTATION_ALREADY_CONFIRMED:
            case FULFILLMENT_RESULT_CONSISTENCY_FAILURE:
                return HttpStatus.CONFLICT;
            case COMMERCIAL_CAPABILITY_UNAVAILABLE:
                return HttpStatus.SERVICE_UNAVAILABLE;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    private String safeMessage(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "资源不存在或不可访问";
        }
        if (status == HttpStatus.CONFLICT) {
            return "请求与当前资源状态冲突";
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return "请求参数校验失败";
        }
        if (status == HttpStatus.SERVICE_UNAVAILABLE) {
            return "依赖能力暂不可用";
        }
        return "服务内部错误";
    }
}
