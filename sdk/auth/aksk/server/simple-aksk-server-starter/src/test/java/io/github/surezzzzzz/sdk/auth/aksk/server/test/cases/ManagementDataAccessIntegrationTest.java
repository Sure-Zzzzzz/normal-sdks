package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2AuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AkskApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.ApplicationAuthorizationTestHelper;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.JwtTokenTestHelper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 管理 REST 数据范围集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ManagementDataAccessIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private OAuth2AuthorizationEntityRepository authorizationRepository;

    @Autowired
    private AkskApplicationAuthorizationRepository applicationAuthorizationRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private ClientInfoResponse managementClient;

    @BeforeEach
    void setUp() {
        managementClient = clientManagementService.createPlatformClient("Management Data Access Client");
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(
                applicationAuthorizationRepository, managementClient);
    }

    @AfterEach
    void tearDown() {
        authorizationRepository.deleteAll();
        applicationAuthorizationRepository.deleteAll();
        clientRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void shouldFilterClientResultsBeforePaginationForOwnerRestriction() {
        ClientInfoResponse allowedFirst = clientManagementService.createUserClient(
                "owner-a", "Owner A", "Allowed User Client A");
        ClientInfoResponse allowedSecond = clientManagementService.createUserClient(
                "owner-a", "Owner A", "Allowed User Client B");
        clientManagementService.createUserClient("owner-b", "Owner B", "Denied User Client");
        clientManagementService.createPlatformClient("Denied Platform Client");

        String token = issueManagementToken(Arrays.asList(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_READ),
                document(grant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
                        SimpleAkskServerConstant.MANAGEMENT_ACTION_READ,
                        constraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_TYPE,
                                ClientType.USER.getValue()),
                        constraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_OWNER_USER_ID, "owner-a"))));

        PageResponse<ClientInfoResponse> firstPage = getClientPage(token, 1, 1);
        PageResponse<ClientInfoResponse> secondPage = getClientPage(token, 2, 1);

        assertEquals(2L, firstPage.getTotal().longValue(), "总数必须在数据范围过滤后统计");
        assertEquals(1, firstPage.getData().size());
        assertEquals(1, secondPage.getData().size());
        assertTrue(firstPage.getData().stream().allMatch(client -> "owner-a".equals(client.getOwnerUserId())));
        assertTrue(secondPage.getData().stream().allMatch(client -> "owner-a".equals(client.getOwnerUserId())));
        assertTrue(containsClient(firstPage, allowedFirst.getClientId()) || containsClient(secondPage, allowedFirst.getClientId()));
        assertTrue(containsClient(firstPage, allowedSecond.getClientId()) || containsClient(secondPage, allowedSecond.getClientId()));
    }

    @Test
    void shouldNotCombineConstraintsAcrossDifferentGrants() {
        ClientInfoResponse target = clientManagementService.createUserClient(
                "owner-a", "Owner A", "Cross Grant Target");

        String token = issueManagementToken(Arrays.asList(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_READ),
                document(
                        grant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
                                SimpleAkskServerConstant.MANAGEMENT_ACTION_READ,
                                constraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_TYPE,
                                        ClientType.USER.getValue()),
                                constraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_OWNER_USER_ID, "owner-b")),
                        grant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
                                SimpleAkskServerConstant.MANAGEMENT_ACTION_READ,
                                constraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_TYPE,
                                        ClientType.PLATFORM.getValue()),
                                constraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_OWNER_USER_ID, "owner-a"))));

        ResponseEntity<String> response = restTemplate.exchange(clientUrl("/api/client/" + target.getClientId()),
                HttpMethod.GET, JwtTokenTestHelper.createAuthEntity(token), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "不同 grant 的维度不能拼接授权目标");
    }

    @Test
    void shouldFilterMysqlAndRedisTokensByTokenId() {
        String managementToken = issueManagementToken(
                Arrays.asList(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_READ),
                document(new DataGrant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
                        Collections.singletonList(SimpleAkskServerConstant.MANAGEMENT_ACTION_READ), true,
                        Collections.<DataConstraint>emptyList())));
        ClientInfoResponse targetClient = clientManagementService.createPlatformClient("Restricted Token Target");
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(applicationAuthorizationRepository, targetClient);
        createAuthorizationWithoutAccessToken(targetClient.getClientId());
        JwtTokenTestHelper.getTokenByClientCredentials(restTemplate, port,
                targetClient.getClientId(), targetClient.getClientSecret());
        String targetTokenId = getTokenPage(managementToken, "/api/token?clientId=" + targetClient.getClientId()).getData()
                .get(0).getId();

        String restrictedToken = issueManagementToken(
                Arrays.asList(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_READ),
                document(grant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
                        SimpleAkskServerConstant.MANAGEMENT_ACTION_READ,
                        constraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_TOKEN_ID, targetTokenId))));

        PageResponse<TokenInfoResponse> mysqlTokens = getTokenPage(restrictedToken,
                "/api/token?clientId=" + targetClient.getClientId() + "&page=1&size=10");
        PageResponse<TokenInfoResponse> redisTokens = getTokenPage(restrictedToken, "/api/token/redis?page=1&size=10");

        assertEquals(1L, mysqlTokens.getTotal().longValue(), "MySQL 总数必须在数据范围过滤后统计");
        assertEquals(targetTokenId, mysqlTokens.getData().get(0).getId());
        assertEquals(1L, redisTokens.getTotal().longValue(), "Redis 总数必须在数据范围过滤后统计");
        assertEquals(targetTokenId, redisTokens.getData().get(0).getId());
    }

    @Test
    void shouldRejectClientDeletionWhenAffectedTokenIsOutsideTokenUpdatePlan() {
        ClientInfoResponse targetClient = clientManagementService.createPlatformClient("Delete Token Restriction Target");
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(applicationAuthorizationRepository, targetClient);
        JwtTokenTestHelper.getTokenByClientCredentials(restTemplate, port,
                targetClient.getClientId(), targetClient.getClientSecret());

        String token = issueManagementToken(Arrays.asList(
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_DELETE,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE),
                document(
                        new DataGrant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
                                Collections.singletonList(SimpleAkskServerConstant.MANAGEMENT_ACTION_DELETE), true,
                                Collections.<DataConstraint>emptyList()),
                        grant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
                                SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                                constraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_TOKEN_ID, "not-authorized"))));

        ResponseEntity<Void> response = restTemplate.exchange(clientUrl("/api/client/" + targetClient.getClientId()),
                HttpMethod.DELETE, JwtTokenTestHelper.createAuthEntity(token), Void.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "受影响 Token 未获 update 授权必须整体拒绝");
        assertTrue(clientRepository.findByClientId(targetClient.getClientId()).isPresent(),
                "Token 范围预检失败时不得删除 Client");
    }

    private void createAuthorizationWithoutAccessToken(String clientId) {
        OAuth2AuthorizationEntity authorization = new OAuth2AuthorizationEntity();
        authorization.setId("authorization-without-access-token-" + System.nanoTime());
        authorization.setRegisteredClientId(clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalStateException("测试 Client 不存在")).getId());
        authorization.setPrincipalName(clientId);
        authorization.setAuthorizationGrantType("authorization_code");
        authorizationRepository.save(authorization);
    }

    private String issueManagementToken(List<String> permissions, DataGrantDocument document) {
        AkskApplicationAuthorizationEntity authorization = applicationAuthorizationRepository
                .findByClientId(managementClient.getClientId())
                .orElseThrow(() -> new IllegalStateException("测试授权投影不存在"));
        authorization.setApiPermissionsJson(AkskApplicationAuthorizationJsonCodec.writeStringList(permissions));
        authorization.setDataGrantDocumentJson(AkskApplicationAuthorizationJsonCodec.writeDataGrantDocument(document));
        authorization.setUpdatedAt(Instant.now());
        applicationAuthorizationRepository.save(authorization);
        return JwtTokenTestHelper.getTokenByClientCredentials(restTemplate, port,
                managementClient.getClientId(), managementClient.getClientSecret());
    }

    private DataGrantDocument document(DataGrant... grants) {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Arrays.asList(grants));
    }

    private DataGrant grant(String resource, String action, DataConstraint... constraints) {
        return new DataGrant(resource, Collections.singletonList(action), false, Arrays.asList(constraints));
    }

    private DataConstraint constraint(String dimension, String value) {
        return new DataConstraint(dimension, DataConstraintOperator.IN, Collections.singletonList(value));
    }

    private PageResponse<ClientInfoResponse> getClientPage(String token, int page, int size) {
        ResponseEntity<PageResponse<ClientInfoResponse>> response = restTemplate.exchange(
                clientUrl("/api/client?page=" + page + "&size=" + size), HttpMethod.GET,
                JwtTokenTestHelper.createAuthEntity(token), new ParameterizedTypeReference<PageResponse<ClientInfoResponse>>() {
                });
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private PageResponse<TokenInfoResponse> getTokenPage(String token, String path) {
        ResponseEntity<PageResponse<TokenInfoResponse>> response = restTemplate.exchange(clientUrl(path), HttpMethod.GET,
                JwtTokenTestHelper.createAuthEntity(token), new ParameterizedTypeReference<PageResponse<TokenInfoResponse>>() {
                });
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private boolean containsClient(PageResponse<ClientInfoResponse> page, String clientId) {
        return page.getData().stream().anyMatch(client -> clientId.equals(client.getClientId()));
    }

    private String clientUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
