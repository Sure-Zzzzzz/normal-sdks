package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.test.exception;

import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.exception.AkskSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器（仅用于测试）
 *
 * <p>处理 AkskSecurityException 并返回 403 状态码
 *
 * <p>注意：此类仅用于测试，不会被打包到 starter 中
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 AkskSecurityException
     */
    @ExceptionHandler(AkskSecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleAkskSecurityException(AkskSecurityException e) {
        log.warn("Security check failed: {}", e.getMessage());
        Map<String, Object> result = new HashMap<>();
        result.put("error", "Forbidden");
        result.put("message", e.getMessage());
        result.put("status", 403);
        return result;
    }
}
