package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.IamAuthorizeContextStatus;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamAuthorizeContextEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamAuthorizeContextRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 会话撤销时联动清理关联 OAuth2 授权
 *
 * <p>绑定会话的 refresh token 已由 CachedOAuth2AuthorizationService 的
 * isBoundSessionActive 校验兜底（会话吊销即失效）；本类补齐 access/code 维度的
 * 物理清理——删除授权行并经缓存 evict 跨实例广播，避免登出后已签发授权
 * 在缓存 TTL 内残留。清理为尽力而为：失败仅记日志，不阻断撤销流程。</p>
 *
 * <p>DENIED/EXPIRED 交易的授权本就不可用，不在清理范围；
 * cleanupExpiredSessions 不挂本逻辑（自然过期由绑定校验与 Redis TTL 兜底，
 * 避免周期任务放大批量 JPA/JDBC 扫描）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamAuthorizationRevocationSupport {

    private static final List<IamAuthorizeContextStatus> REVOKABLE_STATUSES = Arrays.asList(
            IamAuthorizeContextStatus.PENDING_LOGIN,
            IamAuthorizeContextStatus.PENDING_CONSENT,
            IamAuthorizeContextStatus.APPROVED,
            IamAuthorizeContextStatus.COMPLETED);

    private final IamAuthorizeContextRepository authorizeContextRepository;

    /**
     * 按用户整批吊销 refresh token 族（无授权行兜底；授权行路径经装饰器 remove 联动）
     */
    private final RefreshTokenFamilyService refreshTokenFamilyService;

    /**
     * 延迟解析打断构造环：CachedOAuth2AuthorizationService 构造依赖 SessionService，
     * 而 SessionService 依赖本类。
     */
    private final ObjectProvider<OAuth2AuthorizationService> authorizationServiceProvider;

    /**
     * 按 Servlet 会话哈希撤销关联授权（登出 / 重登踢旧会话路径）。
     *
     * @param servletSessionIdHash Servlet 会话 ID 的 SHA-256 哈希，可为 null（未绑定会话）
     */
    public void revokeByServletSessionIdHash(String servletSessionIdHash) {
        if (!StringUtils.hasText(servletSessionIdHash)) {
            return;
        }
        revokeAll(authorizeContextRepository
                .findByServletSessionIdHashAndStatusIn(servletSessionIdHash, REVOKABLE_STATUSES));
    }

    /**
     * 按用户撤销全部关联授权（禁用 / 删除 / 重置密码等批量吊销路径）。
     *
     * <p>先按用户整批吊销 refresh token 族（覆盖没有授权行的族），授权行的
     * 物理清理经族防线装饰器的 remove 联动族吊销，双路径幂等。
     *
     * @param userId 用户 ID
     */
    public void revokeByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        revokeFamiliesByUserId(userId);
        revokeAll(authorizeContextRepository.findByUserIdAndStatusIn(userId, REVOKABLE_STATUSES));
    }

    private void revokeFamiliesByUserId(Long userId) {
        try {
            int revoked = refreshTokenFamilyService.revokeAllByUserId(userId);
            log.info("按用户吊销 refresh token 族：userId={}, count={}", userId, revoked);
        } catch (Exception exception) {
            log.error("按用户吊销 refresh token 族失败：userId={}", userId, exception);
        }
    }

    private void revokeAll(List<IamAuthorizeContextEntity> contexts) {
        if (contexts.isEmpty()) {
            log.debug("无待联动撤销的 OAuth2 授权");
            return;
        }
        OAuth2AuthorizationService authorizationService = authorizationServiceProvider.getIfAvailable();
        if (authorizationService == null) {
            log.warn("上下文缺失 OAuth2AuthorizationService，跳过 {} 条关联授权的物理清理", contexts.size());
            return;
        }
        log.info("联动撤销 OAuth2 授权：共 {} 条", contexts.size());
        for (IamAuthorizeContextEntity context : contexts) {
            revokeAuthorization(authorizationService, context.getId(), context.getStatus());
        }
    }

    private void revokeAuthorization(OAuth2AuthorizationService authorizationService,
                                     String authorizationId, IamAuthorizeContextStatus status) {
        try {
            OAuth2Authorization authorization = authorizationService.findById(authorizationId);
            if (authorization != null) {
                authorizationService.remove(authorization);
                log.info("已物理清理 OAuth2 授权：authorizationId={}, status={}", authorizationId, status);
            } else {
                log.debug("关联授权已不存在，跳过清理：authorizationId={}, status={}", authorizationId, status);
            }
        } catch (Exception exception) {
            log.error("撤销关联 OAuth2 授权失败：authorizationId={}", authorizationId, exception);
        }
    }
}
