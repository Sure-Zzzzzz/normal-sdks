package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationStateEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamOwnerAuthorizationChangeLogEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationStateRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamOwnerAuthorizationChangeLogRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamApplicationAuthorizationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamOwnerAuthorizationChangeLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IamOwnerAuthorizationChangeLogBarrierTest {

    private static final Long APPLICATION_ID = 73L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private SimpleIamServerProperties properties;
    @Mock
    private IamOwnerAuthorizationChangeLogRepository changeRepository;
    @Mock
    private IamApplicationAuthorizationStateRepository stateRepository;
    @Mock
    private IamUserRepository userRepository;
    @Mock
    private IamTrustedApplicationRepository trustedApplicationRepository;
    @Mock
    private IamApplicationAuthorizationService applicationAuthorizationService;

    private IamOwnerAuthorizationChangeLogService service;

    @BeforeEach
    void setUp() {
        service = new IamOwnerAuthorizationChangeLogService(properties, changeRepository, stateRepository,
                userRepository, trustedApplicationRepository, applicationAuthorizationService);
        IamTrustedApplicationEntity application = new IamTrustedApplicationEntity();
        application.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        when(trustedApplicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
    }

    @Test
    void targetStateEventClosesLocalProjectionGateWhileRecomputeIsActive() throws Exception {
        IamApplicationAuthorizationStateEntity state = state(SimpleIamServerConstant.STATUS_ACTIVE);
        when(stateRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(state));

        service.recordTargetApplicationState(APPLICATION_ID,
                IamOwnerAuthorizationChangeLogService.REASON_APPLICATION_AUTHORIZATION_RECOMPUTE_STARTED);

        Map<String, Object> payload = savedPayload();
        assertFalse((Boolean) payload.get("active"));
    }

    @Test
    void targetStateEventOpensLocalProjectionGateAfterRecomputeFinishes() throws Exception {
        IamApplicationAuthorizationStateEntity state = state(SimpleIamServerConstant.STATUS_INACTIVE);
        when(stateRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(state));

        service.recordTargetApplicationState(APPLICATION_ID,
                IamOwnerAuthorizationChangeLogService.REASON_APPLICATION_AUTHORIZATION_RECOMPUTE_FINISHED);

        Map<String, Object> payload = savedPayload();
        assertTrue((Boolean) payload.get("active"));
    }

    private IamApplicationAuthorizationStateEntity state(int barrier) {
        IamApplicationAuthorizationStateEntity state = new IamApplicationAuthorizationStateEntity();
        state.setApplicationId(APPLICATION_ID);
        state.setAuthorizationEpoch(5L);
        state.setOwnerInheritedAccessEpoch(2L);
        state.setOwnerInheritanceEnabled(SimpleIamServerConstant.STATUS_ACTIVE);
        state.setRecomputeBarrier(barrier);
        state.setBarrierVersion(3L);
        return state;
    }

    private Map<String, Object> savedPayload() throws Exception {
        ArgumentCaptor<IamOwnerAuthorizationChangeLogEntity> captor =
                ArgumentCaptor.forClass(IamOwnerAuthorizationChangeLogEntity.class);
        verify(changeRepository).save(captor.capture());
        return OBJECT_MAPPER.readValue(captor.getValue().getPayloadJson(),
                new TypeReference<Map<String, Object>>() {
                });
    }
}
