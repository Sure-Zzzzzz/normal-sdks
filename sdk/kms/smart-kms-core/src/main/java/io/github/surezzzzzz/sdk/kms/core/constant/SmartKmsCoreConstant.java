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
     * 审计元数据最大条数。
     */
    public static final int AUDIT_METADATA_MAX_ENTRIES = 7;
    /**
     * 审计元数据单值最大长度。
     */
    public static final int AUDIT_METADATA_VALUE_MAX_LENGTH = 32;
    /**
     * 审计元数据键：资源类型。
     */
    public static final String AUDIT_METADATA_KEY_RESOURCE_TYPE = "resourceType";
    /**
     * 审计元数据键：逻辑密钥状态。
     */
    public static final String AUDIT_METADATA_KEY_KEY_STATE = "keyState";
    /**
     * 审计元数据键：密钥版本状态。
     */
    public static final String AUDIT_METADATA_KEY_VERSION_STATE = "versionState";
    /**
     * 审计元数据键：输入长度。
     */
    public static final String AUDIT_METADATA_KEY_INPUT_LENGTH = "inputLength";
    /**
     * 审计元数据键：输出长度。
     */
    public static final String AUDIT_METADATA_KEY_OUTPUT_LENGTH = "outputLength";
    /**
     * 审计元数据键：失败类别。
     */
    public static final String AUDIT_METADATA_KEY_FAILURE_CATEGORY = "failureCategory";
    /**
     * 审计元数据键：幂等重放标记。
     */
    public static final String AUDIT_METADATA_KEY_IDEMPOTENCY_REPLAY = "idempotencyReplay";
    /**
     * 审计资源类型：逻辑密钥。
     */
    public static final String AUDIT_RESOURCE_TYPE_KEY = "KEY";
    /**
     * 审计资源类型：密钥策略。
     */
    public static final String AUDIT_RESOURCE_TYPE_KEY_POLICY = "KEY_POLICY";
    /**
     * 审计资源类型：密钥版本。
     */
    public static final String AUDIT_RESOURCE_TYPE_KEY_VERSION = "KEY_VERSION";
    /**
     * 审计资源类型：销毁任务。
     */
    public static final String AUDIT_RESOURCE_TYPE_DESTRUCTION_JOB = "DESTRUCTION_JOB";
    /**
     * 审计失败类别：参数校验。
     */
    public static final String AUDIT_FAILURE_CATEGORY_VALIDATION = "VALIDATION";
    /**
     * 审计失败类别：授权拒绝。
     */
    public static final String AUDIT_FAILURE_CATEGORY_AUTHORIZATION = "AUTHORIZATION";
    /**
     * 审计失败类别：资源不存在。
     */
    public static final String AUDIT_FAILURE_CATEGORY_NOT_FOUND = "NOT_FOUND";
    /**
     * 审计失败类别：状态冲突。
     */
    public static final String AUDIT_FAILURE_CATEGORY_STATE_CONFLICT = "STATE_CONFLICT";
    /**
     * 审计失败类别：策略冲突。
     */
    public static final String AUDIT_FAILURE_CATEGORY_POLICY_CONFLICT = "POLICY_CONFLICT";
    /**
     * 审计失败类别：幂等冲突。
     */
    public static final String AUDIT_FAILURE_CATEGORY_IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    /**
     * 审计失败类别：持久化失败。
     */
    public static final String AUDIT_FAILURE_CATEGORY_PERSISTENCE = "PERSISTENCE";
    /**
     * 审计失败类别：密码学失败。
     */
    public static final String AUDIT_FAILURE_CATEGORY_CRYPTOGRAPHIC = "CRYPTOGRAPHIC";
    /**
     * 审计失败类别：服务不可用。
     */
    public static final String AUDIT_FAILURE_CATEGORY_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
    /**
     * 审计布尔值：是。
     */
    public static final String AUDIT_BOOLEAN_TRUE = "true";
    /**
     * 审计布尔值：否。
     */
    public static final String AUDIT_BOOLEAN_FALSE = "false";
    /**
     * 销毁 worker 审计使用的固定系统主体标识。
     */
    public static final String AUDIT_SYSTEM_PRINCIPAL_ID = "KMS_SYSTEM";
    /**
     * 常量类禁止实例化消息。
     */
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";
    /**
     * SKMS 魔数的内部不可变字节表示。
     */
    private static final byte[] SKMS_MAGIC_BYTES = SKMS_MAGIC_TEXT.getBytes(StandardCharsets.US_ASCII);

    private SmartKmsCoreConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }

    /**
     * 获取 SKMS 魔数字节副本。
     *
     * @return 4 字节 ASCII 魔数副本，调用方修改不会影响全局格式定义
     */
    public static byte[] getSkmsMagic() {
        return Arrays.copyOf(SKMS_MAGIC_BYTES, SKMS_MAGIC_BYTES.length);
    }
}
