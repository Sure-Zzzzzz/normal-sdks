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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 可信应用 AKU 授权纪元的唯一写入口。
 *
 * <p>纪元只增不减；屏障存在或状态行缺失时，上游 reader 均须失败关闭。
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
@Slf4j
public class IamApplicationAuthorizationStateService {

    private static final String RECOMPUTE_BARRIER_CONFLICT_MESSAGE = "授权投影重算屏障已被其他实例接管或状态不匹配";
    private static final String RECOMPUTE_APPLICATION_ID_REQUIRED_MESSAGE = "授权投影重算必须指定可信应用ID";

    private final IamApplicationAuthorizationStateRepository stateRepository;
    private final IamApplicationAuthorizationRepository authorizationRepository;
    private final IamTrustedApplicationMutationGuard mutationGuard;
    private final SimpleIamServerProperties properties;
    private final IamOwnerAuthorizationChangeLogService changeService;

    /**
     * 首次开启屏障只推进一次授权纪元；已有屏障由新执行者递增独立 fencing token 接管。
     *
     * <p>调用方必须保证同一引导租约对每个应用只调用一次。若事务结果未知，不得由同一租约重试。</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecomputeToken beginRecompute(Long applicationId) {
        requireApplicationId(applicationId);
        stateRepository.insertIfAbsent(applicationId);
        IamApplicationAuthorizationStateEntity state = stateRepository.findByApplicationIdForUpdate(applicationId)
                .orElseThrow(() -> recomputeBarrierConflict());
        requireCompleteRecomputeState(state);

        if (state.getRecomputeBarrier().intValue() == SimpleIamServerConstant.STATUS_INACTIVE) {
            requireIncrementable(state.getAuthorizationEpoch());
            state.setAuthorizationEpoch(state.getAuthorizationEpoch() + 1L);
            state.setRecomputeBarrier(SimpleIamServerConstant.STATUS_ACTIVE);
        } else if (state.getRecomputeBarrier().intValue() != SimpleIamServerConstant.STATUS_ACTIVE) {
            throw recomputeBarrierConflict();
        }
        requireIncrementable(state.getBarrierVersion());
        state.setBarrierVersion(state.getBarrierVersion() + 1L);
        state.setUpdatedAt(Instant.now());
        stateRepository.saveAndFlush(state);
        changeService.recordTargetApplicationState(applicationId,
                IamOwnerAuthorizationChangeLogService.REASON_APPLICATION_AUTHORIZATION_RECOMPUTE_STARTED);

        RecomputeToken token = new RecomputeToken(state.getBarrierVersion(), state.getAuthorizationEpoch());
        log.info("授权投影重算屏障已开启：applicationId={}, barrierVersion={}, authorizationEpoch={}",
                applicationId, token.getBarrierVersion(), token.getAuthorizationEpoch());
        return token;
    }

    /**
     * 在调用方已有事务内锁定并验证屏障 token，锁会保持到调用方事务结束。
     *
     * <p>若同一事务还访问 trusted_application，调用方必须先锁 trusted_application 行以保持全局锁顺序。</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActiveRecomputeToken(Long applicationId, RecomputeToken expectedToken) {
        requireApplicationId(applicationId);
        if (expectedToken == null) {
            throw recomputeBarrierConflict();
        }
        IamApplicationAuthorizationStateEntity state = stateRepository.findByApplicationIdForUpdate(applicationId)
                .orElseThrow(() -> recomputeBarrierConflict());
        requireCompleteRecomputeState(state);
        if (state.getRecomputeBarrier().intValue() != SimpleIamServerConstant.STATUS_ACTIVE
                || !state.getBarrierVersion().equals(expectedToken.getBarrierVersion())
                || !state.getAuthorizationEpoch().equals(expectedToken.getAuthorizationEpoch())) {
            log.warn("授权投影重算屏障校验不匹配：applicationId={}, expectedBarrierVersion={}, actualBarrierVersion={}, "
                            + "expectedAuthorizationEpoch={}, actualAuthorizationEpoch={}, actualBarrier={}",
                    applicationId, expectedToken.getBarrierVersion(), state.getBarrierVersion(),
                    expectedToken.getAuthorizationEpoch(), state.getAuthorizationEpoch(), state.getRecomputeBarrier());
            throw recomputeBarrierConflict();
        }
    }

    /**
     * 普通管理事务在写 source 或 projection 前取得同一状态行锁，屏障期间整笔拒绝。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireNoActiveRecompute(Long applicationId) {
        requireApplicationId(applicationId);
        stateRepository.insertIfAbsent(applicationId);
        IamApplicationAuthorizationStateEntity state = stateRepository.findByApplicationIdForUpdate(applicationId)
                .orElseThrow(() -> recomputeBarrierConflict());
        requireCompleteRecomputeState(state);
        if (state.getRecomputeBarrier().intValue() == SimpleIamServerConstant.STATUS_ACTIVE) {
            log.warn("拒绝普通授权写入：应用授权投影正在启动对账，applicationId={}, barrierVersion={}",
                    applicationId, state.getBarrierVersion());
            throw recomputeBarrierConflict();
        }
    }

    @Transactional(readOnly = true)
    public boolean isRecomputePending(Long applicationId) {
        IamApplicationAuthorizationStateEntity state = stateRepository.findById(applicationId).orElse(null);
        return state != null && state.getRecomputeBarrier() != null
                && state.getRecomputeBarrier().intValue() == SimpleIamServerConstant.STATUS_ACTIVE;
    }

    /**
     * 仅当前 token 且目标纪元一致的执行者可以关闭屏障。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishRecompute(Long applicationId, RecomputeToken expectedToken) {
        requireApplicationId(applicationId);
        if (expectedToken == null) {
            throw recomputeBarrierConflict();
        }
        IamApplicationAuthorizationStateEntity state = stateRepository.findByApplicationIdForUpdate(applicationId)
                .orElseThrow(() -> recomputeBarrierConflict());
        requireCompleteRecomputeState(state);
        if (state.getRecomputeBarrier().intValue() != SimpleIamServerConstant.STATUS_ACTIVE
                || !state.getBarrierVersion().equals(expectedToken.getBarrierVersion())
                || !state.getAuthorizationEpoch().equals(expectedToken.getAuthorizationEpoch())) {
            log.warn("拒绝关闭已被接管或目标纪元不匹配的授权投影重算屏障：applicationId={}, expectedBarrierVersion={}, actualBarrierVersion={}",
                    applicationId, expectedToken.getBarrierVersion(), state.getBarrierVersion());
            throw recomputeBarrierConflict();
        }
        long staleProjectionCount = authorizationRepository.countValidSubjectsWithStaleApplicationEpoch(
                applicationId, expectedToken.getAuthorizationEpoch());
        if (staleProjectionCount > 0L) {
            log.warn("授权投影尚未追平，拒绝关闭重算屏障：applicationId={}, authorizationEpoch={}, staleProjectionCount={}",
                    applicationId, expectedToken.getAuthorizationEpoch(), staleProjectionCount);
            throw recomputeBarrierConflict();
        }
        state.setRecomputeBarrier(SimpleIamServerConstant.STATUS_INACTIVE);
        state.setUpdatedAt(Instant.now());
        stateRepository.saveAndFlush(state);
        changeService.recordTargetApplicationState(applicationId,
                IamOwnerAuthorizationChangeLogService.REASON_APPLICATION_AUTHORIZATION_RECOMPUTE_FINISHED);
        log.info("授权投影重算屏障已关闭：applicationId={}, barrierVersion={}, authorizationEpoch={}",
                applicationId, state.getBarrierVersion(), state.getAuthorizationEpoch());
    }

    private void requireApplicationId(Long applicationId) {
        if (applicationId == null) {
            throw new SimpleIamServerException(ErrorCode.VALIDATION_FAILED,
                    RECOMPUTE_APPLICATION_ID_REQUIRED_MESSAGE);
        }
    }

    private void requireCompleteRecomputeState(IamApplicationAuthorizationStateEntity state) {
        if (state.getAuthorizationEpoch() == null || state.getRecomputeBarrier() == null
                || state.getBarrierVersion() == null) {
            throw recomputeBarrierConflict();
        }
    }

    private void requireIncrementable(Long version) {
        if (version.longValue() == Long.MAX_VALUE) {
            throw recomputeBarrierConflict();
        }
    }

    private SimpleIamServerException recomputeBarrierConflict() {
        return new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CONFLICT,
                RECOMPUTE_BARRIER_CONFLICT_MESSAGE);
    }

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
        requireNoActiveRecompute(applicationId);
        IamApplicationAuthorizationStateEntity state = ensureInitialState(applicationId);
        state.setAuthorizationEpoch(state.getAuthorizationEpoch() + 1L);
        state.setUpdatedAt(Instant.now());
        state = stateRepository.save(state);
        synchronizeProjectionEpoch(state);
        changeService.recordTargetApplicationState(applicationId, IamOwnerAuthorizationChangeLogService.REASON_APPLICATION_AUTHORIZATION_EPOCH_CHANGED);
        return state;
    }

    @Transactional
    public IamApplicationAuthorizationStateEntity updateOwnerInheritance(Long applicationId, boolean enabled) {
        mutationGuard.requireMutable(applicationId);
        requireNoActiveRecompute(applicationId);
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
            changeService.recordTargetApplicationState(applicationId, IamOwnerAuthorizationChangeLogService.REASON_OWNER_INHERITANCE_CHANGED);
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
        mutationGuard.requireMutable(applicationId);
        requireNoActiveRecompute(applicationId);
        IamApplicationAuthorizationStateEntity state = ensureInitialState(applicationId);
        state.setOwnerInheritedAccessEpoch(state.getOwnerInheritedAccessEpoch() + 1L);
        state.setUpdatedAt(Instant.now());
        state = stateRepository.save(state);
        changeService.recordTargetApplicationState(applicationId, IamOwnerAuthorizationChangeLogService.REASON_OWNER_INHERITED_ACCESS_EPOCH_CHANGED);
        return state;
    }

    /**
     * 状态纪元与投影标签必须在同一事务内收敛；否则在线 reader 会按旧标签失败关闭。
     */
    private void synchronizeProjectionEpoch(IamApplicationAuthorizationStateEntity state) {
        authorizationRepository.synchronizeApplicationAuthorizationEpoch(state.getApplicationId(),
                state.getAuthorizationEpoch(), Instant.now());
    }

    @Getter
    public static final class RecomputeToken {

        private final Long barrierVersion;
        private final Long authorizationEpoch;

        private RecomputeToken(Long barrierVersion, Long authorizationEpoch) {
            this.barrierVersion = barrierVersion;
            this.authorizationEpoch = authorizationEpoch;
        }
    }
}
