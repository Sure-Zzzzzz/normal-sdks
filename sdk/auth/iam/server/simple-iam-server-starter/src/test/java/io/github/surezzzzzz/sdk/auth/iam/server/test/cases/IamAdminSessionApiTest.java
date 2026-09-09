package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamSessionRepository;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM 管理台会话管理 API 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminSessionApiTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> userIds = new ArrayList<Long>();
    private final List<String> sessionIds = new ArrayList<String>();
    private Cookie adminSession;
    private Cookie userSession;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamSessionRepository sessionRepository;

    @BeforeEach
    void loginUsers() throws Exception {
        String adminUsername = "session-admin-" + suffix;
        String userUsername = "session-user-" + suffix;
        Long adminUserId = createUserWithUsername(adminUsername).getId();
        Long adminRoleId = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).getId();
        roleService.assignRole(adminUserId, adminRoleId);
        adminSession = login(adminUsername);
        createUserWithUsername(userUsername);
        userSession = login(userUsername);
    }

    @AfterEach
    void cleanup() {
        for (String sessionId : sessionIds) {
            sessionRepository.findById(sessionId).ifPresent(sessionRepository::delete);
        }
        for (Long userId : userIds) {
            userRepository.findById(userId).ifPresent(userRepository::delete);
        }
    }

    @Test
    @DisplayName("会话列表应仅含未过期活跃会话，按最近活跃倒序，支持 userId 过滤与分页")
    void testListActiveSessionsFiltersOrdersAndPaginates() throws Exception {
        Instant now = Instant.now();
        IamUserEntity targetUser = createUser("session-target");
        IamUserEntity otherUser = createUser("session-other");

        createSession(targetUser, now.minusSeconds(150), now.plusSeconds(1800), "console-a");
        createSession(targetUser, now.minusSeconds(90), now.plusSeconds(1800), "console-b");
        IamSessionEntity newestSession = createSession(otherUser, now.minusSeconds(5), now.plusSeconds(1800),
                "console-newest");
        createSession(targetUser, now.minusSeconds(30), now.minusSeconds(60), "console-expired");
        createSession(targetUser, now.minusSeconds(30), now.plusSeconds(1800), "console-revoked",
                SimpleIamServerConstant.STATUS_INACTIVE);

        // 库中可能有其他未过期会话（真实登录残留），列表断言全部走 userId 过滤以隔离外部数据
        mockMvc.perform(get("/iam/admin/sessions")
                        .param("userId", String.valueOf(targetUser.getId()))
                        .param("size", "500").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].sessionId").value(sessionIds.get(1)))
                .andExpect(jsonPath("$.content[*].sessionId").value(hasItem(sessionIds.get(0))))
                .andExpect(jsonPath("$.content[*].sessionId").value(not(hasItem(sessionIds.get(3)))))
                .andExpect(jsonPath("$.content[*].sessionId").value(not(hasItem(sessionIds.get(4)))))
                .andExpect(jsonPath("$.content[*].username")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(targetUser.getUsername()))));

        mockMvc.perform(get("/iam/admin/sessions")
                        .param("userId", String.valueOf(otherUser.getId())).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sessionId").value(newestSession.getId()))
                .andExpect(jsonPath("$.content[0].username").value(otherUser.getUsername()))
                .andExpect(jsonPath("$.content[0].remoteIp").value("10.0.0.9"))
                .andExpect(jsonPath("$.content[0].userAgent").value("Mozilla/5.0 session-api-test"));

        mockMvc.perform(get("/iam/admin/sessions")
                        .param("userId", String.valueOf(targetUser.getId()))
                        .param("page", "1").param("size", "1").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content[0].sessionId").value(sessionIds.get(1)));

        mockMvc.perform(get("/iam/admin/sessions")
                        .param("userId", String.valueOf(targetUser.getId()))
                        .param("page", "2").param("size", "1").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].sessionId").value(sessionIds.get(0)));
    }

    @Test
    @DisplayName("强制下线应撤销用户全部活跃会话并从列表消失，用户不存在返回 400")
    void testRevokeUserSessionsTearsDownAndRejectsUnknownUser() throws Exception {
        Instant now = Instant.now();
        IamUserEntity targetUser = createUser("session-revoke");
        createSession(targetUser, now.minusSeconds(60), now.plusSeconds(1800), "console-r1");
        createSession(targetUser, now.minusSeconds(30), now.plusSeconds(1800), "console-r2");

        mockMvc.perform(put("/iam/admin/sessions/users/" + targetUser.getId() + "/revoke")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(2));

        for (String sessionId : sessionIds) {
            sessionRepository.findById(sessionId).ifPresent(session -> {
                org.junit.jupiter.api.Assertions.assertEquals(
                        SimpleIamServerConstant.STATUS_INACTIVE, session.getStatus(),
                        "撤销后 status 应回落为 0：" + sessionId);
            });
        }

        mockMvc.perform(get("/iam/admin/sessions")
                        .param("userId", String.valueOf(targetUser.getId())).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(put("/iam/admin/sessions/users/999999999/revoke")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("会话管理接口应拒绝非管理员")
    void testSessionApiRequiresAdminAuthority() throws Exception {
        mockMvc.perform(get("/iam/admin/sessions").cookie(userSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/iam/admin/sessions/users/1/revoke")
                        .cookie(userSession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    private IamSessionEntity createSession(IamUserEntity user, Instant lastActiveAt, Instant expiresAt,
                                           String clientId) {
        return createSession(user, lastActiveAt, expiresAt, clientId,
                SimpleIamServerConstant.STATUS_ACTIVE);
    }

    private IamSessionEntity createSession(IamUserEntity user, Instant lastActiveAt, Instant expiresAt,
                                           String clientId, int status) {
        IamSessionEntity session = new IamSessionEntity();
        session.setId("session-api-" + suffix + "-" + sessionIds.size());
        session.setUserId(user.getId());
        session.setUsername(user.getUsername());
        session.setClientId(clientId);
        session.setRemoteIp("10.0.0.9");
        session.setUserAgent("Mozilla/5.0 session-api-test");
        session.setAuthTime(lastActiveAt);
        session.setIssuedAt(lastActiveAt);
        session.setLastActiveAt(lastActiveAt);
        session.setExpiresAt(expiresAt);
        session.setStatus(status);
        IamSessionEntity saved = sessionRepository.save(session);
        sessionIds.add(saved.getId());
        return saved;
    }

    private IamUserEntity createUser(String prefix) {
        return createUserWithUsername(prefix + "-" + suffix);
    }

    private IamUserEntity createUserWithUsername(String username) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword("User@1234");
        request.setDisplayName("会话测试用户");
        IamUserEntity user = userService.createUser(request);
        userIds.add(user.getId());
        return user;
    }

    private Cookie login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"User@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
    }
}
