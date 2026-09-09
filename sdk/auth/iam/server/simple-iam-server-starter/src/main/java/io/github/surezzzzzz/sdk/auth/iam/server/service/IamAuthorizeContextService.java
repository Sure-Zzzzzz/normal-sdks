package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.IamAuthorizeContextStatus;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamAuthorizeContextEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamAuthorizeContextRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.TreeSet;
import java.util.UUID;

/**
 * IAM 授权交易上下文服务
 *
 * <p>该服务仅维护浏览器交互状态和 IAM 会话绑定，不保存或签发 OAuth2 授权码。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamAuthorizeContextService {

    private final IamAuthorizeContextRepository authorizeContextRepository;
    private final RedisTokenRepository redisTokenRepository;

    /**
     * 创建待登录授权交易
     *
     * @param registeredClientId  SAS 注册客户端 ID
     * @param clientId            客户端 ID
     * @param redirectUri         已验证的精确重定向 URI
     * @param scopes              请求范围
     * @param state               OAuth2 state
     * @param nonce               OIDC nonce
     * @param codeChallenge       PKCE challenge
     * @param codeChallengeMethod PKCE challenge 方法
     * @param servletSessionId    Servlet 会话 ID
     * @return 授权交易
     */
    @Transactional
    public IamAuthorizeContextEntity createPendingLogin(String registeredClientId, String clientId,
                                                        String redirectUri, Iterable<String> scopes,
                                                        String state, String nonce, String codeChallenge,
                                                        String codeChallengeMethod, String servletSessionId) {
        return createPendingLogin(UUID.randomUUID().toString(), registeredClientId, clientId, redirectUri, scopes,
                state, nonce, codeChallenge, codeChallengeMethod, servletSessionId);
    }

    /**
     * 使用 SAS 授权 ID 创建待登录授权交易
     *
     * @param contextId           SAS 授权 ID
     * @param registeredClientId  SAS 注册客户端 ID
     * @param clientId            客户端 ID
     * @param redirectUri         已验证的精确重定向 URI
     * @param scopes              请求范围
     * @param state               OAuth2 state
     * @param nonce               OIDC nonce
     * @param codeChallenge       PKCE challenge
     * @param codeChallengeMethod PKCE challenge 方法
     * @param servletSessionId    Servlet 会话 ID
     * @return 授权交易
     */
    @Transactional
    public IamAuthorizeContextEntity createPendingLogin(String contextId, String registeredClientId, String clientId,
                                                        String redirectUri, Iterable<String> scopes,
                                                        String state, String nonce, String codeChallenge,
                                                        String codeChallengeMethod, String servletSessionId) {
        Instant now = Instant.now();
        IamAuthorizeContextEntity context = new IamAuthorizeContextEntity();
        context.setId(requireValue(contextId));
        context.setRegisteredClientId(requireValue(registeredClientId));
        context.setClientId(requireValue(clientId));
        context.setRedirectUri(requireValue(redirectUri));
        context.setRequestedScopes(canonicalScopes(scopes));
        context.setStateHash(hashOptional(state));
        context.setNonceHash(hashOptional(nonce));
        context.setCodeChallengeHash(hashOptional(codeChallenge));
        context.setCodeChallengeMethod(normalizeOptional(codeChallengeMethod));
        context.setServletSessionIdHash(TokenHashHelper.sha256Hex(requireValue(servletSessionId)));
        context.setStatus(IamAuthorizeContextStatus.PENDING_LOGIN);
        context.setIssuedAt(now);
        context.setExpiresAt(now.plusSeconds(SimpleIamServerConstant.DEFAULT_AUTHORIZE_CONTEXT_EXPIRES_IN));
        IamAuthorizeContextEntity saved = authorizeContextRepository.save(context);
        cache(saved);
        return saved;
    }

    /**
     * 同步 SAS 已验证的交互式授权记录
     *
     * <p>该方法仅保存旁路状态，SAS 授权记录仍是协议真相源。
     *
     * @param contextId           SAS 授权 ID
     * @param registeredClientId  SAS 注册客户端 ID
     * @param clientId            客户端 ID
     * @param redirectUri         已验证的精确重定向 URI
     * @param scopes              请求范围
     * @param state               OAuth2 state
     * @param nonce               OIDC nonce
     * @param codeChallenge       PKCE challenge
     * @param codeChallengeMethod PKCE challenge 方法
     * @param userId              已认证用户 ID
     * @param iamSessionId        活跃 IAM 会话 ID
     * @param servletSessionId    当前 Servlet 会话 ID
     * @param requireConsent      是否仍待授权确认
     */
    @Transactional
    public void synchronizeAuthenticatedAuthorization(String contextId, String registeredClientId, String clientId,
                                                      String redirectUri, Iterable<String> scopes,
                                                      String state, String nonce, String codeChallenge,
                                                      String codeChallengeMethod, Long userId, String iamSessionId,
                                                      String servletSessionId, boolean requireConsent) {
        IamAuthorizeContextEntity context = authorizeContextRepository.findById(contextId).orElse(null);
        if (context == null) {
            createPendingLogin(contextId, registeredClientId, clientId, redirectUri, scopes, state, nonce,
                    codeChallenge, codeChallengeMethod, servletSessionId);
            context = bindAuthenticatedSession(contextId, userId, iamSessionId, servletSessionId, requireConsent);
        }
        if (!requireConsent && context.getStatus() == IamAuthorizeContextStatus.PENDING_CONSENT) {
            approve(contextId, userId, iamSessionId, servletSessionId);
            context = getActive(contextId);
        }
        if (!requireConsent && context.getStatus() == IamAuthorizeContextStatus.APPROVED) {
            complete(contextId, userId, iamSessionId, servletSessionId);
        }
    }

    /**
     * 将成功认证的 IAM 会话绑定到授权交易
     *
     * @param contextId        授权交易 ID
     * @param userId           已认证用户 ID
     * @param iamSessionId     活跃 IAM 会话 ID
     * @param servletSessionId 当前 Servlet 会话 ID
     * @param requireConsent   是否进入授权确认页
     * @return 已推进的授权交易
     */
    @Transactional
    public IamAuthorizeContextEntity bindAuthenticatedSession(String contextId, Long userId, String iamSessionId,
                                                              String servletSessionId, boolean requireConsent) {
        IamAuthorizeContextEntity context = requireActive(contextId);
        requireStatus(context, IamAuthorizeContextStatus.PENDING_LOGIN);
        requireServletSession(context, servletSessionId);
        context.setUserId(userId);
        context.setIamSessionId(requireValue(iamSessionId));
        context.setStatus(requireConsent ? IamAuthorizeContextStatus.PENDING_CONSENT
                : IamAuthorizeContextStatus.APPROVED);
        if (!requireConsent) {
            context.setApprovedAt(Instant.now());
        }
        return saveAndCache(context);
    }

    /**
     * 批准授权交易
     *
     * @param contextId        授权交易 ID
     * @param userId           当前用户 ID
     * @param iamSessionId     当前 IAM 会话 ID
     * @param servletSessionId 当前 Servlet 会话 ID
     * @return 已批准的授权交易
     */
    @Transactional
    public IamAuthorizeContextEntity approve(String contextId, Long userId, String iamSessionId,
                                             String servletSessionId) {
        IamAuthorizeContextEntity context = requireActive(contextId);
        requireStatus(context, IamAuthorizeContextStatus.PENDING_CONSENT);
        requireIdentity(context, userId, iamSessionId, servletSessionId);
        context.setStatus(IamAuthorizeContextStatus.APPROVED);
        context.setApprovedAt(Instant.now());
        return saveAndCache(context);
    }

    /**
     * 拒绝授权交易
     *
     * @param contextId        授权交易 ID
     * @param userId           当前用户 ID
     * @param iamSessionId     当前 IAM 会话 ID
     * @param servletSessionId 当前 Servlet 会话 ID
     * @return 已拒绝的授权交易
     */
    @Transactional
    public IamAuthorizeContextEntity deny(String contextId, Long userId, String iamSessionId,
                                          String servletSessionId) {
        IamAuthorizeContextEntity context = requireActive(contextId);
        requireStatus(context, IamAuthorizeContextStatus.PENDING_CONSENT);
        requireIdentity(context, userId, iamSessionId, servletSessionId);
        context.setStatus(IamAuthorizeContextStatus.DENIED);
        context.setDeniedAt(Instant.now());
        return saveAndCache(context);
    }

    /**
     * 标记 SAS 已完成授权码签发
     *
     * @param contextId        授权交易 ID
     * @param userId           当前用户 ID
     * @param iamSessionId     当前 IAM 会话 ID
     * @param servletSessionId 当前 Servlet 会话 ID
     * @return 已完成的授权交易
     */
    @Transactional
    public IamAuthorizeContextEntity complete(String contextId, Long userId, String iamSessionId,
                                              String servletSessionId) {
        IamAuthorizeContextEntity context = requireActive(contextId);
        requireStatus(context, IamAuthorizeContextStatus.APPROVED);
        requireIdentity(context, userId, iamSessionId, servletSessionId);
        context.setStatus(IamAuthorizeContextStatus.COMPLETED);
        context.setCompletedAt(Instant.now());
        IamAuthorizeContextEntity saved = saveAndCache(context);
        redisTokenRepository.deleteAuthorizeContext(contextId);
        return saved;
    }

    /**
     * 查询有效授权交易
     *
     * @param contextId 授权交易 ID
     * @return 有效授权交易
     */
    public IamAuthorizeContextEntity getActive(String contextId) {
        return requireActive(contextId);
    }

    private IamAuthorizeContextEntity requireActive(String contextId) {
        IamAuthorizeContextEntity context = redisTokenRepository.getAuthorizeContext(contextId);
        if (context == null) {
            context = authorizeContextRepository.findById(contextId)
                    .orElseThrow(() -> new SimpleIamServerException(ErrorCode.AUTHORIZE_CONTEXT_INVALID,
                            ServerErrorMessage.AUTHORIZE_CONTEXT_INVALID));
        }
        if (!context.getExpiresAt().isAfter(Instant.now())) {
            if (context.getStatus() != IamAuthorizeContextStatus.COMPLETED
                    && context.getStatus() != IamAuthorizeContextStatus.DENIED
                    && context.getStatus() != IamAuthorizeContextStatus.EXPIRED) {
                context.setStatus(IamAuthorizeContextStatus.EXPIRED);
                authorizeContextRepository.save(context);
            }
            redisTokenRepository.deleteAuthorizeContext(contextId);
            throw new SimpleIamServerException(ErrorCode.AUTHORIZE_CONTEXT_EXPIRED,
                    ServerErrorMessage.AUTHORIZE_CONTEXT_EXPIRED);
        }
        return context;
    }

    private IamAuthorizeContextEntity saveAndCache(IamAuthorizeContextEntity context) {
        IamAuthorizeContextEntity saved = authorizeContextRepository.save(context);
        cache(saved);
        return saved;
    }

    private void cache(IamAuthorizeContextEntity context) {
        Duration ttl = Duration.between(Instant.now(), context.getExpiresAt());
        if (!ttl.isZero() && !ttl.isNegative()) {
            redisTokenRepository.saveAuthorizeContext(context.getId(), context, ttl);
        }
    }

    private void requireStatus(IamAuthorizeContextEntity context, IamAuthorizeContextStatus expected) {
        if (context.getStatus() != expected) {
            throw new SimpleIamServerException(ErrorCode.AUTHORIZE_CONTEXT_INVALID,
                    ServerErrorMessage.AUTHORIZE_CONTEXT_INVALID);
        }
    }

    private void requireIdentity(IamAuthorizeContextEntity context, Long userId, String iamSessionId,
                                 String servletSessionId) {
        requireServletSession(context, servletSessionId);
        if (userId == null || !userId.equals(context.getUserId())
                || !requireValue(iamSessionId).equals(context.getIamSessionId())) {
            throw new SimpleIamServerException(ErrorCode.AUTHORIZE_CONTEXT_FORBIDDEN,
                    ServerErrorMessage.AUTHORIZE_CONTEXT_FORBIDDEN);
        }
    }

    private void requireServletSession(IamAuthorizeContextEntity context, String servletSessionId) {
        if (!TokenHashHelper.sha256Hex(requireValue(servletSessionId)).equals(context.getServletSessionIdHash())) {
            throw new SimpleIamServerException(ErrorCode.AUTHORIZE_CONTEXT_FORBIDDEN,
                    ServerErrorMessage.AUTHORIZE_CONTEXT_FORBIDDEN);
        }
    }

    private String canonicalScopes(Iterable<String> scopes) {
        if (scopes == null) {
            return "";
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String scope : scopes) {
            if (StringUtils.hasText(scope)) {
                normalized.add(scope.trim());
            }
        }
        return String.join(" ", normalized);
    }

    private String hashOptional(String value) {
        return StringUtils.hasText(value) ? TokenHashHelper.sha256Hex(value) : null;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String requireValue(String value) {
        if (!StringUtils.hasText(value)) {
            throw new SimpleIamServerException(ErrorCode.AUTHORIZE_CONTEXT_INVALID,
                    ServerErrorMessage.AUTHORIZE_CONTEXT_INVALID);
        }
        return value.trim();
    }
}
