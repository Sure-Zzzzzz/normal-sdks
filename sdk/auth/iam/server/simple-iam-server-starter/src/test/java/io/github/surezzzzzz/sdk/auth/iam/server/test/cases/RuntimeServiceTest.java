package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamPasswordResetEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRefreshTokenFamilyEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 运行态服务集成测试：Session / Refresh Token Family / Password Reset
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
class RuntimeServiceTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "runtime-test-" + suffix;

    private IamUserEntity user;

    @Autowired
    private UserService userService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private RefreshTokenFamilyService refreshTokenFamilyService;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private DeploymentPasswordRecoveryService deploymentPasswordRecoveryService;

    @Autowired
    private SimpleIamServerProperties properties;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamSessionRepository sessionRepository;

    @Autowired
    private IamRefreshTokenFamilyRepository refreshTokenFamilyRepository;

    @Autowired
    private IamPasswordResetRepository passwordResetRepository;

    @Autowired
    private RedisTokenRepository redisTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("Runtime@1234");
        request.setDisplayName("运行态测试用户");
        user = userService.createUser(request);
    }

    @AfterEach
    void cleanup() {
        if (user == null) {
            return;
        }
        sessionRepository.findByUserId(user.getId()).forEach(session -> {
            redisTokenRepository.deleteSession(session.getId());
            sessionRepository.delete(session);
        });
        refreshTokenFamilyRepository.findByUserId(user.getId()).forEach(family -> {
            redisTokenRepository.deleteRefreshFamily(family.getId());
            refreshTokenFamilyRepository.delete(family);
        });
        passwordResetRepository.findByUserId(user.getId()).forEach(passwordResetRepository::delete);
        userRepository.findByUsername(username).ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("SessionService 应支持创建、读取、撤销会话")
    void testSessionLifecycle() {
        IamSessionEntity session = sessionService.createSession(
                user.getId(), username, "test-client", "127.0.0.1", "JUnit");

        assertNotNull(session.getId());
        assertEquals(username, sessionService.getSession(session.getId()).getUsername());

        sessionService.revokeSession(session.getId());
        assertEquals(0, sessionRepository.findById(session.getId()).get().getStatus());
        assertNull(redisTokenRepository.getSession(session.getId()));
    }

    @Test
    @DisplayName("RefreshTokenFamilyService 应支持创建、验证、轮换、复用检测")
    void testRefreshTokenFamilyLifecycle() {
        IamRefreshTokenFamilyEntity family = refreshTokenFamilyService.createFamily(
                user.getId(), username, null, "refresh-token-1");

        assertEquals(family.getId(), refreshTokenFamilyService.validateCurrentToken("refresh-token-1").getId());

        refreshTokenFamilyService.rotate(family.getId(), "refresh-token-2");
        assertEquals(family.getId(), refreshTokenFamilyService.validateCurrentToken("refresh-token-2").getId());

        assertThrows(SimpleIamServerException.class,
                () -> refreshTokenFamilyService.detectReuseAndRevoke("refresh-token-1"));
        assertEquals(0, refreshTokenFamilyRepository.findById(family.getId()).get().getStatus());
    }

    @Test
    @DisplayName("PasswordResetService 应生成一次性凭证并重置密码")
    void testPasswordResetLifecycle() {
        String token = passwordResetService.createResetToken(user.getId(), "admin");

        assertNotNull(passwordResetService.validateToken(token));
        passwordResetService.resetByToken(token, "Runtime@5678");

        IamUserEntity updated = userRepository.findById(user.getId()).get();
        assertTrue(passwordEncoder.matches("Runtime@5678", updated.getPasswordHash()));
        assertThrows(SimpleIamServerException.class, () -> passwordResetService.validateToken(token));
    }

    @Test
    @DisplayName("部署恢复码仅在服务端消费一次并撤销管理员全部会话")
    void deploymentRecoveryCodeIsConsumedOnceAndRevokesSessions() {
        String originalUsername = properties.getBootstrap().getUsername();
        String originalRecoveryCode = properties.getBootstrap().getRecoveryCode();
        String originalRecoveryPassword = properties.getBootstrap().getRecoveryPassword();
        String recoveryCode = "recovery-" + UUID.randomUUID();
        String recoveryPassword = "Recover@" + UUID.randomUUID();
        IamSessionEntity session = sessionService.createSession(
                user.getId(), username, "test-client", "127.0.0.1", "JUnit");
        try {
            properties.getBootstrap().setUsername(username);
            properties.getBootstrap().setRecoveryCode(recoveryCode);
            properties.getBootstrap().setRecoveryPassword(recoveryPassword);

            deploymentPasswordRecoveryService.recoverBootstrapAdministratorIfRequested(null);

            IamPasswordResetEntity credential = passwordResetRepository.findById(
                    SimpleIamServerConstant.DEPLOYMENT_RECOVERY_CREDENTIAL_ID).orElseThrow();
            assertEquals(SimpleIamServerConstant.STATUS_INACTIVE, credential.getStatus());
            assertNotNull(credential.getUsedAt());
            assertEquals(SimpleIamServerConstant.DEPLOYMENT_RECOVERY_REQUESTED_BY, credential.getRequestedBy());
            assertEquals(SimpleIamServerConstant.STATUS_INACTIVE,
                    sessionRepository.findById(session.getId()).orElseThrow().getStatus());
            assertNull(redisTokenRepository.getSession(session.getId()));
            String passwordHash = userRepository.findById(user.getId()).orElseThrow().getPasswordHash();
            assertTrue(passwordEncoder.matches(recoveryPassword, passwordHash));

            deploymentPasswordRecoveryService.recoverBootstrapAdministratorIfRequested(null);

            assertEquals(passwordHash, userRepository.findById(user.getId()).orElseThrow().getPasswordHash());
        } finally {
            passwordResetRepository.findById(SimpleIamServerConstant.DEPLOYMENT_RECOVERY_CREDENTIAL_ID)
                    .ifPresent(passwordResetRepository::delete);
            properties.getBootstrap().setUsername(originalUsername);
            properties.getBootstrap().setRecoveryCode(originalRecoveryCode);
            properties.getBootstrap().setRecoveryPassword(originalRecoveryPassword);
        }
    }
}
