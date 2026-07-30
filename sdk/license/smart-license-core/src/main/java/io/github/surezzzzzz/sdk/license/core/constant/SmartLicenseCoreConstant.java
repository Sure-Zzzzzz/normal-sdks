package io.github.surezzzzzz.sdk.license.core.constant;

/**
 * License Core 常量。
 *
 * @author surezzzzzz
 */
public final class SmartLicenseCoreConstant {

    // ==================== 数值常量 ====================

    public static final int ZERO = 0;
    public static final int ONE = 1;
    public static final int BASE64URL_QUANTUM_LENGTH = 4;
    public static final int JWS_SEGMENT_COUNT = 3;
    public static final int ES256_JOSE_SIGNATURE_LENGTH = 64;
    public static final int ES256_JOSE_SIGNATURE_BASE64URL_LENGTH = 86;
    public static final int MAX_IDENTIFIER_LENGTH = 256;
    public static final int MAX_CUSTOMER_ID_LENGTH = 256;
    public static final int MAX_DEVICE_FINGERPRINT_LENGTH = 512;
    public static final int MAX_TERM_COUNT = 64;
    public static final int MAX_COMPACT_JWS_LENGTH = 65536;
    public static final int MAX_FEATURE_COUNT = 256;
    public static final int MAX_FEATURE_LENGTH = 256;
    public static final int MAX_METRIC_LENGTH = 128;
    public static final int MAX_TERM_TYPE_LENGTH = 128;
    public static final int MIN_POSITIVE_INTEGER = 1;

    // ==================== JWS 协议常量 ====================

    public static final String ALGORITHM_ES256 = "ES256";
    public static final String HEADER_TYPE_JWT = "JWT";
    public static final String JWS_SEPARATOR = ".";
    public static final String JWS_HEADER_TEMPLATE = "{\"alg\":\"ES256\",\"kid\":\"%s\",\"typ\":\"JWT\"}";
    public static final char JSON_ESCAPE = '\\';
    public static final char JSON_QUOTE = '"';
    public static final char BASE64URL_PADDING = '=';
    public static final char BASE64URL_MINUS = '-';
    public static final char BASE64URL_UNDERSCORE = '_';
    public static final char BASE64URL_UPPERCASE_BEGIN = 'A';
    public static final char BASE64URL_UPPERCASE_END = 'Z';
    public static final char BASE64URL_LOWERCASE_BEGIN = 'a';
    public static final char BASE64URL_LOWERCASE_END = 'z';
    public static final char BASE64URL_DIGIT_BEGIN = '0';
    public static final char BASE64URL_DIGIT_END = '9';
    // ==================== 密钥状态常量 ====================

    public static final String KMS_KEY_STATE_ACTIVE = "ACTIVE";
    public static final String KMS_KEY_STATE_RETIRED = "RETIRED";

    // ==================== 协议字段常量 ====================

    public static final String FIELD_ALGORITHM = "alg";
    public static final String FIELD_KEY_ID = "kid";
    public static final String FIELD_TYPE = "typ";
    public static final String FIELD_JTI = "jti";
    public static final String FIELD_ISSUER = "iss";
    public static final String FIELD_AUDIENCE = "aud";
    public static final String FIELD_ISSUED_AT = "iat";
    public static final String FIELD_NOT_BEFORE = "nbf";
    public static final String FIELD_EXPIRES_AT = "exp";
    public static final String FIELD_SCHEMA_VERSION = "schemaVersion";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_CUSTOMER_ID = "customerId";
    public static final String FIELD_DEVICE_KEY_FINGERPRINT = "deviceKeyFingerprint";
    public static final String FIELD_TERMS = "terms";
    public static final String FIELD_TERM_TYPE = "type";
    public static final String FIELD_FEATURES = "features";
    public static final String FIELD_METRIC = "metric";
    public static final String FIELD_LIMIT = "limit";
    public static final String FIELD_START_ON_ACTIVATION = "startOnActivation";
    public static final String FIELD_DURATION_DAYS = "durationDays";
    public static final String FIELD_COMPACT_JWS = "compactJws";
    public static final String FIELD_KMS_KEY_REF = "kmsKeyRef";
    public static final String FIELD_KMS_KEY_VERSION = "kmsKeyVersion";
    public static final String FIELD_PUBLIC_KEY = "publicKey";
    public static final String FIELD_KEY_MAPPING = "keyMapping";
    public static final String FIELD_PAYLOAD = "payload";
    // ==================== 内置条款类型常量 ====================

    public static final String TERM_TYPE_FEATURE_SET = "featureSet";
    public static final String TERM_TYPE_CAPACITY = "capacity";
    public static final String TERM_TYPE_TRIAL = "trial";

    // ==================== 安全错误详情常量 ====================

    public static final String DETAIL_CANNOT_BE_NULL = "%s 不能为 null";
    public static final String DETAIL_CANNOT_BE_BLANK = "%s 不能为空白";
    public static final String DETAIL_MAXIMUM_LENGTH = "%s 超出最大长度 %d";
    public static final String DETAIL_MAXIMUM_COUNT = "%s 超出最大数量 %d";
    public static final String DETAIL_INVALID_VALUE = "%s 不合法";
    public static final String DETAIL_MISMATCH = "%s 不一致";
    public static final String DETAIL_UNSUPPORTED_VALUE = "%s 不支持";
    public static final String DETAIL_KMS_RESULT_INVALID = "KMS 返回结果不符合映射约束";
    public static final String DETAIL_KEY_MAPPING_NOT_FOUND = "未找到 tenant 与 kid 对应的密钥映射";
    public static final String DETAIL_KEY_MAPPING_NOT_ACTIVE = "密钥映射不是 ACTIVE 状态";
    public static final String MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE = "常量类不能实例化";

    private SmartLicenseCoreConstant() {
        throw new UnsupportedOperationException(MESSAGE_CONSTANT_CLASS_CANNOT_INSTANTIATE);
    }
}
