package io.github.surezzzzzz.sdk.auth.aksk.server.handler;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ManagementAccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 管理 REST 数据范围不足响应处理。
 *
 * @author surezzzzzz
 */
@RestControllerAdvice
@SimpleAkskServerComponent
public class ManagementAccessDeniedExceptionHandler {

    /**
     * 返回禁止访问状态。
     *
     * @param exception 数据范围不足异常
     * @return 禁止访问响应
     */
    @ExceptionHandler(ManagementAccessDeniedException.class)
    public ResponseEntity<Void> handleManagementAccessDenied(ManagementAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
