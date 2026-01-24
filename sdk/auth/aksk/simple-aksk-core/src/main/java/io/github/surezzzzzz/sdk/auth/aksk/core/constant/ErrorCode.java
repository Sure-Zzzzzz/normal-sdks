package io.github.surezzzzzz.sdk.auth.aksk.core.constant;

/**
 * Error Code Constants
 *
 * @author Sure
 * @since 1.0.0
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }

    // ==================== 参数验证错误 ====================

    /**
     * 参数验证失败
     */
    public static final String VALIDATION_FAILED = "VALIDATION_001";

    /**
     * 长度参数无效
     */
    public static final String INVALID_LENGTH = "VALIDATION_002";

    /**
     * ClientId 格式无效
     */
    public static final String INVALID_CLIENT_ID = "VALIDATION_003";

    /**
     * ClientSecret 格式无效
     */
    public static final String INVALID_CLIENT_SECRET = "VALIDATION_004";

    /**
     * Security Context 大小超限
     */
    public static final String SECURITY_CONTEXT_SIZE_EXCEEDED = "VALIDATION_005";

    // ==================== Client管理错误 ====================

    /**
     * Client创建失败
     */
    public static final String CLIENT_CREATE_FAILED = "CLIENT_001";

    /**
     * Client不存在
     */
    public static final String CLIENT_NOT_FOUND = "CLIENT_002";

    /**
     * Client删除失败
     */
    public static final String CLIENT_DELETE_FAILED = "CLIENT_003";

    /**
     * Client已存在
     */
    public static final String CLIENT_ALREADY_EXISTS = "CLIENT_004";
}
