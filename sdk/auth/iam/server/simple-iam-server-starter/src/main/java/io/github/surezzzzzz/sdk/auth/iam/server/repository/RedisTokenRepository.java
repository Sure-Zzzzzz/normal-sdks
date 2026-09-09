package io.github.surezzzzzz.sdk.auth.iam.server.repository;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamRedisJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamAuthorizeContextEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPasswordResetEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRefreshTokenFamilyEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Redis Token Repository
 *
 * <p>负责 IAM 运行态热数据存储：session、login-flow、refresh-family、password-reset。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class RedisTokenRepository {

    private final RedisRouteTemplate redisRouteTemplate;
    private final RedisKeyHelper redisKeyHelper;
    private final IamRedisJsonCodec jsonCodec = new IamRedisJsonCodec();

    /**
     * 保存 IAM 会话实体（带 TTL）
     */
    public void saveSession(String sessionId, IamSessionEntity value, Duration ttl) {
        setJson(redisKeyHelper.buildSessionKey(sessionId), value, ttl);
    }

    /**
     * 读取 IAM 会话实体
     */
    public IamSessionEntity getSession(String sessionId) {
        return getJson(redisKeyHelper.buildSessionKey(sessionId), IamSessionEntity.class);
    }

    /**
     * 删除 IAM 会话
     */
    public Boolean deleteSession(String sessionId) {
        return delete(redisKeyHelper.buildSessionKey(sessionId));
    }

    /**
     * 保存授权交易上下文（state / PKCE 哈希，带 TTL）
     */
    public void saveAuthorizeContext(String contextId, IamAuthorizeContextEntity value, Duration ttl) {
        setJson(redisKeyHelper.buildAuthorizeContextKey(contextId), value, ttl);
    }

    /**
     * 读取授权交易上下文
     */
    public IamAuthorizeContextEntity getAuthorizeContext(String contextId) {
        return getJson(redisKeyHelper.buildAuthorizeContextKey(contextId), IamAuthorizeContextEntity.class);
    }

    /**
     * 删除授权交易上下文
     */
    public Boolean deleteAuthorizeContext(String contextId) {
        return delete(redisKeyHelper.buildAuthorizeContextKey(contextId));
    }

    /**
     * 本地登录失败计数 +1 并刷新 TTL（Redis 异常包装为统一运行时异常）
     */
    public Long incrementLoginFailure(String username, Duration ttl) {
        String key = redisKeyHelper.buildLoginFailureKey(username);
        requirePositiveTtl(ttl);
        try {
            Long count = redisRouteTemplate.stringTemplate().opsForValue().increment(key, 1L);
            redisRouteTemplate.stringTemplate().expire(key, ttl);
            return count;
        } catch (Exception ex) {
            throw redisFailure("登录失败计数递增", key, ex);
        }
    }

    /**
     * 读取本地登录失败计数
     */
    public Integer getLoginFailureCount(String username) {
        String key = redisKeyHelper.buildLoginFailureKey(username);
        try {
            String value = redisRouteTemplate.stringTemplate().opsForValue().get(key);
            return value == null ? 0 : Integer.valueOf(value);
        } catch (Exception ex) {
            throw redisFailure("登录失败计数读取", key, ex);
        }
    }

    /**
     * 清零本地登录失败计数
     */
    public Boolean deleteLoginFailure(String username) {
        return delete(redisKeyHelper.buildLoginFailureKey(username));
    }

    /**
     * 外部登录失败计数 +1（provider + username 维度，带 TTL）
     */
    public Long incrementExternalLoginFailure(String providerCode, String username, Duration ttl) {
        String key = redisKeyHelper.buildExternalLoginFailureKey(providerCode, username);
        requirePositiveTtl(ttl);
        try {
            Long count = redisRouteTemplate.stringTemplate().opsForValue().increment(key, 1L);
            redisRouteTemplate.stringTemplate().expire(key, ttl);
            return count;
        } catch (Exception ex) {
            throw redisFailure("外部登录失败计数递增", key, ex);
        }
    }

    /**
     * 读取外部登录失败计数
     */
    public Integer getExternalLoginFailureCount(String providerCode, String username) {
        String key = redisKeyHelper.buildExternalLoginFailureKey(providerCode, username);
        try {
            String value = redisRouteTemplate.stringTemplate().opsForValue().get(key);
            return value == null ? 0 : Integer.valueOf(value);
        } catch (Exception ex) {
            throw redisFailure("外部登录失败计数读取", key, ex);
        }
    }

    /**
     * 清零外部登录失败计数
     */
    public Boolean deleteExternalLoginFailure(String providerCode, String username) {
        return delete(redisKeyHelper.buildExternalLoginFailureKey(providerCode, username));
    }

    /**
     * 保存 Refresh Token 族记录
     */
    public void saveRefreshFamily(String familyId, IamRefreshTokenFamilyEntity value, Duration ttl) {
        setJson(redisKeyHelper.buildRefreshFamilyKey(familyId), value, ttl);
    }

    /**
     * 读取 Refresh Token 族记录
     */
    public IamRefreshTokenFamilyEntity getRefreshFamily(String familyId) {
        return getJson(redisKeyHelper.buildRefreshFamilyKey(familyId), IamRefreshTokenFamilyEntity.class);
    }

    /**
     * 删除 Refresh Token 族记录
     */
    public Boolean deleteRefreshFamily(String familyId) {
        return delete(redisKeyHelper.buildRefreshFamilyKey(familyId));
    }

    /**
     * 保存密码重置凭证
     */
    public void savePasswordReset(String token, IamPasswordResetEntity value, Duration ttl) {
        setJson(redisKeyHelper.buildPasswordResetKey(token), value, ttl);
    }

    /**
     * 读取密码重置凭证
     */
    public IamPasswordResetEntity getPasswordReset(String token) {
        return getJson(redisKeyHelper.buildPasswordResetKey(token), IamPasswordResetEntity.class);
    }

    /**
     * 删除密码重置凭证
     */
    public Boolean deletePasswordReset(String token) {
        return delete(redisKeyHelper.buildPasswordResetKey(token));
    }

    private void setJson(String key, Object value, Duration ttl) {
        requirePositiveTtl(ttl);
        try {
            redisRouteTemplate.stringTemplate().opsForValue().set(key, jsonCodec.write(value), ttl);
        } catch (Exception ex) {
            throw redisFailure("JSON状态写入", key, ex);
        }
    }

    private <T> T getJson(String key, Class<T> type) {
        try {
            String value = redisRouteTemplate.stringTemplate().opsForValue().get(key);
            return value == null ? null : jsonCodec.read(value, type);
        } catch (Exception ex) {
            throw redisFailure("JSON状态读取", key, ex);
        }
    }

    private Boolean delete(String key) {
        try {
            return redisRouteTemplate.stringTemplate().delete(key);
        } catch (Exception ex) {
            throw redisFailure("状态删除", key, ex);
        }
    }

    private void requirePositiveTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new SimpleIamServerException(
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, "运行态TTL必须大于0"));
        }
    }

    private SimpleIamServerException redisFailure(String operation, String key, Exception ex) {
        log.error("Redis运行态{}失败，key={}", operation, key, ex);
        return new SimpleIamServerException(
                String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, operation), ex);
    }
}
