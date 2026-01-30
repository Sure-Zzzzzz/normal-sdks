package io.github.surezzzzzz.sdk.auth.aksk.core.constant;

import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;

/**
 * Error Message Constants
 *
 * @author Sure
 * @since 1.0.0
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new AkskException(UTILITY_CLASS_INSTANTIATION);
    }

    // ==================== 工具类错误 ====================

    public static final String UTILITY_CLASS_INSTANTIATION = "工具类不能实例化";

    public static final String INVALID_LENGTH_ARGUMENT = "长度必须为正数";

    public static final String CLIENT_SETTINGS_SERIALIZE_FAILED = "ClientSettings序列化失败";

    public static final String TOKEN_SETTINGS_SERIALIZE_FAILED = "TokenSettings序列化失败";

    // ==================== 参数验证错误 ====================

    public static final String VALIDATION_FAILED = "参数验证失败：%s";

    public static final String INVALID_LENGTH = "长度必须为正数";

    public static final String INVALID_CLIENT_ID = "ClientId 格式无效：%s";

    public static final String INVALID_CLIENT_SECRET = "ClientSecret 格式无效：%s";

    public static final String SECURITY_CONTEXT_SIZE_EXCEEDED = "Security Context 大小超限：%d > %d";

    // ==================== Client管理错误 ====================

    public static final String CLIENT_CREATE_FAILED = "Client创建失败：%s";

    public static final String CLIENT_NOT_FOUND = "Client不存在：%s";

    public static final String CLIENT_DELETE_FAILED = "Client删除失败：%s";

    public static final String CLIENT_ALREADY_EXISTS = "Client已存在：%s";

    public static final String CLIENT_UPDATE_FAILED = "Client更新失败：%s";
}
