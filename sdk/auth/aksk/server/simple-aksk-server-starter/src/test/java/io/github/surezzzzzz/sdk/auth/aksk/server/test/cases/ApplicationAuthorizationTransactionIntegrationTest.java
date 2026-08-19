package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.ApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventCause;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ApplicationAuthorizationManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.ApplicationAuthorizationTestHelper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * 应用授权与 Token 撤销的事务原子性集成测试。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleAkskServerTestApplication.class)
class ApplicationAuthorizationTransactionIntegrationTest {

    private static final String APPLICATION_CODE = "aksk-server";
    private static final String ORIGINAL_API_PERMISSION = "api.original";
    private static final String REPLACED_API_PERMISSION = "api.replaced";
    private static final String MANIFEST_VERSION = "transaction-manifest";
    private static final String MANIFEST_DIGEST = "transaction-manifest-digest";

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private ApplicationAuthorizationManagementService applicationAuthorizationManagementService;

    @Autowired
    private AkskApplicationAuthorizationRepository applicationAuthorizationRepository;

    @Autowired
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private TokenManagementService tokenManagementService;

    private String clientId;

    @BeforeEach
    void setUp() {
        ClientInfoResponse client = clientManagementService.createPlatformClient("Authorization Transaction Client");
        clientId = client.getClientId();
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(applicationAuthorizationRepository, client);
        AkskApplicationAuthorizationEntity authorization = requireAuthorization();
        authorization.setApiPermissionsJson("[\"" + ORIGINAL_API_PERMISSION + "\"]");
        authorization.setUpdatedAt(Instant.now());
        applicationAuthorizationRepository.save(authorization);
    }

