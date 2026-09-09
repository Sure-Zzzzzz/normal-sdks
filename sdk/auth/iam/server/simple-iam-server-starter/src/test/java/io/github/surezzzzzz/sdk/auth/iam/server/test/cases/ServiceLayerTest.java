package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.UpdateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRoleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.AuthenticationService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.RoleService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务层集成测试：UserService / RoleService / AuthenticationService / Bootstrap
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class ServiceLayerTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String testUsername = "svc-test-" + suffix;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamRoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        userRepository.findByUsername(testUsername)
                .ifPresent(user -> userRepository.delete(user));
    }

    // ==================== UserService ====================

    @Test
    @DisplayName("createUser 应成功创建用户并 BCrypt 加密密码")
    void testCreateUser() {
        CreateUserRequest req = createReq(testUsername, "Test@1234");
        IamUserEntity user = userService.createUser(req);

        assertNotNull(user.getId());
        assertEquals(testUsername, user.getUsername());
        assertTrue(passwordEncoder.matches("Test@1234", user.getPasswordHash()));
        assertEquals(1, user.getStatus());
    }

    @Test
    @DisplayName("createUser 重复用户名应抛异常")
    void testCreateUserDuplicate() {
        userService.createUser(createReq(testUsername, "Test@1234"));
        assertThrows(SimpleIamServerException.class,
                () -> userService.createUser(createReq(testUsername, "Test@5678")));
    }

    @Test
    @DisplayName("updateUser 应更新 displayName / email")
    void testUpdateUser() {
        IamUserEntity user = userService.createUser(createReq(testUsername, "Test@1234"));

        UpdateUserRequest req = new UpdateUserRequest();
        req.setDisplayName("新名字");
        req.setEmail("test@example.com");

        IamUserEntity updated = userService.updateUser(user.getId(), req);
        assertEquals("新名字", updated.getDisplayName());
        assertEquals("test@example.com", updated.getEmail());
    }

    @Test
    @DisplayName("enableUser / disableUser 应切换状态")
    void testEnableDisableUser() {
        IamUserEntity user = userService.createUser(createReq(testUsername, "Test@1234"));

        userService.disableUser(user.getId());
        assertEquals(0, userRepository.findById(user.getId()).get().getStatus());

        userService.enableUser(user.getId());
        assertEquals(1, userRepository.findById(user.getId()).get().getStatus());
    }

    @Test
    @DisplayName("resetPassword 应更换密码并清零失败计数")
    void testResetPassword() {
        IamUserEntity user = userService.createUser(createReq(testUsername, "Test@1234"));

        userService.resetPassword(user.getId(), "NewPass@9999", "admin");

        IamUserEntity updated = userRepository.findById(user.getId()).get();
        assertTrue(passwordEncoder.matches("NewPass@9999", updated.getPasswordHash()));
        assertEquals(0, updated.getFailedLoginCount());
    }

    // ==================== RoleService ====================

    @Test
    @DisplayName("assignRole / getUserRoles 应正确关联角色")
    void testAssignRole() {
        IamUserEntity user = userService.createUser(createReq(testUsername, "Test@1234"));
        IamRoleEntity adminRole = roleService.getByCode("iam_admin");

        roleService.assignRole(user.getId(), adminRole.getId());

        List<IamRoleEntity> roles = roleService.getUserRoles(user.getId());
        assertTrue(roles.stream().anyMatch(r -> "iam_admin".equals(r.getCode())));
    }

    @Test
    @DisplayName("assignRole 重复分配不应报错")
    void testAssignRoleIdempotent() {
        IamUserEntity user = userService.createUser(createReq(testUsername, "Test@1234"));
        IamRoleEntity adminRole = roleService.getByCode("iam_admin");

        roleService.assignRole(user.getId(), adminRole.getId());
        assertDoesNotThrow(() -> roleService.assignRole(user.getId(), adminRole.getId()));

        assertEquals(1, roleService.getUserRoles(user.getId()).size());
    }

    @Test
    @DisplayName("revokeRole 应移除角色关联")
    void testRevokeRole() {
        IamUserEntity user = userService.createUser(createReq(testUsername, "Test@1234"));
        IamRoleEntity adminRole = roleService.getByCode("iam_admin");

        roleService.assignRole(user.getId(), adminRole.getId());
        roleService.revokeRole(user.getId(), adminRole.getId());

        assertTrue(roleService.getUserRoles(user.getId()).isEmpty());
    }

    // ==================== AuthenticationService ====================

    @Test
    @DisplayName("authenticate 正确密码应返回用户实体")
    void testAuthenticateSuccess() {
        userService.createUser(createReq(testUsername, "Test@1234"));

        IamUserEntity result = authenticationService.authenticate(testUsername, "Test@1234");
        assertEquals(testUsername, result.getUsername());
        assertNotNull(result.getLastLoginAt());
    }

    @Test
    @DisplayName("authenticate 错误密码应抛异常")
    void testAuthenticateWrongPassword() {
        userService.createUser(createReq(testUsername, "Test@1234"));

        assertThrows(SimpleIamServerException.class,
                () -> authenticationService.authenticate(testUsername, "Wrong@0000"));
    }

    @Test
    @DisplayName("authenticate 不存在的用户应抛异常")
    void testAuthenticateUnknownUser() {
        assertThrows(SimpleIamServerException.class,
                () -> authenticationService.authenticate("no-such-user-" + suffix, "Any@1234"));
    }

    // ==================== Bootstrap ====================

    @Test
    @DisplayName("iam_admin 角色应在应用启动后存在（bootstrap 自动创建）")
    void testBootstrapAdminRoleExists() {
        assertTrue(roleRepository.existsByCode("iam_admin"));
    }

    private CreateUserRequest createReq(String username, String password) {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setDisplayName("测试用户 " + suffix);
        return req;
    }
}
