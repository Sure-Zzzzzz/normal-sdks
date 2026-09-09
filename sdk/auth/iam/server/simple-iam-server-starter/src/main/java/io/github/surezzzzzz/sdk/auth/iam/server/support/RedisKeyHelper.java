package io.github.surezzzzzz.sdk.auth.iam.server.support;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import lombok.RequiredArgsConstructor;

/**
 * Redis Key Helper
 *
 * <p>Key 格式：sure-auth-iam:{businessType}:{me}::{dataId}
 * <ul>
 *   <li>session: 会话存储</li>
 *   <li>login-flow: 登录失败追踪 + 锁定</li>
 *   <li>mfa-stepup: MFA step-up 凭证</li>
 *   <li>refresh-family: Refresh Token 族</li>
 *   <li>password-reset: 密码重置凭证</li>
 *   <li>rate-limit: 限流计数</li>
 *   <li>lock: 分布式锁</li>
 * </ul>
 *
 * <p>{me} 段使用 Redis HashTag 包裹（{me}），保证 Cluster 模式下同一 me 的 key 落在同一 slot。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class RedisKeyHelper {

    private static final String SEPARATOR_COLON = ":";
    private static final String SEPARATOR_DOUBLE_COLON = "::";
    private static final String BRACE_PREFIX = "{";
    private static final String BRACE_SUFFIX = "}";
    private final SimpleIamServerProperties properties;

    private String me() {
        return properties.getMe();
    }

    // ==================== Session ====================

    /**
     * 构建 Session key
     *
     * @param sessionId session id
     * @return Redis key: sure-auth-iam:session:{me}::{sessionId}
     */
    public String buildSessionKey(String sessionId) {
        return buildKey(SimpleIamServerConstant.BUSINESS_SESSION, sessionId);
    }

    // ==================== Authorization Context ====================

    /**
     * 构建授权交易上下文 key
     *
     * @param contextId 服务端生成的授权交易 ID
     * @return Redis key: sure-auth-iam:authorize-context:{me}::{contextId}
     */
    public String buildAuthorizeContextKey(String contextId) {
        return buildKey(SimpleIamServerConstant.BUSINESS_AUTHORIZE_CONTEXT, contextId);
    }

    // ==================== Login Flow ====================

    /**
     * 构建登录失败计数 key（用于锁定）
     *
     * @param username 用户名
     * @return Redis key: sure-auth-iam:login-flow:{me}::{username}
     */
    public String buildLoginFailureKey(String username) {
        return buildKey(SimpleIamServerConstant.BUSINESS_LOGIN_FLOW, username);
    }

    /**
     * 构建外部身份源登录失败计数 key（providerCode 维度，与本地密码计数隔离）
     *
     * @param providerCode 登录方式编码
     * @param username     用户名
     * @return Redis key: sure-auth-iam:login-flow:{me}::{providerCode}::{username}
     */
    public String buildExternalLoginFailureKey(String providerCode, String username) {
        return buildKey(SimpleIamServerConstant.BUSINESS_LOGIN_FLOW,
                providerCode + SEPARATOR_DOUBLE_COLON + username);
    }

    // ==================== MFA Step-up ====================

    /**
     * 构建 MFA step-up 凭证 key
     *
     * @param userId 用户 id
     * @return Redis key: sure-auth-iam:mfa-stepup:{me}::{userId}
     */
    public String buildMfaStepupKey(String userId) {
        return buildKey(SimpleIamServerConstant.BUSINESS_MFA_STEPUP, userId);
    }

    // ==================== Refresh Token Family ====================

    /**
     * 构建 Refresh Token 族 key
     *
     * @param familyId 族 id
     * @return Redis key: sure-auth-iam:refresh-family:{me}::{familyId}
     */
    public String buildRefreshFamilyKey(String familyId) {
        return buildKey(SimpleIamServerConstant.BUSINESS_REFRESH_FAMILY, familyId);
    }

    // ==================== Password Reset ====================

    /**
     * 构建密码重置凭证 key
     *
     * @param token 凭证 token
     * @return Redis key: sure-auth-iam:password-reset:{me}::{token}
     */
    public String buildPasswordResetKey(String token) {
        return buildKey(SimpleIamServerConstant.BUSINESS_PASSWORD_RESET, token);
    }

    // ==================== Rate Limit ====================

    /**
     * 构建限流 key
     *
     * @param key 限流维度 key（如 clientId、IP 等）
     * @return Redis key: sure-auth-iam:rate-limit:{me}::{key}
     */
    public String buildRateLimitKey(String key) {
        return buildKey(SimpleIamServerConstant.BUSINESS_RATE_LIMIT, key);
    }

    // ==================== Lock ====================

    /**
     * 构建分布式锁 key
     *
     * @param lockKey 锁 key（如 userId、resourceId）
     * @return Redis key: sure-auth-iam:lock:{me}::{lockKey}
     */
    public String buildLockKey(String lockKey) {
        return buildKey(SimpleIamServerConstant.BUSINESS_LOCK, lockKey);
    }

    // ==================== Pub/Sub Channel ====================

    /**
     * 构建跨实例广播 channel
     *
     * <p>格式：sure-auth-iam:{businessType}:{me}
     *
     * <p>channel 不携带 dataId（广播面向全体实例而非单条数据）；me 段与 key 家族
     * 保持一致，多应用共用 Redis 时不串扰。发布与订阅必须经同一 channel 字符串
     * 做 byKey 路由，保证落在同一数据源。
     *
     * @param businessType 业务类型
     * @return 广播 channel：sure-auth-iam:{businessType}:{me}
     */
    public String buildPubSubChannel(String businessType) {
        return SimpleIamServerConstant.KEY_PREFIX
                + SEPARATOR_COLON + businessType
                + SEPARATOR_COLON + BRACE_PREFIX + me() + BRACE_SUFFIX;
    }

    // ==================== 通用构建方法 ====================

    /**
     * 构建完整 Redis key
     *
     * <p>格式：sure-auth-iam:{businessType}:{me}::{dataId}
     *
     * @param businessType 业务类型（如 session、login-flow）
     * @param dataId       数据 id
     * @return 完整 Redis key
     */
    public String buildKey(String businessType, String dataId) {
        return SimpleIamServerConstant.KEY_PREFIX
                + SEPARATOR_COLON + businessType
                + SEPARATOR_COLON + BRACE_PREFIX + me() + BRACE_SUFFIX
                + SEPARATOR_DOUBLE_COLON + dataId;
    }

    /**
     * 构建缓存 key（不含 businessType，用于 L1/L2 缓存）
     *
     * @param cacheName 缓存名称
     * @param key       缓存 key
     * @return 缓存 key：{key}（RedisKeyHelper 只负责主 key 构建，缓存层 key 由 smart-cache-starter 生成）
     */
    public String buildCacheKey(String cacheName, String key) {
        return BRACE_PREFIX + key + BRACE_SUFFIX;
    }
}
