package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.AkskOwnerAuthorizationSynchronizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskClientOwnerBindingEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationMode;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskOwnerAuthorizationProjectionEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskClientOwnerBindingRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AkskPersonalCredentialAuthorizationHelper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationKey;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationReadResult;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.spi.OwnerAuthorizationProvider;
import lombok.RequiredArgsConstructor;

import java.time.Instant;

/**
 * AKSK 所有授权出口的唯一结论服务。
 *
 * <p>OWNER_INHERITED 永不读取本地三权作为兜底；静态 AKP 和无 binding 的历史 AKU 保持原有语义。</p>
 */
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class AkskEffectiveAuthorizationService {

    private static final int OWNER_STATE_ACTIVE = 1;

    private final AkskApplicationAuthorizationService localAuthorizationService;
    private final CachedOAuth2RegisteredClientEntityService cachedClientEntityService;
    private final AkskClientOwnerBindingRepository bindingRepository;
    private final OwnerAuthorizationProvider ownerAuthorizationProvider;
    private final AkskOwnerAuthorizationProjectionService projectionService;
    private final SimpleAkskServerProperties properties;

    public AkskEffectiveAuthorizationResult resolve(String clientId, Instant issuedAt, Instant expiresAt) {
        OAuth2RegisteredClientEntity client = cachedClientEntityService.findByClientId(clientId).orElse(null);
        if (client == null || !client.isEnabled()) {
            return null;
        }
        AkskClientOwnerBindingEntity binding = bindingRepository.findByClientId(clientId).orElse(null);
        if (ClientType.USER.equals(ClientType.fromCode(client.getClientType()))
                && binding != null && AkskOwnerAuthorizationMode.OWNER_INHERITED.equals(binding.getAuthorizationMode())) {
            return resolveInherited(binding, issuedAt, expiresAt);
        }
        ApplicationAuthorizationContext authorization = localAuthorizationService.loadActiveContext(clientId, issuedAt, expiresAt);
        return authorization == null ? null : new AkskEffectiveAuthorizationResult(authorization,
                AkskOwnerAuthorizationMode.STATIC_LEGACY, null, null, null, null, null);
    }

    private AkskEffectiveAuthorizationResult resolveInherited(AkskClientOwnerBindingEntity binding,
                                                              Instant issuedAt, Instant expiresAt) {
        if (binding.getOwnerState() == null || binding.getOwnerState().intValue() != OWNER_STATE_ACTIVE
                || binding.getLifecycleVersion() == null || binding.getLifecycleVersion().longValue() <= 0L
                || (binding.getExpiresAt() != null && !Instant.now().isBefore(binding.getExpiresAt()))) {
            return null;
        }
        if (AkskOwnerAuthorizationSynchronizationMode.STRICT_ONLINE.equals(
                properties.getOwnerAuthorization().getSynchronizationMode())) {
            return resolveStrictOnline(binding, issuedAt, expiresAt);
        }
        if (!projectionService.hasValidSynchronizationLease()) {
            return null;
        }
        AkskOwnerAuthorizationProjectionEntity projection = projectionService.findActiveProjection(
                binding.getOwnerSourceId(), binding.getOwnerSubjectId(), binding.getTargetApplicationId());
        ApplicationAuthorizationContext authorization = AkskPersonalCredentialAuthorizationHelper.restrictToOwner(
                projectionService.readAuthorization(projection, issuedAt, expiresAt), binding.getOwnerSubjectId());
        Long applicationEpoch = projectionService.currentApplicationAuthorizationEpoch(binding.getTargetApplicationId());
        if (authorization == null || applicationEpoch == null) {
            return null;
        }
        return new AkskEffectiveAuthorizationResult(authorization, AkskOwnerAuthorizationMode.OWNER_INHERITED,
                binding.getTargetApplicationId(), projection.getOwnerSecurityEpoch(), applicationEpoch,
                binding.getOwnerSourceId(), binding.getOwnerSubjectId(), projection.getOwnerInheritedAccessEpoch(),
                projection.getProjectionAccessEpoch());
    }

    private AkskEffectiveAuthorizationResult resolveStrictOnline(AkskClientOwnerBindingEntity binding,
                                                                 Instant issuedAt, Instant expiresAt) {
        OwnerAuthorizationReadResult readerResult = ownerAuthorizationProvider.resolve(
                new OwnerAuthorizationKey(binding.getOwnerSourceId(), binding.getOwnerSubjectId()),
                binding.getTargetApplicationId());
        if (!readerResult.isActive() || readerResult.getAuthorization() == null) {
            return null;
        }
        projectionService.bootstrap(binding.getOwnerSourceId(), binding.getOwnerSubjectId(),
                binding.getTargetApplicationId(), readerResult);
        ApplicationAuthorizationContext source = readerResult.toApplicationAuthorizationContext();
        ApplicationAuthorizationContext authorization = AkskPersonalCredentialAuthorizationHelper.restrictToOwner(
                new ApplicationAuthorizationContext(
                        source.getProtocol(), source.getVersion(), source.getSubjectType(), source.getSubjectId(),
                        source.getApplicationCode(), source.isAdmitted(), source.getRoles(), source.getPagePermissions(),
                        source.getApiPermissions(), source.getDataGrantDocument(), source.getAuthorizationVersion(),
                        source.getManifestVersion(), source.getManifestDigest(), issuedAt, expiresAt),
                binding.getOwnerSubjectId());
        return new AkskEffectiveAuthorizationResult(authorization,
                AkskOwnerAuthorizationMode.OWNER_INHERITED, binding.getTargetApplicationId(),
                readerResult.getOwnerSecurityEpoch(), readerResult.getApplicationAuthorizationEpoch(),
                binding.getOwnerSourceId(), binding.getOwnerSubjectId(), readerResult.getOwnerInheritedAccessEpoch(),
                readerResult.getProjectionAccessEpoch());
    }
}
