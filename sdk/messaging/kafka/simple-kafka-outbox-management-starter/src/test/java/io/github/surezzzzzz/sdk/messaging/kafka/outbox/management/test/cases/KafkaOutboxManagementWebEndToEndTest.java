package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.KafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration.SimpleKafkaOutboxManagementProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.SimpleKafkaOutboxManagementTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.support.OutboxTestSchemaHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.support.RuntimeOutboxFixtureHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.test.support.RuntimeOutboxFixtureTestConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Management 页面真实 MySQL 端到端测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = {SimpleKafkaOutboxManagementTestApplication.class,
        RuntimeOutboxFixtureTestConfiguration.class})
@AutoConfigureMockMvc
class KafkaOutboxManagementWebEndToEndTest {
    private static final String TABLE = "simple_kafka_outbox";
    private static final String TEST_USERNAME = "management-test";
    private static final String TEST_PASSWORD = "test-" + Long.toUnsignedString(System.nanoTime(), 36);
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SimpleKafkaOutboxManagementProperties properties;
    @Autowired
    private KafkaOutboxEngine outboxEngine;
    @Autowired
    private KafkaOutboxRepository outboxRepository;
    @Autowired
    @Qualifier(SimpleKafkaOutboxConstant.BEAN_TRANSACTION_TEMPLATE)
    private TransactionTemplate outboxTransactionTemplate;
    @Autowired
    private KafkaPublisher kafkaPublisher;
    private RuntimeOutboxFixtureHelper fixtureHelper;

    @DynamicPropertySource
    static void managementProperties(DynamicPropertyRegistry registry) {
        registry.add("io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.admin.username", () -> TEST_USERNAME);
        registry.add("io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.admin.password", () -> TEST_PASSWORD);
    }

    @BeforeEach
    void recreateTable() throws IOException {
        OutboxTestSchemaHelper.recreateTable(jdbcTemplate);
        fixtureHelper = new RuntimeOutboxFixtureHelper(outboxEngine, outboxRepository, outboxTransactionTemplate);
    }

