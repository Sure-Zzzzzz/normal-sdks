package io.github.surezzzzzz.sdk.auth.aksk.client.core.constant;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;

/**
 * Simple AKSK Client Core Constants
 *
 * @author surezzzzzz
 */
public final class SimpleAkskClientCoreConstant {

    private SimpleAkskClientCoreConstant() {
        throw new UnsupportedOperationException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }

    // ==================== 配置相关常量 ====================

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.aksk.client";

    /**
     * 默认 Token 端点
     */
    public static final String DEFAULT_TOKEN_ENDPOINT = "/oauth2/token";

    /**
     * 默认 Token 过期前刷新时间（秒）
     */
    public static final int DEFAULT_REFRESH_BEFORE_EXPIRE = 300;

    // ==================== OAuth2 相关常量 ====================

    /**
     * OAuth2 参数名: grant_type
     */
    public static final String PARAM_GRANT_TYPE = "grant_type";

    /**
     * OAuth2 Grant Type: client_credentials
     */
    public static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";

    /**
     * OAuth2 Token Type
     */
    public static final String TOKEN_TYPE_BEARER = "Bearer";

    // ==================== HTTP Header 相关常量 ====================

    /**
     * Authorization Header
     */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /**
     * Authorization Header 前缀模板: "Bearer %s"
     */
    public static final String HEADER_AUTHORIZATION_TEMPLATE = "Bearer %s";

    // ==================== Session 相关常量 ====================

    /**
     * Session 中存储 Token 的 Key
     */
    public static final String SESSION_TOKEN_KEY = "simple_aksk_access_token";

    /**
     * Session 中存储 Token 过期时间的 Key
     */
    public static final String SESSION_TOKEN_EXPIRY_KEY = "simple_aksk_token_expiry";
}
