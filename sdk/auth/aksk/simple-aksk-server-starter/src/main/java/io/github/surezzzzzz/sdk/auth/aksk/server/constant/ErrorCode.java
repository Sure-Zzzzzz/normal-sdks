package io.github.surezzzzzz.sdk.auth.aksk.server.constant;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;

/**
 * Error Code Constants
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new AkskException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }

    // ==================== 配置错误 ====================

    /**
     * 配置验证失败
     */
    public static final String CONFIG_VALIDATION_FAILED = "CONFIG_001";


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
