package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.manifest.IamApplicationPermissionManifestService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import io.github.surezzzzzz.sdk.auth.iam.server.test.helper.IamTrustedApplicationTestCleanupHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.test.helper.PublishedAkskFixtureHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 页面准入查询端点与 users 投影真 AKP 组合验收（1.3.5）。
 *
 * <p>对齐 {@link IamOpenRoleAkpHttpTest} 范式：发布版 AKSK 独立进程 + 正式 introspect
 * Provider + RANDOM_PORT。断言面：新端点码控（iam:portal:api）、404 语义、
 * 页面准入与门户口径分叉（未挂 Portal 集成的应用仍在清单）、users 响应投影
 * subjectId 回传（列表 + 详情）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths[0]=/iam/api/**", "spring.jpa.show-sql=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IamPageAdmissionApiTest {
    private static final String PREFIX = "io.github.surezzzzzz.sdk.auth.";
    private static final PublishedAkskFixtureHelper AKSK = new PublishedAkskFixtureHelper();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> applications = new ArrayList<>();
    private Long memberUserId;
    private String memberSubjectId;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private IamTrustedApplicationService trustedApplicationService;

    @Autowired
    private IamUserService userService;

    @Autowired
    private IamApplicationAuthorizationRepository authorizationRepository;

    @Autowired
    private IamApplicationPermissionManifestService manifestService;

    @Autowired
    private IamTrustedApplicationTestCleanupHelper cleanup;

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void realProvider(DynamicPropertyRegistry registry) {
        AKSK.start();
        registry.add(PREFIX + "aksk.resource.server.enabled", () -> true);
        registry.add(PREFIX + "aksk.resource.server.introspect.endpoint", () -> AKSK.getEndpoint() + "/oauth2/introspect");
        registry.add(PREFIX + "aksk.resource.server.introspect.client-id", () -> AKSK.getIntrospectionClient());
        registry.add(PREFIX + "aksk.resource.server.introspect.client-secret", () -> AKSK.introspectionSecret());
        registry.add(PREFIX + "aksk.resource.server.introspect.local-cache.enabled", () -> false);
        registry.add(PREFIX + "aksk.resource.server.introspect.local-cache.fallback.enabled", () -> false);
        registry.add(PREFIX + "iam.server.issuer", () -> required("iam.qiankun.origin"));
    }

    @AfterAll
    static void stopExternalFixture() {
        AKSK.close();
    }

    private static String required(String key) {
        String value = System.getProperty(key);
        if (value == null || value.isEmpty()) throw new IllegalStateException("缺少真实组合验收配置：" + key);
        return value;
    }

    @AfterEach
    void releaseOwnedObjects() throws InterruptedException {
        if (memberUserId != null) userService.deleteUser(memberUserId);
        for (Long applicationId : applications) {
            cleanup.deleteAndAwaitCompletion(applicationId);
        }
    }

    @Test
    void pageAdmissionEndpointEnforcesCodeControlAndReturnsAdmittedCodes() throws Exception {
        // 应用 A：挂 Portal 集成 + 清单两页（分裂数据：行只授一页）
        String codeA = "page-adm-portal-" + suffix;
        Long applicationIdA = createApplication(codeA, true);
        putManifest(applicationIdA, Arrays.asList("page-x", "page-y"));
        // 应用 B：不挂 Portal 集成（口径分叉实证载体）
        String codeB = "page-adm-bare-" + suffix;
        Long applicationIdB = createApplication(codeB, false);
        putManifest(applicationIdB, Collections.singletonList("page-b"));
        createUserWithAdmissions(Arrays.asList(
                admission(applicationIdA, Collections.singletonList("page-x")),
                admission(applicationIdB, Collections.singletonList("page-b"))));

        DataGrant userRead = new DataGrant(SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
                Collections.singletonList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ), true, Collections.emptyList());
        DataGrantDocument all = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Collections.singletonList(userRead));
        AKSK.authorize(true, Arrays.asList(
                SimpleIamServerConstant.BUILT_IN_PERMISSION_PORTAL_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API), all);
        String token = AKSK.token();
        String path = "/iam/api/users/" + memberSubjectId + "/page-admitted-applications";

        // 码控三段：无令牌 401、无码 403、持码 200
        assertEquals(401, request(HttpMethod.GET, path, null, null, null).getStatusCodeValue());
        assertEquals(403, request(HttpMethod.GET, path, null, null,
                AKSK.restrictedToken(Collections.emptyList(), all)).getStatusCodeValue());
        ResponseEntity<String> response = request(HttpMethod.GET, path, null, null, token);
        assertEquals(200, response.getStatusCodeValue());
        JsonNode codes = mapper.readTree(response.getBody());
        List<String> codeList = new ArrayList<>();
        codes.forEach(code -> codeList.add(code.asText()));
        assertTrue(codeList.contains(codeA), "挂 Portal 且投影非空的应用必须在清单");
        assertTrue(codeList.contains(codeB), "未挂 Portal 集成但投影非空的应用必须在清单（页面准入与门户口径分叉）");
        assertFalse(codeList.contains("iam"), "普通用户对无授权行的内置应用不进清单（特权不外溢）");

        // subjectId 不存在 → 404（与 users 族 getBySubjectId 同语义）
        assertEquals(404, request(HttpMethod.GET, "/iam/api/users/nonexistent-" + suffix
                + "/page-admitted-applications", null, null, token).getStatusCodeValue());

        AKSK.revoke(token);
        assertEquals(401, request(HttpMethod.GET, path, null, null, token).getStatusCodeValue(), "必须实时感知真实撤销");
        log.info("页面准入端点真 AKP 验收通过：codes={}", codeList);
    }

    @Test
    void userProjectionCarriesSubjectIdInListAndDetail() throws Exception {
        createUserWithAdmissions(Collections.emptyList());
        DataGrant userRead = new DataGrant(SimpleIamServerConstant.DATA_RESOURCE_IAM_USER,
                Collections.singletonList(SimpleIamServerConstant.DATA_RESOURCE_ACTION_READ), true, Collections.emptyList());
        DataGrantDocument all = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                SimpleDataPermissionConstant.VERSION, Collections.singletonList(userRead));
        // fixture 授权行按 subject 唯一，同类第二个用例不得重复 authorize——用受限令牌直接造持码形态
        String token = AKSK.restrictedToken(
                Collections.singletonList(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API), all);

        ResponseEntity<String> list = request(HttpMethod.GET,
                "/iam/api/users?keyword=" + suffix + "&page=0&size=10", null, null, token);
        assertEquals(200, list.getStatusCodeValue());
        JsonNode content = mapper.readTree(list.getBody()).path("content");
        assertTrue(content.size() >= 1, "按用户名关键字应命中本次夹具用户");
        assertEquals(memberSubjectId, content.get(0).path("subjectId").asText(),
                "users 列表投影必须回传 subjectId（对外公开主体标识）");

        ResponseEntity<String> detail = request(HttpMethod.GET,
                "/iam/api/users/" + memberSubjectId, null, null, token);
        assertEquals(200, detail.getStatusCodeValue());
        assertEquals(memberSubjectId, mapper.readTree(detail.getBody()).path("subjectId").asText(),
                "users 详情投影必须回传 subjectId 且与路径参数一致");

        log.info("users 投影 subjectId 回传验收通过：subjectId={}", memberSubjectId);
    }

    private Long createApplication(String code, boolean withPortal) {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(code + "-web");
        client.setClientName(code + "-web");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://" + code + ".example.test/callback"));
        client.setScopes(Arrays.asList("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("none"));
        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(code);
        request.setApplicationName("Page Admission API Test " + code);
        request.setInitialClient(client);
        if (withPortal) {
            io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalIntegrationRequest portal =
                    new io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.PortalIntegrationRequest();
            portal.setEnabled(true);
            portal.setEntry("//localhost:9xxx/" + code + "/");
            portal.setApiBase("/" + code);
            io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.MenuItemRequest menu =
                    new io.github.surezzzzzz.sdk.auth.iam.server.dto.portal.request.MenuItemRequest();
            menu.setCode("home");
            menu.setName("首页");
            menu.setRoute("/home");
            menu.setSortOrder(1);
            portal.setMenus(Collections.singletonList(menu));
            request.setPortal(portal);
        }
        Long applicationId = trustedApplicationService.createApplication(request).getApplication().getId();
        applications.add(applicationId);
        return applicationId;
    }

    private void putManifest(Long applicationId, List<String> pagePermissions) {
        PutApplicationPermissionManifestRequest request = new PutApplicationPermissionManifestRequest();
        request.setRoles(Collections.emptyList());
        request.setPagePermissions(pagePermissions);
        request.setApiPermissions(Collections.emptyList());
        request.setDataResources(Collections.emptyList());
        manifestService.putManifest(applicationId, request);
    }

    private void createUserWithAdmissions(List<IamApplicationAuthorizationEntity> admissions) {
        CreateUserRequest member = new CreateUserRequest();
        member.setUsername("page-adm-user-" + suffix);
        member.setPassword("Sample-" + UUID.randomUUID() + "@9!");
        member.setDisplayName("页面准入验收用户");
        memberUserId = userService.createUser(member).getId();
        memberSubjectId = userService.getById(memberUserId).getSubjectId();
        for (IamApplicationAuthorizationEntity admission : admissions) {
            admission.setUserId(memberUserId);
            authorizationRepository.save(admission);
        }
    }

    private IamApplicationAuthorizationEntity admission(Long applicationId, List<String> pagePermissions) {
        IamApplicationAuthorizationEntity authorization = new IamApplicationAuthorizationEntity();
        authorization.setApplicationId(applicationId);
        authorization.setAdmitted(SimpleIamServerConstant.STATUS_ACTIVE);
        authorization.setRolesJson(IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
        authorization.setPagePermissionsJson(IamApplicationAuthorizationJsonCodec.writeStringList(pagePermissions));
        authorization.setApiPermissionsJson(
                IamApplicationAuthorizationJsonCodec.writeStringList(Collections.emptyList()));
        authorization.setDataGrantDocumentJson(null);
        authorization.setAuthorizationVersion(1L);
        authorization.setManifestVersion("manual");
        authorization.setManifestDigest("manual");
        authorization.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        authorization.setCreatedAt(Instant.now());
        authorization.setUpdatedAt(Instant.now());
        return authorization;
    }

    private ResponseEntity<String> request(HttpMethod method, String path, String body, String etag, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        if (etag != null) headers.setIfMatch(etag);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
