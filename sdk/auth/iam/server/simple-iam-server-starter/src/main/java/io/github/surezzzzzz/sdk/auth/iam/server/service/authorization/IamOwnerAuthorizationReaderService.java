package io.github.surezzzzzz.sdk.auth.iam.server.service.authorization;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response.OwnerAuthorizationCandidateApplicationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response.OwnerAuthorizationCandidateResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.internal.response.OwnerAuthorizationResolveResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamAkskAuthorizationChangeEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationStateEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamAkskAuthorizationChangeRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationStateRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 固定 IAM reader SERVICE 的 AKSK owner 授权投影出口。
 *
 * <p>这是在线授权真值。屏障、纪元落后、应用停用或投影失效均只返回 inactive，
 * 不能让 AKSK 使用旧快照继续签发 AKU。</p>
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamOwnerAuthorizationReaderService {

    private final SimpleIamServerProperties properties;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamApplicationAuthorizationRepository authorizationRepository;
    private final IamApplicationAuthorizationStateRepository authorizationStateRepository;
    private final IamAkskAuthorizationChangeRepository changeRepository;
    private final IamUserRepository userRepository;
    private final IamApplicationAuthorizationService applicationAuthorizationService;
    private final IamEffectiveRoleResolver effectiveRoleResolver;

    /**
     * 解析既有 binding 对应的授权投影。来源、人员或目标应用不存在时返回 null，
     * 由 HTTP 层统一映射为不泄露内部状态的 404；可识别目标下的授权失效返回 active=false。
     */
    public OwnerAuthorizationResolveResponse resolve(String ownerSourceId, String ownerSubjectId,
                                                     Long targetApplicationId) {
        // 先取 stream high-water，再读投影；resolve 只提供恢复起点，绝不能覆盖既有 cursor。
        Long resumeAfterSequence = currentHighWaterSequence();
        Long userId = resolveOwnerUserId(ownerSourceId, ownerSubjectId);
        if (userId == null || targetApplicationId == null || targetApplicationId.longValue() <= 0L) {
            return null;
        }
        IamUserEntity owner = userRepository.findById(userId).orElse(null);
        if (!isActive(owner)) {
            return inactive(resumeAfterSequence);
        }
        IamTrustedApplicationEntity application = trustedApplicationRepository.findById(targetApplicationId)
                .orElse(null);
        if (!isActive(application)) {
            return null;
        }
        // reader 不能复用管理面“缺行即补初始状态”的升级兼容路径；状态缺失时必须失败关闭。
        IamApplicationAuthorizationStateEntity state = authorizationStateRepository.findById(targetApplicationId)
                .orElse(null);
        if (!isInheritanceEnabled(state)) {
            return null;
        }
        IamApplicationAuthorizationEntity authorization = authorizationRepository
                .findByUserIdAndApplicationId(userId, targetApplicationId).orElse(null);
        if (!isActive(authorization) || isBarrierActive(state) || isProjectionStale(userId, authorization, state)) {
            return inactive(resumeAfterSequence);
        }

        Instant issuedAt = Instant.now();
        ApplicationAuthorizationContext context = applicationAuthorizationService.loadActiveContext(
                userId, targetApplicationId, issuedAt,
                issuedAt.plusSeconds(properties.getInternalReader().getAccessExpiresIn()));
        if (context == null || !context.isAdmitted()) {
            return inactive(resumeAfterSequence);
        }
        log.debug("AKSK owner 授权投影读取成功：ownerId={}, applicationId={}, ownerEpoch={}, applicationEpoch={}",
                userId, targetApplicationId, authorization.getOwnerSecurityEpoch(),
                authorization.getApplicationAuthorizationEpoch());
        return OwnerAuthorizationResolveResponse.builder()
                .active(true)
                .ownerSecurityEpoch(authorization.getOwnerSecurityEpoch())
                .applicationAuthorizationEpoch(authorization.getApplicationAuthorizationEpoch())
                .ownerInheritedAccessEpoch(state.getOwnerInheritedAccessEpoch())
                .projectionAccessEpoch(authorization.getProjectionAccessEpoch())
                .resumeAfterSequence(resumeAfterSequence)
                .ownerUsername(owner.getUsername())
                .iamAuthorization(ApplicationAuthorizationContextClaimMapper.toClaim(context))
                .build();
    }

    /**
     * 列出当前可创建 binding 的目标应用。目录只作服务端创建前校验，后续签发仍必须再次 resolve。
     */
    public OwnerAuthorizationCandidateResponse listCandidates(String ownerSourceId, String ownerSubjectId) {
        Long userId = resolveOwnerUserId(ownerSourceId, ownerSubjectId);
        if (userId == null) {
            return OwnerAuthorizationCandidateResponse.builder().items(Collections.emptyList()).build();
        }
        List<OwnerAuthorizationCandidateApplicationResponse> items = new ArrayList<>();
        for (IamApplicationAuthorizationEntity authorization : authorizationRepository.findByUserId(userId)) {
            OwnerAuthorizationResolveResponse resolved = resolve(ownerSourceId, ownerSubjectId,
                    authorization.getApplicationId());
            if (resolved == null || !resolved.isActive()) {
                continue;
            }
            IamTrustedApplicationEntity application = trustedApplicationRepository
                    .findById(authorization.getApplicationId()).orElse(null);
            if (application != null) {
                items.add(OwnerAuthorizationCandidateApplicationResponse.builder()
                        .applicationId(application.getId())
                        .applicationName(application.getApplicationName())
                        .applicationCodeSnapshot(application.getApplicationCode())
                        .build());
            }
        }
        return OwnerAuthorizationCandidateResponse.builder().items(items).build();
    }

    private Long resolveOwnerUserId(String ownerSourceId, String ownerSubjectId) {
        if (!properties.getOwnerSourceId().equals(ownerSourceId) || ownerSubjectId == null
                || !ownerSubjectId.matches("[1-9][0-9]*")) {
            return null;
        }
        try {
            return Long.valueOf(ownerSubjectId);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isActive(IamTrustedApplicationEntity application) {
        return application != null && application.getStatus() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == application.getStatus().intValue();
    }

    private boolean isActive(IamApplicationAuthorizationEntity authorization) {
        return authorization != null
                && authorization.getStatus() != null
                && authorization.getAdmitted() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == authorization.getStatus().intValue()
                && SimpleIamServerConstant.STATUS_ACTIVE == authorization.getAdmitted().intValue();
    }

    private boolean isActive(IamUserEntity user) {
        return user != null && user.getStatus() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == user.getStatus().intValue();
    }

    private boolean isInheritanceEnabled(IamApplicationAuthorizationStateEntity state) {
        return state != null && state.getOwnerInheritanceEnabled() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == state.getOwnerInheritanceEnabled().intValue();
    }

    private boolean isBarrierActive(IamApplicationAuthorizationStateEntity state) {
        return state.getRecomputeBarrier() == null
                || SimpleIamServerConstant.STATUS_ACTIVE == state.getRecomputeBarrier().intValue();
    }

    private boolean isProjectionStale(Long userId, IamApplicationAuthorizationEntity authorization,
                                      IamApplicationAuthorizationStateEntity state) {
        return authorization.getOwnerSecurityEpoch() == null
                || authorization.getApplicationAuthorizationEpoch() == null
                || state.getAuthorizationEpoch() == null
                || authorization.getOwnerSecurityEpoch().longValue()
                != effectiveRoleResolver.resolveUserPermissionVersion(userId).longValue() + 1L
                || authorization.getApplicationAuthorizationEpoch().longValue()
                != state.getAuthorizationEpoch().longValue();
    }

    /**
     * 失效 resolve 仅告知调用方从哪条变更日志恢复，不能借安全纪元等字段暴露旧投影。
     * 实际失效纪元由 change pull 最终态事件提供，AKSK 据此使本地投影失效。
     */
    private OwnerAuthorizationResolveResponse inactive(Long resumeAfterSequence) {
        return OwnerAuthorizationResolveResponse.inactive(resumeAfterSequence);
    }

    private Long currentHighWaterSequence() {
        IamAkskAuthorizationChangeEntity newest = changeRepository.findFirstByOrderBySourceSequenceDesc();
        return newest == null ? 0L : newest.getSourceSequence();
    }
}
