package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamDepartmentEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamSessionEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamDepartmentRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM 管理台仪表盘聚合 API 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminDashboardApiTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> userIds = new ArrayList<Long>();
    private final List<Long> departmentIds = new ArrayList<Long>();
    private final List<String> sessionIds = new ArrayList<String>();
    private String adminUsername;
    private String userUsername;
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
    private IamDepartmentRepository departmentRepository;

    @Autowired
    private IamSessionRepository sessionRepository;

    @BeforeEach
    void loginUsers() throws Exception {
        adminUsername = "dashboard-admin-" + suffix;
        userUsername = "dashboard-user-" + suffix;
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
        for (Long departmentId : departmentIds) {
            departmentRepository.findById(departmentId).ifPresent(departmentRepository::delete);
        }
    }

    @Test
    @DisplayName("仪表盘应聚合治理计数与运行统计（含治理缺口），过期/撤销会话不计入在线，最近登录独立分页")
    void testDashboardAggregatesCountsAndStats() throws Exception {
        Instant now = Instant.now();
        Instant todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        long baselineUsers = userRepository.count();
        long baselineTodayLoggedIn = userRepository.countByLastLoginAtAfter(todayStart);
        long baselineLocked = userRepository.countByLockedUntilAfter(now);
        long baselineActiveSessions = sessionRepository.countByStatusAndExpiresAtAfter(
                SimpleIamServerConstant.STATUS_ACTIVE, now);
        long baselineDisabled = userRepository.countByStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        long baselineWithoutDepartment = userRepository.countByDepartmentIdIsNull();

        IamUserEntity todayUser = createUser("dashboard-today");
        todayUser.setLastLoginAt(now.minusSeconds(1));
        userRepository.save(todayUser);

        IamUserEntity yesterdayUser = createUser("dashboard-yesterday");
        yesterdayUser.setLastLoginAt(todayStart.minusSeconds(3600));
        userRepository.save(yesterdayUser);

        IamUserEntity lockedUser = createUser("dashboard-locked");
        lockedUser.setLockedUntil(now.plusSeconds(3600));
        userRepository.save(lockedUser);

        createSession(todayUser.getId(), todayUser.getUsername(), now, now.plusSeconds(1800),
                SimpleIamServerConstant.STATUS_ACTIVE);
        createSession(yesterdayUser.getId(), yesterdayUser.getUsername(), now, now.minusSeconds(60),
                SimpleIamServerConstant.STATUS_ACTIVE);
        createSession(lockedUser.getId(), lockedUser.getUsername(), now, now.plusSeconds(1800),
                SimpleIamServerConstant.STATUS_INACTIVE);

        mockMvc.perform(get("/iam/admin/dashboard").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.user").value((int) (baselineUsers + 3)))
                .andExpect(jsonPath("$.stats.todayLoggedInUsers").value((int) (baselineTodayLoggedIn + 1)))
                .andExpect(jsonPath("$.stats.lockedUsers").value((int) (baselineLocked + 1)))
                .andExpect(jsonPath("$.stats.activeSessions").value((int) (baselineActiveSessions + 1)))
                .andExpect(jsonPath("$.stats.disabledUsers").value((int) baselineDisabled))
                .andExpect(jsonPath("$.stats.usersWithoutDepartment").value((int) (baselineWithoutDepartment + 3)))
                .andExpect(jsonPath("$.counts").isMap())
                .andExpect(jsonPath("$.recentLogins").doesNotExist());
    }

    @Test
    @DisplayName("最近登录独立分页接口应解析用户部门名")
    void testRecentLoginsResolveDepartmentName() throws Exception {
        IamDepartmentEntity department = new IamDepartmentEntity();
        department.setCode("dashboard-dept-" + suffix);
        department.setName("仪表盘测试部门");
        department.setCreatedAt(Instant.now());
        department.setUpdatedAt(Instant.now());
        IamDepartmentEntity savedDepartment = departmentRepository.save(department);
        departmentIds.add(savedDepartment.getId());

        IamUserEntity departmentUser = createUser("dashboard-deptuser");
        departmentUser.setDepartmentId(savedDepartment.getId());
        departmentUser.setLastLoginAt(Instant.now().minusSeconds(1));
        userRepository.save(departmentUser);

        mockMvc.perform(get("/iam/admin/dashboard/recent-logins")
                        .param("page", "1").param("size", "20").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.username == '" + departmentUser.getUsername()
                        + "')].departmentName").value(hasItem("仪表盘测试部门")));
    }

    @Test
    @DisplayName("最近登录分页接口应按 size 截断并回显分页元数据，仅统计登录过的用户")
    void testRecentLoginsPaginatesIndependently() throws Exception {
        IamUserEntity first = createUser("dashboard-page1");
        first.setLastLoginAt(Instant.now().minusSeconds(60));
        userRepository.save(first);
        IamUserEntity second = createUser("dashboard-page2");
        second.setLastLoginAt(Instant.now().minusSeconds(120));
        userRepository.save(second);
        createUser("dashboard-never-logged");

        mockMvc.perform(get("/iam/admin/dashboard/recent-logins")
                        .param("page", "1").param("size", "2").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2));

        mockMvc.perform(get("/iam/admin/dashboard/recent-logins")
                        .param("page", "1").param("size", "500").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.username == '" + first.getUsername() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.username == '" + second.getUsername() + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.username == '"
                        + "dashboard-never-logged-" + suffix + "')]").doesNotExist());
    }

    @Test
    @DisplayName("仪表盘聚合与最近登录接口应拒绝非管理员")
    void testDashboardRequiresAdminAuthority() throws Exception {
        mockMvc.perform(get("/iam/admin/dashboard").cookie(userSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/iam/admin/dashboard/recent-logins").cookie(userSession))
                .andExpect(status().isForbidden());
    }

    private IamSessionEntity createSession(Long userId, String username, Instant issuedAt, Instant expiresAt,
                                           int status) {
        IamSessionEntity session = new IamSessionEntity();
        session.setId("dashboard-session-" + suffix + "-" + sessionIds.size());
        session.setUserId(userId);
        session.setUsername(username);
        session.setIssuedAt(issuedAt);
        session.setLastActiveAt(issuedAt);
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
        request.setDisplayName("仪表盘测试用户");
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
