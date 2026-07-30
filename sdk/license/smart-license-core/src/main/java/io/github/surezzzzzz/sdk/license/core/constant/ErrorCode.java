package io.github.surezzzzzz.sdk.license.core.constant;

/**
 * License Core 错误码常量。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    // ==================== 业务错误 ====================

    /**
     * 输入或协议校验失败。
     */
    public static final String VALIDATION_FAILED = "BIZ_001";
    /**
     * 密钥映射不可用或不一致。
     */
    public static final String KEY_MAPPING_FAILED = "BIZ_002";
    /**
     * 签发过程失败。
     */
    public static final String ISSUANCE_FAILED = "BIZ_003";
    /**
     * payload 编码失败。
     */
    public static final String SERIALIZATION_FAILED = "BIZ_004";

    private ErrorCode() {
        throw new UnsupportedOperationException(SmartLicenseCoreConstant.MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
