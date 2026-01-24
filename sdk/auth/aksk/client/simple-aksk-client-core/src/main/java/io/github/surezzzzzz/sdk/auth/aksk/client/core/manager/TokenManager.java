package io.github.surezzzzzz.sdk.auth.aksk.client.core.manager;

/**
 * Token Manager Interface
 * <p>
 * 提供统一的 token 获取能力
 *
 * @author surezzzzzz
 */
public interface TokenManager {

    /**
     * 获取当前请求的 token
     * <p>
     * 自动处理缓存和刷新
     *
     * @return Access Token
     */
    String getToken();

    /**
     * 清除当前用户的 token 缓存
     * <p>
     * 用于用户登出或切换租户时
     */
    void clearToken();
}
