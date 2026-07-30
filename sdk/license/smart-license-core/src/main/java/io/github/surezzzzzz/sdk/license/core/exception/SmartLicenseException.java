package io.github.surezzzzzz.sdk.license.core.exception;

import lombok.Getter;

/**
 * License Core 基础异常。
 *
 * @author surezzzzzz
 */
@Getter
public class SmartLicenseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 稳定错误码。
     */
    private final String errorCode;

    /**
     * 创建不包含底层异常链的安全异常。
     *
     * @param errorCode 稳定错误码
     * @param message   安全错误信息
     */
    public SmartLicenseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
