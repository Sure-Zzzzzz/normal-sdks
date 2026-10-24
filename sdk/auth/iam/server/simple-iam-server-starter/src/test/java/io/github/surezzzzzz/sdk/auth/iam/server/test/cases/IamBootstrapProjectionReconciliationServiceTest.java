package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationStateEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamRoleAuthorizationRuleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.manifest.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationStateRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamRoleAuthorizationRuleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.manifest.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamApplicationAuthorizationStateService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamAuthorizationProjectionService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamEffectiveRoleResolver;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamOwnerAuthorizationChangeLogService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.bootstrap.IamBootstrapProjectionReconciliationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationMutationGuard;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
class IamBootstrapProjectionReconciliationServiceTest {

    private static final Long APPLICATION_ID = 71L;

    @Mock
    private IamApplicationAuthorizationStateRepository stateRepository;
    @Mock
    private IamApplicationAuthorizationRepository authorizationRepository;
    @Mock
    private IamRoleAuthorizationRuleRepository ruleRepository;
    @Mock
    private IamApplicationPermissionManifestRepository manifestRepository;
    @Mock
    private IamEffectiveRoleResolver effectiveRoleResolver;
    @Mock
    private IamAuthorizationProjectionService projectionService;
    @Mock
    private IamTrustedApplicationMutationGuard mutationGuard;
    @Mock
    private SimpleIamServerProperties properties;
    @Mock
    private IamOwnerAuthorizationChangeLogService changeLogService;

    private IamBootstrapProjectionReconciliationService service;
    private IamApplicationAuthorizationStateService.RecomputeToken token;

    @BeforeEach
    void setUp() {
        IamApplicationAuthorizationStateEntity state = new IamApplicationAuthorizationStateEntity();
        state.setApplicationId(APPLICATION_ID);
        state.setAuthorizationEpoch(1L);
        state.setRecomputeBarrier(SimpleIamServerConstant.STATUS_INACTIVE);
        state.setBarrierVersion(0L);
        when(stateRepository.findByApplicationIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(state));
        token = new IamApplicationAuthorizationStateService(stateRepository, authorizationRepository, mutationGuard,
                properties, changeLogService).beginRecompute(APPLICATION_ID);
        service = new IamBootstrapProjectionReconciliationService(authorizationRepository, ruleRepository,
                manifestRepository, effectiveRoleResolver, projectionService);
    }

    @Test
    void reconciliationProcessesCoveredAndUncoveredRowsIndependently() {
        IamRoleAuthorizationRuleEntity rule = new IamRoleAuthorizationRuleEntity();
        rule.setRoleId(5L);
        IamApplicationPermissionManifestEntity manifest = new IamApplicationPermissionManifestEntity();
        manifest.setManifestVersion(4L);
        when(manifestRepository.findByApplicationId(APPLICATION_ID)).thenReturn(Optional.of(manifest));
        when(ruleRepository.findByApplicationId(APPLICATION_ID)).thenReturn(Collections.singletonList(rule));
        when(effectiveRoleResolver.resolveUserIdsByRoleIdIn(Collections.singleton(5L)))
                .thenReturn(new java.util.TreeSet<>(Arrays.asList(10L, 11L)));
        when(authorizationRepository.findByApplicationId(APPLICATION_ID)).thenReturn(Arrays.asList(
                authorization(10L), authorization(11L), authorization(12L)));
        when(projectionService.recomputeProjectionForBootstrap(eq(10L), eq(APPLICATION_ID), eq(token)))
                .thenReturn(IamAuthorizationProjectionService.BootstrapProjectionResult.UPDATED);
        when(projectionService.recomputeProjectionForBootstrap(eq(11L), eq(APPLICATION_ID), eq(token)))
                .thenReturn(IamAuthorizationProjectionService.BootstrapProjectionResult.MISSING_SUBJECT);
        when(projectionService.synchronizeManifestForBootstrap(eq(12L), eq(APPLICATION_ID), eq(manifest), eq(token)))
                .thenReturn(IamAuthorizationProjectionService.BootstrapProjectionResult.MISSING_USER);
        AtomicInteger renewals = new AtomicInteger();

        log.info("按授权重建屏障逐条处理投影：applicationId={}, subjectCount={}", APPLICATION_ID, 3);
        service.reconcile(APPLICATION_ID, token, () -> {
            renewals.incrementAndGet();
            return true;
        });

        verify(projectionService).recomputeProjectionForBootstrap(10L, APPLICATION_ID, token);
        verify(projectionService).recomputeProjectionForBootstrap(11L, APPLICATION_ID, token);
        verify(projectionService).synchronizeManifestForBootstrap(12L, APPLICATION_ID, manifest, token);
        log.info("授权重建批次完成：applicationId={}, leaseRenewals={}", APPLICATION_ID, renewals.get());
        org.junit.jupiter.api.Assertions.assertEquals(4, renewals.get());
    }

    @Test
    void leaseLossStopsBeforeTheNextProjectionTransaction() {
        IamRoleAuthorizationRuleEntity rule = new IamRoleAuthorizationRuleEntity();
        rule.setRoleId(5L);
        when(manifestRepository.findByApplicationId(APPLICATION_ID))
                .thenReturn(Optional.of(new IamApplicationPermissionManifestEntity()));
        when(ruleRepository.findByApplicationId(APPLICATION_ID)).thenReturn(Collections.singletonList(rule));
        when(effectiveRoleResolver.resolveUserIdsByRoleIdIn(Collections.singleton(5L)))
                .thenReturn(Collections.singleton(10L));

        log.info("验证授权重建租约丢失后停止后续投影事务：applicationId={}", APPLICATION_ID);
        assertThrows(SimpleIamServerException.class,
                () -> service.reconcile(APPLICATION_ID, token, () -> false));

        verify(projectionService, never()).recomputeProjectionForBootstrap(any(), eq(APPLICATION_ID), eq(token));
    }

    private IamApplicationAuthorizationEntity authorization(Long userId) {
        IamApplicationAuthorizationEntity authorization = new IamApplicationAuthorizationEntity();
        authorization.setApplicationId(APPLICATION_ID);
        authorization.setUserId(userId);
        return authorization;
    }

}
