package io.github.surezzzzzz.sdk.auth.iam.server.service.bootstrap;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamRoleAuthorizationRuleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.manifest.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamRoleAuthorizationRuleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.manifest.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamApplicationAuthorizationStateService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamAuthorizationProjectionService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamEffectiveRoleResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;

/**
 * 启动期逐用户、逐事务追平授权投影；系统性错误直接中止并保留重算屏障。
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
@Slf4j
public class IamBootstrapProjectionReconciliationService {

    private final IamApplicationAuthorizationRepository authorizationRepository;
    private final IamRoleAuthorizationRuleRepository ruleRepository;
    private final IamApplicationPermissionManifestRepository manifestRepository;
    private final IamEffectiveRoleResolver effectiveRoleResolver;
    private final IamAuthorizationProjectionService projectionService;

    public void reconcile(Long applicationId,
                          IamApplicationAuthorizationStateService.RecomputeToken token,
                          BooleanSupplier leaseIsActive) {
        if (applicationId == null || token == null || leaseIsActive == null) {
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONFLICT,
                    "启动授权对账缺少可信应用、fencing token 或租约状态检查");
        }
        IamApplicationPermissionManifestEntity manifest = manifestRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new SimpleIamServerException(ErrorCode.APPLICATION_MANIFEST_NOT_FOUND,
                        "启动授权对账所需的应用权限清单不存在"));

        Set<Long> roleIds = new TreeSet<>();
        for (IamRoleAuthorizationRuleEntity rule : ruleRepository.findByApplicationId(applicationId)) {
            roleIds.add(rule.getRoleId());
        }
        Set<Long> coveredUserIds = new TreeSet<>(effectiveRoleResolver.resolveUserIdsByRoleIdIn(roleIds));
        List<Long> projectionUserIds = new ArrayList<>(coveredUserIds);
        int attempted = 0;
        int succeeded = 0;
        int missingUser = 0;
        int missingSubject = 0;

        for (Long userId : projectionUserIds) {
            requireLease(leaseIsActive, applicationId);
            attempted++;
            IamAuthorizationProjectionService.BootstrapProjectionResult result =
                    projectionService.recomputeProjectionForBootstrap(userId, applicationId, token);
            if (result == IamAuthorizationProjectionService.BootstrapProjectionResult.MISSING_USER) {
                missingUser++;
                log.warn("启动授权对账跳过不存在的用户投影：applicationId={}, userId={}", applicationId, userId);
            } else if (result == IamAuthorizationProjectionService.BootstrapProjectionResult.MISSING_SUBJECT) {
                missingSubject++;
                log.warn("启动授权对账跳过缺少 subjectId 的用户投影：applicationId={}, userId={}", applicationId, userId);
            } else {
                succeeded++;
            }
        }

        for (IamApplicationAuthorizationEntity authorization : authorizationRepository
                .findByApplicationId(applicationId)) {
            if (coveredUserIds.contains(authorization.getUserId())) {
                continue;
            }
            requireLease(leaseIsActive, applicationId);
            attempted++;
            IamAuthorizationProjectionService.BootstrapProjectionResult result =
                    projectionService.synchronizeManifestForBootstrap(authorization.getUserId(), applicationId,
                            manifest, token);
            if (result == IamAuthorizationProjectionService.BootstrapProjectionResult.MISSING_USER) {
                missingUser++;
                log.warn("启动授权对账跳过不存在的用户投影：applicationId={}, userId={}",
                        applicationId, authorization.getUserId());
            } else if (result == IamAuthorizationProjectionService.BootstrapProjectionResult.MISSING_SUBJECT) {
                missingSubject++;
                log.warn("启动授权对账跳过缺少 subjectId 的用户投影：applicationId={}, userId={}",
                        applicationId, authorization.getUserId());
            } else {
                succeeded++;
            }
        }
        requireLease(leaseIsActive, applicationId);
        log.info("启动授权对账完成：applicationId={}, manifestVersion={}, attempted={}, succeeded={}, missingUser={}, missingSubject={}, failed=0",
                applicationId, manifest.getManifestVersion(), attempted, succeeded, missingUser, missingSubject);
    }

    private void requireLease(BooleanSupplier leaseIsActive, Long applicationId) {
        if (!leaseIsActive.getAsBoolean()) {
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONFLICT,
                    "启动授权对账期间引导租约续期失败，授权屏障保持开启，等待下一实例接管");
        }
        log.debug("启动授权对账租约续期成功：applicationId={}", applicationId);
    }
}
