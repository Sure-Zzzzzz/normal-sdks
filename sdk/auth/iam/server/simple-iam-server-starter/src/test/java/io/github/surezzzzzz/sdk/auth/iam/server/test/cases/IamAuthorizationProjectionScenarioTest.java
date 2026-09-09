package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.iam.server.codec.IamApplicationAuthorizationJsonCodec;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.CreateRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.PutApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.*;
import io.github.surezzzzzz.sdk.auth.iam.server.service.*;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一授权投影五场景测试（清单 → 规则 → 投影）。
 *
 * <p>场景1 准入授予、场景2 角色分配 / 撤销、场景3 规则增删改、
 * 场景4 清单替换、场景5 准入撤销，全部走真实服务触发链，
 * 断言投影行内容（并集聚合、版本递增、admitted 归属、清单版本同步）。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamAuthorizationProjectionScenarioTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String userUsername = "proj-user-" + suffix;
    private final String viewerUsername = "proj-viewer-" + suffix;
    private final String noRoleUsername = "proj-norole-" + suffix;
    private final String applicationCode = "proj-app-" + suffix;
    private Long userId;
    private Long viewerId;
    private Long noRoleId;
    private Long roleAId;
    private Long roleBId;
    private Long roleCId;
    private String roleACode;
    private String roleBCode;
    private Long applicationId;

    @Autowired
    private TrustedApplicationService trustedApplicationService;

    @Autowired
    private IamApplicationPermissionManifestService manifestService;

    @Autowired
    private IamRoleAuthorizationRuleService ruleService;

    @Autowired
    private IamAuthorizationProjectionService projectionService;

    @Autowired
    private IamApplicationAuthorizationAdminService authorizationAdminService;

    @Autowired
    private IamApplicationAuthorizationRepository authorizationRepository;

    @Autowired
    private IamRoleAuthorizationRuleRepository ruleRepository;

    @Autowired
    private IamRoleRepository roleRepository;

    @Autowired
    private IamTrustedApplicationRepository trustedApplicationRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IamUserRepository userRepository;

    @BeforeEach
    void prepare() {
        userId = createUser(userUsername);
        viewerId = createUser(viewerUsername);
        noRoleId = createUser(noRoleUsername);

        roleACode = "proj-role-a-" + suffix;
        roleBCode = "proj-role-b-" + suffix;
        roleAId = createRole(roleACode);
        roleBId = createRole(roleBCode);
        roleCId = createRole("proj-role-c-" + suffix);

        applicationId = createApplication();
        putManifest(1);
    }

    @AfterEach
    void cleanup() {
        for (Long uid : Arrays.asList(userId, viewerId, noRoleId)) {
            authorizationRepository.findByUserId(uid).stream()
                    .filter(authorization -> applicationId.equals(authorization.getApplicationId()))
                    .forEach(authorizationRepository::delete);
        }
        ruleRepository.findByRoleIdIn(Arrays.asList(roleAId, roleBId, roleCId))
                .forEach(ruleRepository::delete);
        if (trustedApplicationRepository.existsById(applicationId)) {
            trustedApplicationService.deleteApplication(applicationId);
        }
        for (Long roleId : Arrays.asList(roleAId, roleBId, roleCId)) {
            if (roleRepository.existsById(roleId)) {
                roleService.deleteRole(roleId);
            }
        }
        for (String username : Arrays.asList(userUsername, viewerUsername, noRoleUsername)) {
            userRepository.findByUsername(username).ifPresent(user -> userService.deleteUser(user.getId()));
        }
    }

    @Test
    @DisplayName("场景2：角色分配触发投影创建并集，撤销后收缩，版本单调递增，admitted 保持")
    void roleAssignmentProjectsUnionAndShrinksOnRevoke() {
        putRule(roleAId, Collections.singletonList("proj:p1"), Collections.singletonList("proj:a1"));

        assertTrue(authorizationRepository.findByUserIdAndApplicationId(userId, applicationId).isEmpty(),
                "分配角色前不应有投影行");

        roleService.assignRole(userId, roleAId);
        IamApplicationAuthorizationEntity projection = requireProjection(userId);
        assertEquals(set("proj:p1"), pages(projection));
        assertEquals(set("proj:a1"), apis(projection));
        assertEquals(set(roleACode), roleCodes(projection));
        assertEquals(Long.valueOf(1L), projection.getAuthorizationVersion());
        assertEquals(Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE), projection.getAdmitted());
        assertEquals("1", projection.getManifestVersion());

        putRule(roleBId, Collections.singletonList("proj:p2"), Collections.singletonList("proj:a2"));
        roleService.assignRole(userId, roleBId);
        projection = requireProjection(userId);
        assertEquals(set("proj:p1", "proj:p2"), pages(projection));
        assertEquals(set("proj:a1", "proj:a2"), apis(projection));
        assertEquals(set(roleACode, roleBCode), roleCodes(projection));
        assertEquals(Long.valueOf(2L), projection.getAuthorizationVersion());

        roleService.revokeRole(userId, roleBId);
        projection = requireProjection(userId);
        assertEquals(set("proj:p1"), pages(projection));
        assertEquals(set("proj:a1"), apis(projection));
        assertEquals(set(roleACode), roleCodes(projection));
        assertEquals(Long.valueOf(3L), projection.getAuthorizationVersion());
        assertEquals(Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE), projection.getAdmitted());

        log.info("场景2 投影并集 / 收缩断言完成：userId={}, applicationId={}", userId, applicationId);
    }

    @Test
    @DisplayName("场景3：规则变更重算持有者投影，为无投影持有人新建，删除后收缩为空集")
    void ruleChangeRecomputesAndCreatesProjection() {
        roleService.assignRole(viewerId, roleCId);
        assertTrue(authorizationRepository.findByUserIdAndApplicationId(viewerId, applicationId).isEmpty(),
                "角色无规则时分配角色不应创建投影");

        putRule(roleCId, Collections.singletonList("proj:p2"), Collections.<String>emptyList());
        IamApplicationAuthorizationEntity projection = requireProjection(viewerId);
        assertEquals(set("proj:p2"), pages(projection));
        assertEquals(Long.valueOf(1L), projection.getAuthorizationVersion());
        assertEquals(Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE), projection.getAdmitted());

        putRule(roleCId, Arrays.asList("proj:p2", "proj:p3"), Collections.<String>emptyList());
        projection = requireProjection(viewerId);
        assertEquals(set("proj:p2", "proj:p3"), pages(projection));
        assertEquals(Long.valueOf(2L), projection.getAuthorizationVersion());

        ruleService.deleteRoleAuthorizationRule(roleCId, applicationId);
        projection = requireProjection(viewerId);
        assertEquals(Collections.emptySet(), pages(projection));
        assertEquals(Collections.emptySet(), apis(projection));
        assertEquals(Long.valueOf(3L), projection.getAuthorizationVersion());
        assertEquals(Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE), projection.getAdmitted(),
                "规则收缩为空集不应撤销准入");

        log.info("场景3 规则变更重算断言完成：viewerId={}, applicationId={}", viewerId, applicationId);
    }

    @Test
    @DisplayName("场景4：清单替换后规则覆盖用户全量重算，未覆盖存量投影仅同步清单版本")
    void manifestUpdateRecomputesCoveredAndSyncsVersionForUncovered() {
        putRule(roleAId, Arrays.asList("proj:p1", "proj:p3"), Collections.<String>emptyList());
        roleService.assignRole(userId, roleAId);
        assertEquals(Long.valueOf(1L), requireProjection(userId).getAuthorizationVersion());

        projectionService.grantApplicationAdmission(noRoleId, applicationId);
        IamApplicationAuthorizationEntity uncovered = requireProjection(noRoleId);
        assertEquals(Collections.emptySet(), pages(uncovered));
        assertEquals("1", uncovered.getManifestVersion());
        assertEquals(Long.valueOf(1L), uncovered.getAuthorizationVersion());

        putManifest(2);

        IamApplicationAuthorizationEntity covered = requireProjection(userId);
        assertEquals(set("proj:p1", "proj:p3"), pages(covered));
        assertEquals("2", covered.getManifestVersion());
        assertEquals(Long.valueOf(2L), covered.getAuthorizationVersion());

        uncovered = requireProjection(noRoleId);
        assertEquals(Collections.emptySet(), pages(uncovered));
        assertEquals("2", uncovered.getManifestVersion());
        assertEquals(Long.valueOf(1L), uncovered.getAuthorizationVersion(),
                "未覆盖存量投影只同步清单版本，不应递增授权版本");

        log.info("场景4 清单替换重算 / 仅同步断言完成：applicationId={}", applicationId);
    }

    @Test
    @DisplayName("场景1+5：准入授予置 admitted=1，撤销置 0 留痕保内容，重复操作幂等")
    void admissionGrantAndRevokeSemantics() {
        putRule(roleAId, Collections.singletonList("proj:p1"), Collections.<String>emptyList());
        roleService.assignRole(userId, roleAId);
        assertEquals(Long.valueOf(1L), requireProjection(userId).getAuthorizationVersion());

        projectionService.revokeApplicationAdmission(userId, applicationId);
        IamApplicationAuthorizationEntity projection = requireProjection(userId);
        assertEquals(Integer.valueOf(SimpleIamServerConstant.STATUS_INACTIVE), projection.getAdmitted());
        assertNotNull(projection.getRevokedAt(), "撤销必须记录 revokedAt");
        assertEquals(set("proj:p1"), pages(projection), "撤销后投影内容必须保留");
        assertEquals(Long.valueOf(1L), projection.getAuthorizationVersion(), "撤销不递增授权版本");

        projectionService.revokeApplicationAdmission(userId, applicationId);
        assertEquals(Integer.valueOf(SimpleIamServerConstant.STATUS_INACTIVE),
                requireProjection(userId).getAdmitted());

        projectionService.grantApplicationAdmission(userId, applicationId);
        projection = requireProjection(userId);
        assertEquals(Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE), projection.getAdmitted());
        assertNull(projection.getRevokedAt(), "重新授予必须清空 revokedAt");
        assertEquals(set("proj:p1"), pages(projection));
        assertEquals(Long.valueOf(2L), projection.getAuthorizationVersion());

        projectionService.grantApplicationAdmission(userId, applicationId);
        projection = requireProjection(userId);
        assertEquals(Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE), projection.getAdmitted());
        assertEquals(Long.valueOf(3L), projection.getAuthorizationVersion());

        projectionService.revokeApplicationAdmission(noRoleId, applicationId);
        assertTrue(authorizationRepository.findByUserIdAndApplicationId(noRoleId, applicationId).isEmpty(),
                "无投影时撤销为幂等无操作");

        log.info("场景1+5 准入授予 / 撤销语义断言完成：userId={}", userId);
    }

    @Test
    @DisplayName("角色删除：规则级联删除，成员投影收缩为剩余角色并集")
    void roleDeletionCascadesRulesAndShrinksMemberProjections() {
        putRule(roleAId, Collections.singletonList("proj:p1"), Collections.<String>emptyList());
        putRule(roleBId, Collections.singletonList("proj:p2"), Collections.singletonList("proj:a2"));
        roleService.assignRole(userId, roleAId);
        roleService.assignRole(userId, roleBId);
        assertEquals(set("proj:p1", "proj:p2"), pages(requireProjection(userId)));

        roleService.deleteRole(roleAId);

        assertTrue(ruleRepository.findByRoleIdIn(Collections.singletonList(roleAId)).isEmpty(),
                "角色删除必须级联删除其授权规则");
        IamApplicationAuthorizationEntity projection = requireProjection(userId);
        assertEquals(set("proj:p2"), pages(projection), "成员投影必须收缩为剩余角色规则并集");
        assertEquals(set("proj:a2"), apis(projection));
        assertEquals(set(roleBCode), roleCodes(projection));
        assertEquals(Integer.valueOf(SimpleIamServerConstant.STATUS_ACTIVE), projection.getAdmitted());

        log.info("角色删除级联断言完成：userId={}, deletedRoleId={}", userId, roleAId);
    }

    @Test
    @DisplayName("应用删除级联清理授权规则与投影行")
    void applicationDeletionCascadesRulesAndProjections() {
        putRule(roleAId, Collections.singletonList("proj:p1"), Collections.<String>emptyList());
        roleService.assignRole(userId, roleAId);
        assertTrue(authorizationRepository.findByUserIdAndApplicationId(userId, applicationId).isPresent(),
                "前置：投影行应已创建");

        trustedApplicationService.deleteApplication(applicationId);

        assertTrue(ruleRepository.findByRoleIdIn(Collections.singletonList(roleAId)).isEmpty(),
                "应用删除必须级联删除其授权规则");
        assertTrue(authorizationRepository.findByUserIdAndApplicationId(userId, applicationId).isEmpty(),
                "应用删除必须级联删除授权投影行");

        log.info("应用删除级联断言完成：applicationId={}", applicationId);
    }

    @Test
    @DisplayName("用户删除级联清理其全部授权投影")
    void userDeletionCascadesProjections() {
        putRule(roleAId, Collections.singletonList("proj:p1"), Collections.<String>emptyList());
        roleService.assignRole(userId, roleAId);
        assertTrue(authorizationRepository.findByUserIdAndApplicationId(userId, applicationId).isPresent(),
                "前置：投影行应已创建");

        userService.deleteUser(userId);

        assertTrue(authorizationRepository.findByUserId(userId).isEmpty(),
                "用户删除必须级联删除其全部授权投影");

        log.info("用户删除级联断言完成：userId={}", userId);
    }

    @Test
    @DisplayName("管理面手工 PUT 写入的内容会被下一次触发事件的重算覆盖")
    void manualPutContentIsOverwrittenByNextRecompute() {
        putRule(roleAId, Collections.singletonList("proj:p1"), Collections.<String>emptyList());
        roleService.assignRole(userId, roleAId);

        PutApplicationAuthorizationRequest manualRequest = new PutApplicationAuthorizationRequest();
        manualRequest.setAdmitted(Boolean.TRUE);
        manualRequest.setRoles(Collections.singletonList("proj-admin"));
        manualRequest.setPagePermissions(Collections.singletonList("proj:p2"));
        manualRequest.setApiPermissions(Collections.singletonList("proj:a2"));
        manualRequest.setDataGrantDocument(null);
        authorizationAdminService.putAuthorization(userId, applicationId, manualRequest);

        IamApplicationAuthorizationEntity projection = requireProjection(userId);
        assertEquals(set("proj:p2"), pages(projection), "手工 PUT 应先落库生效");
        assertEquals(Long.valueOf(2L), projection.getAuthorizationVersion());

        roleService.assignRole(userId, roleBId);
        projection = requireProjection(userId);
        assertEquals(set("proj:p1"), pages(projection), "重算后内容必须回到规则并集");
        assertEquals(set(roleACode, roleBCode), roleCodes(projection));
        assertEquals(Long.valueOf(3L), projection.getAuthorizationVersion());

        log.info("手工 PUT 与重算覆盖共存断言完成：userId={}", userId);
    }

    @Test
    @DisplayName("DATA 投影 = 全部角色规则模板并集；无模板即无 DATA（失败关闭语义）")
    void dataGrantProjectionAggregatesRoleTemplates() {
        putRule(roleAId, Collections.singletonList("proj:p1"), Collections.<String>emptyList(),
                dataGrantTemplate("proj:user", Collections.singletonList("read"), false,
                        Collections.singletonMap("departmentId", Collections.singletonList("1"))));
        putRule(roleBId, Collections.<String>emptyList(), Collections.singletonList("proj:a1"),
                dataGrantTemplate("proj:user", Arrays.asList("read", "write"), false,
                        Collections.singletonMap("departmentId", Arrays.asList("2", "3"))));
        putRule(roleCId, Collections.singletonList("proj:p2"), Collections.<String>emptyList());

        roleService.assignRole(userId, roleAId);
        roleService.assignRole(userId, roleBId);
        roleService.assignRole(userId, roleCId);

        DataGrantDocument document = dataGrantDocument(requireProjection(userId));
        assertNotNull(document, "持有模板角色的用户投影必须带 DATA 文档");
        assertEquals(2, document.getGrants().size(), "grants 并集 = 两条角色模板拼接（同资源不同约束并存，DNF OR）");
        for (DataGrant grant : document.getGrants()) {
            assertEquals("proj:user", grant.getResource());
            assertFalse(grant.isAll());
            assertEquals(1, grant.getConstraints().size());
            assertEquals("departmentId", grant.getConstraints().get(0).getDimension());
        }

        // 无模板角色用户：投影 DATA 为 null（资源端失败关闭）
        roleService.assignRole(viewerId, roleCId);
        assertNull(requireProjection(viewerId).getDataGrantDocumentJson(),
                "无任何模板的用户投影 DATA 必须为 null");

        // 规则模板移除（改 null）→ 重算后 DATA 收缩为剩余模板并集
        putRule(roleAId, Collections.singletonList("proj:p1"), Collections.<String>emptyList(), null);
        document = dataGrantDocument(requireProjection(userId));
        assertEquals(1, document.getGrants().size(), "模板移除后 DATA 收缩为剩余角色模板并集");

        log.info("DATA 模板并集投影断言完成：userId={}, viewerId={}", userId, viewerId);
    }

    @Test
    @DisplayName("规则模板越界（资源/动作/维度不在清单 DATA 声明内）被拒")
    void dataGrantTemplateOutOfRangeIsRejected() {
        assertThrows(SimpleIamServerException.class, () -> putRule(roleAId,
                Collections.singletonList("proj:p1"), Collections.<String>emptyList(),
                dataGrantTemplate("proj:unknown", Collections.singletonList("read"), false,
                        Collections.singletonMap("departmentId", Collections.singletonList("1")))));
        assertThrows(SimpleIamServerException.class, () -> putRule(roleAId,
                Collections.singletonList("proj:p1"), Collections.<String>emptyList(),
                dataGrantTemplate("proj:user", Collections.singletonList("admin"), false,
                        Collections.singletonMap("departmentId", Collections.singletonList("1")))));
        assertThrows(SimpleIamServerException.class, () -> putRule(roleAId,
                Collections.singletonList("proj:p1"), Collections.<String>emptyList(),
                dataGrantTemplate("proj:user", Collections.singletonList("read"), false,
                        Collections.singletonMap("orgId", Collections.singletonList("1")))));

        log.info("DATA 模板越界拒绝断言完成");
    }

    @Test
    @DisplayName("手工授权 DATA 编辑为重算前临时精调：角色一变即被模板并集刷新")
    void manualDataGrantIsTemporaryUntilRoleRecompute() {
        Map<String, Object> manualDocument = dataGrantTemplate("proj:user",
                Arrays.asList("read", "write"), true, Collections.<String, List<String>>emptyMap());
        PutApplicationAuthorizationRequest manualRequest = new PutApplicationAuthorizationRequest();
        manualRequest.setAdmitted(Boolean.TRUE);
        manualRequest.setRoles(Collections.singletonList("proj-admin"));
        manualRequest.setPagePermissions(Collections.singletonList("proj:p2"));
        manualRequest.setApiPermissions(Collections.<String>emptyList());
        manualRequest.setDataGrantDocument(manualDocument);
        authorizationAdminService.putAuthorization(userId, applicationId, manualRequest);
        assertTrue(dataGrantDocument(requireProjection(userId)).getGrants().get(0).isAll(),
                "手工 DATA 编辑应先落库生效（all=true）");

        putRule(roleAId, Collections.singletonList("proj:p1"), Collections.<String>emptyList(),
                dataGrantTemplate("proj:user", Collections.singletonList("read"), false,
                        Collections.singletonMap("departmentId", Collections.singletonList("1"))));
        roleService.assignRole(userId, roleAId);
        DataGrantDocument document = dataGrantDocument(requireProjection(userId));
        assertFalse(document.getGrants().get(0).isAll(),
                "角色事件触发重算后，手工 DATA 必须被角色规则模板并集刷新");

        log.info("手工 DATA 临时精调语义断言完成：userId={}", userId);
    }

    private void putRule(Long roleId, List<String> pagePermissions, List<String> apiPermissions) {
        ruleService.putRoleAuthorizationRule(roleId, applicationId, pagePermissions, apiPermissions, null);
    }

    private void putRule(Long roleId, List<String> pagePermissions, List<String> apiPermissions,
                         Map<String, Object> dataGrantTemplate) {
        ruleService.putRoleAuthorizationRule(roleId, applicationId, pagePermissions, apiPermissions,
                dataGrantTemplate);
    }

    private void putManifest(int manifestVersion) {
        PutApplicationPermissionManifestRequest manifest = new PutApplicationPermissionManifestRequest();
        manifest.setRoles(Arrays.asList("proj-admin", "proj-user"));
        if (manifestVersion >= 2) {
            manifest.setPagePermissions(Arrays.asList("proj:p1", "proj:p2", "proj:p3", "proj:p4"));
        } else {
            manifest.setPagePermissions(Arrays.asList("proj:p1", "proj:p2", "proj:p3"));
        }
        manifest.setApiPermissions(Arrays.asList("proj:a1", "proj:a2"));
        manifest.setDataResources(Collections.singletonList(dataResource(
                "proj:user", Arrays.asList("read", "write"), Collections.singletonList("departmentId"))));
        manifestService.putManifest(applicationId, manifest);
    }

    private DataResourceDeclaration dataResource(String resource, List<String> actions, List<String> dimensions) {
        DataResourceDeclaration declaration = new DataResourceDeclaration();
        declaration.setResource(resource);
        declaration.setActions(actions);
        declaration.setDimensions(dimensions);
        return declaration;
    }

    /**
     * 构造 DATA 授权模板（DataGrantDocument claim 形态）。
     */
    private Map<String, Object> dataGrantTemplate(String resource, List<String> actions, boolean all,
                                                  Map<String, List<String>> dimensionValues) {
        List<Map<String, Object>> constraints = new java.util.ArrayList<>();
        for (Map.Entry<String, List<String>> entry : dimensionValues.entrySet()) {
            Map<String, Object> constraint = new java.util.LinkedHashMap<>();
            constraint.put("dimension", entry.getKey());
            constraint.put("operator", "IN");
            constraint.put("values", entry.getValue());
            constraints.add(constraint);
        }
        Map<String, Object> grant = new java.util.LinkedHashMap<>();
        grant.put("resource", resource);
        grant.put("actions", actions);
        grant.put("all", all);
        grant.put("constraints", constraints);
        Map<String, Object> document = new java.util.LinkedHashMap<>();
        document.put("protocol", SimpleDataPermissionConstant.PROTOCOL);
        document.put("version", SimpleDataPermissionConstant.VERSION);
        document.put("grants", Collections.singletonList(grant));
        return document;
    }

    private DataGrantDocument dataGrantDocument(IamApplicationAuthorizationEntity projection) {
        return IamApplicationAuthorizationJsonCodec.readDataGrantDocument(
                projection.getDataGrantDocumentJson());
    }

    private IamApplicationAuthorizationEntity requireProjection(Long targetUserId) {
        return authorizationRepository.findByUserIdAndApplicationId(targetUserId, applicationId)
                .orElseThrow(() -> new AssertionError(
                        "投影行应存在：userId=" + targetUserId + ", applicationId=" + applicationId));
    }

    private Set<String> pages(IamApplicationAuthorizationEntity projection) {
        return new HashSet<>(IamApplicationAuthorizationJsonCodec.readStringList(
                projection.getPagePermissionsJson(), "pagePermissions"));
    }

    private Set<String> apis(IamApplicationAuthorizationEntity projection) {
        return new HashSet<>(IamApplicationAuthorizationJsonCodec.readStringList(
                projection.getApiPermissionsJson(), "apiPermissions"));
    }

    private Set<String> roleCodes(IamApplicationAuthorizationEntity projection) {
        return new HashSet<>(IamApplicationAuthorizationJsonCodec.readStringList(
                projection.getRolesJson(), "roles"));
    }

    private Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private Long createUser(String username) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Admin@1234");
        request.setDisplayName(username);
        return userService.createUser(request).getId();
    }

    private Long createRole(String code) {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setCode(code);
        request.setName("投影测试角色 " + code);
        return roleService.createRole(request).getId();
    }

    private Long createApplication() {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(applicationCode + "-web");
        client.setClientName("Projection Test Public Client");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://projection-test.example.test/callback"));
        client.setScopes(Arrays.asList("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("none"));

        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(applicationCode);
        request.setApplicationName("Projection Test Application");
        request.setInitialClient(client);
        return trustedApplicationService.createApplication(request).getApplication().getId();
    }
}
