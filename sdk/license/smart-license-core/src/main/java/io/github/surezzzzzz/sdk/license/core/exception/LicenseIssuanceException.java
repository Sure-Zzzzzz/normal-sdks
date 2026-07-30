package io.github.surezzzzzz.sdk.license.core.exception;

/**
 * License 签发异常。
 *
 * @author surezzzzzz
 */
public class LicenseIssuanceException extends SmartLicenseException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建签发异常。
     *
     * @param errorCode 稳定错误码
     * @param message   安全错误信息
     */
    public LicenseIssuanceException(String errorCode, String message) {
        super(errorCode, message);
    }
}
