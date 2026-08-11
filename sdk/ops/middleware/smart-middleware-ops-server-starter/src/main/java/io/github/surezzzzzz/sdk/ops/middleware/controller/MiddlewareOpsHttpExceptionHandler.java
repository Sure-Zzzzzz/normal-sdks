package io.github.surezzzzzz.sdk.ops.middleware.controller;

import io.github.surezzzzzz.sdk.ops.middleware.annotation.SmartMiddlewareOpsServerComponent;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.controller.response.MiddlewareOpsErrorResponse;
import io.github.surezzzzzz.sdk.ops.middleware.exception.MiddlewareOpsException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;

/**
 * Middleware Ops HTTP 安全异常映射。
 *
 * @author surezzzzzz
 */
@SmartMiddlewareOpsServerComponent
@RestControllerAdvice(annotations = SmartMiddlewareOpsServerComponent.class)
public class MiddlewareOpsHttpExceptionHandler {

    /**
     * 映射已知安全异常。
     */
    @ExceptionHandler(MiddlewareOpsException.class)
    public ResponseEntity<MiddlewareOpsErrorResponse> handleMiddlewareOpsException(MiddlewareOpsException exception,
                                                                                   HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(response(exception.getMessage(), request));
    }

    /**
     * 隔离未预期异常细节。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<MiddlewareOpsErrorResponse> handleUnexpectedException(HttpServletRequest request) {
        return ResponseEntity.status(503).body(response("中间件运维查询暂不可用", request));
    }

    private MiddlewareOpsErrorResponse response(String message, HttpServletRequest request) {
        Object requestId = request.getAttribute(SmartMiddlewareOpsServerConstant.REQUEST_ID_HEADER);
        return MiddlewareOpsErrorResponse.builder().message(message).timestamp(Instant.now())
                .requestId(requestId == null ? null : requestId.toString()).build();
    }
}
