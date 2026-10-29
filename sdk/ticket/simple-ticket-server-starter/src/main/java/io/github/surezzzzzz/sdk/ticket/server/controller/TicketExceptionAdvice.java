package io.github.surezzzzzz.sdk.ticket.server.controller;

import io.github.surezzzzzz.sdk.ticket.server.annotation.SimpleTicketServerComponent;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.ticket.server.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.ticket.server.constant.TicketServerLogConstant;
import io.github.surezzzzzz.sdk.ticket.server.dto.TicketErrorResponse;
import io.github.surezzzzzz.sdk.ticket.server.exception.SimpleTicketServerException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentNotFoundException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketAttachmentRejectException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketForbiddenActionException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketGroupHasOpenTicketsException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketGroupNotFoundException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketIdempotencyConflictException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketNotAccessibleException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketResourceConflictException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketStateException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketTemplateInvalidException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketTypeDisabledException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketTypeNotFoundException;
import io.github.surezzzzzz.sdk.ticket.server.exception.TicketValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.StringJoiner;

/**
 * 工单异常到 HTTP 状态及安全错误消息的映射。
 *
 * @author surezzzzzz
 */
@RestControllerAdvice(assignableTypes = {ManageAttachmentController.class, ManageTicketController.class,
        OpenApiTicketController.class, PortalAttachmentController.class, PortalTicketController.class,
        TicketAdminController.class})
@SimpleTicketServerComponent
@Slf4j
public class TicketExceptionAdvice {

    /**
     * 统一映射。
     *
     * @param e 领域异常
     * @return 响应
     */
    @ExceptionHandler(SimpleTicketServerException.class)
    public ResponseEntity<TicketErrorResponse> handle(SimpleTicketServerException e) {
        HttpStatus status = statusOf(e);
        TicketErrorResponse body = TicketErrorResponse.builder()
                .message(safeMessageOf(e))
                .time(Instant.now().toString())
                .build();
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            Throwable cause = e.getCause();
            String causeType = cause == null ? "none" : cause.getClass().getSimpleName();
            String causeStack = cause == null ? "[]" : stackSummary(cause);
            log.error("工单服务异常：code={}, causeType={}, stack={}, causeStack={}",
                    e.getErrorCode(), causeType, stackSummary(e), causeStack);
        } else {
            log.debug("工单业务拒绝：status={}", status);
        }
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 请求体、参数或必需请求头解析失败时返回统一的安全 400 响应。
     *
     * @param e 请求解析异常
     * @return 安全错误响应
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<TicketErrorResponse> handleInvalidRequest(Exception e) {
        log.debug("工单请求解析失败：exceptionType={}", e.getClass().getSimpleName());
        return errorResponse(HttpStatus.BAD_REQUEST, ErrorMessage.TICKET_VALIDATION_FAILED);
    }

    /**
     * 未包装的控制器异常统一返回安全 500，避免框架默认错误体泄露底层细节。
     *
     * @param e 未预期异常
     * @return 安全错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<TicketErrorResponse> handleUnexpected(Exception e) {
        log.error("工单控制器内部异常：exceptionType={}, stack={}",
                e.getClass().getSimpleName(), stackSummary(e));
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorMessage.TICKET_SERVICE_ERROR);
    }

    private ResponseEntity<TicketErrorResponse> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(TicketErrorResponse.builder()
                .message(message)
                .time(Instant.now().toString())
                .build());
    }

    private String stackSummary(Throwable error) {
        StringJoiner summary = new StringJoiner(", ", "[", "]");
        StackTraceElement[] frames = error.getStackTrace();
        for (int i = 0; i < Math.min(frames.length, TicketServerLogConstant.MAX_ERROR_STACK_FRAMES); i++) {
            StackTraceElement frame = frames[i];
            summary.add(frame.getClassName() + "#" + frame.getMethodName() + ":" + frame.getLineNumber());
        }
        return summary.toString();
    }

    private String safeMessageOf(SimpleTicketServerException e) {
        switch (e.getErrorCode()) {
            case ErrorCode.TICKET_TYPE_NOT_FOUND:
                return ErrorMessage.TICKET_TYPE_NOT_FOUND;
            case ErrorCode.TICKET_TYPE_DISABLED:
                return ErrorMessage.TICKET_TYPE_DISABLED;
            case ErrorCode.TICKET_GROUP_NOT_FOUND:
                return ErrorMessage.TICKET_GROUP_NOT_FOUND;
            case ErrorCode.TICKET_GROUP_HAS_OPEN_TICKETS:
                return ErrorMessage.TICKET_GROUP_HAS_OPEN_TICKETS;
            case ErrorCode.TICKET_STATE_CONFLICT:
                return ErrorMessage.TICKET_STATE_CONFLICT;
            case ErrorCode.TICKET_NOT_ACCESSIBLE:
                return ErrorMessage.TICKET_NOT_ACCESSIBLE;
            case ErrorCode.TICKET_FORBIDDEN_ACTION:
                return ErrorMessage.TICKET_FORBIDDEN_ACTION;
            case ErrorCode.TICKET_IDEMPOTENT_CONFLICT:
                return ErrorMessage.TICKET_IDEMPOTENT_CONFLICT;
            case ErrorCode.TICKET_RESOURCE_CONFLICT:
                return ErrorMessage.TICKET_RESOURCE_CONFLICT;
            case ErrorCode.TICKET_VALIDATION_FAILED:
                return ErrorMessage.TICKET_VALIDATION_FAILED;
            case ErrorCode.TICKET_TEMPLATE_INVALID:
                return ErrorMessage.TICKET_TEMPLATE_INVALID;
            case ErrorCode.TICKET_ATTACHMENT_REJECTED:
                return ErrorMessage.TICKET_ATTACHMENT_REJECTED;
            case ErrorCode.TICKET_ATTACHMENT_NOT_FOUND:
                return ErrorMessage.TICKET_ATTACHMENT_NOT_FOUND;
            default:
                return ErrorMessage.TICKET_SERVICE_ERROR;
        }
    }

    private HttpStatus statusOf(SimpleTicketServerException e) {
        if (e instanceof TicketNotAccessibleException || e instanceof TicketTypeNotFoundException
                || e instanceof TicketGroupNotFoundException || e instanceof TicketAttachmentNotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (e instanceof TicketValidationException || e instanceof TicketTemplateInvalidException
                || e instanceof TicketAttachmentRejectException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (e instanceof TicketIdempotencyConflictException || e instanceof TicketResourceConflictException
                || e instanceof TicketStateException
                || e instanceof TicketTypeDisabledException || e instanceof TicketGroupHasOpenTicketsException) {
            return HttpStatus.CONFLICT;
        }
        if (e instanceof TicketForbiddenActionException) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
