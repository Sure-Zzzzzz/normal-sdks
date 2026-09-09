package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.CreateRoleRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.authorization.request.PutApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.manifest.request.PutApplicationPermissionManifestRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.resource.request.CreateResourceVerificationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationClientRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.request.CreateTrustedApplicationRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.event.*;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IAM 管理面事件发布测试（DESIGN.iam-audit-event-predefine.md）。
 *
 * <p>覆盖用户创建 / 禁用（联动会话批量撤销）、角色分配、验证客户端密钥轮换、
 * 应用授权授予 / 替换 / 撤销六条管理面链路；service 直调时无认证上下文，
 * operator 解析必须降级为 null 而非报错。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@Import(IamAdminActionEventPublishTest.AdminEventCapture.class)
class IamAdminActionEventPublishTest {

    private static final String PASSWORD = "Admin@1234";
    private static final long AWAIT_TIMEOUT_MS = 15_000;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "admin-event-" + suffix;
    private final String roleCode = "admin-event-role-" + suffix;
    private final String applicationCode = "admin-event-app-" + suffix;
    private final String oauthClientId = "admin-event-client-" + suffix;
    private final String oauthClientSecret = "admin-event-secret-" + suffix;
    private final String verificationClientId = "admin-event-vc-" + suffix;
    private final String redirectUri = "https://admin-event.example.test/callback";

    @Autowired
    private UserService userService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private IamUserRepository userRepository;
    @Autowired
    private IamApplicationAuthorizationRepository applicationAuthorizationRepository;
    @Autowired
    private TrustedApplicationService trustedApplicationService;
    @Autowired
    private IamResourceVerificationClientService verificationClientService;
    @Autowired
    private IamApplicationAuthorizationAdminService authorizationAdminService;
    @Autowired
    private IamApplicationPermissionManifestService manifestService;
    @Autowired
    private RegisteredClientRepository registeredClientRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AdminEventCapture eventCapture;

    private Long userId;
    private Long applicationId;

    @BeforeEach
    void prepare() {
        eventCapture.reset();
    }

