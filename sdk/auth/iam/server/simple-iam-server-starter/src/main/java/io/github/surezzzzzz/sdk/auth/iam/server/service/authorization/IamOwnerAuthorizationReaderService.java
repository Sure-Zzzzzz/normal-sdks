package io.github.surezzzzzz.sdk.auth.iam.server.service.authorization;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationCandidate;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationReadResult;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationStateEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamOwnerAuthorizationChangeLogEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationStateRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamOwnerAuthorizationChangeLogRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 固定协作 reader SERVICE 的 owner 授权投影出口。
 *
 * <p>这是在线授权真值。屏障、纪元落后、应用停用或投影失效均只返回 inactive，
 * 不能让协作消费方使用旧快照继续签发凭证。</p>
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamOwnerAuthorizationReaderService {

    private final SimpleIamServerProperties properties;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamApplicationAuthorizationRepository authorizationRepository;
    private final IamApplicationAuthorizationStateRepository authorizationStateRepository;
    private final IamOwnerAuthorizationChangeLogRepository changeRepository;
    private final IamUserRepository userRepository;
    private final IamApplicationAuthorizationService applicationAuthorizationService;
    private final IamEffectiveRoleResolver effectiveRoleResolver;

    /**
     * 解析既有 binding 对应的授权投影。来源、人员或目标应用不存在时返回 null，
     * 由 HTTP 层统一映射为不泄露内部状态的 404；可识别目标下的授权失效返回 active=false。
     */
    public OwnerAuthorizationReadResult resolve(String ownerSourceId, String ownerSubjectId,
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
        log.debug("协作方 owner 授权投影读取成功：ownerId={}, applicationId={}, ownerEpoch={}, applicationEpoch={}",
                userId, targetApplicationId, authorization.getOwnerSecurityEpoch(),
                authorization.getApplicationAuthorizationEpoch());
        return new OwnerAuthorizationReadResult(true,
                authorization.getOwnerSecurityEpoch(),
                authorization.getApplicationAuthorizationEpoch(),
                state.getOwnerInheritedAccessEpoch(),
                authorization.getProjectionAccessEpoch(),
                resumeAfterSequence,
                owner.getUsername(),
                ApplicationAuthorizationContextClaimMapper.toClaim(context));
    }

    /**
     * 列出当前可创建 binding 的目标应用。目录只作服务端创建前校验，后续签发仍必须再次 resolve。
     */
    public List<OwnerAuthorizationCandidate> listCandidates(String ownerSourceId, String ownerSubjectId) {
        Long userId = resolveOwnerUserId(ownerSourceId, ownerSubjectId);
        if (userId == null) {
            return Collections.emptyList();
        }
        List<OwnerAuthorizationCandidate> items = new ArrayList<>();
        for (IamApplicationAuthorizationEntity authorization : authorizationRepository.findByUserId(userId)) {
            OwnerAuthorizationReadResult resolved = resolve(ownerSourceId, ownerSubjectId,
                    authorization.getApplicationId());
            if (resolved == null || !resolved.isActive()) {
                continue;
            }
            IamTrustedApplicationEntity application = trustedApplicationRepository
                    .findById(authorization.getApplicationId()).orElse(null);
            if (application != null) {
                items.add(new OwnerAuthorizationCandidate(application.getId(),
                        application.getApplicationName(), application.getApplicationCode()));
            }
        }
        return items;
    }

    /**
     * 对外主体解析：按 iam_user.subject_id 查找并翻译为内部 userId 供既有投影链使用；
     * 未匹配/非法一律返回 null（失败关闭，响应不区分原因）。
     * 1.3.0 起主体形态为 subjectId 字符串（原数字 id 形态随 3.2.0 窗口关闭）。
     */
    private Long resolveOwnerUserId(String ownerSourceId, String ownerSubjectId) {
        if (!properties.getOwnerSourceId().equals(ownerSourceId) || ownerSubjectId == null
                || ownerSubjectId.isEmpty()
                || ownerSubjectId.length() > io.github.surezzzzzz.sdk.auth.iam.core.constant.SimpleIamCoreConstant.SUBJECT_ID_MAX_LENGTH) {
            return null;
        }
        return userRepository.findBySubjectId(ownerSubjectId)
                .map(IamUserEntity::getId)
                .orElse(null);
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
     * 实际失效纪元由 change pull 最终态事件提供，协作消费方据此使本地投影失效。
     */
    private OwnerAuthorizationReadResult inactive(Long resumeAfterSequence) {
        return OwnerAuthorizationReadResult.inactive(resumeAfterSequence);
    }

    private Long currentHighWaterSequence() {
        IamOwnerAuthorizationChangeLogEntity newest = changeRepository.findFirstByOrderBySourceSequenceDesc();
        return newest == null ? 0L : newest.getSourceSequence();
    }
}
