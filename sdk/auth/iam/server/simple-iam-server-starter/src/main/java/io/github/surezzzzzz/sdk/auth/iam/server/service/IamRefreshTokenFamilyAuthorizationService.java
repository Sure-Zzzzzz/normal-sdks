package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRefreshTokenFamilyEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRefreshTokenFamilyRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.time.Instant;
import java.util.Optional;

/**
 * Refresh Token 族防线装饰器（授权服务链最外层）。
 *
 * <p>客户端已全局开启 refresh token 轮换（{@code reuseRefreshTokens=false}），
 * 本装饰器把 {@link RefreshTokenFamilyService} 的族表接到 SAS 授权生命周期上：</p>
 *
 * <ul>
 *   <li>save：refresh token 首次出现建族；值变化即轮换（旧哈希落 previous，
 *       新哈希落 current）</li>
 *   <li>findByToken(refresh)：先重放检测——旧 token（命中 previous_token_hash）
 *       二次使用即整族吊销并发布 {@code RefreshTokenReuseDetectedEvent}，返回
 *       null 走 SAS invalid_grant；再验族有效性——族不存在 / 已吊销 / 已过期
 *       一律 null（失败关闭，缓存快照不能越过族状态）</li>
 *   <li>remove：授权物理清理时整族吊销（登出 / 禁用 / 删除 / 改密经
 *       {@link IamAuthorizationRevocationSupport} 传导）</li>
 * </ul>
 *
 * <p>族维护在 delegate.save 成功之后执行且失败即抛出：族表与授权行不一致时
 * 宁可本次签发失败（客户端重新走授权码流程），不放行"防不住"的 refresh token；
 * remove 路径族吊销失败仅记日志（清理尽力而为，与既有撤销口径一致）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class IamRefreshTokenFamilyAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final RefreshTokenFamilyService refreshTokenFamilyService;
    private final IamRefreshTokenFamilyRepository refreshTokenFamilyRepository;
    private final IamUserRepository userRepository;

    /**
     * 落库授权并维护 refresh token 族（建族 / 轮换）。
     */
    @Override
    public void save(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2RefreshToken> newRefresh =
                authorization.getToken(OAuth2RefreshToken.class);
        if (newRefresh == null || newRefresh.getToken() == null) {
            delegate.save(authorization);
            return;
        }
        String newRefreshValue = newRefresh.getToken().getTokenValue();
        OAuth2Authorization existing = delegate.findById(authorization.getId());
        String oldRefreshValue = resolveRefreshValue(existing);

        delegate.save(authorization);

        try {
            maintainFamily(authorization, newRefreshValue, oldRefreshValue);
        } catch (Exception exception) {
            log.error("Refresh token 族维护失败，签发按失败处理：authorizationId={}",
                    authorization.getId(), exception);
            throw exception;
        }
    }

    /**
     * 物理清理授权并吊销关联族。
     */
    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
        String refreshValue = resolveRefreshValue(authorization);
        if (refreshValue == null) {
            return;
        }
        try {
            refreshTokenFamilyRepository.findByCurrentTokenHash(TokenHashHelper.sha256Hex(refreshValue))
                    .ifPresent(family -> refreshTokenFamilyService.revokeFamily(family.getId()));
            log.info("授权移除已联动吊销 refresh token 族：authorizationId={}", authorization.getId());
        } catch (Exception exception) {
            log.error("授权移除时族吊销失败：authorizationId={}", authorization.getId(), exception);
        }
    }

    /**
     * 按 id 读取授权（透传链）。
     */
    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    /**
     * 按 token 读取授权；refresh token 维度前置重放检测与族有效性校验。
     *
     * <p>tokenType 为 null 的调用方（introspect / revoke 端点）不声明 token 类型：
     * 重放检测照做（旧 refresh 命中 previous 哈希即拦，access 值不会命中、无副作用）；
     * 族校验仅在 token 确为该授权的 refresh 值时执行——access token 不得被族状态误伤。</p>
     */
    @Override
    @Nullable
    public OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        if (token == null || token.isEmpty()) {
            return delegate.findByToken(token, tokenType);
        }
        boolean possibleRefresh = tokenType == null
                || OAuth2TokenType.REFRESH_TOKEN.getValue().equals(tokenType.getValue());
        if (possibleRefresh && isReplayedToken(token)) {
            return null;
        }
        OAuth2Authorization authorization = delegate.findByToken(token, tokenType);
        if (authorization == null || !possibleRefresh) {
            return authorization;
        }
        if (tokenType != null) {
            return isFamilyActive(token) ? authorization : null;
        }
        return token.equals(resolveRefreshValue(authorization)) && !isFamilyActive(token)
                ? null : authorization;
    }

    /**
     * 重放检测：token 命中某族 previous_token_hash 即为已轮换旧 token 二次使用，
     * 整族吊销并发布事件（{@link RefreshTokenFamilyService#detectReuseAndRevoke}）。
     */
    private boolean isReplayedToken(String token) {
        try {
            refreshTokenFamilyService.detectReuseAndRevoke(token);
            return false;
        } catch (SimpleIamServerException exception) {
            return true;
        }
    }

    /**
     * 族有效性：current_token_hash 命中且状态活跃未过期才放行。
     * 族行不存在（历史数据 / 建族失败）同样拒绝——失败关闭。
     */
    private boolean isFamilyActive(String token) {
        Optional<IamRefreshTokenFamilyEntity> family = refreshTokenFamilyRepository
                .findByCurrentTokenHash(TokenHashHelper.sha256Hex(token));
        if (family.isEmpty()) {
            log.warn("refresh token 无族记录，按失效处理（token 哈希前 8 位={}）",
                    TokenHashHelper.sha256Hex(token).substring(0, 8));
            return false;
        }
        IamRefreshTokenFamilyEntity entity = family.get();
        return entity.getStatus() != null
                && entity.getStatus() == SimpleIamServerConstant.STATUS_ACTIVE
                && entity.getExpiresAt() != null
                && entity.getExpiresAt().isAfter(Instant.now());
    }

    private void maintainFamily(OAuth2Authorization authorization, String newRefreshValue,
                                String oldRefreshValue) {
        String newTokenHash = TokenHashHelper.sha256Hex(newRefreshValue);
        if (oldRefreshValue != null && !oldRefreshValue.equals(newRefreshValue)) {
            Optional<IamRefreshTokenFamilyEntity> family = refreshTokenFamilyRepository
                    .findByCurrentTokenHash(TokenHashHelper.sha256Hex(oldRefreshValue));
            if (family.isPresent()) {
                refreshTokenFamilyService.rotate(family.get().getId(), newRefreshValue);
                return;
            }
            log.warn("轮换未命中既有族（历史数据或建族缺失），按新签发建族：authorizationId={}",
                    authorization.getId());
        } else if (oldRefreshValue != null
                && refreshTokenFamilyRepository.findByCurrentTokenHash(newTokenHash).isPresent()) {
            return;
        }
        createFamilyForAuthorization(authorization, newRefreshValue);
    }

    private void createFamilyForAuthorization(OAuth2Authorization authorization, String refreshToken) {
        String username = authorization.getPrincipalName();
        IamUserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.warn("建族跳过：授权主体用户不存在，username={}", username);
            return;
        }
        String sessionId = resolveBoundSessionId(authorization);
        refreshTokenFamilyService.createFamily(user.getId(), username, sessionId, refreshToken);
        log.info("refresh token 族已建立：userId={}, username={}, sessionId={}",
                user.getId(), username, sessionId);
    }

    /**
     * 会话绑定属性在缓存装饰器 save 时才写入授权，这里以落库后的最终态为准。
     */
    private String resolveBoundSessionId(OAuth2Authorization authorization) {
        OAuth2Authorization persisted = delegate.findById(authorization.getId());
        OAuth2Authorization source = persisted != null ? persisted : authorization;
        return source.getAttribute(SimpleIamServerConstant.AUTHORIZATION_ATTRIBUTE_IAM_SESSION_ID);
    }

    private String resolveRefreshValue(OAuth2Authorization authorization) {
        if (authorization == null) {
            return null;
        }
        OAuth2Authorization.Token<OAuth2RefreshToken> refresh =
                authorization.getToken(OAuth2RefreshToken.class);
        return refresh == null || refresh.getToken() == null
                ? null : refresh.getToken().getTokenValue();
    }
}
