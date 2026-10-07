package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.user.IamUserService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * admin HTTP 面走查（1.3.3 修复的真实触发场景复刻）：经 admin 登录会话连建两个
 * 空手机号用户（admin web 创建成员表单默认 phone='' 直交的形态），第二个必须成功——
 * 修复前第二个撞 uk_phone 唯一索引 500。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminPhoneHttpWalkthroughTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final List<Long> userIds = new ArrayList<Long>();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IamUserService userService;
    @Autowired
    private IamUserRepository userRepository;

    private String adminUsername;
    private javax.servlet.http.Cookie adminSession;

    @BeforeEach
    void loginAdmin() throws Exception {
        adminUsername = "phone-walk-admin-" + suffix;
        io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest seed =
                new io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest();
        seed.setUsername(adminUsername);
        seed.setPassword("User@1234");
        seed.setDisplayName("走查管理员");
        Long adminId = userService.createUser(seed).getId();
        userIds.add(adminId);
        roleService.assignRole(adminId, roleService.getByCode(
                SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN).getId());

        MvcResult result = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + adminUsername + "\",\"password\":\"User@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = result.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
        assertNotNull(adminSession, "走查前置：admin 登录会话必须建立");
    }

    @AfterEach
    void cleanup() {
        for (Long userId : userIds) {
            userRepository.findById(userId).ifPresent(user -> userService.deleteUser(user.getId()));
        }
    }

    @Test
    void shouldCreateTwoMembersWithEmptyPhoneOverHttpWithoutUniqueCollision() throws Exception {
        String body = "{\"username\":\"%s\",\"password\":\"User@1234\",\"displayName\":\"%s\",\"phone\":\"\"}";

        MvcResult first = mockMvc.perform(post("/iam/admin/users")
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(body, "walk-empty-" + suffix, "第一个空号成员"))
                        .with(csrf()))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        log.info("[走查1] 第一个空手机号成员创建成功（HTTP {}）", first.getResponse().getStatus());

        // 修复前：第二个空手机号成员此处必 500（Duplicate entry '' for key uk_phone）
        MvcResult second = mockMvc.perform(post("/iam/admin/users")
                        .cookie(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(body, "walk-empty2-" + suffix, "第二个空号成员"))
                        .with(csrf()))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        log.info("[走查2] 第二个空手机号成员创建成功（HTTP {}）——uk_phone 不再被空串撞", second.getResponse().getStatus());

        userRepository.findAll().forEach(user -> {
            if (user.getUsername().startsWith("walk-empty")) {
                userIds.add(user.getId());
                assertNull(user.getPhone(), "走查落库核验：空手机号成员 phone 必须为 NULL 而非空串");
            }
        });
        log.info("[走查3] 落库核验完成：空手机号均落 NULL");
    }

    @Autowired
    private io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamRoleService roleService;
}
