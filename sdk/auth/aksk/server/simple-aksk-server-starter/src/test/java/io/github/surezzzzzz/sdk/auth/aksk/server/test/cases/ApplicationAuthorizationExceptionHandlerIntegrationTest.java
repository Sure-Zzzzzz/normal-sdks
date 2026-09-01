package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.ApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ApplicationAuthorizationManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.ApplicationAuthorizationTestHelper;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.JwtTokenTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * 应用授权并发异常 HTTP 映射集成测试。
 *
 * @author surezzzzzz
 */
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ApplicationAuthorizationExceptionHandlerIntegrationTest {

    private static final String APPLICATION_CODE = "aksk-server";
    private static final String MANIFEST_VERSION = "exception-handler-manifest";
    private static final String MANIFEST_DIGEST = "exception-handler-manifest-digest";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private AkskApplicationAuthorizationRepository applicationAuthorizationRepository;

    @Autowired
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ApplicationAuthorizationManagementService applicationAuthorizationManagementService;

    private ClientInfoResponse managementClient;

    @BeforeEach
    void setUp() {
        managementClient = clientManagementService.createPlatformClient("Authorization Conflict Manager");
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(
                applicationAuthorizationRepository, managementClient);
    }

    @AfterEach
    void tearDown() {
        authorizationEntityRepository.deleteAll();
        applicationAuthorizationRepository.deleteAll();
        clientRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void shouldMapOptimisticLockFailureToEmptyConflictResponse() {
        String managementToken = JwtTokenTestHelper.getTokenByClientCredentials(restTemplate, port,
                managementClient.getClientId(), managementClient.getClientSecret());
        doThrow(new OptimisticLockingFailureException("concurrent authorization replacement"))
                .when(applicationAuthorizationManagementService).replace(eq(managementClient.getClientId()),
                        any(ApplicationAuthorizationRequest.class), any(), any());

        ResponseEntity<Void> response = restTemplate.exchange(
                url("/api/application-authorization/" + managementClient.getClientId()), HttpMethod.PUT,
                JwtTokenTestHelper.createAuthEntity(managementToken, request()), Void.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNull(response.getBody(), "并发冲突响应不得暴露异常或授权内容");
    }

    private ApplicationAuthorizationRequest request() {
        ApplicationAuthorizationRequest request = new ApplicationAuthorizationRequest();
        request.setApplicationCode(APPLICATION_CODE);
        request.setAdmitted(Boolean.TRUE);
        request.setRoles(Collections.<String>emptyList());
        request.setPagePermissions(Collections.<String>emptyList());
        request.setApiPermissions(Collections.singletonList("api.concurrent"));
        request.setDataGrantDocument(null);
        request.setManifestVersion(MANIFEST_VERSION);
        request.setManifestDigest(MANIFEST_DIGEST);
        return request;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
