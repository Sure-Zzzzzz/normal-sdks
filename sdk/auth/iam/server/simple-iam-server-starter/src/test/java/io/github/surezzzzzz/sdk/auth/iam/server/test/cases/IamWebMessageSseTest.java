package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.message.request.CreateMessageRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.user.request.CreateUserRequest;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamMessageEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamMessageRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.MessageService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.MessageSseService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.UserService;
import io.github.surezzzzzz.sdk.auth.iam.server.support.TokenHashHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@AutoConfigureMockMvc
class IamWebMessageSseTest {

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String username = "sse-user-" + suffix;
    private final String otherUsername = "sse-other-user-" + suffix;
    private Cookie session;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private IamUserRepository userRepository;

    @Autowired
    private IamMessageRepository messageRepository;

    @MockBean
    private MessageSseService sseService;

    @BeforeEach
    void loginUser() throws Exception {
        createUser();
        session = login();
    }

    @AfterEach
    void cleanup() {
        deleteUserAndMessages(username);
        deleteUserAndMessages(otherUsername);
    }

    @Test
    @DisplayName("未登录访问 SSE 通道应返回 401")
    void testEventsUnauthorized() throws Exception {
        mockMvc.perform(get("/iam/web/messages/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("站内信分页接口应返回当前用户消息")
    void testMessagePageReturnsCurrentUserMessages() throws Exception {
        IamUserEntity recipient = createUser();
        messageService.createMessage(createMessageRequest(recipient.getId()), 1L, "admin");

        mockMvc.perform(get("/iam/web/messages/page")
                        .param("page", "1")
                        .param("size", "10")
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.content[0].title").value("SSE 未读测试"))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    @DisplayName("兼容列表接口始终返回数组且分页参数不改变响应形状")
    void testMessageListCompatibilityResponseShape() throws Exception {
        IamUserEntity recipient = createUser();
        messageService.createMessage(createMessageRequest(recipient.getId()), 1L, "admin");

        mockMvc.perform(get("/iam/web/messages").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("SSE 未读测试"));
        mockMvc.perform(get("/iam/web/messages")
                        .param("page", "1")
                        .param("size", "10")
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("SSE 未读测试"));
    }

    @Test
    @DisplayName("全部标记已读只能更新当前用户未读消息并保持幂等")
    void testMarkAllRead() throws Exception {
        IamUserEntity recipient = createUser();
        IamMessageEntity message = messageService.createMessage(createMessageRequest(recipient.getId()), 1L, "admin");

        mockMvc.perform(put("/iam/web/messages/read-all").cookie(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/iam/web/messages/read-all").cookie(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertEquals(0, messageService.countUnreadMessages(recipient.getId()));
        assertNotNull(messageRepository.findById(message.getId()).orElseThrow().getReadAt());
    }

    @Test
    @DisplayName("SSE 通道建立后应注册 emitter 并推送首帧未读数")
    void testEventsInitialUnreadCount() throws Exception {
        IamUserEntity recipient = createUser();
        messageService.createMessage(createMessageRequest(recipient.getId()), 1L, "admin");
        when(sseService.register(eq(recipient.getId()), any())).thenReturn(new SseEmitter());

        mockMvc.perform(get("/iam/web/messages/events")
                        .cookie(session))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(sseService).register(eq(recipient.getId()), any());
        verify(sseService).pushUnreadCount(org.mockito.ArgumentMatchers.eq(recipient.getId()),
                org.mockito.ArgumentMatchers.any(SseEmitter.class), org.mockito.ArgumentMatchers.eq(1L));
    }

    @Test
    @DisplayName("用户不能标记其他用户的站内信为已读")
    void testMarkReadOtherUserMessageForbidden() throws Exception {
        IamUserEntity otherUser = createOtherUser();
        Long messageId = messageService.createMessage(createMessageRequest(otherUser.getId()), 1L, "admin").getId();

        mockMvc.perform(put("/iam/web/messages/" + messageId + "/read").cookie(session).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("站内信创建和已读应推送最新未读数")
    void testMessageChangePushesUnreadCount() {
        IamUserEntity recipient = createUser();

        Long messageId = messageService.createMessage(createMessageRequest(recipient.getId()), 1L, "admin").getId();
        messageService.markRead(recipient.getId(), messageId);

        verify(sseService).pushUnreadCount(recipient.getId(), 1L);
        verify(sseService).pushUnreadCount(recipient.getId(), 0L);
    }

    @Test
    @DisplayName("事务回滚时不应推送 SSE 未读数，避免回滚前推送假状态")
    @Transactional
    void testRollbackDoesNotPushUnreadCount() {
        IamUserEntity recipient = createUser();
        messageService.createMessage(createMessageRequest(recipient.getId()), 1L, "admin");
        verify(sseService, never()).pushUnreadCount(anyLong(), anyLong());
    }

    @Test
    @DisplayName("多标签页接入时首帧只推给新 emitter，不向旧标签页重发")
    void testSecondTabFirstFrameDoesNotReplayToOldEmitter() throws Exception {
        IamUserEntity recipient = createUser();
        SseEmitter firstEmitter = new SseEmitter();
        SseEmitter secondEmitter = new SseEmitter();
        when(sseService.register(eq(recipient.getId()), any())).thenReturn(firstEmitter, secondEmitter);

        mockMvc.perform(get("/iam/web/messages/events").cookie(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/iam/web/messages/events").cookie(session))
                .andExpect(status().isOk());

        verify(sseService, never()).pushUnreadCount(anyLong(), anyLong());
        verify(sseService).pushUnreadCount(eq(recipient.getId()), eq(firstEmitter), anyLong());
        verify(sseService).pushUnreadCount(eq(recipient.getId()), eq(secondEmitter), anyLong());
    }

    @Test
    @DisplayName("登出时应按本端会话哈希精确清理 SSE 连接，不做用户级全端踢")
    void testLogoutEvictsOnlyLocalSessionEmitters() throws Exception {
        mockMvc.perform(post("/iam/web/auth/logout").cookie(session).with(csrf()))
                .andExpect(status().isNoContent());
        // Cookie 值经 DefaultCookieSerializer base64 编码，须还原原始会话 ID 再取哈希
        String servletSessionId = new String(Base64.getDecoder().decode(session.getValue()),
                StandardCharsets.UTF_8);
        verify(sseService).evictByServletSessionIdHash(TokenHashHelper.sha256Hex(servletSessionId));
        verify(sseService, never()).evictUserEmitters(anyLong());
    }

    private IamUserEntity createUser() {
        return createUser(username);
    }

    private IamUserEntity createOtherUser() {
        return createUser(otherUsername);
    }

    private IamUserEntity createUser(String targetUsername) {
        return userRepository.findByUsername(targetUsername).orElseGet(() -> {
            CreateUserRequest request = new CreateUserRequest();
            request.setUsername(targetUsername);
            request.setPassword("User@1234");
            request.setDisplayName("SSE 用户");
            return userService.createUser(request);
        });
    }

    private void deleteUserAndMessages(String targetUsername) {
        userRepository.findByUsername(targetUsername).ifPresent(user -> {
            messageRepository.findByRecipientUserIdOrderByCreatedAtDesc(user.getId()).forEach(messageRepository::delete);
            userRepository.delete(user);
        });
    }

    private Cookie login() throws Exception {
        MvcResult result = mockMvc.perform(post("/iam/web/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"User@1234\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie(SimpleIamServerConstant.SESSION_COOKIE_NAME);
    }

    private CreateMessageRequest createMessageRequest(Long recipientUserId) {
        CreateMessageRequest request = new CreateMessageRequest();
        request.setRecipientUserId(recipientUserId);
        request.setTitle("SSE 未读测试");
        request.setContent("SSE 未读测试内容");
        return request;
    }
}
