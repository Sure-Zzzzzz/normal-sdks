package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRoleEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamMessageRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 站内信发送批次管理 Admin API 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamAdminMessageApiTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String adminUsername = "message-admin-" + suffix;
    private final List<Long> userIds = new ArrayList<>();
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
    private IamMessageRepository messageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(adminUsername);
        request.setPassword("Admin@1234");
        request.setDisplayName(adminUsername);
        Long adminUserId = userService.createUser(request).getId();
        IamRoleEntity adminRole = roleService.getByCode(SimpleIamServerConstant.BUILT_IN_ROLE_IAM_ADMIN);
        roleService.assignRole(adminUserId, adminRole.getId());
        MvcResult login = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + adminUsername + "\",\"password\":\"Admin@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = login.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
    }

    @AfterEach
    void cleanup() {
        for (Long userId : userIds) {
            messageRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId).forEach(messageRepository::delete);
            userRepository.deleteById(userId);
        }
        userRepository.findByUsername(adminUsername).ifPresent(user -> userService.deleteUser(user.getId()));
    }

    private Long createUser(String label) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(label + "-" + suffix);
        request.setPassword("User@1234");
        request.setDisplayName(label + "-展示名");
        Long userId = userService.createUser(request).getId();
        userIds.add(userId);
        return userId;
    }

    private String sendMessage(Long recipientUserId, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/iam/admin/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[" + recipientUserId + "],\"title\":\"" + title
                                + "\",\"content\":\"批次测试内容\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("sendBatchId").asText();
    }

    @Test
    @DisplayName("批次分页应聚合发送记录并返回结构化目标数")
    void testMessageBatchPageContract() throws Exception {
        Long firstRecipient = createUser("batch-first");
        Long secondRecipient = createUser("batch-second");
        String firstBatchId = sendMessage(firstRecipient, "批次列表第一条");
        String secondBatchId = sendMessage(secondRecipient, "批次列表第二条");

        mockMvc.perform(get("/iam/admin/messages/page")
                        .param("page", "1").param("size", String.valueOf(SimpleIamServerConstant.MAX_ADMIN_PAGE_SIZE))
                        .cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.content[?(@.sendBatchId == '" + firstBatchId + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.sendBatchId == '" + secondBatchId + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.sendBatchId == '" + firstBatchId + "')].title")
                        .value(org.hamcrest.Matchers.hasItem("批次列表第一条")))
                .andExpect(jsonPath("$.content[?(@.sendBatchId == '" + firstBatchId + "')].targetUserCount")
                        .value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.content[?(@.sendBatchId == '" + firstBatchId + "')].recipientCount")
                        .value(org.hamcrest.Matchers.hasItem(1)))
                .andExpect(jsonPath("$.content[?(@.sendBatchId == '" + firstBatchId + "')].senderUsername")
                        .value(org.hamcrest.Matchers.hasItem(adminUsername)));
        log.info("批次分页契约验证成功：first={}, second={}", firstBatchId, secondBatchId);
    }

    @Test
    @DisplayName("批次详情应返回内容与目标结构化字段")
    void testMessageBatchDetailContract() throws Exception {
        Long recipient = createUser("batch-detail");
        String batchId = sendMessage(recipient, "批次详情标题");

        mockMvc.perform(get("/iam/admin/messages/" + batchId).cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sendBatchId").value(batchId))
                .andExpect(jsonPath("$.title").value("批次详情标题"))
                .andExpect(jsonPath("$.content").value("批次测试内容"))
                .andExpect(jsonPath("$.senderUsername").value(adminUsername))
                .andExpect(jsonPath("$.targetUserCount").value(1))
                .andExpect(jsonPath("$.targetDepartmentCount").value(0))
                .andExpect(jsonPath("$.targetUserGroupCount").value(0))
                .andExpect(jsonPath("$.targetIncludeChildDepartments").value(false))
                .andExpect(jsonPath("$.recipientCount").value(1))
                .andExpect(jsonPath("$.readCount").value(0));
        log.info("批次详情契约验证成功：batchId={}", batchId);
    }

    @Test
    @DisplayName("批次收件人应分页返回并携带已读状态")
    void testMessageBatchRecipientsPageContract() throws Exception {
        Long firstRecipient = createUser("batch-recipient-a");
        Long secondRecipient = createUser("batch-recipient-b");
        MvcResult result = mockMvc.perform(post("/iam/admin/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserIds\":[" + firstRecipient + "," + secondRecipient
                                + "],\"title\":\"收件人分页标题\",\"content\":\"批次测试内容\"}")
                        .cookie(adminSession).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        String batchId = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString()).get("sendBatchId").asText();
        jdbcTemplate.update("UPDATE iam_message SET read_at = NOW() WHERE send_batch_id = ? AND recipient_user_id = ?",
                batchId, firstRecipient);

        mockMvc.perform(get("/iam/admin/messages/" + batchId + "/recipients")
                        .param("page", "1").param("size", "1").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].userId").value(firstRecipient))
                .andExpect(jsonPath("$.content[0].readAt").isNotEmpty());

        mockMvc.perform(get("/iam/admin/messages/" + batchId + "/recipients")
                        .param("page", "2").param("size", "1").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].userId").value(secondRecipient))
                .andExpect(jsonPath("$.content[0].readAt").isEmpty());
        log.info("批次收件人分页契约验证成功：batchId={}", batchId);
    }

    @Test
    @DisplayName("批次不存在时详情与收件人应返回 404")
    void testMessageBatchNotFound() throws Exception {
        String missingBatchId = "missing-" + suffix;
        mockMvc.perform(get("/iam/admin/messages/" + missingBatchId).cookie(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("站内信不存在")));
        mockMvc.perform(get("/iam/admin/messages/" + missingBatchId + "/recipients").cookie(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("站内信不存在")));
    }
}
