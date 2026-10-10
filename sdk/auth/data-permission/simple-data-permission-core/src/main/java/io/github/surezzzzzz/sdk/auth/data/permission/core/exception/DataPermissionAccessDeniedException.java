package io.github.surezzzzzz.sdk.auth.data.permission.core.exception;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.ErrorCode;

/**
 * 数据权限拒绝异常（契约形态，1.2.0 起）。
 *
 * <p>纯 RuntimeException 不携带 Spring Web 语义：HTTP 403 的呈现由装配件负责
 * （MVC 线薄壳保留 @ResponseStatus，另注册兜底 advice）。调用方捕获与
 * {@code @ExceptionHandler} 应面向本类型——装配件抛出的薄壳是其子类，天然命中。</p>
 *
 * @author surezzzzzz
 */
public class DataPermissionAccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 数据权限错误码。
     */
    private final String errorCode;

    /**
     * 创建数据权限拒绝异常（默认错误码）。
     *
     * @param message 拒绝原因
     */
    public DataPermissionAccessDeniedException(String message) {
        this(ErrorCode.DATA_ACCESS_DENIED, message);
    }

    /**
     * 创建数据权限拒绝异常。
     *
     * @param errorCode 数据权限错误码
     * @param message   拒绝原因
     */
    public DataPermissionAccessDeniedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 获取数据权限错误码。
     *
     * @return 错误码
     */
    public String getErrorCode() {
        return errorCode;
    }
}
