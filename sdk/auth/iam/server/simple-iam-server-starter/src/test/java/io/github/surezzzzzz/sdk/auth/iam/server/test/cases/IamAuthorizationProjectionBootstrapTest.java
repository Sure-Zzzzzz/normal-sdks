package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationStateEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamRoleAuthorizationRuleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.manifest.IamApplicationPermissionManifestEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationStateRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamRoleAuthorizationRuleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamRoleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamApplicationAuthorizationStateService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamAuthorizationProjectionService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamEffectiveRoleResolver;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamOwnerAuthorizationChangeLogService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.manifest.IamApplicationPermissionManifestService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationMutationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IamAuthorizationProjectionBootstrapTest {

    private static final Long APPLICATION_ID = 73L;
    private static final Long USER_ID = 12L;
    private static final Long ROLE_ID = 8L;

    @Mock
    private IamApplicationAuthorizationRepository authorizationRepository;
    @Mock
    private IamApplicationAuthorizationStateRepository stateRepository;
    @Mock
    private IamRoleRepository roleRepository;
    @Mock
    private IamRoleAuthorizationRuleRepository ruleRepository;
    @Mock
    private IamApplicationPermissionManifestService manifestService;
    @Mock
    private IamEffectiveRoleResolver effectiveRoleResolver;
    @Mock
    private IamOwnerAuthorizationChangeLogService changeService;
    @Mock
    private IamUserRepository userRepository;
    @Mock
    private IamTrustedApplicationMutationGuard mutationGuard;
    @Mock
    private SimpleIamServerProperties properties;

    private IamApplicationAuthorizationStateService stateService;
    private IamAuthorizationProjectionService projectionService;

    @BeforeEach
    void setUp() {
        stateService = new IamApplicationAuthorizationStateService(stateRepository, authorizationRepository,
                mutationGuard, properties, changeService);
        projectionService = new IamAuthorizationProjectionService(authorizationRepository, roleRepository,
                ruleRepository, manifestService, effectiveRoleResolver, stateService, changeService,
                userRepository, mutationGuard);
    }

    @Test
    void bootstrapProjectionRecomputesWithActiveBarrierToken() {
        IamApplicationAuthorizationStateEntity state = new IamApplicationAuthorizationStateEntity();
        state.setApplicationId(APPLICATION_ID);
        state.setAuthorizationEpoch(4L);
        state.setRecomputeBarrier(SimpleIamServerConstant.STATUS_INACTIVE);
        state.setBarrierVersion(0L);
        state.setOwnerInheritedAccessEpoch(1L);
        state.setOwnerInheritanceEnabled(SimpleIamServerConstant.STATUS_INACTIVE);
        when(stateRepository.findByApplicationIdForUpdate(APPLICATION_ID)).thenReturn(Optional.of(state));
        when(stateRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(state));
        IamApplicationAuthorizationStateService.RecomputeToken token = stateService.beginRecompute(APPLICATION_ID);

        IamUserEntity user = new IamUserEntity();
        user.setSubjectId("subject-12");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(effectiveRoleResolver.resolveRoleIds(USER_ID)).thenReturn(Collections.singleton(ROLE_ID));
        when(effectiveRoleResolver.resolveUserPermissionVersion(USER_ID)).thenReturn(2L);
        IamRoleEntity role = new IamRoleEntity();
        role.setId(ROLE_ID);
        role.setCode("operator");
        when(roleRepository.findAllById(anyIterable())).thenReturn(Collections.singletonList(role));

        IamRoleAuthorizationRuleEntity rule = new IamRoleAuthorizationRuleEntity();
        rule.setPagePermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(
                Collections.singletonList("application.home")));
        rule.setApiPermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
        when(ruleRepository.findByRoleIdInAndApplicationId(anyList(), eq(APPLICATION_ID)))
                .thenReturn(Collections.singletonList(rule));
        IamApplicationPermissionManifestEntity manifest = new IamApplicationPermissionManifestEntity();
        manifest.setManifestVersion(3L);
        manifest.setManifestDigest("manifest-digest");
        when(manifestService.requireManifest(APPLICATION_ID)).thenReturn(manifest);
        when(authorizationRepository.findByUserIdAndApplicationId(USER_ID, APPLICATION_ID))
                .thenReturn(Optional.empty());

        IamAuthorizationProjectionService.BootstrapProjectionResult result =
                projectionService.recomputeProjectionForBootstrap(USER_ID, APPLICATION_ID, token);

        assertEquals(IamAuthorizationProjectionService.BootstrapProjectionResult.UPDATED, result);
        ArgumentCaptor<IamApplicationAuthorizationEntity> projectionCaptor =
                ArgumentCaptor.forClass(IamApplicationAuthorizationEntity.class);
        verify(authorizationRepository).save(projectionCaptor.capture());
        IamApplicationAuthorizationEntity projection = projectionCaptor.getValue();
        assertNotNull(projection);
        assertEquals(5L, projection.getApplicationAuthorizationEpoch());
        assertEquals(Collections.singletonList("operator"),
                IamApplicationAuthorizationJsonCodec.readStringList(projection.getRolesJson(), "roles"));
        verify(stateRepository, times(1)).insertIfAbsent(APPLICATION_ID);
        verify(stateRepository, times(2)).findByApplicationIdForUpdate(APPLICATION_ID);
    }
}