    @AfterEach
    void tearDown() {
        reset(tokenManagementService);
        authorizationEntityRepository.deleteAll();
        applicationAuthorizationRepository.deleteAll();
        clientRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void shouldRollbackReplacementWhenTokenRevocationFails() {
        AkskApplicationAuthorizationEntity before = requireAuthorization();
        doThrow(new IllegalStateException("token revocation failed"))
                .when(tokenManagementService).revokeAllByClientId(eq(clientId), eq(TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED));

        assertThrows(IllegalStateException.class, () -> applicationAuthorizationManagementService.replaceLocal(
                clientId, request(REPLACED_API_PERMISSION)));

        AkskApplicationAuthorizationEntity after = requireAuthorization();
        assertEquals(before.getAuthorizationVersion(), after.getAuthorizationVersion());
        assertEquals(before.getApiPermissionsJson(), after.getApiPermissionsJson());
        assertTrue(after.getEnabled());
        assertTrue(after.getAdmitted());
        assertNull(after.getRevokedAt());
        verify(tokenManagementService).revokeAllByClientId(clientId,
                TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED);
    }

    @Test
    void shouldUseAuthorizationReplacedCauseForLocalReplacement() {
        applicationAuthorizationManagementService.replaceLocal(clientId, request(REPLACED_API_PERMISSION));

        verify(tokenManagementService).revokeAllByClientId(clientId,
                TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED);
    }

    @Test
    void shouldUseAuthorizationRevokedCauseForLocalRevocation() {
        applicationAuthorizationManagementService.revokeLocal(clientId);

        verify(tokenManagementService).revokeAllByClientId(clientId,
                TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED);
    }

    @Test
    void shouldRollbackMachineReplacementWhenTokenRevocationFails() {
        AkskApplicationAuthorizationEntity before = requireAuthorization();
        DataAccessPlan applicationPlan = plan(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION);
        DataAccessPlan tokenPlan = plan(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN);
        doThrow(new IllegalStateException("token revocation failed"))
                .when(tokenManagementService).revokeAllByClientId(eq(clientId), eq(tokenPlan),
                        eq(TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED));

        assertThrows(IllegalStateException.class, () -> applicationAuthorizationManagementService.replace(
                clientId, request(REPLACED_API_PERMISSION), applicationPlan, tokenPlan));

        assertUnchanged(before);
        verify(tokenManagementService).requireAllByClientIdAllowed(clientId, tokenPlan);
        verify(tokenManagementService).revokeAllByClientId(clientId, tokenPlan,
                TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED);
    }

    @Test
    void shouldUseAuthorizationReplacedCauseForMachineReplacement() {
        DataAccessPlan applicationPlan = plan(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION);
        DataAccessPlan tokenPlan = plan(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN);

        applicationAuthorizationManagementService.replace(clientId, request(REPLACED_API_PERMISSION),
                applicationPlan, tokenPlan);

        verify(tokenManagementService).revokeAllByClientId(clientId, tokenPlan,
                TokenEventCause.APPLICATION_AUTHORIZATION_REPLACED);
    }

    @Test
    void shouldUseAuthorizationRevokedCauseForMachineRevocation() {
        DataAccessPlan applicationPlan = plan(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION);
        DataAccessPlan tokenPlan = plan(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN);

        applicationAuthorizationManagementService.revoke(clientId, applicationPlan, tokenPlan);

        verify(tokenManagementService).revokeAllByClientId(clientId, tokenPlan,
                TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED);
    }

    @Test
    void shouldRollbackMachineRevocationWhenTokenRevocationFails() {
        AkskApplicationAuthorizationEntity before = requireAuthorization();
        DataAccessPlan applicationPlan = plan(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION);
        DataAccessPlan tokenPlan = plan(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN);
        doThrow(new IllegalStateException("token revocation failed"))
                .when(tokenManagementService).revokeAllByClientId(eq(clientId), eq(tokenPlan),
                        eq(TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED));

        assertThrows(IllegalStateException.class, () -> applicationAuthorizationManagementService.revoke(
                clientId, applicationPlan, tokenPlan));

        assertUnchanged(before);
        verify(tokenManagementService).requireAllByClientIdAllowed(clientId, tokenPlan);
        verify(tokenManagementService).revokeAllByClientId(clientId, tokenPlan,
                TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED);
    }

    @Test
    void shouldRollbackRevocationWhenTokenRevocationFails() {
        AkskApplicationAuthorizationEntity before = requireAuthorization();
        doThrow(new IllegalStateException("token revocation failed"))
                .when(tokenManagementService).revokeAllByClientId(eq(clientId),
                        eq(TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED));

        assertThrows(IllegalStateException.class,
                () -> applicationAuthorizationManagementService.revokeLocal(clientId));

        AkskApplicationAuthorizationEntity after = requireAuthorization();
        assertEquals(before.getAuthorizationVersion(), after.getAuthorizationVersion());
        assertEquals(before.getApiPermissionsJson(), after.getApiPermissionsJson());
        assertTrue(after.getEnabled());
        assertTrue(after.getAdmitted());
        assertNull(after.getRevokedAt());
        verify(tokenManagementService).revokeAllByClientId(clientId,
                TokenEventCause.APPLICATION_AUTHORIZATION_REVOKED);
    }

    private void assertUnchanged(AkskApplicationAuthorizationEntity before) {
        AkskApplicationAuthorizationEntity after = requireAuthorization();
        assertEquals(before.getAuthorizationVersion(), after.getAuthorizationVersion());
        assertEquals(before.getApiPermissionsJson(), after.getApiPermissionsJson());
        assertTrue(after.getEnabled());
        assertTrue(after.getAdmitted());
        assertNull(after.getRevokedAt());
    }

    private DataAccessPlan plan(String resource) {
        DataGrantDocument document = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Collections.singletonList(new DataGrant(resource,
                Collections.singletonList(SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE), false,
                Collections.singletonList(new DataConstraint(
                        SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_ID,
                        DataConstraintOperator.IN, Arrays.asList(clientId))))));
        return DataAccessPlan.evaluate(document, new DataPermissionRequest(resource,
                SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE));
    }

    private AkskApplicationAuthorizationEntity requireAuthorization() {
        return applicationAuthorizationRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalStateException("测试授权投影不存在"));
    }

    private ApplicationAuthorizationRequest request(String apiPermission) {
        ApplicationAuthorizationRequest request = new ApplicationAuthorizationRequest();
        request.setApplicationCode(APPLICATION_CODE);
        request.setAdmitted(Boolean.TRUE);
        request.setRoles(Collections.<String>emptyList());
        request.setPagePermissions(Collections.<String>emptyList());
        request.setApiPermissions(Collections.singletonList(apiPermission));
        request.setDataGrantDocument(null);
        request.setManifestVersion(MANIFEST_VERSION);
        request.setManifestDigest(MANIFEST_DIGEST);
        return request;
    }
}
