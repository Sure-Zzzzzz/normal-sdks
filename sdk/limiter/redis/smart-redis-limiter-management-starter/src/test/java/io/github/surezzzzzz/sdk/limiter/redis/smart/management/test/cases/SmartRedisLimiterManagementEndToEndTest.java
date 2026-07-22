package io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterTimeUnit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterManagementEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyCreateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyUpdateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyMutationResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyPageResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicySnapshotView;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicyManagementService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.service.SmartRedisLimiterPolicySnapshotService;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterEtagHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.test.SmartRedisLimiterManagementTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Management 真实 MySQL 端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterManagementTestApplication.class)
@AutoConfigureMockMvc
@Import(SmartRedisLimiterManagementEndToEndTest.ManagementEndToEndTestConfiguration.class)
public class SmartRedisLimiterManagementEndToEndTest {

    private static final String MODULE_PATH =
            "sdk/limiter/redis/smart-redis-limiter-management-starter";
    private static final String DDL_PATH = MODULE_PATH + "/docs/mysql-schema.sql";
    private static final String SERVICE_CODE = "test-service";
    private static final String RESOURCE_CODE = "test-resource";
    private static final String SUBJECT = "test-subject";

    @SpyBean
    private SmartRedisLimiterPolicyManagementService managementService;
    @Autowired
    private SmartRedisLimiterPolicySnapshotService snapshotService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestManagementEventListener eventListener;
    @Autowired
    private SmartRedisLimiterManagementProperties managementProperties;

