package io.github.surezzzzzz.sdk.auth.iam.server.service.oauth2;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.oauth2.IamConsentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.oauth2.IamConsentRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationAccessGuard;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.TreeSet;
import java.util.UUID;

/**
 * IAM 授权确认服务装饰器
 *
 * <p>委托 JDBC 实现持久化协议数据，同步维护 {@code iam_consent} 投影供审计和查询使用。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class IamOAuth2ConsentService implements OAuth2AuthorizationConsentService {

    private final OAuth2AuthorizationConsentService delegate;
    private final IamConsentRepository consentRepository;
    private final IamUserRepository userRepository;
    private final RegisteredClientRepository registeredClientRepository;
    private final IamTrustedApplicationClientService trustedApplicationClientService;
    private final IamTrustedApplicationAccessGuard trustedApplicationAccessGuard;

    /**
     * 标准落库之上同步投影到 iam_consent 表
     */
    @Override
    @Transactional
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        if (!trustedApplicationAccessGuard.isNewAuthorizationAllowed(
                authorizationConsent.getRegisteredClientId())) {
            // SAS provider 会将 OAuth2AuthenticationException 转为协议错误，避免“假保存”后继续签码。
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT));
        }
        delegate.save(authorizationConsent);
        syncProjection(authorizationConsent);
    }

    /**
     * 双删：标准 storage 与 iam_consent 投影
     */
    @Override
    @Transactional
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        delegate.remove(authorizationConsent);
        revokeProjection(authorizationConsent);
    }

    /**
     * 读取 Consent 记录（透传）
     */
    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        OAuth2AuthorizationConsent consent = delegate.findById(registeredClientId, principalName);
        if (consent == null) {
            return null;
        }
        return trustedApplicationClientService.findApplicationByRegisteredClientId(registeredClientId)
                .map(application -> isCurrentApplicationConsent(application, registeredClientId, principalName)
                        ? consent : null)
                // application_id 为空的历史 SAS client 不纳入可信应用生命周期门禁。
                .orElse(consent);
    }

    private void syncProjection(OAuth2AuthorizationConsent consent) {
        try {
            Long userId = userRepository.findByUsername(consent.getPrincipalName())
                    .map(u -> u.getId())
                    .orElse(null);
            if (userId == null) {
                return;
            }
            RegisteredClient registeredClient = registeredClientRepository.findById(consent.getRegisteredClientId());
            if (registeredClient == null) {
                return;
            }
            String scopes = consent.getScopes() != null
                    ? String.join(" ", new TreeSet<>(consent.getScopes()))
                    : "";
            IamConsentEntity existing = consentRepository
                    .findByUserIdAndRegisteredClientId(userId, consent.getRegisteredClientId())
                    .orElse(null);
            if (existing != null) {
                existing.setAuthorizedScopes(scopes);
                existing.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
                existing.setApplicationSecurityEpoch(resolveApplicationSecurityEpoch(
                        consent.getRegisteredClientId()));
                existing.setGrantedAt(Instant.now());
                existing.setRevokedAt(null);
                consentRepository.save(existing);
            } else {
                IamConsentEntity entity = new IamConsentEntity();
                entity.setId(UUID.randomUUID().toString());
                entity.setUserId(userId);
                entity.setRegisteredClientId(consent.getRegisteredClientId());
                entity.setClientId(registeredClient.getClientId());
                entity.setAuthorizedScopes(scopes);
                entity.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
                entity.setApplicationSecurityEpoch(resolveApplicationSecurityEpoch(
                        consent.getRegisteredClientId()));
                entity.setGrantedAt(Instant.now());
                consentRepository.save(entity);
            }
        } catch (Exception e) {
            log.warn("IAM consent 投影同步失败（不影响协议主流程）：{}", e.getMessage());
        }
    }

    private void revokeProjection(OAuth2AuthorizationConsent consent) {
        try {
            Long userId = userRepository.findByUsername(consent.getPrincipalName())
                    .map(u -> u.getId())
                    .orElse(null);
            if (userId == null) {
                return;
            }
            consentRepository.findByUserIdAndRegisteredClientId(userId, consent.getRegisteredClientId())
                    .ifPresent(entity -> {
                        entity.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
                        entity.setRevokedAt(Instant.now());
                        consentRepository.save(entity);
                    });
        } catch (Exception e) {
            log.warn("IAM consent 撤销投影同步失败（不影响协议主流程）：{}", e.getMessage());
        }
    }

    /**
     * 非可信应用 client 保持历史兼容语义，固定使用 epoch 1；可信应用读取当前纪元。
     */
    private Long resolveApplicationSecurityEpoch(String registeredClientId) {
        return trustedApplicationClientService.findApplicationByRegisteredClientId(registeredClientId)
                .map(application -> application.getApplicationSecurityEpoch())
                .orElse(1L);
    }

    /**
     * 可信应用只接受与当前安全纪元一致的 consent，恢复应用也不能复用停用前确认。
     */
    private boolean isCurrentApplicationConsent(
            io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity application,
            String registeredClientId, String principalName) {
        if (application.getStatus() == null
                || SimpleIamServerConstant.STATUS_ACTIVE != application.getStatus().intValue()
                || application.getApplicationSecurityEpoch() == null) {
            return false;
        }
        Long userId = userRepository.findByUsername(principalName).map(u -> u.getId()).orElse(null);
        if (userId == null) {
            return false;
        }
        return consentRepository.findByUserIdAndRegisteredClientId(userId, registeredClientId)
                .map(projection -> projection.getStatus() != null
                        && SimpleIamServerConstant.STATUS_ACTIVE == projection.getStatus().intValue()
                        && application.getApplicationSecurityEpoch().equals(
                        projection.getApplicationSecurityEpoch()))
                .orElse(false);
    }
}
