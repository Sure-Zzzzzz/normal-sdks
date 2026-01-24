package io.github.surezzzzzz.sdk.auth.aksk.client.core.constant;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;

/**
 * Client Error Message Constants
 *
 * @author surezzzzzz
 */
public final class ClientErrorMessage {

    private ClientErrorMessage() {
        throw new UnsupportedOperationException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }

    // ==================== Token 相关错误 ====================

    /**
     * Token 获取失败模板: "Token 获取失败: %s"
     */
    public static final String TOKEN_FETCH_FAILED = "Token 获取失败: %s";

    /**
     * Token 解析失败模板: "Token 解析失败: %s"
     */
    public static final String TOKEN_PARSE_FAILED = "Token 解析失败: %s";

    /**
     * Token 已过期
     */
    public static final String TOKEN_EXPIRED = "Token 已过期";

    /**
     * Token 分布式锁获取失败: "分布式锁获取失败且缓存中无Token"
     */
    public static final String TOKEN_LOCK_FAILED = "分布式锁获取失败且缓存中无Token";

    /**
     * Token 分布式锁等待中断
     */
    public static final String TOKEN_LOCK_INTERRUPTED = "分布式锁等待过程中被中断";

    // ==================== 配置相关错误 ====================

    /**
     * 配置缺失模板: "缺少必需配置: %s"
     */
    public static final String CONFIG_MISSING = "缺少必需配置: %s";

    /**
     * 配置无效模板: "配置无效: %s"
     */
    public static final String CONFIG_INVALID = "配置无效: %s";

    // ==================== 网络相关错误 ====================

    /**
     * HTTP 请求失败模板: "HTTP 请求失败: %s"
     */
    public static final String HTTP_REQUEST_FAILED = "HTTP 请求失败: %s";

    /**
     * HTTP 响应无效模板: "HTTP 响应无效: %s"
     */
    public static final String HTTP_RESPONSE_INVALID = "HTTP 响应无效: %s";
}