    @BeforeEach
    public void recreateTables() throws IOException {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy_limit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy");
        jdbcTemplate.execute("DROP TABLE IF EXISTS smart_redis_limiter_policy_revision");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        executeDdl(resolveDdlPath());
        eventListener.clear();

        Integer policyTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name = 'smart_redis_limiter_policy'",
                Integer.class);
        Integer limitTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name = 'smart_redis_limiter_policy_limit'",
                Integer.class);
        Integer revisionTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name = 'smart_redis_limiter_policy_revision'",
                Integer.class);
        log.info("重建真实 MySQL 表: policy={}, limit={}, revision={}",
                policyTableCount, limitTableCount, revisionTableCount);
        assertEquals(Integer.valueOf(1), policyTableCount, "策略表必须且只能存在一张");
        assertEquals(Integer.valueOf(1), limitTableCount, "窗口表必须且只能存在一张");
        assertEquals(Integer.valueOf(1), revisionTableCount, "revision 表必须且只能存在一张");
    }

    @Test
    public void testMySqlSchemaPreservesEngineBinaryIdentityKeysAndCascadeContract() {
        assertEquals("InnoDB", tableOption("smart_redis_limiter_policy", "ENGINE"),
                "策略表必须使用 InnoDB 以保障事务和行锁");
        assertEquals("utf8mb4_bin", tableOption("smart_redis_limiter_policy", "TABLE_COLLATION"),
                "策略表默认排序规则必须为 binary");
        assertEquals("utf8mb4_bin", columnCollation("smart_redis_limiter_policy", "service_code"),
                "serviceCode 必须大小写敏感");
        assertEquals("utf8mb4_bin", columnCollation("smart_redis_limiter_policy", "resource_code"),
                "resourceCode 必须大小写敏感");
        assertEquals("utf8mb4_bin", columnCollation("smart_redis_limiter_policy", "subject"),
                "subject 必须大小写敏感");
        assertEquals("datetime(3)", columnType("smart_redis_limiter_policy", "created_at"),
                "创建时间必须保留毫秒精度");
        assertEquals("datetime(3)", columnType("smart_redis_limiter_policy_revision", "published_at"),
                "revision 发布时间必须保留毫秒精度");
        assertIndex("smart_redis_limiter_policy", "uk_smart_limiter_policy_identity",
                "service_code,resource_code,subject", false);
        assertIndex("smart_redis_limiter_policy", "idx_smart_limiter_policy_snapshot",
                "service_code,enabled,resource_code,subject", true);
        assertIndex("smart_redis_limiter_policy_limit", "uk_smart_limiter_policy_limit_window",
                "policy_id,window_seconds", false);
        assertEquals("CASCADE", jdbcTemplate.queryForObject(
                "SELECT DELETE_RULE FROM information_schema.referential_constraints "
                        + "WHERE constraint_schema = DATABASE() "
                        + "AND constraint_name = 'fk_smart_limiter_policy_limit_policy'",
                String.class), "窗口外键必须在策略删除时级联删除");

        SmartRedisLimiterPolicyMutationResponse upper = managementService.create(
                createRequest(SERVICE_CODE, "resource-case", "subject-case", true,
                        new SmartRedisLimiterLimit(1L, 1L, SmartRedisLimiterTimeUnit.SECONDS)), operator());
        SmartRedisLimiterPolicyMutationResponse lower = managementService.create(
                createRequest(SERVICE_CODE, "resource-CASE", "subject-case", true,
                        new SmartRedisLimiterLimit(1L, 1L, SmartRedisLimiterTimeUnit.SECONDS)), operator());
        assertNotEquals(upper.getPolicy().getId(), lower.getPolicy().getId(),
                "仅大小写不同的 resourceCode 必须作为两个精确策略持久化");
        assertEquals(2, count("smart_redis_limiter_policy"), "binary identity 不得合并大小写不同策略");
    }

    @Test
    public void testEmptyPolicyPageRendersHtmlWithDefaultJdbcManagementService() throws Exception {
        MockHttpSession session = loginSession();
        String policyPath = managementProperties.getUi().getBasePath()
                + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE;

        MvcResult policyPage = mockMvc.perform(get(policyPath).session(session))
                .andExpect(status().isOk())
                .andExpect(view().name(SmartRedisLimiterManagementConstant.VIEW_POLICY_LIST))
                .andReturn();
        String policyHtml = policyPage.getResponse().getContentAsString();
        log.info("空策略页面响应: status={}, html={}",
                policyPage.getResponse().getStatus(), policyHtml);
        assertTrue(policyHtml.contains("限流策略管理"), "空策略页必须返回管理页面 HTML");
        assertTrue(policyHtml.contains("<tbody>"), "空策略页必须渲染列表容器");
        assertTrue(policyHtml.contains("暂未创建限流策略"), "空策略页必须展示明确空状态");
        assertTrue(policyHtml.contains("创建策略"), "空策略页必须提供创建入口");
        assertTrue(policyHtml.contains("policyForm"), "空策略页必须渲染创建编辑表单");
        assertTrue(policyHtml.contains("bootstrap.min.css"), "空策略页必须引用本地 Bootstrap 样式");
        assertTrue(policyHtml.contains("management-ui.css"), "空策略页必须引用 Management 页面样式");
        assertFalse(policyHtml.contains("https://"), "页面不得依赖外部样式或脚本地址");
    }

    @Test
    public void testPolicyPageNullResultRendersSanitizedHtmlErrorPage() throws Exception {
        MockHttpSession session = loginSession();
        String policyPath = managementProperties.getUi().getBasePath()
                + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE;
        doReturn(null).when(managementService)
                .query(org.mockito.ArgumentMatchers.any(SmartRedisLimiterPolicyQuery.class));

        try {
            MvcResult errorPage = mockMvc.perform(get(policyPath).session(session))
                    .andExpect(status().isInternalServerError())
                    .andExpect(view().name(SmartRedisLimiterManagementConstant.VIEW_ERROR))
                    .andReturn();
            String errorHtml = errorPage.getResponse().getContentAsString();
            assertTrue(errorHtml.contains(ErrorMessage.PAGE_UNAVAILABLE),
                    "空查询结果必须展示固定脱敏提示");
            assertFalse(errorHtml.contains("NullPointerException"), "页面不得向管理员暴露 NPE");
            assertFalse(errorHtml.contains("{\"message\""), "页面故障不得返回 JSON 错误体");
        } finally {
            reset(managementService);
        }
    }

    @Test
    public void testPolicyPageNullItemsRendersSanitizedHtmlErrorPage() throws Exception {
        MockHttpSession session = loginSession();
        String policyPath = managementProperties.getUi().getBasePath()
                + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE;
        doReturn(SmartRedisLimiterPolicyPageResponse.builder()
                .page(1).size(20).totalElements(0L).totalPages(0).build())
                .when(managementService)
                .query(org.mockito.ArgumentMatchers.any(SmartRedisLimiterPolicyQuery.class));

        try {
            MvcResult errorPage = mockMvc.perform(get(policyPath).session(session))
                    .andExpect(status().isInternalServerError())
                    .andExpect(view().name(SmartRedisLimiterManagementConstant.VIEW_ERROR))
                    .andReturn();
            String errorHtml = errorPage.getResponse().getContentAsString();
            assertTrue(errorHtml.contains(ErrorMessage.PAGE_UNAVAILABLE),
                    "空 items 必须展示固定脱敏提示");
            assertFalse(errorHtml.contains("NullPointerException"), "页面不得向管理员暴露 NPE");
            assertFalse(errorHtml.contains("{\"message\""), "页面故障不得返回 JSON 错误体");
        } finally {
            reset(managementService);
        }
    }

    @Test
    public void testPolicyPageFailureRendersSanitizedHtmlErrorPage() throws Exception {
        MockHttpSession session = loginSession();
        String policyPath = managementProperties.getUi().getBasePath()
                + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE;
        String failureMessage = "jdbc:mysql://local-secret/test?password=hidden subject=" + SUBJECT;
        doThrow(new RuntimeException(failureMessage)).when(managementService)
                .query(org.mockito.ArgumentMatchers.any(SmartRedisLimiterPolicyQuery.class));

        try {
            MvcResult errorPage = mockMvc.perform(get(policyPath).session(session))
                    .andExpect(status().isInternalServerError())
                    .andExpect(view().name(SmartRedisLimiterManagementConstant.VIEW_ERROR))
                    .andReturn();
            String errorHtml = errorPage.getResponse().getContentAsString();
            log.info("策略页面故障响应: status={}, html={}",
                    errorPage.getResponse().getStatus(), errorHtml);
            assertTrue(errorHtml.contains(ErrorMessage.PAGE_UNAVAILABLE),
                    "页面故障必须展示固定脱敏提示");
            assertFalse(errorHtml.contains("jdbc:mysql"), "页面不得泄露 JDBC 连接信息");
            assertFalse(errorHtml.contains("password"), "页面不得泄露密码信息");
            assertFalse(errorHtml.contains(SUBJECT), "页面不得泄露策略 subject");
            assertFalse(errorHtml.contains("{\"message\""), "页面故障不得返回 JSON 错误体");
        } finally {
            reset(managementService);
        }
    }

    @Test
    public void testUiLoginSessionRenderingAndLogoutStayInsideManagementPath() throws Exception {
        String basePath = managementProperties.getUi().getBasePath();
        String loginPath = basePath + SmartRedisLimiterManagementConstant.PATH_LOGIN;
        String policyPath = basePath + SmartRedisLimiterManagementConstant.PATH_POLICY_PAGE;
        String logoutPath = basePath + SmartRedisLimiterManagementConstant.PATH_LOGOUT;

        MvcResult loginPage = mockMvc.perform(get(loginPath))
                .andExpect(status().isOk())
                .andExpect(view().name(SmartRedisLimiterManagementConstant.VIEW_LOGIN))
                .andReturn();
        String loginHtml = loginPage.getResponse().getContentAsString();
        assertTrue(loginHtml.contains("name=\"username\""), "登录页必须渲染用户名字段");
        assertTrue(loginHtml.contains("name=\"password\""), "登录页必须渲染密码字段");
        assertTrue(loginHtml.contains("_csrf"), "登录页必须渲染 CSRF 字段");
        assertTrue(loginHtml.contains("action=\"" + loginPath + "\""),
                "登录表单必须提交到 UI 窄路径");
        assertTrue(loginHtml.contains("bootstrap.min.css"), "登录页必须加载本地 Bootstrap 样式");
        assertTrue(loginHtml.contains("management-ui.css"), "登录页必须加载 Management 页面样式");
        assertFalse(loginHtml.contains("https://"), "登录页不得依赖外部资源");

        String bootstrapCssPath = basePath + SmartRedisLimiterManagementConstant.PATH_BOOTSTRAP_CSS;
        mockMvc.perform(get(bootstrapCssPath))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(result.getResponse().getContentAsString().contains("Bootstrap"),
                        "匿名访问必须能加载本地 Bootstrap 资源"));

        mockMvc.perform(get(policyPath))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertTrue(result.getResponse().getRedirectedUrl()
                        .endsWith(loginPath), "未认证页面访问必须跳转 management 登录页"));

        MvcResult failedLogin = mockMvc.perform(post(loginPath)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("username", operator())
                        .param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertTrue(failedLogin.getResponse().getRedirectedUrl().startsWith(loginPath + "?error"),
                "错误凭据必须回到 management 登录页");

        MvcResult successLogin = mockMvc.perform(post(loginPath)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("username", operator())
                        .param("password", managementProperties.getAdmin().getPassword()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertEquals(policyPath, successLogin.getResponse().getRedirectedUrl(),
                "成功登录必须进入 management 策略页面");
        MockHttpSession session = (MockHttpSession) successLogin.getRequest().getSession(false);
        assertNotNull(session, "成功登录必须建立认证会话");

        managementService.create(createRequest(SERVICE_CODE, RESOURCE_CODE, SUBJECT, true,
                new SmartRedisLimiterLimit(1L, 1L, SmartRedisLimiterTimeUnit.SECONDS)), operator());
        MvcResult policyPage = mockMvc.perform(get(policyPath).session(session)
                        .param("serviceCode", SERVICE_CODE))
                .andExpect(status().isOk())
                .andExpect(view().name(SmartRedisLimiterManagementConstant.VIEW_POLICY_LIST))
                .andReturn();
        String policyHtml = policyPage.getResponse().getContentAsString();
        assertTrue(policyHtml.contains(RESOURCE_CODE), "策略页面必须渲染查询出的 resourceCode");
        assertTrue(policyHtml.contains(SUBJECT), "策略页面必须渲染查询出的 subject");
        assertTrue(policyHtml.contains("已启用"), "策略页面必须渲染策略启用状态");
        assertTrue(policyHtml.contains("value=\"" + SERVICE_CODE + "\""),
                "策略页面必须回显 serviceCode 过滤条件");
        assertTrue(policyHtml.contains("adminApiUrl"), "策略页面必须向同源变更请求提供 API 地址");
        assertEquals(managementProperties.getApi().getBasePath()
                        + SmartRedisLimiterManagementConstant.PATH_POLICY_ADMIN,
                policyPage.getModelAndView().getModel().get(
                        SmartRedisLimiterManagementConstant.MODEL_ATTRIBUTE_ADMIN_API_URL),
                "策略页面必须向 API 根下的管理接口发起请求");
        assertTrue(policyHtml.contains("_csrf"), "策略页面必须向变更操作提供 CSRF 数据");

        MvcResult logout = mockMvc.perform(post(logoutPath).session(session)
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertEquals(loginPath + "?logout", logout.getResponse().getRedirectedUrl(),
                "登出必须回到 management 登录页");
        assertTrue(session.isInvalid(), "登出必须使认证会话失效");
    }

    @Test
    public void testApiAndUiUrlsRespectServletContextPathAndRejectLegacyRoot() throws Exception {
        String contextPath = "/host-app";
        String uiBasePath = managementProperties.getUi().getBasePath();
        String apiBasePath = managementProperties.getApi().getBasePath();
        MvcResult loginPage = mockMvc.perform(get(contextPath + uiBasePath
                        + SmartRedisLimiterManagementConstant.PATH_LOGIN)
                        .contextPath(contextPath))
                .andExpect(status().isOk())
                .andReturn();
        String loginHtml = loginPage.getResponse().getContentAsString();
        assertTrue(loginHtml.contains("action=\"" + contextPath + uiBasePath
                        + SmartRedisLimiterManagementConstant.PATH_LOGIN + "\""),
                "servlet context path 必须加入登录表单地址");
        MvcResult snapshot = mockMvc.perform(get(contextPath + apiBasePath
                        + SmartRedisLimiterManagementConstant.PATH_POLICY_SNAPSHOT)
                        .contextPath(contextPath)
                        .with(policyToken())
                        .param(SmartRedisLimiterManagementConstant.QUERY_PARAM_SERVICE_CODE, SERVICE_CODE))
                .andExpect(status().isOk())
                .andReturn();
        String snapshotEtag = snapshot.getResponse()
                .getHeader(SmartRedisLimiterManagementConstant.HEADER_ETAG);
        assertTrue(snapshotEtag != null && snapshotEtag.startsWith("\"srlm-")
                        && snapshotEtag.endsWith("\""),
                "context path 下快照响应必须返回强 ETag");
        mockMvc.perform(get("/smart-redis-limiter/management/policies"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testCompleteLifecycleMaintainsDatabaseRevisionSnapshotAndEvents() {
        SmartRedisLimiterPolicyMutationResponse created = managementService.create(
                createRequest(true, 10L), operator());
        long id = created.getPolicy().getId();

        log.info("创建结果: id={}, revision={}, rowVersion={}, eventCount={}",
                id, created.getRevision(), created.getPolicy().getRowVersion(), eventListener.events.size());
        assertEquals(1L, created.getRevision(), "首次有效创建必须分配 revision 1");
        assertEquals(0L, created.getPolicy().getRowVersion(), "新策略 rowVersion 必须从 0 开始");
        assertEquals(Boolean.TRUE, created.getChanged(), "创建必须标记 changed=true");
        assertDatabaseState(id, true, 0L, 1, 10L, 1L);
        assertEvent(0, "CREATE", 1L, null, true);

        SmartRedisLimiterPolicySnapshotView firstSnapshot = snapshotService.getSnapshot(SERVICE_CODE);
        assertSnapshot(firstSnapshot, 1L, 1, 10L);

        SmartRedisLimiterPolicyMutationResponse noOpEnable = managementService.enable(id, 0L, operator());
        log.info("重复启用结果: changed={}, revision={}, eventCount={}",
                noOpEnable.getChanged(), noOpEnable.getRevision(), eventListener.events.size());
        assertFalse(noOpEnable.getChanged(), "重复 ENABLE 必须是 no-op");
        assertEquals(1L, noOpEnable.getRevision(), "no-op 不得消耗 revision");
        assertEquals(1, eventListener.events.size(), "no-op 不得发布事件");
        assertDatabaseState(id, true, 0L, 1, 10L, 1L);

        SmartRedisLimiterPolicyUpdateRequest updateRequest = new SmartRedisLimiterPolicyUpdateRequest();
        updateRequest.setExpectedRowVersion(0L);
        updateRequest.setLimits(Collections.singletonList(
                new SmartRedisLimiterLimit(20L, 1L, SmartRedisLimiterTimeUnit.MINUTES)));
        SmartRedisLimiterPolicyMutationResponse updated =
                managementService.update(id, updateRequest, operator());

        assertTrue(updated.getChanged(), "limits 语义变化必须执行更新");
        assertEquals(2L, updated.getRevision(), "UPDATE 必须且只增加一次 revision");
        assertEquals(1L, updated.getPolicy().getRowVersion(), "UPDATE 必须增加一次 rowVersion");
        assertDatabaseState(id, true, 1L, 1, 20L, 60L);
        assertEvent(1, "UPDATE", 2L, true, true);

        SmartRedisLimiterPolicySnapshotView secondSnapshot = snapshotService.getSnapshot(SERVICE_CODE);
        assertSnapshot(secondSnapshot, 2L, 1, 20L);
        assertNotEquals(firstSnapshot.getEtag(), secondSnapshot.getEtag(),
                "新 revision 必须生成不同 ETag");

        SmartRedisLimiterPolicyMutationResponse disabled = managementService.disable(id, 1L, operator());
        assertEquals(3L, disabled.getRevision(), "DISABLE 必须增加一次 revision");
        assertEquals(2L, disabled.getPolicy().getRowVersion(), "DISABLE 必须增加一次 rowVersion");
        assertDatabaseState(id, false, 2L, 1, 20L, 60L);
        assertEvent(2, "DISABLE", 3L, true, false);
        assertSnapshot(snapshotService.getSnapshot(SERVICE_CODE), 3L, 0, 0L);

        SmartRedisLimiterPolicyMutationResponse enabled = managementService.enable(id, 2L, operator());
        assertEquals(4L, enabled.getRevision(), "ENABLE 必须增加一次 revision");
        assertEquals(3L, enabled.getPolicy().getRowVersion(), "ENABLE 必须增加一次 rowVersion");
        assertDatabaseState(id, true, 3L, 1, 20L, 60L);
        assertEvent(3, "ENABLE", 4L, false, true);

        SmartRedisLimiterPolicyMutationResponse deleted = managementService.delete(id, 3L, operator());
        assertEquals(5L, deleted.getRevision(), "DELETE 必须增加一次 revision");
        assertEquals(new SmartRedisLimiterPolicyKey(SERVICE_CODE, RESOURCE_CODE, SUBJECT),
                deleted.getDeletedPolicyKey(), "删除响应必须保留精确策略键");
        assertNull(deleted.getPolicy(), "删除响应不得伪造仍存在的策略");
        assertEquals(0, count("smart_redis_limiter_policy"), "策略主表行必须物理删除");
        assertEquals(0, count("smart_redis_limiter_policy_limit"), "外键级联必须删除全部窗口");
        assertEquals(5L, revision(), "删除最后策略后 revision 行必须保留");
        assertEvent(4, "DELETE", 5L, true, null);
        assertSnapshot(snapshotService.getSnapshot(SERVICE_CODE), 5L, 0, 0L);
    }

    @Test
    public void testRollbackVersionConflictAndNoOpDoNotConsumeRevisionOrPublishEvent() {
        SmartRedisLimiterPolicyMutationResponse created = managementService.create(
                createRequest(true, 10L), operator());
        long id = created.getPolicy().getId();
        eventListener.clear();

        SmartRedisLimiterPolicyUpdateRequest same = new SmartRedisLimiterPolicyUpdateRequest();
        same.setExpectedRowVersion(0L);
        same.setLimits(Collections.singletonList(
                new SmartRedisLimiterLimit(10L, 1L, SmartRedisLimiterTimeUnit.SECONDS)));
        SmartRedisLimiterPolicyMutationResponse noOp = managementService.update(id, same, operator());
        assertFalse(noOp.getChanged(), "语义相同 UPDATE 必须是 no-op");
        assertEquals(1L, revision(), "no-op UPDATE 不得增加 revision");
        assertEquals(0, eventListener.events.size(), "no-op UPDATE 不得发布事件");

        SmartRedisLimiterPolicyUpdateRequest stale = new SmartRedisLimiterPolicyUpdateRequest();
        stale.setExpectedRowVersion(9L);
        stale.setLimits(Collections.singletonList(
                new SmartRedisLimiterLimit(20L, 1L, SmartRedisLimiterTimeUnit.SECONDS)));
        RuntimeException conflict = assertThrows(RuntimeException.class,
                () -> managementService.update(id, stale, operator()),
                "过期 rowVersion 必须拒绝");
        log.info("rowVersion 冲突: type={}, message={}",
                conflict.getClass().getSimpleName(), conflict.getMessage());
        assertEquals(1L, revision(), "rowVersion 冲突不得增加 revision");
        assertDatabaseState(id, true, 0L, 1, 10L, 1L);
        assertEquals(0, eventListener.events.size(), "rowVersion 冲突不得发布事件");

        assertThrows(RuntimeException.class,
                () -> managementService.create(createRequest(true, 30L), operator()),
                "重复三元身份必须拒绝");
        assertEquals(1L, revision(), "唯一键冲突不得增加 revision");
        assertEquals(1, count("smart_redis_limiter_policy"), "冲突不得插入额外策略行");
        assertEquals(1, count("smart_redis_limiter_policy_limit"), "冲突不得插入额外窗口行");
        assertEquals(0, eventListener.events.size(), "唯一键冲突不得发布事件");
    }

    @Test
    public void testUnknownServiceReturnsStableEmptySnapshotWithoutDatabaseWrite() {
        SmartRedisLimiterPolicySnapshotView first = snapshotService.getSnapshot("unknown-service");
        SmartRedisLimiterPolicySnapshotView second = snapshotService.getSnapshot("unknown-service");

        log.info("未知服务快照: revision={}, publishedAt={}, etag={}",
                first.getSnapshot().getRevision(), first.getSnapshot().getPublishedAt(), first.getEtag());
        assertEquals(SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                first.getSnapshot().getSchemaVersion(), "schemaVersion 必须来自 core 稳定契约");
        assertEquals(0L, first.getSnapshot().getRevision(), "未知服务必须返回 revision 0");
        assertEquals(java.time.Instant.EPOCH, first.getSnapshot().getPublishedAt(),
                "未知服务 publishedAt 必须稳定为 epoch");
        assertTrue(first.getSnapshot().getPolicies().isEmpty(), "未知服务 policies 必须为空");
        assertEquals(first.getEtag(), second.getEtag(), "未知服务 ETag 必须稳定");
        assertEquals(0, count("smart_redis_limiter_policy_revision"),
                "只读未知服务快照不得写 revision 行");
    }

    @Test
    public void testSnapshotEndpointReturns200Then304AndValidEmptySnapshot() throws Exception {
        MvcResult firstResult = mockMvc.perform(get(
                        managementProperties.getApi().getBasePath()
                                + SmartRedisLimiterManagementConstant.PATH_POLICY_SNAPSHOT)
                        .with(policyToken())
                        .param(SmartRedisLimiterManagementConstant.QUERY_PARAM_SERVICE_CODE, SERVICE_CODE))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpServletResponse firstResponse = firstResult.getResponse();
        String firstEtag = firstResponse.getHeader(SmartRedisLimiterManagementConstant.HEADER_ETAG);
        String firstBody = firstResponse.getContentAsString();
        JsonNode firstSnapshot = objectMapper.readTree(firstBody);
        log.info("首次快照响应: status={}, etag={}, body={}",
                firstResponse.getStatus(), firstEtag, firstBody);
        assertEquals(200, firstResponse.getStatus(), "未知服务首次快照必须 200");
        assertEquals(SmartRedisLimiterManagementConstant.CACHE_CONTROL_NO_CACHE,
                firstResponse.getHeader(SmartRedisLimiterManagementConstant.HEADER_CACHE_CONTROL),
                "200 响应必须禁止缓存过期副本");
        assertTrue(firstEtag.startsWith("\"srlm-"), "ETag 必须是强引用标签");
        assertTrue(firstEtag.endsWith("\""), "ETag 必须以双引号结束");
        assertEquals(SmartRedisLimiterConstant.POLICY_SCHEMA_VERSION,
                firstSnapshot.get("schemaVersion").asText(), "未知服务必须返回 core schemaVersion");
        assertEquals(SERVICE_CODE, firstSnapshot.get("serviceCode").asText(),
                "未知服务快照必须保留请求 serviceCode");
        assertEquals(0L, firstSnapshot.get("revision").asLong(),
                "未知服务首次快照必须返回 revision 0");
        assertEquals(java.time.Instant.EPOCH.toString(), firstSnapshot.get("publishedAt").asText(),
                "未知服务首次快照 publishedAt 必须为 epoch");
        assertTrue(firstSnapshot.get("policies").isArray() && firstSnapshot.get("policies").isEmpty(),
                "未知服务首次快照必须返回有效空 policies");

        MvcResult secondResult = mockMvc.perform(get(
                        managementProperties.getApi().getBasePath()
                                + SmartRedisLimiterManagementConstant.PATH_POLICY_SNAPSHOT)
                        .with(policyToken())
                        .param(SmartRedisLimiterManagementConstant.QUERY_PARAM_SERVICE_CODE, SERVICE_CODE)
                        .header(SmartRedisLimiterManagementConstant.HEADER_IF_NONE_MATCH, firstEtag))
                .andExpect(status().isNotModified())
                .andReturn();
        log.info("第二次快照响应: status={}, bodyLength={}",
                secondResult.getResponse().getStatus(),
                secondResult.getResponse().getContentLength());
        assertEquals(304, secondResult.getResponse().getStatus(), "相同 ETag 必须命中 304");
        assertEquals(0, secondResult.getResponse().getContentLength(),
                "304 响应不得携带 body");
        assertEquals(firstEtag,
                secondResult.getResponse().getHeader(SmartRedisLimiterManagementConstant.HEADER_ETAG),
                "304 必须返回相同 ETag");
        assertEquals(SmartRedisLimiterManagementConstant.CACHE_CONTROL_NO_CACHE,
                secondResult.getResponse().getHeader(SmartRedisLimiterManagementConstant.HEADER_CACHE_CONTROL),
                "304 响应必须保留 Cache-Control 契约");

        managementService.create(createRequest(true, 10L), operator());
        MvcResult thirdResult = mockMvc.perform(get(
                        managementProperties.getApi().getBasePath()
                                + SmartRedisLimiterManagementConstant.PATH_POLICY_SNAPSHOT)
                        .with(policyToken())
                        .param(SmartRedisLimiterManagementConstant.QUERY_PARAM_SERVICE_CODE, SERVICE_CODE)
                        .header(SmartRedisLimiterManagementConstant.HEADER_IF_NONE_MATCH, firstEtag))
                .andExpect(status().isOk())
                .andReturn();
        String thirdEtag = thirdResult.getResponse()
                .getHeader(SmartRedisLimiterManagementConstant.HEADER_ETAG);
        log.info("策略创建后快照响应: status={}, etag={}",
                thirdResult.getResponse().getStatus(), thirdEtag);
        assertEquals(200, thirdResult.getResponse().getStatus(), "revision 变化后必须返回 200");
        JsonNode thirdSnapshot = objectMapper.readTree(thirdResult.getResponse().getContentAsString());
        assertNotEquals(firstEtag, thirdEtag, "新 revision 必须生成不同 ETag");
        assertEquals(SmartRedisLimiterManagementConstant.CACHE_CONTROL_NO_CACHE,
                thirdResult.getResponse().getHeader(SmartRedisLimiterManagementConstant.HEADER_CACHE_CONTROL),
                "200 响应必须设置 Cache-Control");
        assertEquals(1L, thirdSnapshot.get("revision").asLong(),
                "200 响应必须反映最新 revision");
        assertEquals(1, thirdSnapshot.get("policies").size(), "创建后快照必须精确包含一个已启用策略");
        assertEquals(RESOURCE_CODE, thirdSnapshot.at("/policies/0/key/resourceCode").asText(),
                "HTTP 快照不得暴露数据库主键而必须输出 core policy key");
        assertEquals(SUBJECT, thirdSnapshot.at("/policies/0/key/subject").asText(),
                "HTTP 快照必须精确保留 subject");
        assertEquals(10L, thirdSnapshot.at("/policies/0/limits/0/count").asLong(),
                "HTTP 快照必须输出完整 limits");
        assertTrue(thirdSnapshot.at("/policies/0/id").isMissingNode(),
                "HTTP 快照不得泄露管理数据库主键");
        assertTrue(thirdSnapshot.at("/policies/0/enabled").isMissingNode(),
                "HTTP 快照不得泄露管理启停字段");
        assertTrue(thirdSnapshot.at("/policies/0/rowVersion").isMissingNode(),
                "HTTP 快照不得泄露管理乐观锁字段");
    }

    @Test
    public void testAdminApiRequiresAuthenticationCsrfAndPreservesOperatorAndErrorContract() throws Exception {
        String adminPath = managementProperties.getApi().getBasePath()
                + SmartRedisLimiterManagementConstant.PATH_POLICY_ADMIN;
        String createBody = objectMapper.writeValueAsString(createRequest(true, 10L));

        MvcResult anonymousResult = mockMvc.perform(post(adminPath)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isUnauthorized())
                .andReturn();
        log.info("匿名管理创建响应: status={}", anonymousResult.getResponse().getStatus());
        assertEquals(0, count("smart_redis_limiter_policy"), "匿名请求不得写入策略");

        MvcResult csrfResult = mockMvc.perform(post(adminPath)
                        .with(SecurityMockMvcRequestPostProcessors.user("http-admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isForbidden())
                .andReturn();
        log.info("缺失 CSRF 管理创建响应: status={}", csrfResult.getResponse().getStatus());
        assertEquals(0, count("smart_redis_limiter_policy"), "缺失 CSRF 的认证请求不得写入策略");

        MvcResult createdResult = mockMvc.perform(post(adminPath)
                        .with(SecurityMockMvcRequestPostProcessors.user("http-admin").roles("ADMIN"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString());
        long id = created.at("/policy/id").asLong();
        log.info("认证管理创建响应: id={}, response={}", id, created);
        assertTrue(id > 0L, "创建响应必须返回持久化策略 ID");
        assertEquals(1L, created.get("revision").asLong(), "HTTP 创建必须返回 revision 1");
        assertTrue(created.get("changed").asBoolean(), "HTTP 创建必须标记 changed=true");
        assertEquals("http-admin", eventListener.events.get(0).getPayload().getOperator(),
                "operator 必须来自认证上下文，不能来自请求体");
        assertDatabaseState(id, true, 0L, 1, 10L, 1L);

        MvcResult staleResult = mockMvc.perform(put(adminPath + "/" + id)
                        .with(SecurityMockMvcRequestPostProcessors.user("http-admin").roles("ADMIN"))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRowVersion\":9,\"limits\":[{\"count\":20,\"window\":1,\"unit\":\"SECONDS\"}]}"))
                .andExpect(status().isConflict())
                .andReturn();
        JsonNode staleError = objectMapper.readTree(staleResult.getResponse().getContentAsString());
        log.info("过期版本管理更新响应: {}", staleError);
        assertFalse(staleError.has("code"),
                "HTTP 错误响应不得暴露内部业务 code");
        assertTrue(staleError.get("message").asText().length() > 0,
                "错误响应必须提供面向调用方的说明");
        assertTrue(staleError.get("timestamp").asText().length() > 0,
                "错误响应必须提供服务端时间戳");
        assertFalse(staleError.toString().contains(SUBJECT), "冲突错误体不得泄露 subject");
        assertEquals(1L, revision(), "HTTP 冲突不得消耗 revision");

        MvcResult absentResult = mockMvc.perform(get(adminPath + "/999999")
                        .with(SecurityMockMvcRequestPostProcessors.user("http-admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode absentError = objectMapper.readTree(absentResult.getResponse().getContentAsString());
        log.info("不存在策略查询响应: {}", absentError);
        assertFalse(absentError.has("code"),
                "404 错误响应不得暴露内部业务 code");
        assertTrue(absentError.get("message").asText().length() > 0,
                "404 错误响应必须提供面向调用方的说明");
        assertTrue(absentError.get("timestamp").asText().length() > 0,
                "404 错误响应必须提供服务端时间戳");

        MvcResult invalidPageResult = mockMvc.perform(get(adminPath).param("page", "0")
                        .with(SecurityMockMvcRequestPostProcessors.user("http-admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode invalidPageError = objectMapper.readTree(invalidPageResult.getResponse().getContentAsString());
        log.info("非法分页查询响应: {}", invalidPageError);
        assertFalse(invalidPageError.has("code"),
                "400 错误响应不得暴露内部业务 code");
        assertTrue(invalidPageError.get("message").asText().length() > 0,
                "400 错误响应必须提供面向调用方的说明");
        assertTrue(invalidPageError.get("timestamp").asText().length() > 0,
                "400 错误响应必须提供服务端时间戳");
    }

    @Test
    public void testAdminApiInternalFailureUsesSanitizedHttp500Response() throws Exception {
        String adminPath = managementProperties.getApi().getBasePath()
                + SmartRedisLimiterManagementConstant.PATH_POLICY_ADMIN;
        RuntimeException failure = new RuntimeException(
                "jdbc:mysql://local-secret/test?password=hidden subject=" + SUBJECT);
        doThrow(failure).when(managementService).create(
                org.mockito.ArgumentMatchers.any(SmartRedisLimiterPolicyCreateRequest.class),
                org.mockito.ArgumentMatchers.anyString());
        try {
            MvcResult result = mockMvc.perform(post(adminPath)
                            .with(SecurityMockMvcRequestPostProcessors.user("http-admin").roles("ADMIN"))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest(true, 10L))))
                    .andExpect(status().isInternalServerError())
                    .andReturn();
            assertTrue(result.getResponse().getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE),
                    "500 错误响应必须使用 JSON Content-Type");
            JsonNode error = objectMapper.readTree(new String(
                    result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
            assertFalse(error.has("code"), "500 错误响应不得暴露内部业务 code");
            assertEquals("限流策略持久化失败", error.get("message").asText(),
                    "500 错误响应必须使用固定脱敏提示");
            assertTrue(error.get("timestamp").asText().length() > 0,
                    "500 错误响应必须提供服务端时间戳");
            assertFalse(error.toString().contains(SUBJECT), "500 错误体不得泄露 subject");
            assertFalse(error.toString().contains("jdbc:mysql"), "500 错误体不得泄露连接信息");
            assertFalse(error.toString().contains("password"), "500 错误体不得泄露凭据线索");
            assertEquals(0, count("smart_redis_limiter_policy"), "500 路径不得写入策略");
            assertEquals(0, count("smart_redis_limiter_policy_revision"), "500 路径不得初始化 revision");
        } finally {
            reset(managementService);
        }
    }

    @Test
    public void testAdminApiCompleteHttpLifecycleReplacesLimitsAndCascadesDelete() throws Exception {
        String adminPath = managementProperties.getApi().getBasePath()
                + SmartRedisLimiterManagementConstant.PATH_POLICY_ADMIN;
        SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin =
                SecurityMockMvcRequestPostProcessors.user("http-lifecycle").roles("ADMIN");
        String createBody = objectMapper.writeValueAsString(createRequest(true, 10L));

        JsonNode created = objectMapper.readTree(mockMvc.perform(post(adminPath)
                        .with(admin).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = created.at("/policy/id").asLong();
        assertTrue(id > 0L, "创建 API 必须返回有效策略主键");
        assertEquals(1L, created.get("revision").asLong(), "HTTP 创建必须分配 revision 1");
        assertEquals(0L, created.at("/policy/rowVersion").asLong(), "HTTP 创建 rowVersion 必须从 0 开始");

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get(adminPath + "/" + id).with(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(SERVICE_CODE, detail.at("/key/serviceCode").asText(), "详情必须返回精确 serviceCode");
        assertEquals(10L, detail.at("/limits/0/count").asLong(), "详情必须完整返回初始窗口");
        assertTrue(detail.get("createdAt").asText().endsWith("Z"), "详情时间必须使用 UTC Instant");

        JsonNode page = objectMapper.readTree(mockMvc.perform(get(adminPath).with(admin)
                        .param("serviceCode", SERVICE_CODE).param("enabled", "true")
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1L, page.get("totalElements").asLong(), "过滤分页必须精确统计匹配策略");
        assertEquals(1, page.get("items").size(), "过滤分页必须完整返回唯一策略");
        assertEquals(id, page.at("/items/0/id").asLong(), "分页结果必须匹配创建策略");

        String updateBody = "{\"expectedRowVersion\":0,\"limits\":[{\"count\":20,\"window\":2,\"unit\":\"SECONDS\"},"
                + "{\"count\":40,\"window\":1,\"unit\":\"MINUTES\"}]}";
        JsonNode updated = objectMapper.readTree(mockMvc.perform(put(adminPath + "/" + id)
                        .with(admin).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(updated.get("changed").asBoolean(), "完整替换 limits 必须标记 changed=true");
        assertEquals(2L, updated.get("revision").asLong(), "更新必须只增加一个 revision");
        assertEquals(1L, updated.at("/policy/rowVersion").asLong(), "更新必须只增加一个 rowVersion");
        assertEquals(2, updated.at("/policy/limits").size(), "更新响应必须包含完整替换后的 limits");
        assertEquals(20L, updated.at("/policy/limits/0/count").asLong(), "limits 必须按标准化秒数排序");
        assertEquals(40L, updated.at("/policy/limits/1/count").asLong(), "较大窗口不得在 HTTP 更新中丢失");
        assertEquals(2, count("smart_redis_limiter_policy_limit"), "更新必须物理替换为两个窗口行");

        MvcResult missingStateResult = mockMvc.perform(patch(adminPath + "/" + id)
                        .with(admin).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRowVersion\":1}"))
                .andExpect(status().isBadRequest()).andReturn();
        JsonNode missingStateError = objectMapper.readTree(
                missingStateResult.getResponse().getContentAsString());
        assertFalse(missingStateError.has("code"),
                "PATCH 缺失 enabled 时不得暴露内部业务 code");
        assertTrue(missingStateError.get("message").asText().length() > 0,
                "PATCH 缺失 enabled 时必须返回 400 说明");
        assertEquals(2L, revision(), "无效 PATCH 不得消耗 revision");
        assertEquals(1L, jdbcTemplate.queryForObject(
                        "SELECT row_version FROM smart_redis_limiter_policy WHERE id = ?", Long.class, id),
                "无效 PATCH 不得修改 rowVersion");

        mockMvc.perform(patch(adminPath + "/" + id)
                        .with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRowVersion\":1,\"enabled\":false}"))
                .andExpect(status().isForbidden());
        assertEquals(2L, revision(), "缺失 CSRF 的 PATCH 不得消耗 revision");
        assertEquals(1L, jdbcTemplate.queryForObject(
                        "SELECT row_version FROM smart_redis_limiter_policy WHERE id = ?", Long.class, id),
                "缺失 CSRF 的 PATCH 不得修改 rowVersion");

        mockMvc.perform(post(adminPath + "/" + id + "/enable")
                        .with(admin).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRowVersion\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(adminPath + "/" + id + "/disable")
                        .with(admin).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRowVersion\":1}"))
                .andExpect(status().isNotFound());

        JsonNode disabled = objectMapper.readTree(mockMvc.perform(patch(adminPath + "/" + id)
                        .with(admin).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRowVersion\":1,\"enabled\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(3L, disabled.get("revision").asLong(), "停用必须只增加一个 revision");
        assertFalse(disabled.at("/policy/enabled").asBoolean(), "停用响应必须返回 false 状态");
        assertEquals(2L, disabled.at("/policy/rowVersion").asLong(), "停用必须增加 rowVersion");

        MvcResult stalePatchResult = mockMvc.perform(patch(adminPath + "/" + id)
                        .with(admin).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRowVersion\":1,\"enabled\":true}"))
                .andExpect(status().isConflict()).andReturn();
        JsonNode stalePatchError = objectMapper.readTree(
                stalePatchResult.getResponse().getContentAsString());
        assertFalse(stalePatchError.has("code"), "过期 PATCH 不得暴露内部业务 code");
        assertTrue(stalePatchError.get("message").asText().length() > 0,
                "过期 PATCH 必须返回冲突说明");
        assertTrue(stalePatchError.get("timestamp").asText().length() > 0,
                "过期 PATCH 必须返回服务端时间戳");
        assertEquals(3L, revision(), "过期 PATCH 不得消耗 revision");
        assertEquals(2L, jdbcTemplate.queryForObject(
                        "SELECT row_version FROM smart_redis_limiter_policy WHERE id = ?", Long.class, id),
                "过期 PATCH 不得修改 rowVersion");

        JsonNode disableNoOp = objectMapper.readTree(mockMvc.perform(patch(adminPath + "/" + id)
                        .with(admin).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRowVersion\":2,\"enabled\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertFalse(disableNoOp.get("changed").asBoolean(), "重复停用必须保持 no-op");
        assertEquals(3L, disableNoOp.get("revision").asLong(), "重复停用不得消耗 revision");
        assertEquals(2L, disableNoOp.at("/policy/rowVersion").asLong(), "重复停用不得修改 rowVersion");

        JsonNode enabled = objectMapper.readTree(mockMvc.perform(patch(adminPath + "/" + id)
                        .with(admin).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRowVersion\":2,\"enabled\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(4L, enabled.get("revision").asLong(), "启用必须只增加一个 revision");
        assertTrue(enabled.at("/policy/enabled").asBoolean(), "启用响应必须返回 true 状态");
        assertEquals(3L, enabled.at("/policy/rowVersion").asLong(), "启用必须增加 rowVersion");

        JsonNode deleted = objectMapper.readTree(mockMvc.perform(delete(adminPath + "/" + id)
                        .with(admin).with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("expectedRowVersion", "3"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(deleted.get("changed").asBoolean(), "删除必须标记 changed=true");
        assertEquals(5L, deleted.get("revision").asLong(), "删除必须只增加一个 revision");
        assertTrue(deleted.get("policy").isNull(), "删除响应不得返回已删除策略");
        assertEquals(RESOURCE_CODE, deleted.at("/deletedPolicyKey/resourceCode").asText(),
                "删除响应必须返回被删除的精确策略键");
        assertEquals(0, count("smart_redis_limiter_policy"), "删除 API 必须物理删除策略行");
        assertEquals(0, count("smart_redis_limiter_policy_limit"), "删除 API 必须通过外键级联删除窗口行");
        assertEquals(5L, revision(), "删除后服务 revision 行必须保留最新值");
    }

    @Test
    public void testRestApiTokenAuthFullLifecycleWithMultiWindowPolicy() throws Exception {
        String restPath = managementProperties.getApi().getBasePath() + "/v1/policy";
        RequestPostProcessor policyToken = policyToken();
        log.info("REST 固定 token 认证全流程: restPath={}", restPath);

        MvcResult anonymousResult = mockMvc.perform(post(restPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(true, 10L))))
                .andExpect(status().isUnauthorized())
                .andReturn();
        log.info("REST 匿名创建响应: status={}", anonymousResult.getResponse().getStatus());
        assertEquals(0, count("smart_redis_limiter_policy"), "REST 匿名请求不得写入策略");

        SmartRedisLimiterPolicyCreateRequest multiWindowRequest = createRequest(
                SERVICE_CODE, RESOURCE_CODE, SUBJECT, true,
                new SmartRedisLimiterLimit(10L, 1L, SmartRedisLimiterTimeUnit.SECONDS),
                new SmartRedisLimiterLimit(100L, 1L, SmartRedisLimiterTimeUnit.MINUTES));
        JsonNode created = objectMapper.readTree(mockMvc.perform(post(restPath)
                        .with(policyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(multiWindowRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long id = created.at("/policy/id").asLong();
        assertTrue(id > 0L, "REST 创建必须返回有效策略主键");
        assertEquals(1L, created.get("revision").asLong(), "REST 创建必须分配 revision 1");
        assertEquals(2, created.at("/policy/limits").size(), "REST 创建必须完整返回两个窗口");
        assertEquals(10L, created.at("/policy/limits/0/count").asLong(),
                "REST 创建 limits 必须按标准化秒数升序返回");
        assertEquals(100L, created.at("/policy/limits/1/count").asLong(),
                "REST 创建不得丢失较大窗口");
        assertEquals(SmartRedisLimiterManagementConstant.POLICY_TOKEN_PRINCIPAL,
                eventListener.events.get(0).getPayload().getOperator(),
                "REST operator 必须来自固定 token 客户端身份");
        assertEquals(2, count("smart_redis_limiter_policy_limit"), "REST 多窗口必须全部落库");

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get(restPath + "/" + id).with(policyToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(SERVICE_CODE, detail.at("/key/serviceCode").asText(), "REST 详情必须返回精确 serviceCode");
        assertEquals(2, detail.at("/limits").size(), "REST 详情必须完整返回多窗口");

        JsonNode page = objectMapper.readTree(mockMvc.perform(get(restPath).with(policyToken)
                        .param("serviceCode", SERVICE_CODE).param("enabled", "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(1L, page.get("totalElements").asLong(), "REST 分页必须精确统计匹配策略");
        assertEquals(id, page.at("/items/0/id").asLong(), "REST 分页必须返回创建策略");

        String updateBody = "{\"expectedRowVersion\":0,\"limits\":[{\"count\":20,\"window\":2,\"unit\":\"SECONDS\"},"
                + "{\"count\":200,\"window\":1,\"unit\":\"MINUTES\"}]}";
        JsonNode updated = objectMapper.readTree(mockMvc.perform(put(restPath + "/" + id)
                        .with(policyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertTrue(updated.get("changed").asBoolean(), "REST 整体替换 limits 必须标记 changed=true");
        assertEquals(2L, updated.get("revision").asLong(), "REST 更新必须只增加一个 revision");
        assertEquals(2, updated.at("/policy/limits").size(), "REST 更新必须完整返回替换后多窗口");
        assertEquals(2, count("smart_redis_limiter_policy_limit"), "REST 更新必须物理替换为两个窗口行");

        JsonNode disabled = objectMapper.readTree(mockMvc.perform(patch(restPath + "/" + id)
                        .with(policyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRowVersion\":1,\"enabled\":false}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(3L, disabled.get("revision").asLong(), "REST 停用必须增加一次 revision");
        assertFalse(disabled.at("/policy/enabled").asBoolean(), "REST 停用响应必须返回 false 状态");

        JsonNode deleted = objectMapper.readTree(mockMvc.perform(delete(restPath + "/" + id)
                        .with(policyToken)
                        .param("expectedRowVersion", "2"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertTrue(deleted.get("changed").asBoolean(), "REST 删除必须标记 changed=true");
        assertEquals(4L, deleted.get("revision").asLong(), "REST 删除必须增加一次 revision");
        assertEquals(RESOURCE_CODE, deleted.at("/deletedPolicyKey/resourceCode").asText(),
                "REST 删除响应必须返回被删除的精确策略键");
        assertEquals(0, count("smart_redis_limiter_policy"), "REST 删除必须物理删除策略行");
        assertEquals(0, count("smart_redis_limiter_policy_limit"), "REST 删除必须级联删除窗口行");
    }

    @Test
    public void testRestApiErrorContractRejectsBadTokenAndPreservesSanitizedErrors() throws Exception {
        String restPath = managementProperties.getApi().getBasePath() + "/v1/policy";
        RequestPostProcessor policyToken = policyToken();
        RequestPostProcessor invalidPolicyToken = policyToken("wrong-token");
        log.info("REST 错误契约验证: restPath={}", restPath);

        MvcResult badCredentialResult = mockMvc.perform(post(restPath).with(invalidPolicyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(true, 10L))))
                .andExpect(status().isUnauthorized())
                .andReturn();
        log.info("REST 错误 token 创建响应: status={}", badCredentialResult.getResponse().getStatus());
        assertEquals(0, count("smart_redis_limiter_policy"), "REST 错误 token 不得写入策略");

        mockMvc.perform(post(restPath)
                        .header(SmartRedisLimiterManagementConstant.HEADER_POLICY_TOKEN, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(true, 10L))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(restPath)
                        .header(SmartRedisLimiterManagementConstant.HEADER_POLICY_TOKEN, "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(true, 10L))))
                .andExpect(status().isUnauthorized());
        assertEquals(0, count("smart_redis_limiter_policy"), "空白 token 不得写入策略");

        mockMvc.perform(post(restPath)
                        .header(SmartRedisLimiterManagementConstant.HEADER_POLICY_TOKEN, "wrong-token")
                        .header(SmartRedisLimiterManagementConstant.HEADER_POLICY_TOKEN, "another-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(true, 10L))))
                .andExpect(status().isUnauthorized());
        assertEquals(0, count("smart_redis_limiter_policy"), "重复 token header 不得写入策略");

        mockMvc.perform(post(restPath)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("rest-user", "rest-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(true, 10L))))
                .andExpect(status().isUnauthorized());
        assertEquals(0, count("smart_redis_limiter_policy"), "Basic 凭据不得替代固定 token");

        MvcResult createdResult = mockMvc.perform(post(restPath).with(policyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(true, 10L))))
                .andExpect(status().isCreated())
                .andReturn();
        assertTrue(createdResult.getRequest().getSession(false) == null,
                "固定 token REST 请求不得创建会话");
        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString());
        long id = created.at("/policy/id").asLong();
        assertEquals(1L, revision(), "REST 创建后 revision 必须为 1");

        MvcResult absentResult = mockMvc.perform(get(restPath + "/999999").with(policyToken))
                .andExpect(status().isNotFound())
                .andReturn();
        JsonNode absentError = objectMapper.readTree(absentResult.getResponse().getContentAsString());
        log.info("REST 不存在策略查询响应: {}", absentError);
        assertFalse(absentError.has("code"), "REST 404 不得暴露内部业务 code");
        assertTrue(absentError.get("message").asText().length() > 0, "REST 404 必须提供面向调用方的说明");
        assertTrue(absentError.get("timestamp").asText().length() > 0, "REST 404 必须提供服务端时间戳");

        MvcResult staleResult = mockMvc.perform(put(restPath + "/" + id).with(policyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRowVersion\":9,\"limits\":[{\"count\":20,\"window\":1,\"unit\":\"SECONDS\"}]}"))
                .andExpect(status().isConflict())
                .andReturn();
        JsonNode staleError = objectMapper.readTree(staleResult.getResponse().getContentAsString());
        log.info("REST 过期版本更新响应: {}", staleError);
        assertFalse(staleError.has("code"), "REST 409 不得暴露内部业务 code");
        assertTrue(staleError.get("message").asText().length() > 0, "REST 409 必须提供面向调用方的说明");
        assertTrue(staleError.get("timestamp").asText().length() > 0, "REST 409 必须提供服务端时间戳");
        assertFalse(staleError.toString().contains(SUBJECT), "REST 冲突错误体不得泄露 subject");
        assertEquals(1L, revision(), "REST 冲突不得消耗 revision");

        MvcResult invalidPageResult = mockMvc.perform(get(restPath).with(policyToken).param("page", "0"))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode invalidPageError = objectMapper.readTree(invalidPageResult.getResponse().getContentAsString());
        log.info("REST 非法分页查询响应: {}", invalidPageError);
        assertFalse(invalidPageError.has("code"), "REST 400 不得暴露内部业务 code");
        assertTrue(invalidPageError.get("message").asText().length() > 0, "REST 400 必须提供面向调用方的说明");
        assertTrue(invalidPageError.get("timestamp").asText().length() > 0, "REST 400 必须提供服务端时间戳");

        MvcResult missingStateResult = mockMvc.perform(patch(restPath + "/" + id).with(policyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRowVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode missingStateError = objectMapper.readTree(missingStateResult.getResponse().getContentAsString());
        log.info("REST PATCH 缺失 enabled 响应: {}", missingStateError);
        assertFalse(missingStateError.has("code"), "REST PATCH 缺失 enabled 不得暴露内部业务 code");
        assertTrue(missingStateError.get("message").asText().length() > 0,
                "REST PATCH 缺失 enabled 必须返回 400 说明");
        assertEquals(1L, revision(), "REST 无效 PATCH 不得消耗 revision");
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT row_version FROM smart_redis_limiter_policy WHERE id = ?", Long.class, id),
                "REST 无效 PATCH 不得修改 rowVersion");
    }

    @Test
    public void testRestApiPatchEnableRestoresEnabledStateAndOperator() throws Exception {
        String restPath = managementProperties.getApi().getBasePath() + "/v1/policy";
        RequestPostProcessor policyToken = policyToken();

        SmartRedisLimiterPolicyCreateRequest disabledRequest = createRequest(
                SERVICE_CODE, RESOURCE_CODE, SUBJECT, false,
                new SmartRedisLimiterLimit(10L, 1L, SmartRedisLimiterTimeUnit.SECONDS));
        JsonNode created = objectMapper.readTree(mockMvc.perform(post(restPath).with(policyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disabledRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        long id = created.at("/policy/id").asLong();
        assertFalse(created.at("/policy/enabled").asBoolean(), "REST 创建停用策略必须返回 enabled=false");
        assertEquals(1L, created.get("revision").asLong(), "REST 创建停用策略仍分配 revision 1");
        eventListener.clear();

        JsonNode enabled = objectMapper.readTree(mockMvc.perform(patch(restPath + "/" + id).with(policyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRowVersion\":0,\"enabled\":true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        log.info("REST PATCH enable 响应: {}", enabled);
        assertTrue(enabled.get("changed").asBoolean(), "REST PATCH enable 必须标记 changed=true");
        assertTrue(enabled.at("/policy/enabled").asBoolean(), "REST PATCH enable 必须返回 enabled=true");
        assertEquals(2L, enabled.get("revision").asLong(), "REST PATCH enable 必须增加一次 revision");
        assertEquals(1L, enabled.at("/policy/rowVersion").asLong(), "REST PATCH enable 必须增加一次 rowVersion");
        assertEquals(SmartRedisLimiterManagementConstant.POLICY_TOKEN_PRINCIPAL,
                eventListener.events.get(0).getPayload().getOperator(),
                "REST PATCH enable operator 必须来自固定 token 客户端身份");
        assertDatabaseState(id, true, 1L, 1, 10L, 1L);
        SmartRedisLimiterManagementEvent event = eventListener.events.get(0);
        assertEquals("ENABLE", event.getPayload().getOperation().getCode(),
                "REST PATCH enable 必须发布 ENABLE 事件");
        assertEquals(2L, event.getPayload().getRevision(),
                "REST PATCH enable 事件 revision 必须为提交后版本");
        assertEquals(Boolean.FALSE, event.getPayload().getBeforeEnabled(),
                "REST PATCH enable 事件 beforeEnabled 必须为 false");
        assertEquals(Boolean.TRUE, event.getPayload().getAfterEnabled(),
                "REST PATCH enable 事件 afterEnabled 必须为 true");
    }

    @Test
    public void testQueryAndSnapshotPreserveExactMultiPolicyMultiWindowState() {
        SmartRedisLimiterPolicyMutationResponse alpha = managementService.create(
                createRequest(SERVICE_CODE, "resource-A", "subject-Z", true,
                        new SmartRedisLimiterLimit(90L, 90L, SmartRedisLimiterTimeUnit.SECONDS),
                        new SmartRedisLimiterLimit(10L, 1L, SmartRedisLimiterTimeUnit.SECONDS)), operator());
        SmartRedisLimiterPolicyMutationResponse beta = managementService.create(
                createRequest(SERVICE_CODE, "resource-a", "subject-A", true,
                        new SmartRedisLimiterLimit(30L, 30L, SmartRedisLimiterTimeUnit.SECONDS),
                        new SmartRedisLimiterLimit(20L, 2L, SmartRedisLimiterTimeUnit.MINUTES)), operator());
        SmartRedisLimiterPolicyMutationResponse disabled = managementService.create(
                createRequest(SERVICE_CODE, "resource-B", "subject-A", false,
                        new SmartRedisLimiterLimit(40L, 40L, SmartRedisLimiterTimeUnit.SECONDS)), operator());
        SmartRedisLimiterPolicyMutationResponse otherService = managementService.create(
                createRequest("other-service", "resource-A", "subject-A", true,
                        new SmartRedisLimiterLimit(50L, 50L, SmartRedisLimiterTimeUnit.SECONDS)), operator());

        SmartRedisLimiterPolicyPageResponse firstPage = managementService.query(
                SmartRedisLimiterPolicyQuery.builder().serviceCode(SERVICE_CODE).page(1).size(2).build());
        SmartRedisLimiterPolicyPageResponse secondPage = managementService.query(
                SmartRedisLimiterPolicyQuery.builder().serviceCode(SERVICE_CODE).page(2).size(2).build());
        log.info("分页查询: first={}, second={}", firstPage, secondPage);
        assertEquals(3L, firstPage.getTotalElements(), "服务过滤后的总数必须精确排除其他服务");
        assertEquals(2, firstPage.getTotalPages(), "总页数必须按完整数据集计算");
        assertEquals(2, firstPage.getItems().size(), "第一页必须精确包含两个策略");
        assertEquals(1, secondPage.getItems().size(), "第二页必须精确包含剩余策略");
        assertEquals("resource-A", firstPage.getItems().get(0).getKey().getResourceCode(),
                "分页排序必须区分大小写且按 resourceCode 确定");
        assertEquals("resource-B", firstPage.getItems().get(1).getKey().getResourceCode(),
                "分页必须保留禁用策略供管理面查看");
        assertEquals("resource-a", secondPage.getItems().get(0).getKey().getResourceCode(),
                "分页排序必须按数据库 binary collation 稳定执行");
        assertEquals(2, firstPage.getItems().get(0).getLimits().size(),
                "分页查询必须批量完整加载多窗口策略");
        assertEquals(10L, firstPage.getItems().get(0).getLimits().get(0).getCount(),
                "limits 必须以标准化窗口秒数升序返回");
        assertEquals(90L, firstPage.getItems().get(0).getLimits().get(1).getCount(),
                "多窗口策略不得在查询中丢失后续窗口");

        SmartRedisLimiterPolicySnapshotView firstSnapshot = snapshotService.getSnapshot(SERVICE_CODE);
        SmartRedisLimiterPolicySnapshotView secondSnapshot = snapshotService.getSnapshot(SERVICE_CODE);
        log.info("多策略快照: revision={}, etag={}, policies={}",
                firstSnapshot.getSnapshot().getRevision(), firstSnapshot.getEtag(),
                firstSnapshot.getSnapshot().getPolicies());
        assertEquals(3L, firstSnapshot.getSnapshot().getRevision(),
                "其他服务写入不得污染当前服务 revision");
        assertEquals(firstSnapshot.getSnapshot(), secondSnapshot.getSnapshot(),
                "同一 revision 的快照正文必须完全稳定");
        assertEquals(firstSnapshot.getEtag(), secondSnapshot.getEtag(),
                "同一 revision 的 ETag 必须完全稳定");
        assertEquals(2, firstSnapshot.getSnapshot().getPolicies().size(),
                "快照必须精确排除禁用策略和其他服务策略");
        assertEquals("resource-A", firstSnapshot.getSnapshot().getPolicies().get(0).getKey().getResourceCode(),
                "快照必须按 resourceCode、subject 稳定排序");
        assertEquals("resource-a", firstSnapshot.getSnapshot().getPolicies().get(1).getKey().getResourceCode(),
                "快照排序不得把大小写不同 identity 合并");
        assertEquals(2, firstSnapshot.getSnapshot().getPolicies().get(0).getLimits().size(),
                "快照必须完整输出多窗口策略");
        assertEquals(1L, firstSnapshot.getSnapshot().getPolicies().get(0).getLimits().get(0).getWindow(),
                "快照 limits 必须以 windowSeconds 升序返回");
        assertEquals(90L, firstSnapshot.getSnapshot().getPolicies().get(0).getLimits().get(1).getWindow(),
                "快照不得丢失较大窗口");
        assertEquals(4, count("smart_redis_limiter_policy"), "三个同服务策略及一个其他服务策略必须全部物理存在");
        assertEquals(6, count("smart_redis_limiter_policy_limit"), "全部多窗口行必须精确落库");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT revision FROM smart_redis_limiter_policy_revision WHERE service_code = ?",
                Long.class, "other-service").longValue(), "不同服务必须维护独立 revision");
        assertTrue(alpha.getPolicy().getId() > 0L && beta.getPolicy().getId() > 0L
                        && disabled.getPolicy().getId() > 0L && otherService.getPolicy().getId() > 0L,
                "所有创建响应必须返回持久化主键");
    }

    @Test
    public void testCoreValidationRejectsEquivalentWindowsWithoutDatabaseSideEffects() {
        SmartRedisLimiterPolicyCreateRequest request = createRequest(true, 10L);
        request.setLimits(java.util.Arrays.asList(
                new SmartRedisLimiterLimit(10L, 60L, SmartRedisLimiterTimeUnit.SECONDS),
                new SmartRedisLimiterLimit(20L, 1L, SmartRedisLimiterTimeUnit.MINUTES)));
        SmartRedisLimiterException exception = assertThrows(SmartRedisLimiterException.class,
                () -> managementService.create(request, operator()),
                "等价窗口不得绕过 core 去重校验");
        log.info("等价窗口拒绝: code={}, message={}", exception.getErrorCode(), exception.getMessage());
        assertEquals(io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode.POLICY_DUPLICATE_WINDOW,
                exception.getErrorCode(), "等价窗口必须返回 core 稳定错误码");
        assertEquals(0, count("smart_redis_limiter_policy"), "无效窗口不得写入策略主表");
        assertEquals(0, count("smart_redis_limiter_policy_limit"), "无效窗口不得写入窗口表");
        assertEquals(0, count("smart_redis_limiter_policy_revision"), "校验失败不得初始化 revision 行");
    }

    @Test
    public void testConcurrentSameServiceMutationsProduceUniqueMonotonicRevisions() throws Exception {
        int threadCount = 8;
        List<Thread> threads = new ArrayList<>();
        List<Long> revisions = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads.add(new Thread(() -> {
                ready.countDown();
                try {
                    if (!start.await(10L, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("并发创建未按时开始");
                    }
                    SmartRedisLimiterPolicyCreateRequest request = new SmartRedisLimiterPolicyCreateRequest();
                    request.setKey(new SmartRedisLimiterPolicyKey(
                            SERVICE_CODE, "test-resource-" + index, SUBJECT));
                    request.setLimits(Collections.singletonList(
                            new SmartRedisLimiterLimit(10L, 1L, SmartRedisLimiterTimeUnit.SECONDS)));
                    request.setEnabled(true);
                    SmartRedisLimiterPolicyMutationResponse response =
                            managementService.create(request, operator());
                    revisions.add(response.getRevision());
                } catch (Throwable ex) {
                    errors.add(ex);
                } finally {
                    completed.countDown();
                }
            }));
        }
        for (Thread thread : threads) {
            thread.start();
        }
        assertTrue(ready.await(10L, TimeUnit.SECONDS), "所有并发写线程必须在超时前就绪");
        start.countDown();
        assertTrue(completed.await(30L, TimeUnit.SECONDS), "并发写入不得死锁或无限阻塞");
        for (Thread thread : threads) {
            thread.join(1000L);
        }
        log.info("并发 revision 结果: revisions={}, errors={}", revisions, errors.size());
        assertTrue(errors.isEmpty(), "并发创建不应产生未捕获异常");
        Collections.sort(revisions);
        List<Long> distinct = new ArrayList<>(new java.util.LinkedHashSet<>(revisions));
        assertEquals(threadCount, revisions.size(), "每个并发创建都必须返回一个 revision");
        assertEquals(threadCount, distinct.size(), "同服务并发 revision 必须唯一");
        for (int i = 0; i < distinct.size(); i++) {
            assertEquals(Long.valueOf(i + 1L), distinct.get(i),
                    "并发 revision 必须从 1 开始单调递增");
        }
        assertEquals(threadCount, count("smart_redis_limiter_policy"),
                "每个成功并发写入必须各自持久化一条策略");
        assertEquals(threadCount, count("smart_redis_limiter_policy_limit"),
                "每个成功并发写入必须各自持久化完整窗口");
        assertEquals(threadCount, revision(), "最终 revision 必须与同服务有效写入次数精确一致");
        assertEquals(threadCount, eventListener.events.size(),
                "每个并发提交必须在 afterCommit 后恰好发布一个事件");
        assertSnapshot(snapshotService.getSnapshot(SERVICE_CODE), threadCount, threadCount, 10L);
    }

    @Test
    public void testConcurrentSnapshotsAlwaysMatchCommittedRevisionAndFullLimits() throws Exception {
        SmartRedisLimiterPolicyMutationResponse created =
                managementService.create(createRequest(true, 1L), operator());
        long policyId = created.getPolicy().getId();
        int updateCount = 24;
        int readerCount = 4;
        CyclicBarrier start = new CyclicBarrier(readerCount + 1);
        CountDownLatch completed = new CountDownLatch(readerCount + 1);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<SmartRedisLimiterPolicySnapshotView> snapshots = new ConcurrentLinkedQueue<>();

        Thread writer = new Thread(() -> {
            try {
                start.await(10L, TimeUnit.SECONDS);
                long rowVersion = 0L;
                for (int revision = 2; revision <= updateCount + 1; revision++) {
                    SmartRedisLimiterPolicyUpdateRequest request = new SmartRedisLimiterPolicyUpdateRequest();
                    request.setExpectedRowVersion(rowVersion++);
                    request.setLimits(Collections.singletonList(new SmartRedisLimiterLimit(
                            (long) revision, 1L, SmartRedisLimiterTimeUnit.SECONDS)));
                    SmartRedisLimiterPolicyMutationResponse response =
                            managementService.update(policyId, request, operator());
                    assertTrue(response.getChanged(), "每次并发写入必须是有效完整 limits 替换");
                    assertEquals((long) revision, response.getRevision(),
                            "写入 revision 必须按一次有效变更精确递增");
                }
            } catch (Throwable ex) {
                errors.add(ex);
            } finally {
                completed.countDown();
            }
        }, "snapshot-consistency-writer");
        List<Thread> readers = new ArrayList<>();
        for (int index = 0; index < readerCount; index++) {
            Thread reader = new Thread(() -> {
                try {
                    start.await(10L, TimeUnit.SECONDS);
                    for (int attempt = 0; attempt < updateCount * 3; attempt++) {
                        snapshots.add(snapshotService.getSnapshot(SERVICE_CODE));
                    }
                } catch (Throwable ex) {
                    errors.add(ex);
                } finally {
                    completed.countDown();
                }
            }, "snapshot-consistency-reader-" + index);
            readers.add(reader);
        }
        writer.start();
        for (Thread reader : readers) {
            reader.start();
        }
        assertTrue(completed.await(60L, TimeUnit.SECONDS), "并发快照读写不得死锁或无限阻塞");
        writer.join(1000L);
        for (Thread reader : readers) {
            reader.join(1000L);
        }

        log.info("并发快照一致性: snapshots={}, errors={}, finalRevision={}",
                snapshots.size(), errors.size(), revision());
        assertTrue(errors.isEmpty(), "并发读写不得产生未捕获异常: " + errors);
        assertEquals(readerCount * updateCount * 3, snapshots.size(),
                "每个 reader 必须完整返回预定次数的快照");
        for (SmartRedisLimiterPolicySnapshotView view : snapshots) {
            long snapshotRevision = view.getSnapshot().getRevision();
            assertTrue(snapshotRevision >= 1L && snapshotRevision <= updateCount + 1L,
                    "快照 revision 必须属于已提交版本范围");
            assertEquals(1, view.getSnapshot().getPolicies().size(),
                    "每个中间版本必须包含一条完整 enabled 策略");
            assertEquals(1, view.getSnapshot().getPolicies().get(0).getLimits().size(),
                    "每个中间版本必须包含完整 limits，不能读取到删除后的撕裂状态");
            assertEquals(snapshotRevision,
                    view.getSnapshot().getPolicies().get(0).getLimits().get(0).getCount(),
                    "revision 与唯一窗口计数必须同属一次已提交版本，不能拼接旧新状态");
            assertEquals(SmartRedisLimiterEtagHelper.build(SERVICE_CODE, snapshotRevision), view.getEtag(),
                    "ETag 必须只对应本次一致快照 revision");
        }
        SmartRedisLimiterPolicySnapshotView finalSnapshot = snapshotService.getSnapshot(SERVICE_CODE);
        assertEquals(updateCount + 1L, finalSnapshot.getSnapshot().getRevision(),
                "最终快照 revision 必须等于全部有效写入后的版本");
        assertEquals(updateCount + 1L,
                finalSnapshot.getSnapshot().getPolicies().get(0).getLimits().get(0).getCount(),
                "最终快照必须包含最后一次完整替换后的 limits");
    }

    @Test
    public void testListenerFailureDoesNotRollbackCommittedMutation() {
        eventListener.throwOnNext = true;
        SmartRedisLimiterPolicyMutationResponse created =
                managementService.create(createRequest(true, 10L), operator());
        log.info("listener 异常路径创建结果: revision={}, eventCount={}",
                created.getRevision(), eventListener.events.size());
        assertEquals(1L, created.getRevision(), "已提交策略的 revision 不得因 listener 异常回滚");
        assertEquals(1, count("smart_redis_limiter_policy"), "已提交策略行必须持久化");
        assertEquals(0, eventListener.events.size(),
                "抛异常监听器在记录事件前失败时不得伪报已成功消费事件");
    }

    private SmartRedisLimiterPolicyCreateRequest createRequest(boolean enabled, long count) {
        return createRequest(SERVICE_CODE, RESOURCE_CODE, SUBJECT, enabled,
                new SmartRedisLimiterLimit(count, 1L, SmartRedisLimiterTimeUnit.SECONDS));
    }

    private SmartRedisLimiterPolicyCreateRequest createRequest(
            String serviceCode, String resourceCode, String subject, boolean enabled,
            SmartRedisLimiterLimit... limits) {
        SmartRedisLimiterPolicyCreateRequest request = new SmartRedisLimiterPolicyCreateRequest();
        request.setKey(new SmartRedisLimiterPolicyKey(serviceCode, resourceCode, subject));
        request.setLimits(java.util.Arrays.asList(limits));
        request.setEnabled(enabled);
        return request;
    }

    private void assertDatabaseState(long id, boolean enabled, long rowVersion,
                                     int limitRows, long count, long windowSeconds) {
        Boolean actualEnabled = jdbcTemplate.queryForObject(
                "SELECT enabled FROM smart_redis_limiter_policy WHERE id = ?", Boolean.class, id);
        Long actualVersion = jdbcTemplate.queryForObject(
                "SELECT row_version FROM smart_redis_limiter_policy WHERE id = ?", Long.class, id);
        Integer actualLimitRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM smart_redis_limiter_policy_limit WHERE policy_id = ?",
                Integer.class, id);
        Long actualCount = jdbcTemplate.queryForObject(
                "SELECT limit_count FROM smart_redis_limiter_policy_limit WHERE policy_id = ?",
                Long.class, id);
        Long actualWindow = jdbcTemplate.queryForObject(
                "SELECT window_seconds FROM smart_redis_limiter_policy_limit WHERE policy_id = ?",
                Long.class, id);
        log.info("数据库状态: id={}, enabled={}, rowVersion={}, limits={}, count={}, windowSeconds={}, revision={}",
                id, actualEnabled, actualVersion, actualLimitRows, actualCount, actualWindow, revision());
        assertEquals(enabled, actualEnabled, "enabled 必须精确落库");
        assertEquals(rowVersion, actualVersion, "rowVersion 必须精确递增");
        assertEquals(limitRows, actualLimitRows, "完整 limits 行数必须精确一致");
        assertEquals(count, actualCount, "limit_count 必须精确一致");
        assertEquals(windowSeconds, actualWindow, "标准化窗口秒数必须精确一致");
    }

    private void assertSnapshot(SmartRedisLimiterPolicySnapshotView view,
                                long revision, int policyCount, long count) {
        log.info("快照: revision={}, policyCount={}, etag={}",
                view.getSnapshot().getRevision(), view.getSnapshot().getPolicies().size(), view.getEtag());
        assertEquals(revision, view.getSnapshot().getRevision(), "快照 revision 必须与数据库一致");
        assertEquals(policyCount, view.getSnapshot().getPolicies().size(), "快照必须只包含 enabled 策略");
        assertTrue(view.getEtag().startsWith("\"srlm-"), "ETag 必须是强引用标签");
        assertTrue(view.getEtag().endsWith("\""), "ETag 必须以双引号结束");
        if (policyCount > 0) {
            assertEquals(count, view.getSnapshot().getPolicies().get(0).getLimits().get(0).getCount(),
                    "快照 count 必须来自当前完整策略");
            assertEquals(SERVICE_CODE,
                    view.getSnapshot().getPolicies().get(0).getKey().getServiceCode(),
                    "快照策略 serviceCode 必须与顶层一致");
        }
    }

    private void assertEvent(int index, String operation, long revision,
                             Boolean beforeEnabled, Boolean afterEnabled) {
        assertEquals(index + 1, eventListener.events.size(), "每次有效变更必须且只发布一个事件");
        SmartRedisLimiterManagementEvent event = eventListener.events.get(index);
        log.info("事件: operation={}, revision={}, beforeEnabled={}, afterEnabled={}",
                event.getPayload().getOperation(), event.getPayload().getRevision(),
                event.getPayload().getBeforeEnabled(), event.getPayload().getAfterEnabled());
        assertEquals(operation, event.getPayload().getOperation().getCode(), "事件 operation 必须精确匹配");
        assertEquals(revision, event.getPayload().getRevision(), "事件 revision 必须是提交后版本");
        assertEquals(beforeEnabled, event.getPayload().getBeforeEnabled(), "beforeEnabled 必须符合操作矩阵");
        assertEquals(afterEnabled, event.getPayload().getAfterEnabled(), "afterEnabled 必须符合操作矩阵");
        assertEquals(operator(), event.getPayload().getOperator(), "operator 必须来自服务端 Provider");
    }

    private RequestPostProcessor policyToken() {
        return policyToken(managementProperties.getRest().getPolicyToken());
    }

    private RequestPostProcessor policyToken(String token) {
        return request -> {
            request.addHeader(SmartRedisLimiterManagementConstant.HEADER_POLICY_TOKEN, token);
            return request;
        };
    }

    private MockHttpSession loginSession() throws Exception {
        String loginPath = managementProperties.getUi().getBasePath()
                + SmartRedisLimiterManagementConstant.PATH_LOGIN;
        MvcResult login = mockMvc.perform(post(loginPath)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .param("username", operator())
                        .param("password", managementProperties.getAdmin().getPassword()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session, "成功登录必须建立认证会话");
        return session;
    }

    private String operator() {
        return managementProperties.getAdmin().getUsername();
    }

    private long revision() {
        Long revision = jdbcTemplate.queryForObject(
                "SELECT revision FROM smart_redis_limiter_policy_revision WHERE service_code = ?",
                Long.class, SERVICE_CODE);
        return revision == null ? -1L : revision;
    }

    private int count(String table) {
        Integer result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return result == null ? 0 : result;
    }

    private String tableOption(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                String.class, table);
    }

    private String columnCollation(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT collation_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                String.class, table, column);
    }

    private String columnType(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT column_type FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                String.class, table, column);
    }

    private void assertIndex(String table, String index, String expectedColumns, boolean nonUnique) {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? "
                        + "ORDER BY seq_in_index",
                String.class, table, index);
        Integer actualNonUnique = jdbcTemplate.queryForObject(
                "SELECT MIN(non_unique) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, table, index);
        assertEquals(expectedColumns, String.join(",", columns), "索引列及顺序必须稳定");
        assertEquals(nonUnique ? 1 : 0, actualNonUnique, "索引唯一性必须符合 DDL 契约");
    }

    private void executeDdl(Path path) throws IOException {
        String ddl = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        for (String statement : ddl.split(";")) {
            if (!statement.trim().isEmpty()) {
                jdbcTemplate.execute(statement);
            }
        }
    }

    private Path resolveDdlPath() {
        Path rootPath = Paths.get(System.getProperty("user.dir"), DDL_PATH);
        if (Files.exists(rootPath)) {
            return rootPath;
        }
        return Paths.get(System.getProperty("user.dir"), "docs", "mysql-schema.sql");
    }

    /**
     * 测试专用 Bean 配置
     */
    @TestConfiguration
    public static class ManagementEndToEndTestConfiguration {

        /**
         * 注册测试事件监听器
         */
        @Bean
        public TestManagementEventListener testManagementEventListener() {
            return new TestManagementEventListener();
        }
    }

    /**
     * 测试事件监听器
     */
    public static class TestManagementEventListener {
        private final List<SmartRedisLimiterManagementEvent> events =
                Collections.synchronizedList(new ArrayList<>());
        private volatile boolean throwOnNext;

        @EventListener
        public void onEvent(SmartRedisLimiterManagementEvent event) {
            if (throwOnNext) {
                throwOnNext = false;
                throw new IllegalStateException("listener 主动失败");
            }
            events.add(event);
        }

        public void clear() {
            events.clear();
            throwOnNext = false;
        }
    }
}
