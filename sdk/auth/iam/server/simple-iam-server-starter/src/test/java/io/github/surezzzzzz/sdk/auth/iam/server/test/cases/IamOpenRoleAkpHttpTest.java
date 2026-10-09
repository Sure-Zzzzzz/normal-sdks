package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request.CreateDepartmentRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamOpenRoleBindingEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamOpenRoleBindingRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamRoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.bootstrap.IamOpenRoleManifestUpgradeService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.department.IamDepartmentService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.manifest.IamApplicationPermissionManifestService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import io.github.surezzzzzz.sdk.auth.iam.server.test.helper.IamTrustedApplicationTestCleanupHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.test.helper.PublishedAkskFixtureHelper;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 发布版 AKSK 独立进程、正式 introspect Provider、随机 HTTP 监听的纯后台组合验收。
 * 不依赖浏览器与门户形态（2026-10-09 改造：原浏览器全链段依赖仓外验收脚本，
 * 不可再生导致本机永久红；HTTP 全链断言不受影响）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths[0]=/iam/api/**", "spring.jpa.show-sql=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IamOpenRoleAkpHttpTest {
    private static final String PREFIX = "io.github.surezzzzzz.sdk.auth.";
    private static final PublishedAkskFixtureHelper AKSK = new PublishedAkskFixtureHelper();
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Long> departments = new ArrayList<>();
    private final List<Long> users = new ArrayList<>();
    private Long applicationId;
    private String openRoleId;
    @Autowired
    private TestRestTemplate http;
    @Autowired
    private IamTrustedApplicationService applications;
    @Autowired
    private IamApplicationPermissionManifestService manifests;
    @Autowired
    private IamDepartmentService departmentService;
    @Autowired
    private IamRoleService roles;
    @Autowired
    private IamUserService userService;
    @Autowired
    private IamOpenRoleBindingRepository bindings;
    @Autowired
    private IamTrustedApplicationTestCleanupHelper cleanup;
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext context;

    @DynamicPropertySource
    static void realProvider(DynamicPropertyRegistry registry) {
        AKSK.start();
        registry.add(PREFIX + "aksk.resource.server.enabled", () -> true);
        registry.add(PREFIX + "aksk.resource.server.introspect.endpoint", () -> AKSK.getEndpoint() + "/oauth2/introspect");
        registry.add(PREFIX + "aksk.resource.server.introspect.client-id", AKSK::getIntrospectionClient);
        registry.add(PREFIX + "aksk.resource.server.introspect.client-secret", AKSK::introspectionSecret);
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
    void releaseOwnedObjects() {
        for (Long user : users) userService.deleteUser(user);
        if (openRoleId != null) {
            IamOpenRoleBindingEntity binding = bindings.findById(openRoleId).orElse(null);
            if (binding != null) {
                if ("ACTIVE".equals(binding.getState())) roles.deleteRole(binding.getRoleId());
                bindings.deleteById(openRoleId);
            }
        }
        Collections.reverse(departments);
        for (Long department : departments) departmentService.deleteDepartment(department);
        if (applicationId != null) cleanup.deleteAndAwaitCompletion(applicationId);
    }

    @Test
    void realAkpPreservesAuthorizationRevisionAndConditionalWrites() throws Exception {
        Collection<ResourceAuthenticationAdapter> adapters = context.getBeansOfType(ResourceAuthenticationAdapter.class).values();
        assertEquals(1, adapters.size());
        ResourceAuthenticationAdapter adapter = adapters.iterator().next();
        assertTrue(adapter.getClass().getProtectionDomain().getCodeSource().getLocation().toString()
                .contains("simple-aksk-resource-server-starter-3.1.0.jar"), "不能使用源码或替身 Provider");
        assertEquals("aksk", adapter.sourceId().getValue());
        String suffix = UUID.randomUUID().toString();
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId("akp-product-" + suffix);
        client.setClientName("示例产品客户端");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://sample.example.test/callback"));
        client.setScopes(Collections.singletonList("openid"));
        client.setGrantTypes(Collections.singletonList("authorization_code"));
        client.setAuthenticationMethods(Collections.singletonList("none"));
        CreateTrustedApplicationRequest product = new CreateTrustedApplicationRequest();
        product.setApplicationCode("akp-product-" + suffix);
        product.setApplicationName("示例授权产品");
        product.setInitialClient(client);
        applicationId = applications.createApplication(product).getApplication().getId();
        PutApplicationPermissionManifestRequest manifest = new PutApplicationPermissionManifestRequest();
        manifest.setRoles(Collections.emptyList());
        manifest.setPagePermissions(Collections.singletonList("sample:page"));
        manifest.setApiPermissions(Collections.singletonList("sample:api"));
        manifest.setDataResources(Collections.emptyList());
        manifests.putManifest(applicationId, manifest);
        Long root = department(null, "示例客户根部门");
        Long child = department(root, "示例客户子部门");
        Long outside = department(null, "示例客户外部门");
        CreateUserRequest member = new CreateUserRequest();
        member.setUsername("akp-member-" + suffix);
        String memberPassword = "Sample-" + UUID.randomUUID() + "@9!";
        member.setPassword(memberPassword);
        member.setDisplayName("示例客户成员");
        member.setDepartmentId(child);
        io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity memberEntity = userService.createUser(member);
        users.add(memberEntity.getId());
        List<DataGrant> grants = new ArrayList<>();
        IamOpenRoleManifestUpgradeService.declarations().forEach(resource -> grants.add(
                new DataGrant(resource.getResource(), resource.getActions(), true, Collections.emptyList())));
        DataGrantDocument all = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION, grants);
        AKSK.authorize(true, IamOpenRoleManifestUpgradeService.apiCodes(), all);
        String token = AKSK.token();
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("externalId", suffix);
        create.put("applicationId", applicationId);
        create.put("rootDepartmentId", root);
        create.put("name", "示例委托角色");
        ResponseEntity<String> created = request(HttpMethod.POST, "/iam/api/roles", mapper.writeValueAsString(create), null, token);
        assertEquals(201, created.getStatusCodeValue());
        JsonNode role = mapper.readTree(created.getBody());
        openRoleId = role.path("openRoleId").asText();
        String path = "/iam/api/roles/" + openRoleId;
        String revision = created.getHeaders().getETag();
        assertEquals(AKSK.subjectId(), bindings.findById(openRoleId).get().getOwnerSubjectId());
        assertEquals(401, request(HttpMethod.GET, path, null, null, null).getStatusCodeValue());
        assertEquals(403, request(HttpMethod.GET, path, null, null, AKSK.restrictedToken(Collections.emptyList(), all)).getStatusCodeValue());
        assertEquals(403, request(HttpMethod.GET, path, null, null,
                AKSK.restrictedToken(IamOpenRoleManifestUpgradeService.apiCodes(), null)).getStatusCodeValue());
        assertEquals(404, request(HttpMethod.GET, path, null, null,
                AKSK.restrictedToken(IamOpenRoleManifestUpgradeService.apiCodes(), all)).getStatusCodeValue());
        String relation = "/iam/api/departments/" + child + "/roles/" + openRoleId;
        assertEquals(428, request(HttpMethod.PUT, relation, null, null, token).getStatusCodeValue());
        ResponseEntity<String> assigned = request(HttpMethod.PUT, relation, null, revision, token);
        assertEquals(204, assigned.getStatusCodeValue());
        assertNotEquals(revision, assigned.getHeaders().getETag());
        assertEquals(412, request(HttpMethod.DELETE, relation, null, revision, token).getStatusCodeValue());
        assertEquals(409, request(HttpMethod.PUT, "/iam/api/departments/" + outside + "/roles/" + openRoleId,
                null, assigned.getHeaders().getETag(), token).getStatusCodeValue());
        assertEquals(200, request(HttpMethod.GET, "/iam/api/organization-directories/" + root + "/departments", null, null, token).getStatusCodeValue());
        assertEquals(1, mapper.readTree(request(HttpMethod.GET, path + "/departments", null, null, token).getBody()).path("total").asInt());
        assertEquals(200, request(HttpMethod.GET, path, null, null, token).getStatusCodeValue());
        AKSK.revoke(token);
        assertEquals(401, request(HttpMethod.GET, path, null, null, token).getStatusCodeValue(), "必须实时感知真实撤销");
        log.info("正式 AKP 回源、三权拒绝、版本冲突纯后台组合验收通过。");
    }

    private Long department(Long parent, String name) {
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setCode("akp-dept-" + UUID.randomUUID());
        request.setName(name);
        request.setParentId(parent);
        Long id = departmentService.createDepartment(request).getId();
        departments.add(id);
        return id;
    }

    private ResponseEntity<String> request(HttpMethod method, String path, String body, String etag, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        if (etag != null) headers.setIfMatch(etag);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
