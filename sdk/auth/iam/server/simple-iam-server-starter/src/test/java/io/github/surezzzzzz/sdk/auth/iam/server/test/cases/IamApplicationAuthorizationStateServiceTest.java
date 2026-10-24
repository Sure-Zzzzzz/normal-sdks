package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationStateEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationStateRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamApplicationAuthorizationStateService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamOwnerAuthorizationChangeLogService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationMutationGuard;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Slf4j
class IamApplicationAuthorizationStateServiceTest {

    private static final Long APPLICATION_ID = 71L;

    @Mock
    private IamApplicationAuthorizationStateRepository stateRepository;

    @Mock
    private IamApplicationAuthorizationRepository authorizationRepository;

    @Mock
    private IamTrustedApplicationMutationGuard mutationGuard;

    @Mock
    private SimpleIamServerProperties properties;

    @Mock
    private IamOwnerAuthorizationChangeLogService changeService;

    private IamApplicationAuthorizationStateService stateService;

    @BeforeEach
    void setUp() {
        stateService = new IamApplicationAuthorizationStateService(stateRepository, authorizationRepository,
                mutationGuard, properties, changeService);
    }

    @Test
    void beginRecomputeInitializesBeforeLockingAndAdvancesEpochOnlyWhenOpeningBarrier() {
        IamApplicationAuthorizationStateEntity state = state(4L, 10L, SimpleIamServerConstant.STATUS_INACTIVE);
        when(stateRepository.findByApplicationIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(state));

        IamApplicationAuthorizationStateService.RecomputeToken token = stateService.beginRecompute(APPLICATION_ID);

        log.info("首次开启屏障：applicationId={}, barrierVersion={}, authorizationEpoch={}",
                APPLICATION_ID, token.getBarrierVersion(), token.getAuthorizationEpoch());
        assertEquals(11L, token.getBarrierVersion());
        assertEquals(5L, token.getAuthorizationEpoch());
        assertEquals(SimpleIamServerConstant.STATUS_ACTIVE, state.getRecomputeBarrier());
        InOrder order = inOrder(stateRepository);
        order.verify(stateRepository).insertIfAbsent(APPLICATION_ID);
        order.verify(stateRepository).findByApplicationIdForUpdate(APPLICATION_ID);
        order.verify(stateRepository).saveAndFlush(state);
        verify(changeService).recordTargetApplicationState(APPLICATION_ID,
                IamOwnerAuthorizationChangeLogService.REASON_APPLICATION_AUTHORIZATION_RECOMPUTE_STARTED);
    }

    @Test
    void beginRecomputeTakeoverAdvancesOnlyBarrierVersion() {
        IamApplicationAuthorizationStateEntity state = state(8L, 3L, SimpleIamServerConstant.STATUS_ACTIVE);
        when(stateRepository.findByApplicationIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(state));

        IamApplicationAuthorizationStateService.RecomputeToken token = stateService.beginRecompute(APPLICATION_ID);

        log.info("接管屏障：applicationId={}, barrierVersion={}, authorizationEpoch={}",
                APPLICATION_ID, token.getBarrierVersion(), token.getAuthorizationEpoch());
        assertEquals(4L, token.getBarrierVersion());
        assertEquals(8L, token.getAuthorizationEpoch());
        assertEquals(SimpleIamServerConstant.STATUS_ACTIVE, state.getRecomputeBarrier());
        verify(stateRepository).insertIfAbsent(APPLICATION_ID);
        verify(stateRepository).saveAndFlush(state);
    }

    @Test
    void requireActiveRecomputeTokenAcceptsCurrentToken() {
        IamApplicationAuthorizationStateService.RecomputeToken token = begin(state(7L, 2L,
                SimpleIamServerConstant.STATUS_INACTIVE));

        stateService.requireActiveRecomputeToken(APPLICATION_ID, token);

        log.info("当前 fencing token 被接受：applicationId={}, barrierVersion={}, authorizationEpoch={}",
                APPLICATION_ID, token.getBarrierVersion(), token.getAuthorizationEpoch());
        verify(stateRepository, times(2)).findByApplicationIdForUpdate(APPLICATION_ID);
    }

