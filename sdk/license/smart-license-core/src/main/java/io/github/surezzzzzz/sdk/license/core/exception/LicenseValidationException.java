package io.github.surezzzzzz.sdk.license.core.exception;

/**
 * License 输入或协议校验异常。
 *
 * @author surezzzzzz
 */
public class LicenseValidationException extends SmartLicenseException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建输入或协议校验异常。
     *
     * @param errorCode 稳定错误码
     * @param message   安全错误信息
     */
    public LicenseValidationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
