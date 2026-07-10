package io.github.surezzzzzz.sdk.mail.constant;

/**
 * Mail 错误码
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String CONFIGURATION_ERROR = "MAIL_001";
    public static final String VALIDATION_ERROR = "MAIL_002";
    public static final String PROVIDER_NOT_SUPPORTED = "MAIL_003";
    public static final String SEND_FAILED = "MAIL_004";
    public static final String CONNECT_FAILED = "MAIL_005";
    public static final String READ_FAILED = "MAIL_006";
    public static final String PARSE_FAILED = "MAIL_007";
    public static final String ATTACHMENT_FAILED = "MAIL_008";
    public static final String STATUS_CHANGE_FAILED = "MAIL_009";
}
