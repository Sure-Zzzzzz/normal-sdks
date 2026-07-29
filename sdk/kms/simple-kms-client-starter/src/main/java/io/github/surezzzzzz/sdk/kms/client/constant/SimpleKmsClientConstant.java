package io.github.surezzzzzz.sdk.kms.client.constant;

/**
 * Simple KMS Client 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleKmsClientConstant {

    // ==================== 配置与默认 传输 边界 ====================

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.kms.client";
    public static final String API_BASE_PATH = "/api/v1/kms";
    public static final String HTTP_CLIENT_BEAN_NAME = "simpleKmsClientHttpClient";
    public static final String REST_TEMPLATE_BEAN_NAME = "simpleKmsClientRestTemplate";
    public static final boolean DEFAULT_ENABLED = false;
    public static final int DEFAULT_MAX_TOTAL = 50;
    public static final int DEFAULT_MAX_PER_ROUTE = 20;
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;
    public static final int DEFAULT_CONNECTION_REQUEST_TIMEOUT_MILLIS = 3000;
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;
    public static final int DEFAULT_MAX_REQUEST_BYTES = 2 * 1024 * 1024;
    public static final int DEFAULT_MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    // ==================== 逻辑密钥与算法契约 ====================

    public static final String PURPOSE_SIGN = "SIGN";
    public static final String PURPOSE_ENCRYPT = "ENCRYPT";
    public static final String ALGORITHM_ES256 = "ES256";
    public static final String ALGORITHM_AES_256_GCM = "AES_256_GCM";
    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_DISABLED = "DISABLED";
    public static final String STATE_RETIRED = "RETIRED";
    public static final String STATE_PENDING_DESTRUCTION = "PENDING_DESTRUCTION";
    public static final String STATE_DESTROYED = "DESTROYED";

    // ==================== 策略操作契约 ====================

    public static final String OPERATION_VERIFY = "VERIFY";
    public static final String OPERATION_DECRYPT = "DECRYPT";
    public static final String OPERATION_READ_PUBLIC_KEY = "READ_PUBLIC_KEY";
    public static final String OPERATION_CREATE_KEY = "CREATE_KEY";
    public static final String OPERATION_ROTATE_KEY = "ROTATE_KEY";
    public static final String OPERATION_CHANGE_KEY_STATE = "CHANGE_KEY_STATE";
    public static final String OPERATION_SCHEDULE_KEY_DESTRUCTION = "SCHEDULE_KEY_DESTRUCTION";
    public static final String OPERATION_CANCEL_KEY_DESTRUCTION = "CANCEL_KEY_DESTRUCTION";
    public static final String OPERATION_CREATE_KEY_POLICY = "CREATE_KEY_POLICY";
    public static final String OPERATION_REVOKE_KEY_POLICY = "REVOKE_KEY_POLICY";
    public static final String OPERATION_PROCESS_KEY_DESTRUCTION = "PROCESS_KEY_DESTRUCTION";

    // ==================== 对外安全错误消息 ====================

    public static final String MESSAGE_INVALID_CONFIGURATION = "KMS Client 配置不合法";
    public static final String MESSAGE_INVALID_REQUEST = "KMS Client 请求不合法";
    public static final String MESSAGE_SERVICE_ERROR = "KMS 服务请求失败";
    public static final String MESSAGE_ERROR_RESPONSE = "KMS 服务返回错误响应";
    public static final String MESSAGE_PROTOCOL_ERROR = "KMS 服务响应不符合协议";
    public static final String MESSAGE_RESPONSE_TOO_LARGE = "KMS 服务响应超过允许范围";
    public static final String MESSAGE_TRANSPORT_ERROR = "KMS 服务通信失败";

    // ==================== 固定 API 资源路径 ====================

    public static final String RESOURCE_KEYS = "keys";
    public static final String RESOURCE_CRYPTO = "crypto";
    public static final String RESOURCE_SIGNATURES = "signatures";
    public static final String RESOURCE_VERIFICATIONS = "verifications";
    public static final String RESOURCE_ENVELOPES = "envelopes";
    public static final String RESOURCE_DECRYPTIONS = "decryptions";
    /**
     * 仅作为 URL path segment 的状态资源名，不可与 JSON 字段状态混用。
     */
    public static final String RESOURCE_STATE = "state";
    public static final String RESOURCE_VERSIONS = "versions";
    public static final String RESOURCE_DESTRUCTION = "destruction";
    public static final String RESOURCE_POLICIES = "policies";
    public static final String RESOURCE_PUBLIC_KEY = "public-key";
    public static final String RESOURCE_PUBLIC_KEYS = "public-keys";

    // ==================== 请求头与 传输协议 字段 ====================

    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String FIELD_KEY_REF = "keyRef";
    public static final String FIELD_KEY_ALIAS = "keyAlias";
    public static final String FIELD_PURPOSE = "purpose";
    public static final String FIELD_ALGORITHM = "algorithm";
    /**
     * JSON 与查询参数中的逻辑密钥或版本状态字段。
     */
    public static final String FIELD_STATE = "state";
    public static final String FIELD_ACTIVE_VERSION = "activeVersion";
    public static final String FIELD_ROW_VERSION = "rowVersion";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_UPDATED_AT = "updatedAt";
    public static final String FIELD_EXPECTED_ROW_VERSION = "expectedRowVersion";
    public static final String FIELD_DESTROY_AFTER = "destroyAfter";
    public static final String FIELD_POLICY_ID = "policyId";
    public static final String FIELD_PRINCIPAL_ID = "principalId";
    /**
     * 密码学和公钥资源中实际使用的版本字段。
     */
    public static final String FIELD_VERSION = "version";
    /**
     * 策略资源限定目标逻辑密钥版本时使用的字段。
     */
    public static final String FIELD_KEY_VERSION = "keyVersion";
    public static final String FIELD_OPERATION = "operation";
    public static final String FIELD_EXPIRES_AT = "expiresAt";
    public static final String FIELD_INPUT = "input";
    public static final String FIELD_SIGNATURE = "signature";
    public static final String FIELD_VALID = "valid";
    public static final String FIELD_PLAINTEXT = "plaintext";
    public static final String FIELD_AAD = "aad";
    public static final String FIELD_ENVELOPE = "envelope";
    public static final String FIELD_PUBLIC_KEY = "publicKey";
    public static final String FIELD_ITEMS = "items";
    public static final String QUERY_ALIAS = "alias";
    public static final String FIELD_PAGE = "page";
    public static final String FIELD_SIZE = "size";
    public static final String FIELD_TOTAL = "total";
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_REQUEST_ID = "requestId";
    public static final String FIELD_TIMESTAMP = "timestamp";

    // ==================== HTTP 状态与编码限制 ====================

    public static final int HTTP_STATUS_SUCCESS_MIN = 200;
    public static final int HTTP_STATUS_SUCCESS_MAX_EXCLUSIVE = 300;
    public static final int HTTP_STATUS_NO_CONTENT = 204;
    public static final int HTTP_STATUS_BAD_REQUEST = 400;
    public static final int HTTP_STATUS_UNAUTHORIZED = 401;
    public static final int HTTP_STATUS_FORBIDDEN = 403;
    public static final int HTTP_STATUS_NOT_FOUND = 404;
    public static final int HTTP_STATUS_METHOD_NOT_ALLOWED = 405;
    public static final int HTTP_STATUS_CONFLICT = 409;
    public static final int HTTP_STATUS_PAYLOAD_TOO_LARGE = 413;
    public static final int HTTP_STATUS_UNSUPPORTED_MEDIA_TYPE = 415;
    public static final int HTTP_STATUS_UNPROCESSABLE_ENTITY = 422;
    public static final int HTTP_STATUS_SERVER_ERROR = 500;
    /**
     * 响应流累计读取的固定缓冲区大小。
     */
    public static final int RESPONSE_BUFFER_BYTES = 4096;
    /**
     * 无 padding Base64url 编码长度预估使用的字节分组大小。
     */
    public static final int BASE64_GROUP_BYTES = 3;
    public static final int BASE64_GROUP_CHARACTERS = 4;
    public static final char BASE64_PADDING = '=';

    /**
     * 常量类不允许实例化。
     */
    private SimpleKmsClientConstant() {
    }
}