    @Test
    void requireActiveRecomputeTokenRejectsSupersededToken() {
        IamApplicationAuthorizationStateEntity state = state(7L, 2L, SimpleIamServerConstant.STATUS_INACTIVE);
        IamApplicationAuthorizationStateService.RecomputeToken token = begin(state);
        state.setBarrierVersion(state.getBarrierVersion() + 1L);

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> {
                    stateService.requireActiveRecomputeToken(APPLICATION_ID, token);
                    authorizationRepository.synchronizeApplicationAuthorizationEpoch(APPLICATION_ID,
                            token.getAuthorizationEpoch(), Instant.now());
                });

        log.info("已被接管的 token 被拒绝：applicationId={}, expectedBarrierVersion={}, actualBarrierVersion={}",
                APPLICATION_ID, token.getBarrierVersion(), state.getBarrierVersion());
        assertEquals(ErrorCode.APPLICATION_AUTHORIZATION_CONFLICT, exception.getErrorCode());
        verify(authorizationRepository, never()).synchronizeApplicationAuthorizationEpoch(
                eq(APPLICATION_ID), eq(token.getAuthorizationEpoch()), any(Instant.class));
    }

    @Test
    void finishRecomputeClosesBarrierForCurrentToken() {
        IamApplicationAuthorizationStateEntity state = state(7L, 2L, SimpleIamServerConstant.STATUS_INACTIVE);
        IamApplicationAuthorizationStateService.RecomputeToken token = begin(state);

        stateService.finishRecompute(APPLICATION_ID, token);

        log.info("当前 token 关闭屏障：applicationId={}, barrierVersion={}, authorizationEpoch={}",
                APPLICATION_ID, token.getBarrierVersion(), token.getAuthorizationEpoch());
        assertEquals(SimpleIamServerConstant.STATUS_INACTIVE, state.getRecomputeBarrier());
        verify(stateRepository, times(2)).saveAndFlush(state);
        verify(changeService).recordTargetApplicationState(APPLICATION_ID,
                IamOwnerAuthorizationChangeLogService.REASON_APPLICATION_AUTHORIZATION_RECOMPUTE_FINISHED);
    }

    @Test
    void finishRecomputeRejectsStaleTokenAndKeepsBarrierActive() {
        IamApplicationAuthorizationStateEntity state = state(7L, 2L, SimpleIamServerConstant.STATUS_INACTIVE);
        IamApplicationAuthorizationStateService.RecomputeToken token = begin(state);
        state.setBarrierVersion(state.getBarrierVersion() + 1L);

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> stateService.finishRecompute(APPLICATION_ID, token));

        log.info("过期 token 不能关闭屏障：applicationId={}, expectedBarrierVersion={}, actualBarrierVersion={}",
                APPLICATION_ID, token.getBarrierVersion(), state.getBarrierVersion());
        assertEquals(ErrorCode.APPLICATION_AUTHORIZATION_CONFLICT, exception.getErrorCode());
        assertEquals(SimpleIamServerConstant.STATUS_ACTIVE, state.getRecomputeBarrier());
    }

    @Test
    void finishRecomputeKeepsBarrierActiveUntilValidProjectionsCatchUp() {
        IamApplicationAuthorizationStateEntity state = state(7L, 2L, SimpleIamServerConstant.STATUS_INACTIVE);
        IamApplicationAuthorizationStateService.RecomputeToken token = begin(state);
        when(authorizationRepository.countValidSubjectsWithStaleApplicationEpoch(APPLICATION_ID,
                token.getAuthorizationEpoch())).thenReturn(1L);

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> stateService.finishRecompute(APPLICATION_ID, token));

        log.info("合法主体投影未追平时保持屏障：applicationId={}, authorizationEpoch={}",
                APPLICATION_ID, token.getAuthorizationEpoch());
        assertEquals(ErrorCode.APPLICATION_AUTHORIZATION_CONFLICT, exception.getErrorCode());
        assertEquals(SimpleIamServerConstant.STATUS_ACTIVE, state.getRecomputeBarrier());
    }

    private IamApplicationAuthorizationStateEntity state(long epoch, long barrierVersion, int barrier) {
        IamApplicationAuthorizationStateEntity state = new IamApplicationAuthorizationStateEntity();
        state.setApplicationId(APPLICATION_ID);
        state.setAuthorizationEpoch(epoch);
        state.setBarrierVersion(barrierVersion);
        state.setRecomputeBarrier(barrier);
        return state;
    }

    private IamApplicationAuthorizationStateService.RecomputeToken begin(
            IamApplicationAuthorizationStateEntity state) {
        when(stateRepository.findByApplicationIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(state));
        return stateService.beginRecompute(APPLICATION_ID);
    }
}
