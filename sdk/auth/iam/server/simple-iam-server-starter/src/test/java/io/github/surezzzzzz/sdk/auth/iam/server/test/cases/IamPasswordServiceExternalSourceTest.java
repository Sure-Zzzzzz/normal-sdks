package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 外部身份源用户密码操作的服务层防线测试。
 *
 * <p>LDAP / OIDC 用户的密码由外部系统管理：自助改密必须拒绝
 * （PASSWORD_CHANGE_NOT_ALLOWED）；管理员重置也必须拒绝——若允许重置，
 * 用户会被置须改密标记但改密又被拒，形成永久锁死。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class IamPasswordServiceExternalSourceTest {

    private final String username = "ext-source-" + UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private UserService userService;
    @Autowired
    private IamUserRepository userRepository;

    @AfterEach
    void cleanup() {
        userRepository.findByUsername(username)
                .ifPresent(user -> userService.deleteUser(user.getId()));
    }

    private IamUserEntity createExternalSourceUser() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Admin@1234");
        request.setDisplayName(username);
        request.setEmail(username + "@example.test");
        IamUserEntity user = userService.createUser(request);
        user.setIdentitySource("ldap");
        // 真实外部源用户由 auto-provision 建号，初始即无须改密标记（FALSE）；
        // createUser 置 TRUE 是管理员建号流程的行为，不能带进本测试的前置
        user.setMustChangePassword(Boolean.FALSE);
        userRepository.save(user);
        return user;
    }

    @Test
    @DisplayName("外部身份源用户自助改密被拒（AUTH_010），密码哈希不变")
    void changePasswordRejectsExternalSourceUser() {
        IamUserEntity user = createExternalSourceUser();
        String passwordHashBefore = user.getPasswordHash();

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> userService.changePassword(user.getId(), "Admin@1234", "NewPass@1234", null));

        assertEquals(ErrorCode.PASSWORD_CHANGE_NOT_ALLOWED, exception.getErrorCode());
        IamUserEntity after = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(passwordHashBefore, after.getPasswordHash(), "被拒路径不得改动密码哈希");
        log.info("✓ 外部身份源改密拒绝：username={}", username);
    }

    @Test
    @DisplayName("外部身份源用户被管理员重置密码直接拒绝（防置标记后锁死）")
    void resetPasswordRejectsExternalSourceUser() {
        IamUserEntity user = createExternalSourceUser();

        SimpleIamServerException exception = assertThrows(SimpleIamServerException.class,
                () -> userService.resetPassword(user.getId(), "NewPass@1234", "ext-admin"));

        assertEquals(ErrorCode.PASSWORD_CHANGE_NOT_ALLOWED, exception.getErrorCode());
        IamUserEntity after = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(!Boolean.TRUE.equals(after.getMustChangePassword()),
                "重置被拒时不得置须改密标记");
        log.info("✓ 外部身份源重置拒绝（死锁防护）：username={}", username);
    }
}
