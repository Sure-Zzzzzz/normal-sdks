package io.github.surezzzzzz.sdk.auth.aksk.client.core.manager;

/**
 * 支持多 AKP Profile 的 Token 管理器契约
 *
 * <p>旧 {@link TokenManager} 继续表达默认链路；只有实现本接口的管理器才能使用命名 Profile。
 *
 * @author surezzzzzz
 */
public interface ProfileTokenManager extends TokenManager {

    /**
     * 获取指定 Profile 的当前 Token
     *
     * @param profile Profile 名称；default 表示顶层默认凭据
     * @return Access Token
     */
    String getToken(String profile);

    /**
     * 清除指定 Profile 当前安全上下文的 Token 缓存
     *
     * @param profile Profile 名称；default 表示顶层默认凭据
     */
    void clearToken(String profile);

    /**
     * 判断 Profile 是否已配置
     *
     * @param profile Profile 名称
     * @return true 表示已配置
     */
    boolean containsProfile(String profile);
}
