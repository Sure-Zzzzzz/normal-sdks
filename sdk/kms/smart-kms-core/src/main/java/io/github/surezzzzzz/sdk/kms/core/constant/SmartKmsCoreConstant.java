package io.github.surezzzzzz.sdk.kms.core.constant;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * KMS Core 常量。
 *
 * @author surezzzzzz
 */
public final class SmartKmsCoreConstant {

    /**
     * 数字零。
     */
    public static final int ZERO = 0;
    /**
     * 数字一。
     */
    public static final int ONE = 1;
    /**
     * SKMS 魔数字符串。
     */
    public static final String SKMS_MAGIC_TEXT = "SKMS";
    /**
     * SKMS 魔数的内部不可变字节表示。
     */
    private static final byte[] SKMS_MAGIC_BYTES = SKMS_MAGIC_TEXT.getBytes(StandardCharsets.US_ASCII);
    /**
     * SKMS 格式版本。
     */
    public static final int SKMS_FORMAT_VERSION = 1;
    /**
     * AES-256-GCM 算法编码。
     */
    public static final int SKMS_AES_256_GCM_ALGORITHM_CODE = 1;
    /**
     * ES256 JOSE 签名长度。
     */
    public static final int ES256_JOSE_SIGNATURE_LENGTH = 64;
    /**
     * ES256 DER 签名最小长度。
     */
    public static final int ES256_MIN_DER_SIGNATURE_LENGTH = 8;
    /**
     * ES256 坐标长度。
     */
    public static final int ES256_COORDINATE_LENGTH = 32;
    /**
     * AES-256 密钥长度。
     */
    public static final int AES_256_KEY_LENGTH = 32;
    /**
     * GCM IV 长度。
     */
    public static final int GCM_IV_LENGTH = 12;
    /**
     * GCM Tag 长度。
     */
    public static final int GCM_TAG_LENGTH = 16;
    /**
     * keyRef 长度字段字节数。
     */
    public static final int SKMS_KEY_REF_LENGTH_FIELD_LENGTH = 2;
    /**
     * keyVersion 长度字段字节数。
     */
    public static final int SKMS_KEY_VERSION_FIELD_LENGTH = 4;
    /**
     * 外部 AAD 长度字段字节数。
     */
    public static final int EXTERNAL_AAD_LENGTH_FIELD_LENGTH = 4;
    /**
     * SKMS 固定头长度，不含 keyRef 与密文。
     */
    public static final int SKMS_FIXED_HEADER_LENGTH = 24;
    /**
     * 无符号 short 最大值。
     */
    public static final int UNSIGNED_SHORT_MAX_VALUE = 65535;
    /**
     * 无符号 int 最大值。
     */
    public static final long UNSIGNED_INT_MAX_VALUE = 4294967295L;
    /**
     * 十六进制基数。
     */
    public static final int HEX_RADIX = 16;
    /**
     * ASN.1 序列标签。
     */
    public static final int ASN1_SEQUENCE_TAG = 48;
    /**
     * ASN.1 整数标签。
     */
    public static final int ASN1_INTEGER_TAG = 2;
    /**
     * ASN.1 长度长格式标记。
     */
    public static final int ASN1_LONG_FORM_LENGTH_MASK = 128;
    /**
     * 单字节无符号掩码。
     */
    public static final int UNSIGNED_BYTE_MASK = 255;
    /**
     * ASN.1 P-256 坐标最大编码长度。
     */
    public static final int ES256_COORDINATE_MAX_DER_LENGTH = ES256_COORDINATE_LENGTH + 1;
    /**
     * tenantId 最大长度。
     */
    public static final int TENANT_ID_MAX_LENGTH = 64;
    /**
     * principalId 最大长度。
     */
    public static final int PRINCIPAL_ID_MAX_LENGTH = 128;
    /**
     * keyRef 最大长度。
     */
    public static final int KEY_REF_MAX_LENGTH = 64;
    /**
     * keyAlias 最大长度。
     */
    public static final int KEY_ALIAS_MAX_LENGTH = 128;
    /**
     * policyId 最大长度。
     */
    public static final int POLICY_ID_MAX_LENGTH = 64;
    /**
     * requestId 最大长度。
     */
    public static final int REQUEST_ID_MAX_LENGTH = 128;
    /**
     * 幂等键最大长度。
     */
    public static final int IDEMPOTENCY_KEY_MAX_LENGTH = 128;
    /**
     * 常量类禁止实例化消息。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";

    /**
     * 获取 SKMS 魔数字节副本。
     *
     * @return 4 字节 ASCII 魔数副本，调用方修改不会影响全局格式定义
     */
    public static byte[] getSkmsMagic() {
        return Arrays.copyOf(SKMS_MAGIC_BYTES, SKMS_MAGIC_BYTES.length);
    }

    private SmartKmsCoreConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
