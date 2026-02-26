package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.exception.SimpleAkskSecurityException;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Simple AKSK Security Exception Handler
 * <p>
 * 处理 SimpleAkskSecurityException，将其转换为 403 FORBIDDEN 响应
 *
 * @author surezzzzzz
 */
@Slf4j
@RestControllerAdvice
@SimpleAkskServerComponent
public class SimpleAkskSecurityExceptionHandler {

    /**
     * 处理 SimpleAkskSecurityException
     *
     * @param ex SimpleAkskSecurityException
     * @return 403 FORBIDDEN 响应
     */
    @ExceptionHandler(SimpleAkskSecurityException.class)
    public ResponseEntity<String> handleSimpleAkskSecurityException(SimpleAkskSecurityException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}
