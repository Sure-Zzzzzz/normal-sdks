package io.github.surezzzzzz.sdk.auth.iam.server.service.authorization;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationStateEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationStateRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationMutationGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 可信应用 AKU 授权纪元的唯一写入口。
 *
 * <p>纪元只增不减；屏障存在或状态行缺失时，上游 reader 均须失败关闭。
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamApplicationAuthorizationStateService {

    private final IamApplicationAuthorizationStateRepository stateRepository;
    private final IamApplicationAuthorizationRepository authorizationRepository;
    private final IamTrustedApplicationMutationGuard mutationGuard;
    private final SimpleIamServerProperties properties;
    private final IamAkskAuthorizationChangeService changeService;

    @Transactional
    public IamApplicationAuthorizationStateEntity ensureInitialState(Long applicationId) {
        IamApplicationAuthorizationStateEntity existing = stateRepository.findById(applicationId).orElse(null);
        if (existing != null) {
            return existing;
        }
        Instant now = Instant.now();
        IamApplicationAuthorizationStateEntity state = new IamApplicationAuthorizationStateEntity();
        state.setApplicationId(applicationId);
        state.setAuthorizationEpoch(1L);
        state.setOwnerInheritedAccessEpoch(1L);
        state.setOwnerInheritanceEnabled(SimpleIamServerConstant.STATUS_INACTIVE);
        state.setRecomputeBarrier(SimpleIamServerConstant.STATUS_INACTIVE);
        state.setBarrierVersion(0L);
        state.setCreatedAt(now);
        state.setUpdatedAt(now);
        return stateRepository.save(state);
    }

    @Transactional
    public IamApplicationAuthorizationStateEntity advanceAuthorizationEpoch(Long applicationId) {
        IamApplicationAuthorizationStateEntity state = ensureInitialState(applicationId);
        state.setAuthorizationEpoch(state.getAuthorizationEpoch() + 1L);
        state.setUpdatedAt(Instant.now());
        state = stateRepository.save(state);
        synchronizeProjectionEpoch(state);
        changeService.recordTargetApplicationState(applicationId, IamAkskAuthorizationChangeService.REASON_APPLICATION_AUTHORIZATION_EPOCH_CHANGED);
        return state;
    }

    @Transactional
    public IamApplicationAuthorizationStateEntity updateOwnerInheritance(Long applicationId, boolean enabled) {
        mutationGuard.requireMutable(applicationId);
        if (enabled && !properties.getInternalReader().isEnabled()) {
            throw new SimpleIamServerException(ErrorCode.CONFIG_VALIDATION_FAILED,
                    "AKU 授权继承依赖 IAM 内部 reader SERVICE，当前未启用");
        }
        IamApplicationAuthorizationStateEntity state = ensureInitialState(applicationId);
        int expected = enabled ? SimpleIamServerConstant.STATUS_ACTIVE : SimpleIamServerConstant.STATUS_INACTIVE;
        if (state.getOwnerInheritanceEnabled() == null || state.getOwnerInheritanceEnabled().intValue() != expected) {
            state.setOwnerInheritanceEnabled(expected);
            state.setAuthorizationEpoch(state.getAuthorizationEpoch() + 1L);
            state.setOwnerInheritedAccessEpoch(state.getOwnerInheritedAccessEpoch() + 1L);
            state.setUpdatedAt(Instant.now());
            state = stateRepository.save(state);
            synchronizeProjectionEpoch(state);
            changeService.recordTargetApplicationState(applicationId, IamAkskAuthorizationChangeService.REASON_OWNER_INHERITANCE_CHANGED);
        }
        return state;
    }

    @Transactional
    public IamApplicationAuthorizationStateEntity getState(Long applicationId) {
        return stateRepository.findById(applicationId).orElseGet(
                () -> ensureInitialState(applicationId));
    }

    @Transactional
    public void deleteState(Long applicationId) {
        stateRepository.deleteById(applicationId);
    }

    /**
     * 推进仅属于 OWNER_INHERITED 的目标应用访问纪元。
     *
     * <p>可信应用停用、恢复或删除前调用，避免恢复后旧 AKU 因三权纪元未变化而复活。</p>
     */
    @Transactional
    public IamApplicationAuthorizationStateEntity advanceOwnerInheritedAccessEpoch(Long applicationId) {
        IamApplicationAuthorizationStateEntity state = ensureInitialState(applicationId);
        state.setOwnerInheritedAccessEpoch(state.getOwnerInheritedAccessEpoch() + 1L);
        state.setUpdatedAt(Instant.now());
        state = stateRepository.save(state);
        changeService.recordTargetApplicationState(applicationId, IamAkskAuthorizationChangeService.REASON_OWNER_INHERITED_ACCESS_EPOCH_CHANGED);
        return state;
    }

    /**
     * 状态纪元与投影标签必须在同一事务内收敛；否则在线 reader 会按旧标签失败关闭。
     */
    private void synchronizeProjectionEpoch(IamApplicationAuthorizationStateEntity state) {
        authorizationRepository.synchronizeApplicationAuthorizationEpoch(state.getApplicationId(),
                state.getAuthorizationEpoch(), Instant.now());
    }
}
