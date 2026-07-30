package io.github.surezzzzzz.sdk.license.core.constant;

/**
 * License Core 错误信息常量。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    // ==================== 业务错误 ====================

    /**
     * 输入或协议校验失败。
     */
    public static final String VALIDATION_FAILED = "License 输入或协议校验失败：%s";
    /**
     * 密钥映射不可用或不一致。
     */
    public static final String KEY_MAPPING_FAILED = "License 密钥映射不可用或不一致：%s";
    /**
     * 签发过程失败。
     */
    public static final String ISSUANCE_FAILED = "License 签发失败：%s";
    /**
     * payload 编码失败。
     */
    public static final String SERIALIZATION_FAILED = "License payload 编码失败：%s";

    private ErrorMessage() {
        throw new UnsupportedOperationException(SmartLicenseCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
