package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPermissionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamPermissionRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.Cookie;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理面 iam:*:api 权限码消费测试（DESIGN.admin-permission-code-enforcement.md）。
 *
 * <p>覆盖：管理端点权限码声明全量门禁、权限码解绑后按域拒绝、
 * 内置角色权限绑定补齐、/me 输出权限码。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminApiPermissionEnforcementTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "perm-admin-" + suffix;
    private Cookie adminSession;
    private Long adminUserId;
    private IamRoleEntity adminRole;
    private IamPermissionEntity roleApiPermission;
    private boolean roleApiRevoked;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private IamUserRepository userRepository;
    @Autowired
    private IamPermissionRepository permissionRepository;
    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @BeforeEach
    void prepare() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(adminUsername);
        request.setPassword("Admin@1234");
        request.setDisplayName(adminUsername);
        adminUserId = userService.createUser(request).getId();
        adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(adminUserId, adminRole.getId());
        roleApiPermission = permissionRepository
                .findByCode(SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API)
                .orElseThrow(() -> new AssertionError("内置权限码未 seed：iam:role:api"));
        adminSession = loginSession();
    }

    @AfterEach
    void cleanup() {
        if (roleApiRevoked) {
            roleService.assignPermission(adminRole.getId(), roleApiPermission.getId());
            roleApiRevoked = false;
        }
        userRepository.findByUsername(adminUsername)
                .ifPresent(user -> userService.deleteUser(user.getId()));
    }

    @Test
    @DisplayName("全部 /iam/admin/** 端点必须声明 iam:*:api 权限码（防漏标门禁）")
    void allAdminEndpointsDeclarePermissionCode() {
        List<String> missing = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getKey().getPatternValues().stream()
                        .anyMatch(pattern -> pattern.startsWith("/iam/admin/")))
                .map(entry -> {
                    HandlerMethod handler = entry.getValue();
                    PreAuthorize declared = handler.getMethodAnnotation(PreAuthorize.class);
                    if (declared == null) {
                        declared = handler.getBeanType().getAnnotation(PreAuthorize.class);
                    }
                    boolean valid = declared != null
                            && declared.value().contains("hasAuthority('iam:");
                    return valid ? null
                            : handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
                })
                .filter(result -> result != null)
                .sorted()
                .collect(Collectors.toList());
        assertTrue(missing.isEmpty(), "管理端点缺少 iam:*:api 权限码声明：" + missing);
        log.info("管理端点权限码门禁扫描通过");
    }

    @Test
    @DisplayName("解绑 iam:role:api 后角色域 403、用户域 200，恢复绑定后角色域恢复")
    void permissionCodeGrantsScopedAccess() throws Exception {
        roleService.revokePermission(adminRole.getId(), roleApiPermission.getId());
        roleApiRevoked = true;

        Cookie refreshedSession = loginSession();

        mockMvc.perform(get("/iam/admin/roles").cookie(refreshedSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/iam/admin/users").cookie(refreshedSession))
                .andExpect(status().isOk());

        roleService.assignPermission(adminRole.getId(), roleApiPermission.getId());
        roleApiRevoked = false;

        mockMvc.perform(get("/iam/admin/roles").cookie(adminSession))
                .andExpect(status().isOk());
        log.info("权限码按域拒绝断言完成：roleId={}", adminRole.getId());
    }

    @Test
    @DisplayName("启动引导后内置 iam_admin 角色绑定全部内置权限码")
    void seedBindsAllBuiltInPermissionsToAdminRole() {
        Set<String> codes = roleService.getUserPermissionCodes(adminUserId).stream()
                .collect(Collectors.toSet());
        List<String> expected = Arrays.asList(
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_PAGE,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_ROLE_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_SESSION_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_PERMISSION_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_MESSAGE_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_TRUSTED_APPLICATION_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_DEPARTMENT_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_GROUP_API,
                SimpleIamServerConstant.BUILT_IN_PERMISSION_DATA_ALL);
        for (String code : expected) {
            assertTrue(codes.contains(code), "iam_admin 角色缺少内置权限码：" + code);
        }
        log.info("内置权限绑定断言完成：codes={}", codes.size());
    }

    @Test
    @DisplayName("/me authorities 输出 ROLE_ 与权限码（前端菜单过滤前置）")
    void meExposesPermissionCodes() throws Exception {
        mockMvc.perform(get("/iam/web/auth/me").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorities")
                        .value(org.hamcrest.Matchers.hasItem("ROLE_" + SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN)))
                .andExpect(jsonPath("$.authorities")
                        .value(org.hamcrest.Matchers.hasItem(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API)))
                .andExpect(jsonPath("$.authorities")
                        .value(org.hamcrest.Matchers.hasItem(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_PAGE)));
    }

    private Cookie loginSession() throws Exception {
        MvcResult login = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + adminUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = login.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        assertNotNull(session, "登录必须成功并建立会话：" + adminUsername);
        return session;
    }
}
