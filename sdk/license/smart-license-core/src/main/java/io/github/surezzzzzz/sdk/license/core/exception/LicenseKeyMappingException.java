package io.github.surezzzzzz.sdk.license.core.exception;

/**
 * License 密钥映射异常。
 *
 * @author surezzzzzz
 */
public class LicenseKeyMappingException extends SmartLicenseException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建密钥映射异常。
     *
     * @param errorCode 稳定错误码
     * @param message   安全错误信息
     */
    public LicenseKeyMappingException(String errorCode, String message) {
        super(errorCode, message);
    }
}
