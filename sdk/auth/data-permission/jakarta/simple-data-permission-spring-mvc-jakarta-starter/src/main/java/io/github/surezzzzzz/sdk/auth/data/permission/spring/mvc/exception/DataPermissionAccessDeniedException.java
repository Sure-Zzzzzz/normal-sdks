package io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 数据权限拒绝异常。
 *
 * @author surezzzzzz
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class DataPermissionAccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建数据权限拒绝异常。
     *
     * @param message 拒绝原因
     */
    public DataPermissionAccessDeniedException(String message) {
        super(message);
    }
}
