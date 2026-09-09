package io.github.surezzzzzz.sdk.auth.iam.server.service;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamConsentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamConsentRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class IamDecoratingConsentService implements OAuth2AuthorizationConsentService {

    private final OAuth2AuthorizationConsentService delegate;
    private final IamConsentRepository consentRepository;
    private final IamUserRepository userRepository;
    private final RegisteredClientRepository registeredClientRepository;

    /**
     * 标准落库之上同步投影到 iam_consent 表
     */
    @Override
    @Transactional
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
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
        return delegate.findById(registeredClientId, principalName);
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
}
