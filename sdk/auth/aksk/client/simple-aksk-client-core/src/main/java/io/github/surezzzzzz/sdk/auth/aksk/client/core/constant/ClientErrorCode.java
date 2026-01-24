package io.github.surezzzzzz.sdk.auth.aksk.client.core.constant;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;

/**
 * Client Error Code Constants
 *
 * @author surezzzzzz
 */
public final class ClientErrorCode {

    private ClientErrorCode() {
        throw new UnsupportedOperationException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }

    // ==================== Token 相关错误 ====================

    /**
     * Token 获取失败
     */
    public static final String TOKEN_FETCH_FAILED = "TOKEN_001";

    /**
     * Token 解析失败
     */
    public static final String TOKEN_PARSE_FAILED = "TOKEN_002";

    /**
     * Token 已过期
     */
    public static final String TOKEN_EXPIRED = "TOKEN_003";

    /**
     * Token 分布式锁获取失败
     */
    public static final String TOKEN_LOCK_FAILED = "TOKEN_004";

    /**
     * Token 分布式锁等待中断
     */
    public static final String TOKEN_LOCK_INTERRUPTED = "TOKEN_005";

    // ==================== 配置相关错误 ====================

    /**
     * 配置缺失
     */
    public static final String CONFIG_MISSING = "CONFIG_001";

    /**
     * 配置无效
     */
    public static final String CONFIG_INVALID = "CONFIG_002";

    // ==================== 网络相关错误 ====================

    /**
     * HTTP 请求失败
     */
    public static final String HTTP_REQUEST_FAILED = "HTTP_001";

    /**
     * HTTP 响应无效
     */
    public static final String HTTP_RESPONSE_INVALID = "HTTP_002";
}
