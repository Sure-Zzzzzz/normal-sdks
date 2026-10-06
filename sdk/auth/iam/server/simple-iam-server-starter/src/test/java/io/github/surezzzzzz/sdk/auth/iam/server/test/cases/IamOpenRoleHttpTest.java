package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.audit.iam.server.handler.ServerIamAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.server.model.ServerIamAuditRecord;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.CreateRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.UpdateRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.department.request.CreateDepartmentRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.UpdateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamOpenRoleBindingEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamOpenRoleBindingRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamOpenRoleMutationSupport;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamRoleAuthorizationRuleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamRoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.bootstrap.IamOpenRoleManifestUpgradeService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.department.IamDepartmentService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.manifest.IamApplicationPermissionManifestService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication.IamTrustedApplicationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import io.github.surezzzzzz.sdk.auth.iam.server.test.helper.IamTrustedApplicationTestCleanupHelper;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.*;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 随机端口 HTTP 与真实 MySQL 治理验收；测试适配器不冒充真实 AKP 回源证明。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths[0]=/iam/api/**"})
@Import(IamOpenRoleHttpTest.AuthenticationFixture.class)
class IamOpenRoleHttpTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String suffix = UUID.randomUUID().toString();
    private final String subject = "open-test-" + suffix;
    private final Set<String> ownedSubjects = new HashSet<>(Collections.singletonList(subject));
    private final List<Long> departments = new ArrayList<>();
    private final List<Long> users = new ArrayList<>();
    private final List<Long> ordinaryRoles = new ArrayList<>();
    private Long applicationId;
    private Long root;
    private Long child;
    private String token;
    @Autowired
    private TestRestTemplate http;
    @Autowired
    private FixtureAdapter adapter;
    @Autowired
    private IamTrustedApplicationService applicationService;
    @Autowired
    private IamTrustedApplicationTestCleanupHelper cleanupHelper;
    @Autowired
    private IamApplicationPermissionManifestService manifestService;
    @Autowired
    private IamDepartmentService departmentService;
    @Autowired
    private IamRoleService roleService;
    @Autowired
    private IamOpenRoleMutationSupport support;
    @Autowired
    private IamOpenRoleBindingRepository bindings;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private IamUserService userService;
    @Autowired
    private IamUserRepository userRepository;
    @Autowired
    private IamRoleAuthorizationRuleService ruleService;
    @Autowired
    private IamApplicationAuthorizationRepository authorizations;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private AuditCollector audits;
    @Autowired
    private IamOpenRoleManifestUpgradeService manifestUpgrade;

    @BeforeEach
    void prepare() {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId("open-web-" + suffix);
        client.setClientName("示例客户端");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://sample.example.test/callback"));
        client.setScopes(Collections.singletonList("openid"));
        client.setGrantTypes(Collections.singletonList("authorization_code"));
        client.setAuthenticationMethods(Collections.singletonList("none"));
        CreateTrustedApplicationRequest app = new CreateTrustedApplicationRequest();
        app.setApplicationCode("open-app-" + suffix);
        app.setApplicationName("示例应用");
        app.setInitialClient(client);
        applicationId = applicationService.createApplication(app).getApplication().getId();
        PutApplicationPermissionManifestRequest manifest = new PutApplicationPermissionManifestRequest();
        manifest.setRoles(Collections.emptyList());
        manifest.setPagePermissions(Collections.singletonList("sample:page"));
        manifest.setApiPermissions(Collections.singletonList("sample:api"));
        manifest.setDataResources(Collections.emptyList());
        manifestService.putManifest(applicationId, manifest);
        root = department(null);
        child = department(root);
        token = adapter.register(subject, ResourceSubjectType.SERVICE, IamOpenRoleManifestUpgradeService.apiCodes(), fullData());
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        for (Long id : users) userService.deleteUser(id);
        for (Long id : ordinaryRoles) roleService.deleteRole(id);
        List<IamOpenRoleBindingEntity> owned = bindings.findAll((entity, query, builder) -> entity.get("ownerSubjectId").in(ownedSubjects));
        for (IamOpenRoleBindingEntity binding : owned) {
            if ("ACTIVE".equals(binding.getState())) roleService.deleteRole(binding.getRoleId());
            bindings.deleteById(binding.getOpenRoleId());
        }
        Collections.reverse(departments);
        for (Long id : departments) departmentService.deleteDepartment(id);
        if (applicationId != null) cleanupHelper.deleteAndAwaitCompletion(applicationId);
        ownedSubjects.forEach(adapter::clearSubject);
    }

    @Test
    void lifecycleUsesSameVersionsAndDoesNotRepeatNoopChanges() throws Exception {
        String externalId = UUID.randomUUID().toString();
        ResponseEntity<String> created = create(externalId);
        assertEquals(201, created.getStatusCodeValue());
        JsonNode role = mapper.readTree(created.getBody());
        String id = role.path("openRoleId").asText();
        String location = "/iam/api/roles/" + id;
        String revision1 = created.getHeaders().getETag();
        assertEquals(location, created.getHeaders().getLocation().toString());
        assertEquals(200, create(externalId).getStatusCodeValue());
        assertEquals(1, mapper.readTree(request(HttpMethod.GET, location, null, null, token).getBody()).path("revision").asInt());
        assertEquals(200, request(HttpMethod.GET, "/iam/api/roles?externalId=" + externalId, null, null, token).getStatusCodeValue());
        assertEquals(404, request(HttpMethod.GET, location + "/authorization-rules/" + applicationId, null, null, token).getStatusCodeValue());
        String relation = "/iam/api/departments/" + child + "/roles/" + id;
        assertEquals(428, request(HttpMethod.PUT, relation, null, null, token).getStatusCodeValue());
        ResponseEntity<String> assigned = request(HttpMethod.PUT, relation, null, revision1, token);
        assertEquals(204, assigned.getStatusCodeValue());
        String revision2 = assigned.getHeaders().getETag();
        assertNotEquals(revision1, revision2);
        assertEquals(revision2, request(HttpMethod.PUT, relation, null, revision2, token).getHeaders().getETag());
        ResponseEntity<String> page = request(HttpMethod.GET, location + "/departments", null, null, token);
        assertNull(page.getHeaders().getETag());
        assertEquals("no-store", page.getHeaders().getCacheControl());
        assertEquals(2L, mapper.readTree(page.getBody()).path("revision").asLong());
        assertEquals(1, mapper.readTree(page.getBody()).path("total").asInt());
        assertEquals(412, request(HttpMethod.DELETE, relation, null, revision1, token).getStatusCodeValue());
        String rulePath = location + "/authorization-rules/" + applicationId;
        String ruleBody = ruleBody();
        ResponseEntity<String> rule = request(HttpMethod.PUT, rulePath, ruleBody, revision2, token);
        assertEquals(200, rule.getStatusCodeValue());
        String revision3 = rule.getHeaders().getETag();
        assertEquals(revision3, request(HttpMethod.PUT, rulePath, ruleBody, revision3, token).getHeaders().getETag());
        assertEquals(revision3, request(HttpMethod.GET, rulePath, null, null, token).getHeaders().getETag());
        String revision4 = request(HttpMethod.DELETE, rulePath, null, revision3, token).getHeaders().getETag();
        assertEquals(revision4, request(HttpMethod.DELETE, rulePath, null, revision4, token).getHeaders().getETag());
        assertEquals(204, request(HttpMethod.DELETE, relation, null, revision4, token).getStatusCodeValue());
        assertEquals(200, request(HttpMethod.GET, "/iam/api/target-applications/" + applicationId, null, null, token).getStatusCodeValue());
        assertEquals(200, request(HttpMethod.GET, "/iam/api/target-applications/" + applicationId + "/permission-manifest", null, null, token).getStatusCodeValue());
        assertTrue(mapper.readTree(request(HttpMethod.GET, "/iam/api/organization-directories/" + root + "/departments", null, null, token).getBody()).path("complete").asBoolean());
    }

    @Test
    void authenticationScopesAndHttpFailuresCannotMutate() throws Exception {
        ResponseEntity<String> created = create(UUID.randomUUID().toString());
        assertEquals(201, created.getStatusCodeValue());
        String id = mapper.readTree(created.getBody()).path("openRoleId").asText();
        String path = "/iam/api/roles/" + id;
        String other = adapter.register("other-" + suffix, ResourceSubjectType.SERVICE, IamOpenRoleManifestUpgradeService.apiCodes(), fullData());
        assertEquals(404, request(HttpMethod.GET, path, null, null, other).getStatusCodeValue());
        String noApi = adapter.register(subject, ResourceSubjectType.SERVICE, Collections.emptyList(), fullData());
        assertEquals(403, request(HttpMethod.GET, path, null, null, noApi).getStatusCodeValue());
        String noData = adapter.register(subject, ResourceSubjectType.SERVICE, IamOpenRoleManifestUpgradeService.apiCodes(), null);
        assertEquals(403, request(HttpMethod.GET, path, null, null, noData).getStatusCodeValue());
        String human = adapter.register(subject, ResourceSubjectType.HUMAN, IamOpenRoleManifestUpgradeService.apiCodes(), fullData());
        assertEquals(401, request(HttpMethod.GET, path, null, null, human).getStatusCodeValue());
        assertEquals(401, request(HttpMethod.GET, path, null, null, null).getStatusCodeValue());
        assertEquals(400, request(HttpMethod.POST, "/iam/api/roles", "{", null, token).getStatusCodeValue());
        ResponseEntity<String> unsupportedMethod = request(HttpMethod.PATCH, path, "{}", null, token);
        assertEquals(405, unsupportedMethod.getStatusCodeValue());
        assertTrue(unsupportedMethod.getHeaders().getAllow().contains(HttpMethod.GET));
        HttpHeaders media = headers(token, null);
        media.setContentType(MediaType.TEXT_PLAIN);
        assertEquals(415, http.exchange("/iam/api/roles", HttpMethod.POST, new HttpEntity<>("{}", media), String.class).getStatusCodeValue());
        ResponseEntity<String> oversized = request(HttpMethod.POST, "/iam/api/roles", String.join("", Collections.nCopies(1048577, "a")), null, token);
        assertEquals(413, oversized.getStatusCodeValue());
        JsonNode error = mapper.readTree(oversized.getBody());
        assertEquals(3, error.size());
        assertTrue(error.has("message") && error.has("timestamp") && error.has("requestId"));
        assertEquals(created.getHeaders().getETag(), request(HttpMethod.GET, path, null, null, token).getHeaders().getETag());
    }

    @Test
    void conditionalDepartmentReadsAlwaysReturnCurrentOrganizationAndDataScope() throws Exception {
        ResponseEntity<String> created = create(UUID.randomUUID().toString());
        String id = mapper.readTree(created.getBody()).path("openRoleId").asText();
        String rolePath = "/iam/api/roles/" + id;
        String version = request(HttpMethod.PUT, "/iam/api/departments/" + child + "/roles/" + id,
                null, created.getHeaders().getETag(), token).getHeaders().getETag();
        HttpHeaders conditional = headers(token, null);
        conditional.setIfNoneMatch(version);
        ResponseEntity<String> before = http.exchange(rolePath + "/departments", HttpMethod.GET,
                new HttpEntity<>(conditional), String.class);
        assertEquals(200, before.getStatusCodeValue());
        assertTrue(mapper.readTree(before.getBody()).path("items").get(0).path("inCurrentRoot").asBoolean());
        long revision = mapper.readTree(before.getBody()).path("revision").asLong();
        jdbc.update("UPDATE iam_department SET parent_id = NULL WHERE id = ?", child);
        ResponseEntity<String> moved = http.exchange(rolePath + "/departments", HttpMethod.GET,
                new HttpEntity<>(conditional), String.class);
        assertEquals(200, moved.getStatusCodeValue());
        assertNull(moved.getHeaders().getETag());
        assertEquals("no-store", moved.getHeaders().getCacheControl());
        JsonNode movedBody = mapper.readTree(moved.getBody());
        assertEquals(revision, movedBody.path("revision").asLong());
        assertFalse(movedBody.path("items").get(0).path("inCurrentRoot").asBoolean());

        List<DataConstraint> constraints = new ArrayList<>(scopedGrant("iam:open-department-role",
                Collections.singletonList("read"), applicationId, root).getConstraints());
        constraints.add(new DataConstraint("departmentId", DataConstraintOperator.IN,
                Collections.singletonList(root.toString())));
        String restricted = adapter.register(subject, ResourceSubjectType.SERVICE,
                IamOpenRoleManifestUpgradeService.apiCodes(), document(Collections.singletonList(
                        new DataGrant("iam:open-department-role", Collections.singletonList("read"), false, constraints))));
        conditional.setBearerAuth(restricted);
        ResponseEntity<String> narrowed = http.exchange(rolePath + "/departments", HttpMethod.GET,
                new HttpEntity<>(conditional), String.class);
        assertEquals(200, narrowed.getStatusCodeValue());
        JsonNode narrowedBody = mapper.readTree(narrowed.getBody());
        assertEquals(revision, narrowedBody.path("revision").asLong());
        assertEquals(0, narrowedBody.path("total").asInt());
        assertEquals(0, narrowedBody.path("items").size());
        assertEquals(version, request(HttpMethod.GET, rolePath, null, null, token).getHeaders().getETag());
    }

    @Test
    void invalidCreationIntegersAreRejectedWithoutCreatingRoles() throws Exception {
        for (String field : Arrays.asList("applicationId", "rootDepartmentId")) {
            for (String invalid : Arrays.asList("1.9", "1.0", "1e2", "\"1\"", "true", "null",
                    "9223372036854775808", "0", "-1", "[]", "{}")) {
                String external = UUID.randomUUID().toString();
                ObjectNode body = (ObjectNode) mapper.readTree(createBody(external, applicationId, root));
                body.set(field, mapper.readTree(invalid));
                ResponseEntity<String> response = request(HttpMethod.POST, "/iam/api/roles",
                        mapper.writeValueAsString(body), null, token);
                assertEquals(400, response.getStatusCodeValue(), field + " 必须拒绝非法整数类型或范围");
                JsonNode error = mapper.readTree(response.getBody());
                assertEquals(3, error.size());
                assertTrue(error.has("message") && error.has("timestamp") && error.has("requestId"));
                assertFalse(error.has("code"));
                assertFalse(bindings.exists((entity, query, builder) -> builder.and(
                        builder.equal(entity.get("ownerSubjectId"), subject), builder.equal(entity.get("externalId"), external))));
            }
        }
        assertEquals(0, bindings.findAll((entity, query, builder) -> builder.equal(entity.get("ownerSubjectId"), subject)).size());
        assertEquals(201, create(UUID.randomUUID().toString()).getStatusCodeValue());
    }

    @Test
    void invalidManifestIntegersCannotWriteRulesOrAdvanceRevision() throws Exception {
        ResponseEntity<String> created = create(UUID.randomUUID().toString());
        String id = mapper.readTree(created.getBody()).path("openRoleId").asText();
        String version = created.getHeaders().getETag();
        String rulePath = "/iam/api/roles/" + id + "/authorization-rules/" + applicationId;
        for (String invalid : Arrays.asList("1.9", "1.0", "1e2", "\"1\"", "false", "null",
                "9223372036854775808", "0", "-1", "[]", "{}")) {
            ObjectNode body = (ObjectNode) mapper.readTree(ruleBody());
            body.set("manifestVersion", mapper.readTree(invalid));
            assertEquals(400, request(HttpMethod.PUT, rulePath, mapper.writeValueAsString(body), version, token).getStatusCodeValue());
            assertEquals(404, request(HttpMethod.GET, rulePath, null, null, token).getStatusCodeValue());
            assertEquals(version, request(HttpMethod.GET, "/iam/api/roles/" + id, null, null, token).getHeaders().getETag());
        }
        assertEquals(200, request(HttpMethod.PUT, rulePath, ruleBody(), version, token).getStatusCodeValue());
    }

    @Test
    void migratedAndMissingDepartmentsCanOnlyBeCleaned() throws Exception {
        ResponseEntity<String> created = create(UUID.randomUUID().toString());
        String id = mapper.readTree(created.getBody()).path("openRoleId").asText();
        String path = "/iam/api/departments/" + child + "/roles/" + id;
        String version = request(HttpMethod.PUT, path, null, created.getHeaders().getETag(), token).getHeaders().getETag();
        jdbc.update("UPDATE iam_department SET parent_id = NULL WHERE id = ?", child);
        // 迁出批准子树与外部部门同属结构冲突（409），与 OpenAPI OpenRole409 口径一致。
        assertEquals(409, request(HttpMethod.PUT, path, null, version, token).getStatusCodeValue());
        JsonNode relations = mapper.readTree(request(HttpMethod.GET, "/iam/api/roles/" + id + "/departments", null, null, token).getBody());
        assertFalse(relations.path("items").get(0).path("inCurrentRoot").asBoolean());
        departmentService.deleteDepartment(child);
        departments.remove(child);
        assertEquals(204, request(HttpMethod.DELETE, path, null, version, token).getStatusCodeValue());
        assertEquals(404, request(HttpMethod.GET, "/iam/api/organization-directories/" + root + "/members/nonexistent", null, null, token).getStatusCodeValue());
    }

    @Test
    void concurrentCreatesAndStaleWritesAreFenced() throws Exception {
        String external = UUID.randomUUID().toString();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<ResponseEntity<String>> operation = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return create(external);
            };
            Future<ResponseEntity<String>> first = executor.submit(operation);
            Future<ResponseEntity<String>> second = executor.submit(operation);
            start.countDown();
            ResponseEntity<String> one = first.get(30, TimeUnit.SECONDS), two = second.get(30, TimeUnit.SECONDS);
            assertEquals(new HashSet<>(Arrays.asList(200, 201)), new HashSet<>(Arrays.asList(one.getStatusCodeValue(), two.getStatusCodeValue())));
            String id = mapper.readTree(one.getBody()).path("openRoleId").asText();
            assertEquals(id, mapper.readTree(two.getBody()).path("openRoleId").asText());
            String path = "/iam/api/departments/" + child + "/roles/" + id;
            Future<ResponseEntity<String>> assign = executor.submit(() -> request(HttpMethod.PUT, path, null, one.getHeaders().getETag(), token));
            Future<ResponseEntity<String>> rule = executor.submit(() -> request(HttpMethod.PUT,
                    "/iam/api/roles/" + id + "/authorization-rules/" + applicationId, ruleBody(), one.getHeaders().getETag(), token));
            Set<Integer> statuses = new HashSet<>(Arrays.asList(assign.get(30, TimeUnit.SECONDS).getStatusCodeValue(), rule.get(30, TimeUnit.SECONDS).getStatusCodeValue()));
            assertTrue(statuses.contains(412));
            assertTrue(statuses.contains(200) || statuses.contains(204));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void adminPathsPreserveScopeRevisionAndDeletionTombstone() throws Exception {
        String external = UUID.randomUUID().toString();
        ResponseEntity<String> created = create(external);
        String id = mapper.readTree(created.getBody()).path("openRoleId").asText();
        IamOpenRoleBindingEntity binding = bindings.findById(id).get();
        assertThrows(io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException.class,
                () -> support.adminMutation(binding.getRoleId(), null, () -> null));
        assertThrows(io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException.class,
                () -> roleService.assignRole(1L, binding.getRoleId()));
        assertThrows(io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException.class,
                () -> roleService.assignPermission(binding.getRoleId(), 1L));
        assertThrows(io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException.class,
                () -> departmentService.deleteDepartment(root));
        assertThrows(io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException.class,
                () -> applicationService.deleteApplication(applicationId));
        UpdateTrustedApplicationRequest builtIn = new UpdateTrustedApplicationRequest();
        builtIn.setBuiltIn(true);
        assertThrows(io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException.class,
                () -> applicationService.updateApplication(applicationId, builtIn));
        assertNotNull(roleService.getDetail(binding.getRoleId()).getOpenRoleBinding());
        support.adminMutation(binding.getRoleId(), created.getHeaders().getETag(), () -> {
            roleService.deleteRole(binding.getRoleId());
            return null;
        });
        JsonNode tombstone = mapper.readTree(request(HttpMethod.GET, "/iam/api/roles/" + id, null, null, token).getBody());
        assertEquals("DELETED", tombstone.path("state").asText());
        assertFalse(tombstone.has("name"));
        assertEquals(409, create(external).getStatusCodeValue());
        jdbc.update("UPDATE iam_open_role_binding SET revision = 0 WHERE open_role_id = ?", id);
        try {
            assertEquals(409, request(HttpMethod.GET, "/iam/api/roles/" + id, null, null, token).getStatusCodeValue());
        } finally {
            jdbc.update("UPDATE iam_open_role_binding SET revision = ? WHERE open_role_id = ?", tombstone.path("revision").asLong(), id);
        }
    }

    @Test
    void databasePagesHonorWholeGrantsAndOwnerEvenForAll() throws Exception {
        Long otherRoot = department(null);
        ResponseEntity<String> first = create(UUID.randomUUID().toString());
        String id = mapper.readTree(first.getBody()).path("openRoleId").asText();
        assertEquals(201, createAt(UUID.randomUUID().toString(), applicationId, otherRoot, token).getStatusCodeValue());
        String otherActor = adapter.register("other-" + suffix, ResourceSubjectType.SERVICE, IamOpenRoleManifestUpgradeService.apiCodes(), fullData());
        ownedSubjects.add("other-" + suffix);
        assertEquals(201, createAt(UUID.randomUUID().toString(), applicationId, root, otherActor).getStatusCodeValue());
        // 不同完整项中的应用和组织根不能交叉拼成一条授权。
        DataGrantDocument crossed = document(Arrays.asList(
                scopedGrant("iam:open-role", Arrays.asList("read", "create"), applicationId, otherRoot),
                scopedGrant("iam:open-role", Arrays.asList("read", "create"), Long.MAX_VALUE, root)));
        String scoped = adapter.register(subject, ResourceSubjectType.SERVICE, IamOpenRoleManifestUpgradeService.apiCodes(), crossed);
        ResponseEntity<String> filtered = request(HttpMethod.GET, "/iam/api/roles?size=1", null, null, scoped);
        assertEquals(200, filtered.getStatusCodeValue());
        JsonNode page = mapper.readTree(filtered.getBody());
        assertEquals(1, page.path("total").asInt());
        assertEquals(otherRoot.longValue(), page.path("items").get(0).path("rootDepartmentId").asLong());
        assertEquals(403, request(HttpMethod.GET, "/iam/api/roles/" + id, null, null, scoped).getStatusCodeValue());
        assertEquals(403, createAt(UUID.randomUUID().toString(), applicationId, root, scoped).getStatusCodeValue());
        assertEquals(2, mapper.readTree(request(HttpMethod.GET, "/iam/api/roles", null, null, token).getBody()).path("total").asInt());
        assertEquals(400, request(HttpMethod.GET, "/iam/api/roles?size=101", null, null, token).getStatusCodeValue());
    }

    @Test
    void projectionsShrinkOnlyOwnContributionAndMembersExposeMinimalFacts() throws Exception {
        CreateUserRequest user = new CreateUserRequest();
        user.setUsername("open-member-" + suffix);
        user.setPassword("Sample-Only@2026!9");
        user.setDisplayName("示例成员");
        user.setDepartmentId(child);
        Long userId = userService.createUser(user).getId();
        users.add(userId);
        String subjectId = userRepository.findById(userId).get().getSubjectId();
        JsonNode member = mapper.readTree(request(HttpMethod.GET,
                "/iam/api/organization-directories/" + root + "/members/" + subjectId, null, null, token).getBody());
        assertEquals(4, member.size());
        assertTrue(member.path("inCurrentRoot").asBoolean());
        assertEquals(subjectId, member.path("subjectId").asText());
        CreateRoleRequest ordinary = new CreateRoleRequest();
        ordinary.setCode("open-ordinary-" + suffix);
        ordinary.setName("其他有效角色");
        Long ordinaryId = roleService.createRole(ordinary).getId();
        ordinaryRoles.add(ordinaryId);
        ruleService.putRoleAuthorizationRule(ordinaryId, applicationId, Collections.singletonList("sample:page"), Collections.emptyList(), null);
        roleService.assignRole(userId, ordinaryId);
        ResponseEntity<String> created = create(UUID.randomUUID().toString());
        String id = mapper.readTree(created.getBody()).path("openRoleId").asText();
        String rulePath = "/iam/api/roles/" + id + "/authorization-rules/" + applicationId;
        String version = request(HttpMethod.PUT, rulePath, ruleBody(), created.getHeaders().getETag(), token).getHeaders().getETag();
        String relationPath = "/iam/api/departments/" + child + "/roles/" + id;
        ResponseEntity<String> assigned = request(HttpMethod.PUT, relationPath, null, version, token);
        assertEquals(204, assigned.getStatusCodeValue());
        io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity before = authorizations.findByUserIdAndApplicationId(userId, applicationId).get();
        assertEquals(Collections.singletonList("sample:api"), IamApplicationAuthorizationJsonCodec.readStringList(before.getApiPermissionsJson(), "apiPermissions"));
        Long permissionsBefore = userRepository.findById(userId).get().getPermissionVersion();
        assertEquals(204, request(HttpMethod.DELETE, relationPath, null, assigned.getHeaders().getETag(), token).getStatusCodeValue());
        io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity after = authorizations.findByUserIdAndApplicationId(userId, applicationId).get();
        assertTrue(IamApplicationAuthorizationJsonCodec.readStringList(after.getApiPermissionsJson(), "apiPermissions").isEmpty());
        assertEquals(Collections.singletonList("sample:page"), IamApplicationAuthorizationJsonCodec.readStringList(after.getPagePermissionsJson(), "pagePermissions"));
        assertTrue(after.getAuthorizationVersion() > before.getAuthorizationVersion());
        assertTrue(userRepository.findById(userId).get().getPermissionVersion() > permissionsBefore);
        assertEquals(Integer.valueOf(1), after.getAdmitted());
    }

    @Test
    void publishedAuditConsumerObservesCommitButNotReplayOrRollback() throws Exception {
        String externalId = UUID.randomUUID().toString();
        ResponseEntity<String> created = create(externalId);
        String id = mapper.readTree(created.getBody()).path("openRoleId").asText();
        List<ServerIamAuditRecord> records = audits.forRole(id);
        assertEquals(1, records.size(), "旧发布监听器必须收到一次提交成功的创建");
        JsonNode detail = mapper.readTree(records.get(0).getDetail());
        assertEquals(subject, detail.path("actorSubjectId").asText());
        assertEquals("aksk", detail.path("actorSourceId").asText());
        assertEquals("SERVICE", detail.path("actorSubjectType").asText());
        assertEquals(1, detail.path("revision").asInt());
        assertTrue(detail.has("requestId"));
        assertFalse(detail.has("apiPermissions") || detail.has("name") || detail.has("token"));
        assertEquals(200, create(externalId).getStatusCodeValue());
        assertEquals(1, audits.forRole(id).size());
        IamOpenRoleBindingEntity binding = bindings.findById(id).get();
        UpdateRoleRequest update = new UpdateRoleRequest();
        update.setName("回滚中的变更");
        new TransactionTemplate(transactionManager).execute(status -> {
            roleService.updateRole(binding.getRoleId(), update);
            assertEquals(1, audits.forRole(id).size(), "未提交时不能消费成功审计");
            status.setRollbackOnly();
            return null;
        });
        assertEquals(1, audits.forRole(id).size());
        assertEquals(created.getHeaders().getETag(), request(HttpMethod.GET, "/iam/api/roles/" + id, null, null, token).getHeaders().getETag());
        String relation = "/iam/api/departments/" + child + "/roles/" + id;
        ResponseEntity<String> changed = request(HttpMethod.PUT, relation, null, created.getHeaders().getETag(), token);
        assertEquals(204, changed.getStatusCodeValue());
        assertEquals(2, audits.forRole(id).size());
        assertEquals(204, request(HttpMethod.PUT, relation, null, changed.getHeaders().getETag(), token).getStatusCodeValue());
        assertEquals(2, audits.forRole(id).size());
    }

    @Test
    void twoIndependentInstancesShareCreationAndConditionFence() throws Exception {
        SpringApplication application = new SpringApplication(SimpleIamServerTestApplication.class, AuthenticationFixture.class);
        try (ConfigurableApplicationContext second = application.run("--server.port=0",
                "--io.github.surezzzzzz.sdk.auth.iam.server.bootstrap.enabled=false",
                "--io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths[0]=/iam/api/**")) {
            FixtureAdapter secondAdapter = second.getBean(FixtureAdapter.class);
            assertNotSame(adapter, secondAdapter);
            assertNotSame(bindings, second.getBean(IamOpenRoleBindingRepository.class));
            int secondPort = ((WebServerApplicationContext) second).getWebServer().getPort();
            TestRestTemplate other = new TestRestTemplate();
            String secondToken = secondAdapter.register(subject, ResourceSubjectType.SERVICE, IamOpenRoleManifestUpgradeService.apiCodes(), fullData());
            String externalId = UUID.randomUUID().toString();
            ResponseEntity<String> created = create(externalId);
            String id = mapper.readTree(created.getBody()).path("openRoleId").asText();
            ResponseEntity<String> replay = other.exchange("http://localhost:" + secondPort + "/iam/api/roles", HttpMethod.POST,
                    new HttpEntity<>(createBody(externalId, applicationId, root), headers(secondToken, null)), String.class);
            assertEquals(200, replay.getStatusCodeValue());
            assertEquals(id, mapper.readTree(replay.getBody()).path("openRoleId").asText());
            String version = created.getHeaders().getETag();
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                CountDownLatch start = new CountDownLatch(1);
                Future<ResponseEntity<String>> first = executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return request(HttpMethod.PUT, "/iam/api/departments/" + child + "/roles/" + id, null, version, token);
                });
                Future<ResponseEntity<String>> next = executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return other.exchange("http://localhost:" + secondPort + "/iam/api/roles/" + id + "/authorization-rules/" + applicationId,
                            HttpMethod.PUT, new HttpEntity<>(ruleBody(), headers(secondToken, version)), String.class);
                });
                start.countDown();
                Set<Integer> statuses = new HashSet<>(Arrays.asList(first.get(30, TimeUnit.SECONDS).getStatusCodeValue(), next.get(30, TimeUnit.SECONDS).getStatusCodeValue()));
                assertTrue(statuses.contains(412));
                assertTrue(statuses.contains(200) || statuses.contains(204));
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void staleManifestAndExhaustedRevisionLeaveNoPartialChanges() throws Exception {
        ResponseEntity<String> created = create(UUID.randomUUID().toString());
        String id = mapper.readTree(created.getBody()).path("openRoleId").asText();
        String rulePath = "/iam/api/roles/" + id + "/authorization-rules/" + applicationId;
        String staleBody = ruleBody();
        PutApplicationPermissionManifestRequest manifest = new PutApplicationPermissionManifestRequest();
        manifest.setRoles(Collections.emptyList());
        manifest.setPagePermissions(Collections.singletonList("sample:page"));
        manifest.setApiPermissions(Collections.singletonList("sample:api"));
        manifest.setDataResources(Collections.emptyList());
        manifestService.putManifest(applicationId, manifest);
        assertEquals(409, request(HttpMethod.PUT, rulePath, staleBody, created.getHeaders().getETag(), token).getStatusCodeValue());
        jdbc.update("UPDATE iam_open_role_binding SET revision = ? WHERE open_role_id = ?", Long.MAX_VALUE, id);
        try {
            String maxVersion = "\"open-role:" + id + ":" + Long.MAX_VALUE + "\"";
            String relation = "/iam/api/departments/" + child + "/roles/" + id;
            assertEquals(409, request(HttpMethod.PUT, relation, null, maxVersion, token).getStatusCodeValue());
            assertEquals(0, mapper.readTree(request(HttpMethod.GET, "/iam/api/roles/" + id + "/departments", null, null, token).getBody()).path("total").asInt());
            assertEquals(1, audits.forRole(id).size());
        } finally {
            // 断言失败也恢复本用例注入的上限，确保清理仍走正常业务事务。
            jdbc.update("UPDATE iam_open_role_binding SET revision = 1 WHERE open_role_id = ?", id);
        }
    }

    @Test
    void administratorHttpWritesUseSameRevisionAndTrustedActor() throws Exception {
        CreateUserRequest user = new CreateUserRequest();
        user.setUsername("open-admin-" + suffix);
        user.setPassword("Sample-Only@2026!9");
        user.setDisplayName("示例管理员");
        user.setDepartmentId(child);
        Long userId = userService.createUser(user).getId();
        users.add(userId);
        roleService.assignRole(userId, roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).getId());
        ResponseEntity<String> csrf = http.getForEntity("/iam/web/auth/csrf", String.class);
        assertEquals(200, csrf.getStatusCodeValue());
        JsonNode csrfBody = mapper.readTree(csrf.getBody());
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);
        adminHeaders.set(HttpHeaders.COOKIE, csrf.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]);
        adminHeaders.set(csrfBody.path("headerName").asText(), csrfBody.path("token").asText());
        ResponseEntity<String> login = http.exchange("/iam/web/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"" + user.getUsername() + "\",\"password\":\"Sample-Only@2026!9\"}", adminHeaders), String.class);
        assertEquals(200, login.getStatusCodeValue());
        String loginCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        if (loginCookie != null) adminHeaders.set(HttpHeaders.COOKIE, loginCookie.split(";", 2)[0]);
        ResponseEntity<String> created = create(UUID.randomUUID().toString());
        String id = mapper.readTree(created.getBody()).path("openRoleId").asText();
        String path = "/iam/admin/roles/" + bindings.findById(id).get().getRoleId();
        assertEquals(428, http.exchange(path, HttpMethod.PUT, new HttpEntity<>("{\"name\":\"管理变更\"}", adminHeaders), String.class).getStatusCodeValue());
        adminHeaders.set(HttpHeaders.IF_MATCH, created.getHeaders().getETag());
        ResponseEntity<String> updated = http.exchange(path, HttpMethod.PUT,
                new HttpEntity<>("{\"name\":\"管理变更\"}", adminHeaders), String.class);
        assertEquals(200, updated.getStatusCodeValue());
        assertEquals(2, mapper.readTree(updated.getBody()).path("openRoleBinding").path("revision").asInt());
        assertEquals(412, http.exchange(path, HttpMethod.PUT, new HttpEntity<>("{\"name\":\"迟到变更\"}", adminHeaders), String.class).getStatusCodeValue());
        JsonNode last = mapper.readTree(audits.forRole(id).get(1).getDetail());
        assertEquals(userRepository.findById(userId).get().getSubjectId(), last.path("actorSubjectId").asText());
        assertEquals("iam", last.path("actorSourceId").asText());
        assertEquals("HUMAN", last.path("actorSubjectType").asText());

        Long roleId = bindings.findById(id).get().getRoleId();
        adminHeaders.set(HttpHeaders.IF_MATCH, "\"open-role:" + id + ":2\"");
        assertEquals(200, http.exchange("/iam/admin/departments/" + child + "/roles/" + roleId,
                HttpMethod.POST, new HttpEntity<>(adminHeaders), String.class).getStatusCodeValue());
        ResponseEntity<String> detail = http.exchange(path, HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
        assertEquals(200, detail.getStatusCodeValue());
        JsonNode current = mapper.readTree(detail.getBody());
        JsonNode summary = current.path("openRoleBinding");
        assertEquals(id, summary.path("openRoleId").asText());
        assertEquals(applicationId.longValue(), summary.path("applicationId").asLong());
        assertEquals(root.longValue(), summary.path("rootDepartmentId").asLong());
        assertEquals(3, summary.path("revision").asInt());
        assertEquals("ACTIVE", summary.path("state").asText());

        String subjectId = userRepository.findById(userId).get().getSubjectId();
        for (String listPath : Arrays.asList("/iam/admin/roles", "/iam/admin/departments/" + child + "/roles",
                "/iam/admin/users/" + subjectId + "/roles")) {
            ResponseEntity<String> listed = http.exchange(listPath, HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
            assertEquals(200, listed.getStatusCodeValue());
            assertEquals(summary, findRoleResponse(mapper.readTree(listed.getBody()), roleId).path("openRoleBinding"));
        }
        ResponseEntity<String> listedPage = http.exchange("/iam/admin/roles/page?keyword=" + current.path("code").asText()
                + "&page=1&size=10", HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
        assertEquals(200, listedPage.getStatusCodeValue());
        JsonNode page = mapper.readTree(listedPage.getBody());
        assertEquals(summary, findRoleResponse(page.path("content"), roleId).path("openRoleBinding"));
        assertEquals(1, page.path("totalElements").asInt());
        assertEquals(1, page.path("page").asInt());
        assertEquals(10, page.path("size").asInt());

        Long ordinaryId = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).getId();
        JsonNode userRoles = mapper.readTree(http.exchange("/iam/admin/users/" + subjectId + "/roles",
                HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class).getBody());
        assertTrue(findRoleResponse(userRoles, ordinaryId).path("openRoleBinding").isNull());
        ResponseEntity<String> emptyPage = http.exchange("/iam/admin/roles/page?keyword=missing-" + suffix,
                HttpMethod.GET, new HttpEntity<>(adminHeaders), String.class);
        assertEquals(200, emptyPage.getStatusCodeValue());
        assertEquals(0, mapper.readTree(emptyPage.getBody()).path("content").size());
    }

    private JsonNode findRoleResponse(JsonNode roles, Long roleId) {
        assertTrue(roles.isArray());
        for (JsonNode role : roles) {
            if (role.path("id").asLong() == roleId.longValue()) return role;
        }
        throw new AssertionError("角色响应中缺少目标角色");
    }

    @Test
    void manifestUpgradePreservesCustomDeclarationsAndDoesNotExpandOtherRoles() {
        Long iamId = jdbc.queryForObject("SELECT id FROM iam_trusted_application WHERE application_code = 'iam'", Long.class);
        io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.response.ApplicationPermissionManifestResponse original = manifestService.getManifest(iamId);
        PutApplicationPermissionManifestRequest restore = copyManifest(original);
        CreateRoleRequest ordinary = new CreateRoleRequest();
        ordinary.setCode("open-legacy-" + suffix);
        ordinary.setName("既有治理角色");
        Long ordinaryId = roleService.createRole(ordinary).getId();
        ordinaryRoles.add(ordinaryId);
        String oldCode = original.getApiPermissions().stream().filter(code -> !IamOpenRoleManifestUpgradeService.apiCodes().contains(code)).findFirst().get();
        ruleService.putRoleAuthorizationRule(ordinaryId, iamId, Collections.emptyList(), Collections.singletonList(oldCode), null);
        try {
            PutApplicationPermissionManifestRequest legacy = copyManifest(original);
            legacy.getApiPermissions().removeAll(IamOpenRoleManifestUpgradeService.apiCodes());
            legacy.getApiPermissions().add("sample:custom:api");
            legacy.getDataResources().removeIf(resource -> IamOpenRoleManifestUpgradeService.declarations().stream().anyMatch(expected -> expected.getResource().equals(resource.getResource())));
            manifestService.putManifest(iamId, legacy);
            Long legacyVersion = manifestService.getManifest(iamId).getManifestVersion();
            // 两个独立 Bean 图同时升级缺项清单，数据库锁必须只允许一次实际合并。
            try (ConfigurableApplicationContext second = new SpringApplication(SimpleIamServerTestApplication.class, AuthenticationFixture.class)
                    .run("--server.port=0", "--io.github.surezzzzzz.sdk.auth.iam.server.bootstrap.enabled=false",
                            "--io.github.surezzzzzz.sdk.auth.resource.server.security.protected-paths[0]=/iam/api/**")) {
                IamOpenRoleManifestUpgradeService otherUpgrade = second.getBean(IamOpenRoleManifestUpgradeService.class);
                assertNotSame(manifestUpgrade, otherUpgrade);
                ExecutorService upgrades = Executors.newFixedThreadPool(2);
                try {
                    Future<?> first = upgrades.submit(manifestUpgrade::upgrade);
                    Future<?> next = upgrades.submit(otherUpgrade::upgrade);
                    first.get(30, TimeUnit.SECONDS);
                    next.get(30, TimeUnit.SECONDS);
                } catch (Exception failed) {
                    throw new IllegalStateException(failed);
                } finally {
                    upgrades.shutdownNow();
                }
            }
            io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.response.ApplicationPermissionManifestResponse upgraded = manifestService.getManifest(iamId);
            assertEquals(Long.valueOf(legacyVersion + 1L), upgraded.getManifestVersion());
            assertTrue(upgraded.getApiPermissions().contains("sample:custom:api"));
            assertTrue(upgraded.getApiPermissions().containsAll(IamOpenRoleManifestUpgradeService.apiCodes()));
            assertEquals(Collections.singletonList(oldCode), ruleService.getRoleAuthorizationRule(ordinaryId, iamId).getApiPermissions());
            manifestUpgrade.upgrade();
            assertEquals(upgraded.getManifestVersion(), manifestService.getManifest(iamId).getManifestVersion());
            PutApplicationPermissionManifestRequest conflicting = copyManifest(upgraded);
            conflicting.getDataResources().stream().filter(resource -> "iam:open-role".equals(resource.getResource())).findFirst().get().setActions(Collections.singletonList("read"));
            manifestService.putManifest(iamId, conflicting);
            Long conflictVersion = manifestService.getManifest(iamId).getManifestVersion();
            assertThrows(io.github.surezzzzzz.sdk.auth.iam.server.exception.IamOpenRoleException.class, manifestUpgrade::upgrade);
            assertEquals(conflictVersion, manifestService.getManifest(iamId).getManifestVersion());
        } finally {
            manifestService.putManifest(iamId, restore);
        }
    }

    @Test
    void directoryCyclesAndDepthBudgetAreNotReportedAsComplete() throws Exception {
        String path = "/iam/api/organization-directories/" + root + "/departments";
        jdbc.update("UPDATE iam_department SET parent_id = ? WHERE id = ?", child, root);
        try {
            assertEquals(409, request(HttpMethod.GET, path, null, null, token).getStatusCodeValue());
            assertEquals(409, create(UUID.randomUUID().toString()).getStatusCodeValue());
        } finally {
            jdbc.update("UPDATE iam_department SET parent_id = NULL WHERE id = ?", root);
        }
        Long parent = child;
        for (int index = 2; index < 64; index++) parent = department(parent);
        assertEquals(200, request(HttpMethod.GET, path, null, null, token).getStatusCodeValue());
        Long overflow = department(parent);
        assertEquals(422, request(HttpMethod.GET, path, null, null, token).getStatusCodeValue());
        ResponseEntity<String> created = create(UUID.randomUUID().toString());
        String roleId = mapper.readTree(created.getBody()).path("openRoleId").asText();
        assertEquals(422, request(HttpMethod.PUT, "/iam/api/departments/" + overflow + "/roles/" + roleId,
                null, created.getHeaders().getETag(), token).getStatusCodeValue());
    }

    @Test
    void exactNodeBudgetIsCompleteButOneMoreNodeFailsClosed() throws Exception {
        String prefix = "open-budget-" + suffix + "-";
        List<Object[]> nodes = new ArrayList<>();
        for (int index = 0; index < 4094; index++) nodes.add(new Object[]{prefix + index, root});
        String insert = "INSERT INTO iam_department (code,name,parent_id,sort_order,status,created_at,updated_at) VALUES (?,'示例预算节点',?,0,1,NOW(),NOW())";
        try {
            jdbc.batchUpdate(insert, nodes);
            String path = "/iam/api/organization-directories/" + root + "/departments";
            ResponseEntity<String> exact = request(HttpMethod.GET, path, null, null, token);
            assertEquals(200, exact.getStatusCodeValue());
            assertEquals(4096, mapper.readTree(exact.getBody()).path("departments").size());
            jdbc.update(insert, prefix + "overflow", root);
            assertEquals(422, request(HttpMethod.GET, path, null, null, token).getStatusCodeValue());
        } finally {
            jdbc.update("DELETE FROM iam_department WHERE parent_id = ? AND code LIKE ?", root, prefix + "%");
        }
    }

    @Test
    void ancestorMoveCannotSlipThroughAnAssignmentAlreadyWaitingForItsLock() throws Exception {
        Long grandchild = department(child);
        ResponseEntity<String> created = create(UUID.randomUUID().toString());
        String id = mapper.readTree(created.getBody()).path("openRoleId").asText();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch moved = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            Future<?> movement = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("SELECT id FROM iam_department WHERE id = ? FOR UPDATE", Long.class, child);
                jdbc.update("UPDATE iam_department SET parent_id = NULL WHERE id = ?", child);
                moved.countDown();
                try {
                    assertTrue(release.await(15, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return null;
            }));
            assertTrue(moved.await(10, TimeUnit.SECONDS));
            Future<ResponseEntity<String>> assignment = executor.submit(() -> request(HttpMethod.PUT,
                    "/iam/api/departments/" + grandchild + "/roles/" + id, null, created.getHeaders().getETag(), token));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            boolean waiting = false;
            while (System.nanoTime() < deadline && !assignment.isDone()) {
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks b "
                                + "ON w.BLOCKING_ENGINE_LOCK_ID = b.ENGINE_LOCK_ID WHERE b.OBJECT_SCHEMA = DATABASE() AND b.OBJECT_NAME = 'iam_department' AND b.LOCK_DATA = ?",
                        Integer.class, child.toString());
                if (count != null && count > 0) {
                    waiting = true;
                    break;
                }
                Thread.sleep(25);
            }
            assertTrue(waiting, "必须直接证明挂载在数据库祖先锁上等待");
            release.countDown();
            movement.get(10, TimeUnit.SECONDS);
            assertEquals(409, assignment.get(10, TimeUnit.SECONDS).getStatusCodeValue());
            assertEquals(0, mapper.readTree(request(HttpMethod.GET, "/iam/api/roles/" + id + "/departments", null, null, token).getBody()).path("total").asInt());
            assertEquals(created.getHeaders().getETag(), request(HttpMethod.GET, "/iam/api/roles/" + id, null, null, token).getHeaders().getETag());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
            jdbc.update("UPDATE iam_department SET parent_id = ? WHERE id = ?", root, child);
        }
    }

    @Test
    void sameCreationKeyWithDifferentContentAndInvalidScopeCannotCreateOrphans() throws Exception {
        String externalId = UUID.randomUUID().toString();
        ResponseEntity<String> created = create(externalId);
        assertEquals(201, created.getStatusCodeValue());
        Long outside = department(null);
        assertEquals(409, createAt(externalId, applicationId, outside, token).getStatusCodeValue());
        Long before = jdbc.queryForObject("SELECT COUNT(*) FROM iam_role", Long.class);
        assertEquals(409, createAt(UUID.randomUUID().toString(), applicationId, Long.MAX_VALUE, token).getStatusCodeValue());
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM iam_role", Long.class));
        assertEquals(1, bindings.findAll((entity, query, builder) -> builder.equal(entity.get("ownerSubjectId"), subject)).size());
    }

    private PutApplicationPermissionManifestRequest copyManifest(io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.response.ApplicationPermissionManifestResponse source) {
        PutApplicationPermissionManifestRequest copy = new PutApplicationPermissionManifestRequest();
        copy.setRoles(new ArrayList<>(source.getRoles()));
        copy.setPagePermissions(new ArrayList<>(source.getPagePermissions()));
        copy.setApiPermissions(new ArrayList<>(source.getApiPermissions()));
        List<io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration> resources = new ArrayList<>();
        for (io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration old : source.getDataResources()) {
            io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration value = new io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration();
            value.setResource(old.getResource());
            value.setActions(new ArrayList<>(old.getActions()));
            value.setDimensions(new ArrayList<>(old.getDimensions()));
            resources.add(value);
        }
        copy.setDataResources(resources);
        return copy;
    }

    private Long department(Long parent) {
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setCode("open-dept-" + UUID.randomUUID());
        request.setName("示例部门");
        request.setParentId(parent);
        Long id = departmentService.createDepartment(request).getId();
        departments.add(id);
        return id;
    }

    private ResponseEntity<String> create(String externalId) throws Exception {
        return createAt(externalId, applicationId, root, token);
    }

    private ResponseEntity<String> createAt(String externalId, Long app, Long department, String credential) throws Exception {
        return request(HttpMethod.POST, "/iam/api/roles", createBody(externalId, app, department), null, credential);
    }

    private String createBody(String externalId, Long app, Long department) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("externalId", externalId);
        request.put("applicationId", app);
        request.put("rootDepartmentId", department);
        request.put("name", "示例角色");
        return mapper.writeValueAsString(request);
    }

    private String ruleBody() throws Exception {
        io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.response.ApplicationPermissionManifestResponse manifest = manifestService.getManifest(applicationId);
        Map<String, Object> body = new HashMap<>();
        body.put("manifestVersion", manifest.getManifestVersion());
        body.put("manifestDigest", manifest.getManifestDigest());
        body.put("pagePermissions", Collections.singletonList("sample:page"));
        body.put("apiPermissions", Collections.singletonList("sample:api"));
        return mapper.writeValueAsString(body);
    }

    private ResponseEntity<String> request(HttpMethod method, String path, String body, String etag, String credential) {
        return http.exchange(path, method, new HttpEntity<>(body, headers(credential, etag)), String.class);
    }

    private HttpHeaders headers(String credential, String etag) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (credential != null) headers.setBearerAuth(credential);
        if (etag != null) headers.set(HttpHeaders.IF_MATCH, etag);
        return headers;
    }

    private DataGrantDocument fullData() {
        List<DataGrant> grants = new ArrayList<>();
        IamOpenRoleManifestUpgradeService.declarations().forEach(resource -> grants.add(new DataGrant(resource.getResource(), resource.getActions(), true, Collections.emptyList())));
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION, grants);
    }

    private DataGrantDocument document(List<DataGrant> grants) {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION, grants);
    }

    private DataGrant scopedGrant(String resource, List<String> actions, Long app, Long department) {
        return new DataGrant(resource, actions, false, Arrays.asList(
                new DataConstraint("applicationId", DataConstraintOperator.IN, Collections.singletonList(app.toString())),
                new DataConstraint("rootDepartmentId", DataConstraintOperator.IN, Collections.singletonList(department.toString()))));
    }

    /**
     * 仅在本测试装配，未知凭据拒绝，不进入发布物。
     */
    @TestConfiguration
    static class AuthenticationFixture {
        @Bean
        FixtureAdapter openRoleFixtureAdapter() {
            return new FixtureAdapter();
        }

        @Bean
        AuditCollector openRoleAuditCollector() {
            return new AuditCollector();
        }
    }

    /**
     * 使用旧发布监听制品的 SPI 捕获提交后记录，不模拟监听实现。
     */
    static class AuditCollector implements ServerIamAuditHandler {
        private final List<ServerIamAuditRecord> records = new CopyOnWriteArrayList<>();

        @Override
        public void handle(ServerIamAuditRecord record) {
            records.add(record);
        }

        List<ServerIamAuditRecord> forRole(String id) {
            List<ServerIamAuditRecord> found = new ArrayList<>();
            for (ServerIamAuditRecord record : records) {
                if (record.getDetail() != null && record.getDetail().contains(id)) found.add(record);
            }
            return found;
        }
    }

    static class FixtureAdapter implements ResourceAuthenticationAdapter {
        private final ResourceAuthenticationSourceId source = new ResourceAuthenticationSourceId("aksk");
        private final Map<String, ResourceAuthenticationResult> results = new ConcurrentHashMap<>();

        @Override
        public ResourceAuthenticationSourceId sourceId() {
            return source;
        }

        @Override
        public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
            return results.getOrDefault(((BearerResourceCredential) credential).getToken(), ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.TOKEN_INACTIVE));
        }

        String register(String subject, ResourceSubjectType type, List<String> apis, DataGrantDocument data) {
            Instant now = Instant.now();
            ApplicationAuthorizationContext context = new ApplicationAuthorizationContext(SimpleApplicationAuthorizationConstant.PROTOCOL,
                    SimpleApplicationAuthorizationConstant.VERSION, type == ResourceSubjectType.SERVICE ? ApplicationAuthorizationSubjectType.SERVICE : ApplicationAuthorizationSubjectType.HUMAN,
                    subject, "iam", true, Collections.emptyList(), Collections.emptyList(), apis, data, 1L, "fixture", "fixture", now.minusSeconds(1), now.plusSeconds(600));
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"kid\":\"aksk/fixture\"}".getBytes(StandardCharsets.UTF_8)) + "." + UUID.randomUUID() + ".fixture";
            results.put(token, ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(source, type, subject), context));
            return token;
        }

        void clearSubject(String subject) {
            results.entrySet().removeIf(entry -> subject.equals(entry.getValue().getPrincipal().getSubjectId()));
        }
    }
}
