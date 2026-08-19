package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.ApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ApplicationAuthorizationResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ApplicationAuthorizationManagementService;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 应用授权管理 REST 集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(
        classes = SimpleAkskServerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class ApplicationAuthorizationManagementIntegrationTest {

    private static final String APPLICATION_CODE_ALPHA = "application-alpha";
    private static final String APPLICATION_CODE_BETA = "application-beta";
    private static final String MANIFEST_VERSION = "test-manifest";
    private static final String MANIFEST_DIGEST = "test-manifest-digest";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private ApplicationAuthorizationManagementService applicationAuthorizationManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private OAuth2AuthorizationEntityRepository oauthAuthorizationRepository;

    @Autowired
    private AkskApplicationAuthorizationRepository applicationAuthorizationRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private ClientInfoResponse managementClient;

    @BeforeEach
    void setUp() {
        managementClient = clientManagementService.createPlatformClient("Application Authorization Manager");
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(
                applicationAuthorizationRepository, managementClient);
    }

    @AfterEach
    void tearDown() {
        oauthAuthorizationRepository.deleteAll();
        applicationAuthorizationRepository.deleteAll();
        clientRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void shouldRejectTokenIssuanceWhenClientHasNoApplicationAuthorization() {
        ClientInfoResponse target = clientManagementService.createPlatformClient("No Authorization Client");
        applicationAuthorizationRepository.findByClientId(target.getClientId())
                .ifPresent(applicationAuthorizationRepository::delete);

        ResponseEntity<Map> response = requestToken(target);

        log.info("未配置应用授权的换取令牌状态: {}", response.getStatusCode());
        assertFalse(response.getStatusCode().is2xxSuccessful(), "缺失应用授权投影时不得签发 Token");
        assertTrue(applicationAuthorizationRepository.findByClientId(target.getClientId()).isEmpty(),
                "创建 Client 不得隐式写入应用授权投影");
    }

    @Test
    void shouldCreateReplaceAndRevokeApplicationAuthorizationThroughRestApi() {
        ClientInfoResponse target = clientManagementService.createPlatformClient("Authorization Lifecycle Client");
        applicationAuthorizationRepository.findByClientId(target.getClientId())
                .ifPresent(applicationAuthorizationRepository::delete);
        String managementToken = issueManagementToken(
                applicationAuthorizationPermissions(), managementDocument());

        ResponseEntity<ApplicationAuthorizationResponse> createResponse = restTemplate.exchange(
                url("/api/application-authorization?clientId=" + target.getClientId()),
                HttpMethod.POST,
                JwtTokenTestHelper.createAuthEntity(managementToken, request(APPLICATION_CODE_ALPHA, true)),
                ApplicationAuthorizationResponse.class);

        log.info("创建应用授权状态: {}", createResponse.getStatusCode());
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertNotNull(createResponse.getBody());
        assertEquals(1L, createResponse.getBody().getAuthorizationVersion().longValue());
        assertTrue(createResponse.getBody().getAdmitted(), "显式准入后授权投影必须可用");
        assertTrue(requestToken(target).getStatusCode().is2xxSuccessful(), "准入后必须可以签发 Token");

        ResponseEntity<ApplicationAuthorizationResponse> duplicateResponse = restTemplate.exchange(
                url("/api/application-authorization?clientId=" + target.getClientId()),
                HttpMethod.POST,
                JwtTokenTestHelper.createAuthEntity(managementToken, request(APPLICATION_CODE_ALPHA, true)),
                ApplicationAuthorizationResponse.class);
        assertEquals(HttpStatus.CONFLICT, duplicateResponse.getStatusCode(), "重复创建必须返回冲突状态");

        ResponseEntity<ApplicationAuthorizationResponse> replaceResponse = restTemplate.exchange(
                url("/api/application-authorization/" + target.getClientId()),
                HttpMethod.PUT,
                JwtTokenTestHelper.createAuthEntity(managementToken, request(APPLICATION_CODE_ALPHA, false)),
                ApplicationAuthorizationResponse.class);

        log.info("替换应用授权状态: {}", replaceResponse.getStatusCode());
        assertEquals(HttpStatus.OK, replaceResponse.getStatusCode());
        assertNotNull(replaceResponse.getBody());
        assertEquals(2L, replaceResponse.getBody().getAuthorizationVersion().longValue());
        assertFalse(replaceResponse.getBody().getAdmitted(), "完整替换必须覆盖准入状态");
        assertFalse(requestToken(target).getStatusCode().is2xxSuccessful(), "未准入投影不得签发 Token");

        ResponseEntity<ApplicationAuthorizationResponse> readResponse = restTemplate.exchange(
                url("/api/application-authorization/" + target.getClientId()),
                HttpMethod.GET,
                JwtTokenTestHelper.createAuthEntity(managementToken),
                ApplicationAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, readResponse.getStatusCode());
        assertNotNull(readResponse.getBody());
        assertEquals(2L, readResponse.getBody().getAuthorizationVersion().longValue());

        ResponseEntity<Void> revokeResponse = restTemplate.exchange(
                url("/api/application-authorization/" + target.getClientId() + "/revoke"),
                HttpMethod.POST,
                JwtTokenTestHelper.createAuthEntity(managementToken),
                Void.class);

        log.info("撤销应用授权状态: {}", revokeResponse.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, revokeResponse.getStatusCode());
        AkskApplicationAuthorizationEntity revoked = applicationAuthorizationRepository
                .findByClientId(target.getClientId()).orElseThrow(IllegalStateException::new);
        assertFalse(revoked.getEnabled(), "撤销必须禁用授权投影");
        assertFalse(revoked.getAdmitted(), "撤销必须取消准入状态");
        assertNotNull(revoked.getRevokedAt(), "撤销必须记录撤销时间");
        assertEquals(3L, revoked.getAuthorizationVersion().longValue(), "撤销必须递增授权版本");
        assertFalse(requestToken(target).getStatusCode().is2xxSuccessful(), "撤销后不得再次签发 Token");
    }

    @Test
    void shouldLeaveAuthorizationUnchangedWhenTokenPreflightFails() {
        ClientInfoResponse target = clientManagementService.createPlatformClient("Authorization Preflight Client");
        applicationAuthorizationRepository.findByClientId(target.getClientId())
                .ifPresent(applicationAuthorizationRepository::delete);

        String createToken = issueManagementToken(
                applicationAuthorizationPermissions(), managementDocument());
        createAuthorization(createToken, target.getClientId(), APPLICATION_CODE_ALPHA);
        AkskApplicationAuthorizationEntity before = applicationAuthorizationRepository
                .findByClientId(target.getClientId()).orElseThrow(IllegalStateException::new);
        Long beforeVersion = before.getAuthorizationVersion();
        String beforePermissions = before.getApiPermissionsJson();

        String updateOnlyToken = issueManagementToken(
                Arrays.asList(
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_READ,
                        SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_UPDATE),
                document(new DataGrant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION,
                        Collections.singletonList(SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE), true,
                        Collections.<DataConstraint>emptyList())));
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/application-authorization/" + target.getClientId()),
                HttpMethod.PUT,
                JwtTokenTestHelper.createAuthEntity(updateOnlyToken, request(APPLICATION_CODE_ALPHA, true)),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "缺少 tokenPlan 权限时替换必须被拒绝");
        AkskApplicationAuthorizationEntity after = applicationAuthorizationRepository
                .findByClientId(target.getClientId()).orElseThrow(IllegalStateException::new);
        assertEquals(beforeVersion, after.getAuthorizationVersion(), "预检失败不得递增授权版本");
        assertEquals(beforePermissions, after.getApiPermissionsJson(), "预检失败不得修改授权内容");
    }

    @Test
    void shouldReturnSafeHttpStatusForInvalidOrMissingApplicationAuthorization() {
        ClientInfoResponse target = clientManagementService.createPlatformClient("Authorization Error Contract Client");
        applicationAuthorizationRepository.findByClientId(target.getClientId())
                .ifPresent(applicationAuthorizationRepository::delete);
        String managementToken = issueManagementToken(applicationAuthorizationPermissions(), managementDocument());

        ResponseEntity<Void> invalidRequestResponse = restTemplate.exchange(
                url("/api/application-authorization?clientId=" + target.getClientId()),
                HttpMethod.POST,
                JwtTokenTestHelper.createAuthEntity(managementToken, request("", true)),
                Void.class);
        assertEquals(HttpStatus.BAD_REQUEST, invalidRequestResponse.getStatusCode(),
                "无效应用授权请求必须返回错误请求状态");

        ResponseEntity<Void> missingProjectionResponse = restTemplate.exchange(
                url("/api/application-authorization/" + target.getClientId()),
                HttpMethod.GET,
                JwtTokenTestHelper.createAuthEntity(managementToken),
                Void.class);
        assertEquals(HttpStatus.NOT_FOUND, missingProjectionResponse.getStatusCode(),
                "缺失应用授权投影必须返回未找到状态");

        ResponseEntity<Void> missingClientResponse = restTemplate.exchange(
                url("/api/application-authorization?clientId=missing-client"),
                HttpMethod.POST,
                JwtTokenTestHelper.createAuthEntity(managementToken, request(APPLICATION_CODE_ALPHA, true)),
                Void.class);
        assertEquals(HttpStatus.NOT_FOUND, missingClientResponse.getStatusCode(),
                "不存在的 Client 必须返回未找到状态");
    }

    @Test
    void shouldReturnOnlyMinimalRequestedLocalAuthorizationStates() {
        ClientInfoResponse admittedClient = clientManagementService.createPlatformClient("Batch Admitted Client");
        ClientInfoResponse revokedClient = clientManagementService.createPlatformClient("Batch Revoked Client");
        ClientInfoResponse unconfiguredClient = clientManagementService.createPlatformClient("Batch Unconfigured Client");
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(applicationAuthorizationRepository, admittedClient);
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(applicationAuthorizationRepository, revokedClient);

        AkskApplicationAuthorizationEntity revoked = applicationAuthorizationRepository
                .findByClientId(revokedClient.getClientId()).orElseThrow(IllegalStateException::new);
        revoked.setEnabled(Boolean.FALSE);
        revoked.setAdmitted(Boolean.FALSE);
        revoked.setAuthorizationVersion(2L);
        revoked.setRevokedAt(Instant.now());
        applicationAuthorizationRepository.save(revoked);

        Map<String, ApplicationAuthorizationResponse> responses = applicationAuthorizationManagementService
                .getLocalByClientIds(Arrays.asList(admittedClient.getClientId(), revokedClient.getClientId(),
                        unconfiguredClient.getClientId(), "missing-client"));

        assertEquals(2, responses.size(), "仅有投影的请求 Client 才应出现在结果中");
        assertTrue(responses.containsKey(admittedClient.getClientId()));
        assertTrue(responses.containsKey(revokedClient.getClientId()));
        assertFalse(responses.containsKey(unconfiguredClient.getClientId()));
        assertFalse(responses.containsKey("missing-client"));

        ApplicationAuthorizationResponse admitted = responses.get(admittedClient.getClientId());
        assertTrue(admitted.getEnabled());
        assertTrue(admitted.getAdmitted());
        assertEquals(1L, admitted.getAuthorizationVersion().longValue());
        assertNull(admitted.getApplicationCode(), "列表状态查询不得携带应用编码");
        assertNull(admitted.getApiPermissions(), "列表状态查询不得携带 API 权限详情");
        assertNull(admitted.getDataGrantDocument(), "列表状态查询不得携带 DATA 授权详情");

        ApplicationAuthorizationResponse revokedResponse = responses.get(revokedClient.getClientId());
        assertFalse(revokedResponse.getEnabled());
        assertFalse(revokedResponse.getAdmitted());
        assertEquals(2L, revokedResponse.getAuthorizationVersion().longValue());
        assertTrue(applicationAuthorizationManagementService.getLocalByClientIds(Collections.<String>emptyList())
                .isEmpty(), "空输入必须返回空结果");
        assertNotNull(applicationAuthorizationRepository.findByClientId(revokedClient.getClientId())
                .orElseThrow(IllegalStateException::new).getRevokedAt(), "状态查询不得修改已有投影");
    }

    @Test
    void shouldRequireExactPermissionAndFilterAuthorizationListBeforePagination() {
        ClientInfoResponse alpha = clientManagementService.createPlatformClient("Alpha Authorization Client");
        ClientInfoResponse beta = clientManagementService.createPlatformClient("Beta Authorization Client");
        ClientInfoResponse orphan = clientManagementService.createPlatformClient("Orphan Authorization Client");
        applicationAuthorizationRepository.findByClientId(alpha.getClientId())
                .ifPresent(applicationAuthorizationRepository::delete);
        applicationAuthorizationRepository.findByClientId(beta.getClientId())
                .ifPresent(applicationAuthorizationRepository::delete);
        applicationAuthorizationRepository.findByClientId(orphan.getClientId())
                .ifPresent(applicationAuthorizationRepository::delete);

        String unrestrictedToken = issueManagementToken(
                applicationAuthorizationPermissions(), managementDocument());
        createAuthorization(unrestrictedToken, alpha.getClientId(), APPLICATION_CODE_ALPHA);
        createAuthorization(unrestrictedToken, beta.getClientId(), APPLICATION_CODE_BETA);
        createAuthorization(unrestrictedToken, orphan.getClientId(), APPLICATION_CODE_ALPHA);
        clientRepository.findByClientId(orphan.getClientId()).ifPresent(clientRepository::delete);

        String unrelatedPermissionToken = issueManagementToken(
                Collections.singletonList(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_READ),
                managementDocument());
        ResponseEntity<String> unrelatedPermissionResponse = restTemplate.exchange(
                url("/api/application-authorization/" + alpha.getClientId()),
                HttpMethod.GET,
                JwtTokenTestHelper.createAuthEntity(unrelatedPermissionToken),
                String.class);
        assertEquals(HttpStatus.FORBIDDEN, unrelatedPermissionResponse.getStatusCode(),
                "不相关的精确 API permission 不得访问应用授权资源");

        String restrictedToken = issueManagementToken(
                Collections.singletonList(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_READ),
                document(grant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION,
                        SimpleAkskServerConstant.MANAGEMENT_ACTION_READ,
                        constraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_APPLICATION_CODE,
                                APPLICATION_CODE_ALPHA))));
        ResponseEntity<PageResponse<ApplicationAuthorizationResponse>> firstPageResponse = restTemplate.exchange(
                url("/api/application-authorization?page=1&size=1"),
                HttpMethod.GET,
                JwtTokenTestHelper.createAuthEntity(restrictedToken),
                new ParameterizedTypeReference<PageResponse<ApplicationAuthorizationResponse>>() {
                });
        ResponseEntity<PageResponse<ApplicationAuthorizationResponse>> secondPageResponse = restTemplate.exchange(
                url("/api/application-authorization?page=2&size=1"),
                HttpMethod.GET,
                JwtTokenTestHelper.createAuthEntity(restrictedToken),
                new ParameterizedTypeReference<PageResponse<ApplicationAuthorizationResponse>>() {
                });

        assertEquals(HttpStatus.OK, firstPageResponse.getStatusCode());
        assertNotNull(firstPageResponse.getBody());
        assertEquals(1L, firstPageResponse.getBody().getTotal().longValue(),
                "列表总数必须在 DATA 范围过滤后统计，关联 Client 缺失的投影不得可见");
        assertEquals(1, firstPageResponse.getBody().getData().size());
        assertEquals(APPLICATION_CODE_ALPHA, firstPageResponse.getBody().getData().get(0).getApplicationCode());
        assertEquals(HttpStatus.OK, secondPageResponse.getStatusCode());
        assertNotNull(secondPageResponse.getBody());
        assertEquals(0, secondPageResponse.getBody().getData().size(), "过滤后的第二页必须为空");

        ResponseEntity<PageResponse<ApplicationAuthorizationResponse>> overflowPageResponse = restTemplate.exchange(
                url("/api/application-authorization?page=" + Integer.MAX_VALUE + "&size=" + Integer.MAX_VALUE),
                HttpMethod.GET,
                JwtTokenTestHelper.createAuthEntity(restrictedToken),
                new ParameterizedTypeReference<PageResponse<ApplicationAuthorizationResponse>>() {
                });
        assertEquals(HttpStatus.OK, overflowPageResponse.getStatusCode(), "超范围分页不得因整数溢出失败");
        assertNotNull(overflowPageResponse.getBody());
        assertEquals(0, overflowPageResponse.getBody().getData().size(), "超范围分页必须返回空列表");
    }

    private void createAuthorization(String token, String clientId, String applicationCode) {
        ResponseEntity<ApplicationAuthorizationResponse> response = restTemplate.exchange(
                url("/api/application-authorization?clientId=" + clientId),
                HttpMethod.POST,
                JwtTokenTestHelper.createAuthEntity(token, request(applicationCode, true)),
                ApplicationAuthorizationResponse.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private String issueManagementToken(List<String> permissions, DataGrantDocument document) {
        AkskApplicationAuthorizationEntity authorization = applicationAuthorizationRepository
                .findByClientId(managementClient.getClientId()).orElseThrow(IllegalStateException::new);
        authorization.setApiPermissionsJson(AkskApplicationAuthorizationJsonCodec.writeStringList(permissions));
        authorization.setDataGrantDocumentJson(AkskApplicationAuthorizationJsonCodec.writeDataGrantDocument(document));
        authorization.setUpdatedAt(Instant.now());
        applicationAuthorizationRepository.save(authorization);
        return JwtTokenTestHelper.getTokenByClientCredentials(restTemplate, port,
                managementClient.getClientId(), managementClient.getClientSecret());
    }

    private ApplicationAuthorizationRequest request(String applicationCode, boolean admitted) {
        ApplicationAuthorizationRequest request = new ApplicationAuthorizationRequest();
        request.setApplicationCode(applicationCode);
        request.setAdmitted(Boolean.valueOf(admitted));
        request.setRoles(Collections.<String>emptyList());
        request.setPagePermissions(Collections.<String>emptyList());
        request.setApiPermissions(Collections.<String>emptyList());
        request.setDataGrantDocument(null);
        request.setManifestVersion(MANIFEST_VERSION);
        request.setManifestDigest(MANIFEST_DIGEST);
        return request;
    }

    private List<String> applicationAuthorizationPermissions() {
        return Arrays.asList(
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_CREATE,
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_READ,
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_UPDATE,
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_REVOKE,
                SimpleAkskServerConstant.MANAGEMENT_PERMISSION_TOKEN_UPDATE);
    }

    private DataGrantDocument managementDocument() {
        return document(
                new DataGrant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION,
                        Arrays.asList(
                                SimpleAkskServerConstant.MANAGEMENT_ACTION_CREATE,
                                SimpleAkskServerConstant.MANAGEMENT_ACTION_READ,
                                SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE,
                                SimpleAkskServerConstant.MANAGEMENT_ACTION_REVOKE),
                        true,
                        Collections.<DataConstraint>emptyList()),
                new DataGrant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
                        Collections.singletonList(SimpleAkskServerConstant.MANAGEMENT_ACTION_UPDATE),
                        true,
                        Collections.<DataConstraint>emptyList()));
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

    private ResponseEntity<Map> requestToken(ClientInfoResponse client) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(client.getClientId(), client.getClientSecret());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("scope", "read write");
        return restTemplate.postForEntity(url("/oauth2/token"), new HttpEntity<MultiValueMap<String, String>>(form, headers),
                Map.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
