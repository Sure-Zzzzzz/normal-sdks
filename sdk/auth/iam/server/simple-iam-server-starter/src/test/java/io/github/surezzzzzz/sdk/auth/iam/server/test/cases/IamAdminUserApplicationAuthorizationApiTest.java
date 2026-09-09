package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.DataResourceDeclaration;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.*;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户应用授权管理 API 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminUserApplicationAuthorizationApiTest {

    private static final String DATA_GRANT_DOCUMENT =
            "{\"protocol\":\"simple-data-permission\",\"version\":\"1.0\","
                    + "\"grants\":[{\"resource\":\"order\",\"actions\":[\"read\"],\"all\":false,"
                    + "\"constraints\":[{\"dimension\":\"region\",\"operator\":\"IN\",\"values\":[\"north\"]}]}]}";

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "apa-admin-" + suffix;
    private final String userUsername = "apa-user-" + suffix;
    private final String otherUserUsername = "apa-other-" + suffix;
    private final String applicationCode = "apa-app-" + suffix;
    private Cookie adminSession;
    private Cookie userSession;
    private Long adminUserId;
    private Long targetUserId;
    private Long otherUserId;
    private Long applicationId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrustedApplicationService trustedApplicationService;

    @Autowired
    private IamApplicationPermissionManifestService manifestService;

    @Autowired
    private IamApplicationAuthorizationRepository authorizationRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamApplicationAuthorizationService applicationAuthorizationService;

    @BeforeEach
    void prepare() throws Exception {
        CreateUserRequest adminRequest = new CreateUserRequest();
        adminRequest.setUsername(adminUsername);
        adminRequest.setPassword("Admin@1234");
        adminRequest.setDisplayName(adminUsername);
        adminUserId = userService.createUser(adminRequest).getId();
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(adminUserId, adminRole.getId());
        adminSession = loginSession(adminUsername);

        targetUserId = createUser(userUsername);
        otherUserId = createUser(otherUserUsername);
        userSession = loginSession(userUsername);
        applicationId = createApplication(applicationCode, applicationCode + "-web");
        registerManifest(applicationId);
    }

    private void registerManifest(Long applicationId) {
        PutApplicationPermissionManifestRequest manifest = new PutApplicationPermissionManifestRequest();
        manifest.setRoles(Collections.singletonList("app-user"));
        manifest.setPagePermissions(Collections.singletonList("iam:order:page"));
        manifest.setApiPermissions(Collections.singletonList("iam:order:api"));
        manifest.setDataResources(Collections.singletonList(dataResource(
                "order", Collections.singletonList("read"), Collections.singletonList("region"))));
        manifestService.putManifest(applicationId, manifest);
    }

    private DataResourceDeclaration dataResource(String resource, List<String> actions, List<String> dimensions) {
        DataResourceDeclaration declaration = new DataResourceDeclaration();
        declaration.setResource(resource);
        declaration.setActions(actions);
        declaration.setDimensions(dimensions);
        return declaration;
    }

    @AfterEach
    void cleanup() {
        authorizationRepository.findByUserId(adminUserId)
                .forEach(authorization -> authorizationRepository.delete(authorization));
        authorizationRepository.findByUserId(targetUserId)
                .forEach(authorization -> authorizationRepository.delete(authorization));
        trustedApplicationService.deleteApplication(applicationId);
        userRepository.findByUsername(adminUsername).ifPresent(user -> userService.deleteUser(user.getId()));
        userRepository.findByUsername(userUsername).ifPresent(user -> userService.deleteUser(user.getId()));
        userRepository.findByUsername(otherUserUsername).ifPresent(user -> userService.deleteUser(user.getId()));
    }

    @Test
    @DisplayName("全量替换 upsert 应单调递增版本，非法与缺失输入按语义状态拒绝")
    void testPutUpsertContract() throws Exception {
        mockMvc.perform(put(basePath(targetUserId) + "/" + applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorizationVersion").value(1));

        mockMvc.perform(put(basePath(targetUserId) + "/" + applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationVersion").value(2))
                .andExpect(jsonPath("$.apiPermissions[0]").value("iam:order:api"));

        mockMvc.perform(put("/iam/admin/users/999999999/application-authorizations/"
                        + applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(basePath(targetUserId) + "/999999999").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(basePath(targetUserId) + "/" + applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithMalformedDataGrantDocument())
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(basePath(targetUserId) + "/" + applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()).cookie(userSession).with(csrf()))
                .andExpect(status().isForbidden());

        log.info("upsert 契约断言完成：userId={}, applicationId={}", targetUserId, applicationId);
    }

    @Test
    @DisplayName("列表只含摘要字段，详情正确投影四类授权内容")
    void testListAndDetailProjection() throws Exception {
        mockMvc.perform(put(basePath(targetUserId) + "/" + applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get(basePath(targetUserId)).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].applicationId").value(applicationId))
                .andExpect(jsonPath("$[0].admitted").value(true))
                .andExpect(jsonPath("$[0].roles").doesNotExist())
                .andExpect(jsonPath("$[0].apiPermissions").doesNotExist())
                .andExpect(jsonPath("$[0].dataGrantDocument").doesNotExist());

        mockMvc.perform(get(basePath(targetUserId) + "/" + applicationId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("app-user"))
                .andExpect(jsonPath("$.pagePermissions[0]").value("iam:order:page"))
                .andExpect(jsonPath("$.apiPermissions[0]").value("iam:order:api"))
                .andExpect(jsonPath("$.dataGrantDocument.protocol").value("simple-data-permission"))
                .andExpect(jsonPath("$.dataGrantDocument.grants[0].resource").value("order"));
    }

    @Test
    @DisplayName("撤销留痕幂等且立即生效，重激活恢复有效并递增版本，跨用户详情 404")
    void testRevokeReactivateContract() throws Exception {
        mockMvc.perform(put(basePath(targetUserId) + "/" + applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete(basePath(targetUserId) + "/" + applicationId)
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(basePath(targetUserId) + "/" + applicationId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SimpleIamServerConstant.STATUS_INACTIVE))
                .andExpect(jsonPath("$.revokedAt").isNotEmpty());

        mockMvc.perform(delete(basePath(targetUserId) + "/" + applicationId)
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(basePath(otherUserId) + "/" + applicationId).cookie(adminSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(basePath(targetUserId) + "/" + applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(SimpleIamServerConstant.STATUS_ACTIVE))
                .andExpect(jsonPath("$.revokedAt").doesNotExist())
                .andExpect(jsonPath("$.authorizationVersion").value(2));

        log.info("撤销 / 重激活契约断言完成：userId={}, applicationId={}", targetUserId, applicationId);
    }

    @Test
    @DisplayName("码不在清单内 400，未登记清单应用 404，manifest 字段按清单真值下发")
    void testManifestEnforcement() throws Exception {
        mockMvc.perform(put(basePath(targetUserId) + "/" + applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"admitted\":true,\"roles\":[\"not-in-manifest\"],"
                                + "\"pagePermissions\":[],\"apiPermissions\":[],\"dataGrantDocument\":null}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());

        Long bareApplicationId = createApplication(
                "apa-bare-" + suffix, "apa-bare-" + suffix + "-web");
        try {
            mockMvc.perform(put(basePath(targetUserId) + "/" + bareApplicationId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody()).cookie(adminSession).with(csrf()))
                    .andExpect(status().isNotFound());
        } finally {
            trustedApplicationService.deleteApplication(bareApplicationId);
        }

        mockMvc.perform(put(basePath(targetUserId) + "/" + applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.manifestVersion").value("1"));

        log.info("清单执法断言完成：userId={}, applicationId={}", targetUserId, applicationId);
    }

    @Test
    @DisplayName("平台管理员无授权行时解析层特权合成清单全量，摘除角色即失效，普通用户不兜底")
    void testPlatformAdminPrivilegeResolution() throws Exception {
        Instant now = Instant.now();
        Instant later = now.plusSeconds(3600);

        assertNull(applicationAuthorizationService.loadActiveContext(
                targetUserId, applicationId, now, later), "普通用户无授权行不得解析出授权");

        ApplicationAuthorizationContext privileged = applicationAuthorizationService.loadActiveContext(
                adminUserId, applicationId, now, later);
        assertNotNull(privileged, "平台管理员无授权行也应解析出特权授权");
        assertTrue(privileged.isAdmitted(), "特权上下文必须准入");
        assertEquals(Collections.singletonList("app-user"), privileged.getRoles(),
                "特权角色应为清单申报全量");
        assertEquals(Collections.singletonList("iam:order:page"), privileged.getPagePermissions(),
                "特权页面码应为清单申报全量");
        assertEquals(Collections.singletonList("iam:order:api"), privileged.getApiPermissions(),
                "特权接口码应为清单申报全量");
        assertNotNull(privileged.getDataGrantDocument(), "特权应合成数据授权文档");
        assertEquals(1, privileged.getDataGrantDocument().getGrants().size());
        assertEquals("order", privileged.getDataGrantDocument().getGrants().get(0).getResource());
        assertTrue(privileged.getDataGrantDocument().getGrants().get(0).isAll(),
                "特权数据授权必须为全量（all=true 无约束）");

        Long bareApplicationId = createApplication(
                "apa-bare-p-" + suffix, "apa-bare-p-" + suffix + "-web");
        try {
            ApplicationAuthorizationContext bareContext = applicationAuthorizationService.loadActiveContext(
                    adminUserId, bareApplicationId, now, later);
            assertNotNull(bareContext, "无清单应用对平台管理员也应解析出特权授权");
            assertTrue(bareContext.isAdmitted(), "无清单应用特权仍应准入（仅准入）");
            assertTrue(bareContext.getRoles().isEmpty(), "无清单应用特权角色应为空");
            assertTrue(bareContext.getPagePermissions().isEmpty(), "无清单应用特权页面码应为空");
            assertTrue(bareContext.getApiPermissions().isEmpty(), "无清单应用特权接口码应为空");
            assertNull(bareContext.getDataGrantDocument(), "无清单应用无数据授权");
            assertEquals("0", bareContext.getManifestVersion(), "无清单应用清单版本应标记为 0");
        } finally {
            trustedApplicationService.deleteApplication(bareApplicationId);
        }

        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.revokeRole(adminUserId, adminRole.getId());
        assertNull(applicationAuthorizationService.loadActiveContext(
                adminUserId, applicationId, now, later), "摘除 iam_admin 后特权必须立即失效（解析时现查角色）");

        log.info("平台管理员特权解析断言完成：userId={}, applicationId={}", adminUserId, applicationId);
    }

    @Test
    @DisplayName("授权摘要与详情按用户是否平台管理员下发 platformAdmin 标记")
    void testPlatformAdminFlagInResponses() throws Exception {
        mockMvc.perform(put(basePath(adminUserId) + "/" + applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(put(basePath(targetUserId) + "/" + applicationId).contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()).cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(get(basePath(adminUserId)).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].platformAdmin").value(true));

        mockMvc.perform(get(basePath(targetUserId)).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].platformAdmin").value(false));

        mockMvc.perform(get(basePath(adminUserId) + "/" + applicationId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformAdmin").value(true));

        log.info("platformAdmin 标记断言完成：adminUserId={}, targetUserId={}", adminUserId, targetUserId);
    }

    private Cookie loginSession(String username) throws Exception {
        MvcResult login = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = login.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        assertNotNull(session, "登录必须成功并建立会话：" + username);
        return session;
    }

    private Long createUser(String username) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Admin@1234");
        request.setDisplayName(username);
        return userService.createUser(request).getId();
    }

    private Long createApplication(String code, String oauthClientId) {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(oauthClientId);
        client.setClientName("Application Authorization Public Client");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://application-authorization.example.test/callback"));
        client.setScopes(Arrays.asList("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("none"));

        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(code);
        request.setApplicationName("Application Authorization Application");
        request.setInitialClient(client);
        return trustedApplicationService.createApplication(request).getApplication().getId();
    }

    private String basePath(Long userId) {
        return "/iam/admin/users/" + userId + "/application-authorizations";
    }

    private String validBody() {
        return "{\"admitted\":true,\"roles\":[\"app-user\"],\"pagePermissions\":[\"iam:order:page\"],"
                + "\"apiPermissions\":[\"iam:order:api\"],\"dataGrantDocument\":" + DATA_GRANT_DOCUMENT + "}";
    }

    private String bodyWithMalformedDataGrantDocument() {
        return "{\"admitted\":true,\"roles\":[],\"pagePermissions\":[],\"apiPermissions\":[],"
                + "\"dataGrantDocument\":{\"protocol\":\"simple-data-permission\",\"version\":\"1.0\","
                + "\"grants\":{\"not\":\"array\"}}}";
    }
}
