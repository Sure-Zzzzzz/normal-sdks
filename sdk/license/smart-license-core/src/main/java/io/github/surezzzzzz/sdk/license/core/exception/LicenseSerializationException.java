package io.github.surezzzzzz.sdk.license.core.exception;

/**
 * License payload 编码异常。
 *
 * @author surezzzzzz
 */
public class LicenseSerializationException extends SmartLicenseException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建 payload 编码异常。
     *
     * @param errorCode 稳定错误码
     * @param message   安全错误信息
     */
    public LicenseSerializationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
