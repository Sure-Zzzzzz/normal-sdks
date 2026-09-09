package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.CreateRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamApplicationPermissionManifestService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.TrustedApplicationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色应用授权规则管理 API 测试。
 *
 * <p>契约：PUT 固定地址 upsert 统一返 200；无规则 GET 返 404（合法态）；
 * DELETE 返 204 且幂等；权限码必须落在应用清单范围内。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminRoleAuthorizationRuleApiTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "rule-admin-" + suffix;
    private final String userUsername = "rule-user-" + suffix;
    private final String applicationCode = "rule-app-" + suffix;
    private final String bareApplicationCode = "rule-bare-" + suffix;
    private Cookie adminSession;
    private Cookie userSession;
    private Long roleId;
    private Long applicationId;
    private Long bareApplicationId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrustedApplicationService trustedApplicationService;

    @Autowired
    private IamApplicationPermissionManifestService manifestService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IamUserRepository userRepository;

    @BeforeEach
    void prepare() throws Exception {
        CreateUserRequest adminRequest = new CreateUserRequest();
        adminRequest.setUsername(adminUsername);
        adminRequest.setPassword("Admin@1234");
        adminRequest.setDisplayName(adminUsername);
        Long adminId = userService.createUser(adminRequest).getId();
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(adminId, adminRole.getId());
        adminSession = loginSession(adminUsername);

        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(userUsername);
        userRequest.setPassword("Admin@1234");
        userRequest.setDisplayName(userUsername);
        userService.createUser(userRequest);
        userSession = loginSession(userUsername);

        CreateRoleRequest roleRequest = new CreateRoleRequest();
        roleRequest.setCode("rule-role-" + suffix);
        roleRequest.setName("规则测试角色");
        roleId = roleService.createRole(roleRequest).getId();

        applicationId = createApplication(applicationCode);
        registerManifest(applicationId);
        bareApplicationId = createApplication(bareApplicationCode);
    }

    @AfterEach
    void cleanup() {
        trustedApplicationService.deleteApplication(applicationId);
        trustedApplicationService.deleteApplication(bareApplicationId);
        roleService.deleteRole(roleId);
        userRepository.findByUsername(adminUsername).ifPresent(user -> userService.deleteUser(user.getId()));
        userRepository.findByUsername(userUsername).ifPresent(user -> userService.deleteUser(user.getId()));
    }

    @Test
    @DisplayName("PUT 固定地址 upsert：创建与替换均返 200 并回显，非法输入与越界码 400")
    void testPutRuleContract() throws Exception {
        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagePermissions\":[\"rule:p1\"],\"apiPermissions\":[\"rule:a1\"]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(roleId))
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.pagePermissions.length()").value(1))
                .andExpect(jsonPath("$.pagePermissions[0]").value("rule:p1"))
                .andExpect(jsonPath("$.apiPermissions[0]").value("rule:a1"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagePermissions\":[\"rule:p2\"],\"apiPermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagePermissions[0]").value("rule:p2"))
                .andExpect(jsonPath("$.apiPermissions.length()").value(0));

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagePermissions\":[\"rule:p1\",\"rule:not-declared\"],\"apiPermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagePermissions\":[],\"apiPermissions\":[\"rule:not-declared\"]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiPermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagePermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagePermissions\":[],\"apiPermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/iam/admin/roles/999999999/authorization-rules/" + applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagePermissions\":[],\"apiPermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/iam/admin/roles/" + roleId + "/authorization-rules/999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagePermissions\":[],\"apiPermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/iam/admin/roles/" + roleId + "/authorization-rules/" + bareApplicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagePermissions\":[],\"apiPermissions\":[]}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pagePermissions\":[],\"apiPermissions\":[]}")
                        .cookie(userSession).with(csrf()))
                .andExpect(status().isForbidden());

        log.info("规则 PUT 契约断言完成：roleId={}, applicationId={}", roleId, applicationId);
    }

    @Test
    @DisplayName("GET 无规则 404，登记后回显，删除后回落 404")
    void testGetRuleContract() throws Exception {
        mockMvc.perform(get(basePath()).cookie(adminSession))
                .andExpect(status().isNotFound());

        putRule("{\"pagePermissions\":[\"rule:p1\"],\"apiPermissions\":[\"rule:a1\"]}");

        mockMvc.perform(get(basePath()).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleId").value(roleId))
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.pagePermissions[0]").value("rule:p1"))
                .andExpect(jsonPath("$.apiPermissions[0]").value("rule:a1"));

        mockMvc.perform(delete(basePath()).cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(basePath()).cookie(adminSession))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(basePath()).cookie(userSession))
                .andExpect(status().isForbidden());

        log.info("规则 GET 契约断言完成：roleId={}, applicationId={}", roleId, applicationId);
    }

    @Test
    @DisplayName("DELETE 幂等返 204")
    void testDeleteRuleContract() throws Exception {
        mockMvc.perform(delete(basePath()).cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        putRule("{\"pagePermissions\":[\"rule:p1\"],\"apiPermissions\":[]}");

        mockMvc.perform(delete(basePath()).cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete(basePath()).cookie(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete(basePath()).cookie(userSession).with(csrf()))
                .andExpect(status().isForbidden());

        log.info("规则 DELETE 幂等契约断言完成：roleId={}, applicationId={}", roleId, applicationId);
    }

    private void putRule(String body) throws Exception {
        mockMvc.perform(put(basePath()).contentType(MediaType.APPLICATION_JSON)
                        .content(body).cookie(adminSession).with(csrf()))
                .andExpect(status().isOk());
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

    private Long createApplication(String code) {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(code + "-web");
        client.setClientName("Rule Test Public Client");
        client.setClientType("PUBLIC");
        client.setRequireConsent(false);
        client.setRedirectUris(Collections.singletonList("https://rule-test.example.test/callback"));
        client.setScopes(Arrays.asList("openid", "profile"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("none"));

        CreateTrustedApplicationRequest request = new CreateTrustedApplicationRequest();
        request.setApplicationCode(code);
        request.setApplicationName("Rule Test Application");
        request.setInitialClient(client);
        return trustedApplicationService.createApplication(request).getApplication().getId();
    }

    private void registerManifest(Long applicationId) {
        PutApplicationPermissionManifestRequest manifest = new PutApplicationPermissionManifestRequest();
        manifest.setRoles(Collections.singletonList("rule-admin"));
        manifest.setPagePermissions(Arrays.asList("rule:p1", "rule:p2"));
        manifest.setApiPermissions(Arrays.asList("rule:a1", "rule:a2"));
        manifest.setDataResources(Collections.emptyList());
        manifestService.putManifest(applicationId, manifest);
    }

    private String basePath() {
        return "/iam/admin/roles/" + roleId + "/authorization-rules/" + applicationId;
    }
}
