package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.CreateRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理台入口门（Order(4) chain）权限语义测试（DESIGN.admin-console-rbac-closure.md）。
 *
 * <p>门 = iam_admin 角色或任一页面权限码（ADMIN_CONSOLE_ENTRANCE_AUTHORITIES）。
 * 覆盖：持页面权限码的委派用户过门且方法级 @PreAuthorize 仍逐端点拦截、
 * 无权限码用户被门拦、纯 api 权限码不进门、未认证 401、admin 回归。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminConsoleAccessChainTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> createdRoleIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();
    private String adminUsername;
    private Cookie adminSession;
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

    @BeforeEach
    void prepare() throws Exception {
        adminUsername = "console-admin-" + suffix;
        Long adminUserId = createUser(adminUsername);
        roleService.assignRole(adminUserId, roleService
                .getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).getId());
        adminSession = loginSession(adminUsername);
    }

    @AfterEach
    void cleanup() {
        for (Long roleId : createdRoleIds) {
            roleService.deleteRole(roleId);
        }
        createdRoleIds.clear();
        for (Long userId : createdUserIds) {
            userRepository.findById(userId)
                    .ifPresent(user -> userService.deleteUser(userId));
        }
        createdUserIds.clear();
    }

    @Test
    @DisplayName("持 iam:user:page+iam:user:api 的委派用户：过门访问用户域 200，角色域与仪表盘仍 403")
    void pagePermissionHolderPassesEntranceButMethodSecurityStillEnforces() throws Exception {
        Long roleId = createRole("console-delegate-" + suffix);
        roleService.assignPermission(roleId, permissionId(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_PAGE));
        roleService.assignPermission(roleId, permissionId(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API));

        String username = "console-delegate-user-" + suffix;
        Long userId = createUser(username);
        roleService.assignRole(userId, roleId);
        Cookie session = loginSession(username);

        mockMvc.perform(get("/iam/admin/users").cookie(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/iam/admin/roles").cookie(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/iam/admin/dashboard").cookie(session))
                .andExpect(status().isForbidden());
        log.info("委派用户门语义断言完成：roleId={}", roleId);
    }

    @Test
    @DisplayName("无任何 iam 权限码的普通用户被门拦 403；未认证请求 401")
    void plainUserWithoutPermissionsIsRejected() throws Exception {
        String username = "console-plain-" + suffix;
        createUser(username);
        Cookie session = loginSession(username);

        mockMvc.perform(get("/iam/admin/users").cookie(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/iam/admin/dashboard").cookie(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/iam/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("仅持纯 api 权限码（无页面权限码）的用户不进门 403")
    void apiOnlyUserCannotEnterConsole() throws Exception {
        Long roleId = createRole("console-api-only-" + suffix);
        roleService.assignPermission(roleId, permissionId(SimpleIamServerConstant.BUILT_IN_PERMISSION_USER_API));

        String username = "console-api-only-user-" + suffix;
        Long userId = createUser(username);
        roleService.assignRole(userId, roleId);
        Cookie session = loginSession(username);

        mockMvc.perform(get("/iam/admin/users").cookie(session))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("iam_admin 角色回归：门放宽后仍正常访问")
    void adminRoleRegressionStillPasses() throws Exception {
        mockMvc.perform(get("/iam/admin/users").cookie(adminSession))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("门数组必须覆盖全部内置 page 权限码（防新增页面码漏改门）")
    void entranceAuthoritiesCoverAllBuiltInPagePermissions() {
        List<String> entrance = Arrays.asList(SimpleIamServerConstant.ADMIN_CONSOLE_ENTRANCE_AUTHORITIES);
        List<String> missing = permissionRepository.findAll().stream()
                .filter(permission -> "page".equals(permission.getType())
                        && SimpleIamServerConstant.STATUS_ACTIVE == permission.getBuiltIn())
                .map(permission -> permission.getCode())
                .filter(code -> !entrance.contains(code))
                .sorted()
                .collect(Collectors.toList());
        assertTrue(missing.isEmpty(),
                "以下内置 page 权限码未进入管理台入口门（持码用户进不了台）：" + missing);
    }

    private Long createUser(String username) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Admin@1234");
        request.setDisplayName(username);
        Long userId = userService.createUser(request).getId();
        createdUserIds.add(userId);
        return userId;
    }

    private Long createRole(String code) {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setCode(code);
        request.setName(code);
        request.setDescription("入口门语义测试角色");
        Long roleId = roleService.createRole(request).getId();
        createdRoleIds.add(roleId);
        return roleId;
    }

    private Long permissionId(String code) {
        return permissionRepository.findByCode(code)
                .orElseThrow(() -> new AssertionError("内置权限码未 seed：" + code))
                .getId();
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
}