    @AfterEach
    void cleanup() {
        RegisteredClient client = registeredClientRepository.findByClientId(oauthClientId);
        if (client != null) {
            jdbcTemplate.update("DELETE FROM oauth2_authorization_consent WHERE registered_client_id = ?", client.getId());
            jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id = ?", client.getId());
            jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE id = ?", client.getId());
        }
        jdbcTemplate.update("DELETE FROM iam_resource_verification_client WHERE client_id = ?", verificationClientId);
        IamUserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            if (applicationId != null) {
                applicationAuthorizationRepository.findByUserIdAndApplicationId(user.getId(), applicationId)
                        .ifPresent(applicationAuthorizationRepository::delete);
            }
            jdbcTemplate.update("DELETE FROM iam_user_role WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM iam_session WHERE user_id = ?", user.getId());
            userService.deleteUser(user.getId());
        }
        jdbcTemplate.update("DELETE FROM iam_user_role WHERE role_id IN (SELECT id FROM iam_role WHERE code = ?)", roleCode);
        jdbcTemplate.update("DELETE FROM iam_role_permission WHERE role_id IN (SELECT id FROM iam_role WHERE code = ?)", roleCode);
        jdbcTemplate.update("DELETE FROM iam_role WHERE code = ?", roleCode);
        if (applicationId != null) {
            trustedApplicationService.deleteApplication(applicationId);
        }
    }

    @Test
    @DisplayName("创建用户发布 AdminActionEvent：CREATED / USER")
    void createUserPublishesCreatedEvent() throws Exception {
        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(username);
        userRequest.setPassword(PASSWORD);
        userRequest.setDisplayName(username);
        userRequest.setEmail(username + "@example.test");
        Long createdUserId = userService.createUser(userRequest).getId();

        AdminActionEvent event = awaitEvent(AdminActionEvent.class,
                item -> item.getAction() == AdminActionType.CREATED
                        && item.getSubjectType() == AdminSubjectType.USER
                        && String.valueOf(createdUserId).equals(item.getSubjectId()));
        assertNotNull(event);
        assertEquals(username, event.getSubjectName());
        assertNull(event.getOperator(), "service 直调无认证上下文，operator 必须降级为 null");
        userId = createdUserId;
        log.info("USER CREATED 断言完成：username={}, subjectId={}", username, createdUserId);
    }

    @Test
    @DisplayName("禁用用户发布 DISABLED 与 SESSION REVOKED（USER_LIFECYCLE 批量）")
    void disableUserPublishesLifecycleEvents() throws Exception {
        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(username);
        userRequest.setPassword(PASSWORD);
        userRequest.setDisplayName(username);
        userRequest.setEmail(username + "@example.test");
        Long createdUserId = userService.createUser(userRequest).getId();
        userId = createdUserId;

        userService.disableUser(createdUserId);

        AdminActionEvent disabledEvent = awaitEvent(AdminActionEvent.class,
                item -> item.getAction() == AdminActionType.DISABLED
                        && item.getSubjectType() == AdminSubjectType.USER
                        && String.valueOf(createdUserId).equals(item.getSubjectId()));
        assertNotNull(disabledEvent);
        assertEquals(username, disabledEvent.getSubjectName());

        SessionLifecycleEvent sessionEvent = awaitEvent(SessionLifecycleEvent.class,
                item -> item.getEventType() == SessionEventType.REVOKED
                        && item.getCause() == SessionEventCause.USER_LIFECYCLE
                        && createdUserId.equals(item.getUserId()));
        assertNotNull(sessionEvent);
        assertNull(sessionEvent.getSessionId(), "批量吊销事件 sessionId 必须为空");
        assertNotNull(sessionEvent.getRevokedCount());
        log.info("USER DISABLED / SESSION REVOKED（USER_LIFECYCLE）断言完成：username={}, revokedCount={}",
                username, sessionEvent.getRevokedCount());
    }

    @Test
    @DisplayName("分配角色发布 AdminActionEvent：ASSIGNED / USER / detail 携带 roleId")
    void assignRolePublishesEvent() throws Exception {
        CreateRoleRequest roleRequest = new CreateRoleRequest();
        roleRequest.setCode(roleCode);
        roleRequest.setName("管理面事件测试角色");
        Long roleId = roleService.createRole(roleRequest).getId();

        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(username);
        userRequest.setPassword(PASSWORD);
        userRequest.setDisplayName(username);
        userRequest.setEmail(username + "@example.test");
        userId = userService.createUser(userRequest).getId();

        roleService.assignRole(userId, roleId);

        AdminActionEvent event = awaitEvent(AdminActionEvent.class,
                item -> item.getAction() == AdminActionType.ASSIGNED
                        && item.getSubjectType() == AdminSubjectType.USER
                        && String.valueOf(userId).equals(item.getSubjectId()));
        assertNotNull(event);
        assertEquals(username, event.getSubjectName());
        assertNotNull(event.getDetail());
        assertTrue(event.getDetail().contains("roleId=" + roleId));
        log.info("ROLE ASSIGNED 断言完成：username={}, detail={}", username, event.getDetail());
    }

    @Test
    @DisplayName("轮换验证客户端 secret 发布 AdminActionEvent：SECRET_ROTATED / VERIFICATION_CLIENT")
    void rotateVerificationSecretPublishesEvent() throws Exception {
        applicationId = createTrustedApplication();

        CreateResourceVerificationClientRequest verificationRequest = new CreateResourceVerificationClientRequest();
        verificationRequest.setClientId(verificationClientId);
        verificationClientService.createClient(applicationId, verificationRequest);

        verificationClientService.rotateSecret(applicationId, verificationClientId);

        AdminActionEvent rotateEvent = awaitEvent(AdminActionEvent.class,
                item -> item.getAction() == AdminActionType.SECRET_ROTATED
                        && item.getSubjectType() == AdminSubjectType.VERIFICATION_CLIENT
                        && verificationClientId.equals(item.getSubjectId()));
        assertNotNull(rotateEvent);
        assertEquals(verificationClientId, rotateEvent.getSubjectName());
        assertTrue(rotateEvent.getDetail().contains("applicationId=" + applicationId));

        AdminActionEvent createdEvent = awaitEvent(AdminActionEvent.class,
                item -> item.getAction() == AdminActionType.CREATED
                        && item.getSubjectType() == AdminSubjectType.VERIFICATION_CLIENT
                        && verificationClientId.equals(item.getSubjectId()));
        assertNotNull(createdEvent, "创建事件必须先于轮换事件发布");
        log.info("VERIFICATION_CLIENT SECRET_ROTATED 断言完成：clientId={}", verificationClientId);
    }

    @Test
    @DisplayName("应用授权授予 / 替换 / 撤销发布 GRANTED / REPLACED / REVOKED")
    void putAndRevokeAuthorizationPublishesEvents() throws Exception {
        applicationId = createTrustedApplication();

        CreateUserRequest userRequest = new CreateUserRequest();
        userRequest.setUsername(username);
        userRequest.setPassword(PASSWORD);
        userRequest.setDisplayName(username);
        userRequest.setEmail(username + "@example.test");
        userId = userService.createUser(userRequest).getId();

        authorizationAdminService.putAuthorization(userId, applicationId, authorizationRequest());

        AdminActionEvent grantedEvent = awaitEvent(AdminActionEvent.class,
                item -> item.getAction() == AdminActionType.GRANTED
                        && item.getSubjectType() == AdminSubjectType.APPLICATION_AUTHORIZATION
                        && String.valueOf(userId).equals(item.getSubjectId()));
        assertNotNull(grantedEvent);
        assertTrue(grantedEvent.getDetail().contains("applicationId=" + applicationId));
        assertTrue(grantedEvent.getDetail().contains("authorizationVersion=1"));

        authorizationAdminService.putAuthorization(userId, applicationId, authorizationRequest());

        AdminActionEvent replacedEvent = awaitEvent(AdminActionEvent.class,
                item -> item.getAction() == AdminActionType.REPLACED
                        && item.getSubjectType() == AdminSubjectType.APPLICATION_AUTHORIZATION
                        && String.valueOf(userId).equals(item.getSubjectId()));
        assertNotNull(replacedEvent);
        assertTrue(replacedEvent.getDetail().contains("authorizationVersion=2"));

        authorizationAdminService.revokeAuthorization(userId, applicationId);

        AdminActionEvent revokedEvent = awaitEvent(AdminActionEvent.class,
                item -> item.getAction() == AdminActionType.REVOKED
                        && item.getSubjectType() == AdminSubjectType.APPLICATION_AUTHORIZATION
                        && String.valueOf(userId).equals(item.getSubjectId()));
        assertNotNull(revokedEvent);
        assertTrue(revokedEvent.getDetail().contains("applicationId=" + applicationId));
        log.info("APPLICATION_AUTHORIZATION GRANTED / REPLACED / REVOKED 断言完成：userId={}, applicationId={}",
                userId, applicationId);
    }

    // ==================== helpers ====================

    private Long createTrustedApplication() {
        CreateTrustedApplicationClientRequest client = new CreateTrustedApplicationClientRequest();
        client.setClientId(oauthClientId);
        client.setClientName("Admin Event Client");
        client.setClientType("CONFIDENTIAL");
        client.setClientSecret(oauthClientSecret);
        client.setRequireConsent(true);
        client.setRedirectUris(Collections.singletonList(redirectUri));
        client.setScopes(Collections.singletonList("openid"));
        client.setGrantTypes(Collections.singletonList(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()));
        client.setAuthenticationMethods(Collections.singletonList("client_secret_post"));
        CreateTrustedApplicationRequest application = new CreateTrustedApplicationRequest();
        application.setApplicationCode(applicationCode);
        application.setApplicationName("Admin Event Application");
        application.setInitialClient(client);
        Long createdId = trustedApplicationService.createApplication(application).getApplication().getId();
        PutApplicationPermissionManifestRequest manifest = new PutApplicationPermissionManifestRequest();
        manifest.setRoles(Collections.emptyList());
        manifest.setPagePermissions(Collections.emptyList());
        manifest.setApiPermissions(Collections.emptyList());
        manifest.setDataResources(Collections.emptyList());
        manifestService.putManifest(createdId, manifest);
        return createdId;
    }

    private PutApplicationAuthorizationRequest authorizationRequest() {
        PutApplicationAuthorizationRequest request = new PutApplicationAuthorizationRequest();
        request.setAdmitted(Boolean.TRUE);
        request.setRoles(Collections.emptyList());
        request.setPagePermissions(Collections.emptyList());
        request.setApiPermissions(Collections.emptyList());
        return request;
    }

    private <T extends AbstractIamEvent> T awaitEvent(Class<T> type, Predicate<T> matcher)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            for (AbstractIamEvent event : eventCapture.events) {
                if (type.isInstance(event) && matcher.test(type.cast(event))) {
                    return type.cast(event);
                }
            }
            Thread.sleep(50);
        }
        return null;
    }

    /**
     * 测试用事件捕获器（订阅统一根类型，按事件类型与字段断言过滤）
     */
    @Component
    static class AdminEventCapture {

        final List<AbstractIamEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void onIamEvent(AbstractIamEvent event) {
            log.info("捕获 IAM 事件：class={}", event.getClass().getSimpleName());
            events.add(event);
        }

        void reset() {
            events.clear();
        }
    }
}
