package io.github.surezzzzzz.sdk.auth.aksk.client.core.constant;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;

/**
 * Simple AKSK Client Core Constants
 *
 * @author surezzzzzz
 */
public final class SimpleAkskClientCoreConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.aksk.client";

    // ==================== 配置相关常量 ====================
    /**
     * 默认 Token 端点
     */
    public static final String DEFAULT_TOKEN_ENDPOINT = "/oauth2/token";
    /**
     * OAuth2 参数名: grant_type
     */
    public static final String PARAM_GRANT_TYPE = "grant_type";

    // ==================== OAuth2 相关常量 ====================
    /**
     * OAuth2 Grant Type: client_credentials
     */
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    /**
     * OAuth2 Token Type
     */
    public static final String TOKEN_TYPE_BEARER = "Bearer";
    /**
     * Authorization Header
     */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    // ==================== HTTP Header 相关常量 ====================
    /**
     * Authorization Header 前缀模板: "Bearer %s"
     */
    public static final String HEADER_AUTHORIZATION_TEMPLATE = "Bearer %s";
    /**
     * Session 中存储 Token 的 Key
     */
    public static final String SESSION_TOKEN_KEY = "simple_aksk_access_token";

    // ==================== Session 相关常量 ====================
    /**
     * 默认连接超时（毫秒）
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;

    // ==================== HTTP 超时默认值 ====================
    /**
     * 默认读取超时（毫秒）
     */
    public static final int DEFAULT_READ_TIMEOUT_MS = 15000;
    /**
     * 分布式锁默认超时（秒）
     */
    public static final int DEFAULT_LOCK_TIMEOUT_SECONDS = 10;

    // ==================== 分布式锁 ====================
    /**
     * 锁等待重试间隔（毫秒）
     */
    public static final int LOCK_RETRY_SLEEP_MS = 100;
    /**
     * 锁等待最大重试次数（50 × 100ms = 5s）
     */
    public static final int LOCK_MAX_RETRY_TIMES = 50;
    /**
     * Token 刷新重试次数
     */
    public static final int TOKEN_REFRESH_RETRY_TIMES = 3;

    // ==================== Token 刷新重试策略 ====================
    /**
     * Token 刷新初始延迟（毫秒）
     */
    public static final long TOKEN_REFRESH_INITIAL_DELAY_MS = 1000L;
    /**
     * Token 刷新退避系数
     */
    public static final double TOKEN_REFRESH_BACKOFF_MULTIPLIER = 1.5;
    /**
     * Token 刷新最大延迟（毫秒）
     */
    public static final long TOKEN_REFRESH_MAX_DELAY_MS = 5000L;

    /**
     * Token 响应为空的描述
     */
    public static final String TOKEN_RESPONSE_EMPTY = "Token response is empty";

    /**
     * Token 响应过期时间无效的描述
     */
    public static final String TOKEN_RESPONSE_EXPIRY_INVALID = "Token response expires_in is invalid";

    private SimpleAkskClientCoreConstant() {
        throw new UnsupportedOperationException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }
}
