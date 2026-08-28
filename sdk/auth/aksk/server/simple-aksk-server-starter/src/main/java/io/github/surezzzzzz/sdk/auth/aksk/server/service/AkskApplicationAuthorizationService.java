package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AkskApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

/**
 * AKSK服务主体应用授权快照服务。
 *
 * @author surezzzzzz
 */
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class AkskApplicationAuthorizationService {

    private final AkskApplicationAuthorizationRepository authorizationRepository;
    private final OAuth2RegisteredClientEntityRepository clientRepository;

    /**
     * 为仍有效的服务主体授权创建权威快照。
     *
     * @param clientId  服务主体标识
     * @param issuedAt  令牌签发时间
     * @param expiresAt 令牌到期时间
     * @return 已验证授权快照；未准入或数据无效时返回null
     */
    public ApplicationAuthorizationContext loadActiveContext(String clientId,
                                                             Instant issuedAt,
                                                             Instant expiresAt) {
        if (clientId == null || clientId.trim().isEmpty() || issuedAt == null || expiresAt == null) {
            return null;
        }
        AkskApplicationAuthorizationEntity authorization = authorizationRepository
                .findByClientId(clientId).orElse(null);
        if (!isActiveAndAdmitted(authorization)
                || !isClientActive(authorization.getClientId())) {
            return null;
        }
        try {
            return new ApplicationAuthorizationContext(
                    SimpleApplicationAuthorizationConstant.PROTOCOL,
                    SimpleApplicationAuthorizationConstant.VERSION,
                    ApplicationAuthorizationSubjectType.SERVICE,
                    clientId,
                    authorization.getApplicationCode(),
                    true,
                    AkskApplicationAuthorizationJsonCodec.readStringList(authorization.getRolesJson()),
                    AkskApplicationAuthorizationJsonCodec.readStringList(authorization.getPagePermissionsJson()),
                    AkskApplicationAuthorizationJsonCodec.readStringList(authorization.getApiPermissionsJson()),
                    AkskApplicationAuthorizationJsonCodec.readDataGrantDocument(
                            authorization.getDataGrantDocumentJson()),
                    authorization.getAuthorizationVersion(),
                    authorization.getManifestVersion(),
                    authorization.getManifestDigest(),
                    issuedAt,
                    expiresAt);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean isActiveAndAdmitted(AkskApplicationAuthorizationEntity authorization) {
        return authorization != null
                && Boolean.TRUE.equals(authorization.getEnabled())
                && Boolean.TRUE.equals(authorization.getAdmitted())
                && authorization.getAuthorizationVersion() != null
                && authorization.getAuthorizationVersion().longValue() > 0L
                && authorization.getRevokedAt() == null;
    }

    private boolean isClientActive(String clientId) {
        return clientRepository.findByClientId(clientId)
                .map(client -> client.isEnabled())
                .orElse(false);
    }
}
