package io.github.surezzzzzz.sdk.auth.aksk.server.handler;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.ApplicationAuthorizationController;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ApplicationAuthorizationConflictException;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ApplicationAuthorizationNotFoundException;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ClientException;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.SimpleAkskServerException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 应用授权管理 REST 异常响应处理。
 *
 * @author surezzzzzz
 */
@RestControllerAdvice(assignableTypes = ApplicationAuthorizationController.class)
@SimpleAkskServerComponent
public class ApplicationAuthorizationExceptionHandler {

    /**
     * 返回应用授权投影冲突状态。
     *
     * @param exception 应用授权投影冲突异常
     * @return 冲突响应
     */
    @ExceptionHandler(ApplicationAuthorizationConflictException.class)
    public ResponseEntity<Void> handleConflict(ApplicationAuthorizationConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    /**
     * 返回应用授权投影不存在状态。
     *
     * @param exception 应用授权投影不存在异常
     * @return 未找到响应
     */
    @ExceptionHandler(ApplicationAuthorizationNotFoundException.class)
    public ResponseEntity<Void> handleNotFound(ApplicationAuthorizationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * 返回 Client 不存在状态。
     *
     * @param exception Client 不存在异常
     * @return 未找到响应
     */
    @ExceptionHandler(ClientException.class)
    public ResponseEntity<Void> handleClientNotFound(ClientException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * 返回数据范围不足状态。
     *
     * @param exception 数据范围不足异常
     * @return 禁止访问响应
     */
    @ExceptionHandler(ManagementAccessDeniedException.class)
    public ResponseEntity<Void> handleAccessDenied(ManagementAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    /**
     * 返回应用授权请求无效状态。
     *
     * @param exception 应用授权请求校验异常
     * @return 错误请求响应
     */
    @ExceptionHandler(SimpleAkskServerException.class)
    public ResponseEntity<Void> handleBadRequest(SimpleAkskServerException exception) {
        return ResponseEntity.badRequest().build();
    }

    /**
     * 返回应用授权并发冲突状态。
     *
     * @param exception 乐观锁冲突异常
     * @return 冲突响应
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Void> handleOptimisticLock(OptimisticLockingFailureException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