    @Test
    void shouldAuthenticateRenderSafePagesAndProtectPoisonResetWithCsrf() throws Exception {
        long poisonId = fixtureHelper.save("web-message-id-secret", OutboxStatus.POISON);
        String basePath = properties.getUi().getBasePath();
        String loginPath = basePath + "/login";
        String detailPath = basePath + "/records/" + poisonId;
        MvcResult loginPage = mockMvc.perform(get(loginPath)).andExpect(status().isOk()).andReturn();
        mockMvc.perform(get(basePath + "/assets/css/bootstrap.min.css")).andExpect(status().isOk());
        mockMvc.perform(get(basePath + "/assets/css/management-ui.css")).andExpect(status().isOk());
        mockMvc.perform(get(basePath + "/assets/icon.svg")).andExpect(status().isOk());
        assertTrue(loginPage.getResponse().getContentAsString().contains("_csrf"), "登录页面必须带 CSRF 字段");
        assertLocalPageAssets(loginPage.getResponse().getContentAsString(), basePath);
        MvcResult root = mockMvc.perform(get("/")).andExpect(status().is3xxRedirection()).andReturn();
        assertEquals(basePath + "/", root.getResponse().getRedirectedUrl(), "根路径必须跳转到配置的 Management 页面入口");
        mockMvc.perform(get(basePath + "/")).andExpect(status().is3xxRedirection());
        mockMvc.perform(get(detailPath)).andExpect(status().is3xxRedirection());
        MvcResult failedLogin = mockMvc.perform(post(loginPath).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("username", properties.getAdmin().getUsername()).param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertEquals(loginPath + "?error", failedLogin.getResponse().getRedirectedUrl(), "失败登录必须回到管理登录页");
        MockHttpSession failedSession = (MockHttpSession) failedLogin.getRequest().getSession(false);
        if (failedSession == null) {
            mockMvc.perform(get(basePath + "/")).andExpect(status().is3xxRedirection());
        } else {
            mockMvc.perform(get(basePath + "/").session(failedSession)).andExpect(status().is3xxRedirection());
        }
        MvcResult login = mockMvc.perform(post(loginPath).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("username", properties.getAdmin().getUsername()).param("password", properties.getAdmin().getPassword()))
                .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session, "登录成功必须创建会话");
        MvcResult dashboard = mockMvc.perform(get(basePath + "/").session(session)).andExpect(status().isOk()).andReturn();
        assertTrue(dashboard.getResponse().getContentAsString().contains("_csrf"), "定位表单必须带 CSRF 字段");
        assertLocalPageAssets(dashboard.getResponse().getContentAsString(), basePath);
        jdbcTemplate.update("UPDATE " + TABLE + " SET record_key = 'web-record-key-secret', trace_id = 'web-trace-secret', "
                + "headers_json = '{\"header\":\"web-header-secret\"}', "
                + "attributes_json = '{\"attribute\":\"web-attribute-secret\"}', "
                + "owner_token = 'web-owner-secret', version = 9 WHERE id = ?", poisonId);
        MvcResult detail = mockMvc.perform(get(detailPath).session(session)).andExpect(status().isOk()).andReturn();
        String html = detail.getResponse().getContentAsString();
        assertTrue(html.contains("_csrf"), "详情重置表单必须带 CSRF 字段");
        assertLocalPageAssets(html, basePath);
        assertTrue(html.contains("web-message-id-secret"), "详情必须展示 messageId 供人工定位");
        for (String forbidden : new String[]{"mock-payload", "web-record-key-secret", "web-trace-secret",
                "web-header-secret", "web-attribute-secret", "web-owner-secret"}) {
            assertFalse(html.contains(forbidden), "详情不得包含受禁信息：" + forbidden);
        }
        mockMvc.perform(post(detailPath + "/reset-poison").session(session)).andExpect(status().isForbidden());
        assertEquals(OutboxStatus.POISON.getCode(), databaseStatus(poisonId), "缺失 CSRF 不得修改数据库");
        mockMvc.perform(post(detailPath + "/reset-poison").session(session).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(OutboxStatus.PENDING.getCode(), databaseStatus(poisonId), "带 CSRF 的重置必须进入 PENDING");
        mockMvc.perform(post(detailPath + "/reset-poison").session(session).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict());
        assertEquals(OutboxStatus.PENDING.getCode(), databaseStatus(poisonId), "冲突 reset 不得再次修改记录");
        OutboxRecordEntity claimed = fixtureHelper.claim(poisonId);
        assertEquals(Long.valueOf(poisonId), claimed.getId(), "重置后记录必须满足 Runtime 领取条件");
        org.mockito.Mockito.verifyNoInteractions(kafkaPublisher);
        mockMvc.perform(post(basePath + "/records/999999/reset-poison").session(session)
                .with(SecurityMockMvcRequestPostProcessors.csrf())).andExpect(status().isNotFound());
        mockMvc.perform(get(detailPath + "/reset-poison").session(session)).andExpect(status().is4xxClientError());
        mockMvc.perform(post(basePath + "/logout").session(session).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(session.isInvalid(), "退出必须使会话失效");
        log.info("Management 页面登录、CSRF、详情脱敏与 POISON 重置验证完成");
    }

    @Test
    void shouldCreateAllRuntimeFixtureStatusesWithoutPublishing() {
        OutboxStatus[] statuses = {OutboxStatus.PROCESSING, OutboxStatus.SENT, OutboxStatus.POISON,
                OutboxStatus.RETRY_WAIT, OutboxStatus.PENDING};
        for (OutboxStatus status : statuses) {
            long recordId = fixtureHelper.save("runtime-fixture-" + status.getCode(), status);
            String actualStatus = databaseStatus(recordId);
            log.info("Runtime 夹具记录，recordId={}，期望状态={}，实际状态={}", recordId, status.getCode(), actualStatus);
            assertEquals(status.getCode(), actualStatus, "Runtime 夹具必须生成指定状态");
        }
        assertThrows(IllegalArgumentException.class, () -> fixtureHelper.save("runtime-fixture-null", null),
                "空状态必须在写入前被拒绝");
        Integer nullStatusCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + TABLE
                + " WHERE message_id = ?", Integer.class, "runtime-fixture-null");
        log.info("空状态样例记录数：{}", nullStatusCount);
        assertEquals(Integer.valueOf(0), nullStatusCount, "空状态不得留下半成品记录");
        org.mockito.Mockito.verifyNoInteractions(kafkaPublisher);
    }

    @Test
    void shouldReturnExplicitErrorStatusForInvalidBrowseAndMissingRecord() throws Exception {
        MockHttpSession session = login();
        String basePath = properties.getUi().getBasePath();
        mockMvc.perform(get(basePath + "/records?status=PENDING&cursor=invalid").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(basePath + "/records?status=UNKNOWN").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(basePath + "/records/999999").session(session)).andExpect(status().isNotFound());
    }

    @Test
    void shouldBrowseStateWithLoadMore() throws Exception {
        long first = fixtureHelper.save("browse-first", OutboxStatus.PENDING);
        long second = fixtureHelper.save("browse-second", OutboxStatus.PENDING);
        MockHttpSession session = login();
        String listPath = properties.getUi().getBasePath() + "/records?status=PENDING&size=1";
        MvcResult page = mockMvc.perform(get(listPath).session(session)).andExpect(status().isOk()).andReturn();
        String html = page.getResponse().getContentAsString();
        assertLocalPageAssets(html, properties.getUi().getBasePath());
        assertTrue(html.contains("加载更多"), "首批有剩余记录时必须提供加载更多链接");
        assertTrue(html.contains("/records/" + second), "首批必须展示最新记录的详情链接");
        assertFalse(html.contains("/records/" + first), "首批不得跨越 page size 展示下一条记录");
    }

    @Test
    void shouldRejectInvalidLocateInputOnDashboard() throws Exception {
        MockHttpSession session = login();
        String basePath = properties.getUi().getBasePath();
        mockMvc.perform(post(basePath + "/locate").session(session).param("recordId", "1"))
                .andExpect(status().isForbidden());
        MvcResult invalid = mockMvc.perform(post(basePath + "/locate").session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()).param("recordId", "not-a-number"))
                .andExpect(status().isOk()).andReturn();
        assertTrue(invalid.getResponse().getContentAsString().contains("请输入一个有效的记录 ID 或消息 ID"));
        mockMvc.perform(post(basePath + "/locate").session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("recordId", "1").param("messageId", "message-id"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldLocateByMessageIdWithoutLeakingItIntoRedirectUrl() throws Exception {
        long id = fixtureHelper.save("locate-message-id-secret", OutboxStatus.PENDING);
        MockHttpSession session = login();
        String basePath = properties.getUi().getBasePath();
        MvcResult locate = mockMvc.perform(post(basePath + "/locate").session(session).with(SecurityMockMvcRequestPostProcessors.csrf())
                .param("messageId", "  locate-message-id-secret  ")).andExpect(status().is3xxRedirection()).andReturn();
        assertEquals(basePath + "/records/" + id, locate.getResponse().getRedirectedUrl(), "消息 ID 首尾空白必须忽略，且重定向只能包含记录 ID");
        assertFalse(locate.getResponse().getRedirectedUrl().contains("locate-message-id-secret"));
    }

    private void assertLocalPageAssets(String html, String basePath) {
        assertTrue(html.contains(basePath + "/assets/css/bootstrap.min.css"), "页面必须引用本地 Bootstrap 样式");
        assertTrue(html.contains(basePath + "/assets/css/management-ui.css"), "页面必须引用本地 Management 样式");
        assertTrue(html.contains(basePath + "/assets/icon.svg"), "页面必须引用本地 Sure-Zzzzzz 标识");
        assertFalse(html.contains("http://"), "页面不得引用外部 HTTP 资源");
        assertFalse(html.contains("https://"), "页面不得引用外部 HTTPS 资源");
        assertFalse(html.contains("<script"), "页面不得包含脚本");
        assertFalse(html.contains("fetch("), "页面不得使用 AJAX 请求");
        assertFalse(html.contains("WebSocket"), "页面不得使用 WebSocket");
        assertFalse(html.contains("EventSource"), "页面不得使用 SSE");
    }

    private MockHttpSession login() throws Exception {
        String loginPath = properties.getUi().getBasePath() + "/login";
        MvcResult login = mockMvc.perform(post(loginPath).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("username", properties.getAdmin().getUsername()).param("password", properties.getAdmin().getPassword()))
                .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session, "登录帮助方法必须返回认证会话");
        return session;
    }

    private String databaseStatus(long recordId) {
        return jdbcTemplate.queryForObject("SELECT status FROM " + TABLE + " WHERE id = ?", String.class, recordId);
    }
}
