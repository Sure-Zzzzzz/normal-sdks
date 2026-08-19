package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ApplicationAuthorizationResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenStatisticsResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.ApplicationAuthorizationTestHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Admin Controller 测试
 * <p>
 * 使用MockMvc测试Admin管理页面的各个功能
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerTestApplication.class)
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private TokenManagementService tokenManagementService;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;

    @Autowired
    private AkskApplicationAuthorizationRepository applicationAuthorizationRepository;

    /**
     * 每个测试方法执行后清理数据
     */
    @AfterEach
    void cleanupData() {
        log.info("清理测试数据...");
        // 先清理 authorization（外键依赖 client），再清理 client
        authorizationEntityRepository.deleteAll();
        applicationAuthorizationRepository.deleteAll();
        clientRepository.deleteAll();

        // 清理Redis中的测试数据
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("清理Redis测试数据: {} 条", keys.size());
        }

        log.info("测试数据清理完成");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAdminIndexPage() throws Exception {
        log.info("测试Admin首页访问");

        // 创建一些测试数据
        ClientInfoResponse c1 = clientManagementService.createPlatformClient("Test Platform Client 1");
        ClientInfoResponse c2 = clientManagementService.createPlatformClient("Test Platform Client 2");

        // 访问Admin首页
        MvcResult result = mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/index"))
                .andExpect(model().attributeExists("clients"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains(c1.getClientId()), "首页列表应包含 Client 1");
        assertTrue(html.contains(c2.getClientId()), "首页列表应包含 Client 2");

        log.info("Admin首页访问测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testClientListRendersMinimalApplicationAuthorizationStatusAndSharedNavigation() throws Exception {
        ClientInfoResponse admittedClient = clientManagementService.createPlatformClient("Admitted Client");
        ClientInfoResponse unconfiguredClient = clientManagementService.createPlatformClient("Unconfigured Client");
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(applicationAuthorizationRepository, admittedClient);

        MvcResult result = mockMvc.perform(get("/admin"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/index"))
                .andExpect(model().attributeExists("authorizationByClientId"))
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, ApplicationAuthorizationResponse> authorizationByClientId =
                (Map<String, ApplicationAuthorizationResponse>) result.getModelAndView().getModel()
                        .get("authorizationByClientId");
        assertNotNull(authorizationByClientId);
        assertTrue(authorizationByClientId.containsKey(admittedClient.getClientId()));
        assertFalse(authorizationByClientId.containsKey(unconfiguredClient.getClientId()));
        ApplicationAuthorizationResponse authorization = authorizationByClientId.get(admittedClient.getClientId());
        assertTrue(authorization.getAdmitted());
        assertTrue(authorization.getEnabled());
        assertEquals(1L, authorization.getAuthorizationVersion().longValue());
        assertNull(authorization.getApiPermissions(), "列表装饰不得携带 API 权限详情");
        assertNull(authorization.getDataGrantDocument(), "列表装饰不得携带 DATA 授权详情");

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("已准入"));
        assertTrue(html.contains("未配置"));
        assertFalse(html.contains("akskClient:create"), "Client 列表不得输出应用授权详情");
        assertNavigationOrder(html);
    }

    @Test
    void testLoginPageDoesNotRenderAuthenticatedConsoleShell() throws Exception {
        String html = mockMvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/login"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("管理员登录"));
        assertFalse(html.contains("admin-sidebar"));
        assertFalse(html.contains("退出登录"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreatePlatformPage() throws Exception {
        log.info("测试创建平台级AKSK页面访问");

        // 访问创建页面
        mockMvc.perform(get("/admin/create-platform"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/create-platform"));

        log.info("创建平台级AKSK页面访问测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreatePlatformFormSubmit() throws Exception {
        log.info("测试创建平台级AKSK表单提交");

        // 提交创建表单
        mockMvc.perform(post("/admin/create-platform")
                        .param("name", "Test Platform Client")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/create-success"))
                .andExpect(flash().attributeExists("success"))
                .andExpect(flash().attributeExists("clientId"))
                .andExpect(flash().attributeExists("clientSecret"));

        log.info("创建平台级AKSK表单提交测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDetailPage() throws Exception {
        log.info("测试查看AKSK详情页面");

        // 创建测试数据
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Detail Client");

        // 访问详情页面
        MvcResult result = mockMvc.perform(get("/admin/" + clientInfo.getClientId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/detail"))
                .andExpect(model().attributeExists("client"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains(clientInfo.getClientId()), "详情页应显示 Client ID");
        assertTrue(html.contains("Test Detail Client"), "详情页应显示 Client 名称");

        log.info("查看AKSK详情页面测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testApplicationAuthorizationAdminLifecycleAndCsrf() throws Exception {
        ClientInfoResponse client = clientManagementService.createPlatformClient("Admin Authorization Client");
        applicationAuthorizationRepository.findByClientId(client.getClientId())
                .ifPresent(applicationAuthorizationRepository::delete);

        MvcResult detailResult = mockMvc.perform(get("/admin/" + client.getClientId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/detail"))
                .andReturn();
        assertTrue(detailResult.getResponse().getContentAsString().contains("未配置"),
                "未配置应用授权必须在 Client 详情中明确显示");

        MvcResult pageResult = mockMvc.perform(get("/admin/" + client.getClientId() + "/authorization"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/application-authorization"))
                .andReturn();
        String pageHtml = pageResult.getResponse().getContentAsString();
        assertTrue(pageHtml.contains("未配置应用授权"), "授权页必须说明新 Client 默认拒绝");
        assertTrue(pageHtml.contains("OAuth2 Scope 不是"), "授权页必须区分 OAuth2 Scope 与应用授权");

        mockMvc.perform(post("/admin/" + client.getClientId() + "/authorization")
                        .param("applicationCode", "admin-application")
                        .param("manifestVersion", "manifest-v1")
                        .param("manifestDigest", "digest-v1")
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertFalse(applicationAuthorizationRepository.findByClientId(client.getClientId()).isPresent(),
                "无效 CSRF token 的提交不得创建授权投影");

        mockMvc.perform(post("/admin/" + client.getClientId() + "/authorization")
                        .param("applicationCode", "admin-application")
                        .param("roles", "roleA,roleB")
                        .param("pagePermissions", "pageA")
                        .param("apiPermissions", "adminResource-read")
                        .param("dataGrantResource", "adminResource")
                        .param("dataGrantActions", "read")
                        .param("dataGrantConstraintGrantIndex", "0")
                        .param("dataGrantConstraintDimension", "ownerUserId")
                        .param("dataGrantConstraintValues", "owner-a,owner-b")
                        .param("manifestVersion", "manifest-v1")
                        .param("manifestDigest", "digest-v1")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/" + client.getClientId() + "/authorization"));

        MvcResult savedPageResult = mockMvc.perform(get("/admin/" + client.getClientId() + "/authorization"))
                .andExpect(status().isOk())
                .andReturn();
        String savedPageHtml = savedPageResult.getResponse().getContentAsString();
        assertTrue(savedPageHtml.contains("未准入"), "未勾选准入时必须保持草稿状态");
        assertTrue(savedPageHtml.contains("admin-application"), "保存后必须回显应用编码");
        assertTrue(savedPageHtml.contains("adminResource"), "保存后必须回显结构化 DATA 授权项");
        assertTrue(savedPageHtml.contains("ownerUserId"), "保存后必须回显结构化 DATA 约束维度");
        assertTrue(savedPageHtml.contains("owner-a,owner-b"), "保存后必须回显结构化 DATA 约束值");
        assertFalse(savedPageHtml.contains("DATA 授权文档 JSON"), "Admin 页面不得提供原始 DATA JSON 直写入口");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testApplicationAuthorizationPageProtectsImmutableCodeAndExplainsRecovery() throws Exception {
        ClientInfoResponse client = clientManagementService.createPlatformClient("Authorization Recovery Client");
        mockMvc.perform(post("/admin/" + client.getClientId() + "/authorization")
                        .param("applicationCode", "original-application")
                        .param("manifestVersion", "manifest-v1")
                        .param("manifestDigest", "digest-v1")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        String savedHtml = mockMvc.perform(get("/admin/" + client.getClientId() + "/authorization"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(savedHtml.contains("应用编码创建后不可修改"), "已有投影必须说明应用编码不可修改");
        assertTrue(savedHtml.contains("readonly"), "已有投影的应用编码必须只读展示");
        assertTrue(savedHtml.contains("name=\"applicationCode\""), "已有投影必须随提交保留原应用编码");
        assertTrue(savedHtml.contains("data-grant-error"), "页面必须提供 DATA 表单校验提示区域");
        assertTrue(savedHtml.contains("data-grant-all-hint"), "页面必须说明全部数据不能添加约束");

        mockMvc.perform(post("/admin/" + client.getClientId() + "/authorization")
                        .param("applicationCode", "changed-application")
                        .param("manifestVersion", "manifest-v1")
                        .param("manifestDigest", "digest-v1")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "应用编码创建后不可修改"));

        mockMvc.perform(post("/admin/" + client.getClientId() + "/authorization/revoke").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "应用授权已撤销"));
        String revokedHtml = mockMvc.perform(get("/admin/" + client.getClientId() + "/authorization"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(revokedHtml.contains("保存完整授权会重新启用投影"), "撤销后必须明确完整保存会恢复投影");
        assertTrue(revokedHtml.contains("恢复并保存完整授权"), "撤销后保存按钮必须表达恢复影响");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUserQueryRoutesRenderReachableDetailLink() throws Exception {
        ClientInfoResponse client = clientManagementService.createUserClient(
                "query-user-id", "query-user", "User Query Client");

        String formHtml = mockMvc.perform(get("/admin/query-user"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/query-user"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(formHtml.contains("/admin/query-user/result"), "用户查询表单必须指向实际结果路由");

        String resultHtml = mockMvc.perform(get("/admin/query-user/result").param("userId", "query-user-id"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/query-user-result"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(resultHtml.contains(client.getClientId()), "查询结果必须包含该用户的 Client");
        assertTrue(resultHtml.contains("/admin/" + client.getClientId()), "查询结果详情链接必须指向实际详情路由");
        assertTrue(resultHtml.contains("未配置"), "无应用授权投影的 Client 必须明确标记未配置");
        assertNavigationOrder(resultHtml);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testClientDetailPageRetainsAllManagementActions() throws Exception {
        ClientInfoResponse client = clientManagementService.createUserClient(
                "action-owner", "action-user", "Action Client");

        String html = mockMvc.perform(get("/admin/" + client.getClientId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/detail"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("编辑名称"));
        assertTrue(html.contains("编辑 OAuth2 Scope"));
        assertTrue(html.contains("编辑归属信息"));
        assertTrue(html.contains("管理应用授权"));
        assertTrue(html.contains("撤销该 Client 的全部活跃 Token"));
        assertTrue(html.contains("重置 Secret"));
        assertTrue(html.contains("****** (已加密，无法查看)"));
        assertNavigationOrder(html);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testTokenAdminPagesDoNotRenderTokenValues() throws Exception {
        ClientInfoResponse client = clientManagementService.createPlatformClient("Token Redaction Client");
        String tokenId = fetchTokenId(client.getClientId(), client.getClientSecret());
        TokenInfoResponse token = tokenManagementService.getTokenById(tokenId);
        assertNotNull(token);

        String listHtml = mockMvc.perform(get("/admin/token"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String detailHtml = mockMvc.perform(get("/admin/token/" + tokenId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenTestHtml = mockMvc.perform(get("/admin/token/test"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/token-test"))
                .andReturn().getResponse().getContentAsString();

        assertThrows(NoSuchFieldException.class, () -> TokenInfoResponse.class.getDeclaredField("tokenValue"),
                "Token 管理响应不得包含 Token 值字段");
        assertFalse(listHtml.contains("tokenValue"), "Token 列表不得显示 Token 值字段");
        assertFalse(detailHtml.contains("tokenValue"), "Token 详情不得显示 Token 值字段");
        assertFalse(tokenTestHtml.contains("copyAccessToken"), "换 Token 页面不得提供 Token 复制功能");
        assertTrue(tokenTestHtml.contains("浏览器内存"), "换 Token 页面必须说明 Token 的内存生命周期");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteClient() throws Exception {
        log.info("测试删除AKSK操作");

        // 创建测试数据
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Delete Client");

        // 删除AKSK - 现在使用DELETE方法调用RESTful API
        mockMvc.perform(delete("/admin/" + clientInfo.getClientId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // 验证已被删除
        assertFalse(clientRepository.findById(clientInfo.getClientId()).isPresent(), "删除后 client 应不存在");

        log.info("删除AKSK操作测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAdminRevokeToken() throws Exception {
        log.info("========== 测试：Admin 撤销 Token ==========");

        // 创建客户端并换取 token
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Revoke Token Client");
        String tokenId = fetchTokenId(clientInfo.getClientId(), clientInfo.getClientSecret());
        assertNotNull(tokenId, "应该能换取到 token");
        log.info("换取 token 成功，authorization id: {}", tokenId);

        // 撤销前状态应为 ACTIVE
        TokenInfoResponse before = tokenManagementService.getTokenById(tokenId);
        assertEquals(TokenInfo.TokenStatus.ACTIVE, before.getStatus(), "撤销前状态应为 ACTIVE");

        // 调用 Admin 撤销接口
        mockMvc.perform(post("/admin/token/" + tokenId + "/revoke")
                        .with(csrf()))
                .andExpect(status().isOk());

        // 撤销后状态应为 REVOKED
        TokenInfoResponse after = tokenManagementService.getTokenById(tokenId);
        assertEquals(TokenInfo.TokenStatus.REVOKED, after.getStatus(), "撤销后状态应为 REVOKED");

        log.info("✓ Admin 撤销 Token 测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAdminDeleteToken() throws Exception {
        log.info("========== 测试：Admin 删除 Token（先撤销再删除）==========");

        // 创建客户端并换取 token
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Delete Token Client");
        String tokenId = fetchTokenId(clientInfo.getClientId(), clientInfo.getClientSecret());
        assertNotNull(tokenId, "应该能换取到 token");

        // 调用 Admin 删除接口
        mockMvc.perform(delete("/admin/token/" + tokenId)
                        .with(csrf()))
                .andExpect(status().isOk());

        // 删除后应查不到
        TokenInfoResponse deleted = tokenManagementService.getTokenById(tokenId);
        assertNull(deleted, "删除后应查不到 token");

        log.info("✓ Admin 删除 Token 测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testTokenDetailPageActiveStatus() throws Exception {
        log.info("========== 测试：Token详情页 - ACTIVE 状态显示 ==========");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Detail Active Client");
        String tokenId = fetchTokenId(clientInfo.getClientId(), clientInfo.getClientSecret());
        assertNotNull(tokenId, "应该能换取到 token");

        MvcResult result = mockMvc.perform(get("/admin/token/" + tokenId))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/token-detail"))
                .andExpect(model().attributeExists("token"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("有效"), "ACTIVE 状态应显示 [有效]");
        assertFalse(html.contains("已撤销"), "ACTIVE 状态不应显示 [已撤销]");
        assertFalse(html.contains("已过期"), "ACTIVE 状态不应显示 [已过期]");

        log.info("✓ Token详情页 ACTIVE 状态显示测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testTokenDetailPageRevokedStatus() throws Exception {
        log.info("========== 测试：Token详情页 - REVOKED 状态显示 ==========");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Detail Revoked Client");
        String tokenId = fetchTokenId(clientInfo.getClientId(), clientInfo.getClientSecret());
        assertNotNull(tokenId, "应该能换取到 token");

        // 撤销 token
        tokenManagementService.revokeToken(tokenId);

        MvcResult result = mockMvc.perform(get("/admin/token/" + tokenId))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/token-detail"))
                .andExpect(model().attributeExists("token"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("已撤销"), "REVOKED 状态应显示 [已撤销]");
        assertFalse(html.contains("有效"), "REVOKED 状态不应显示 [有效]");
        // 撤销后剩余时间行应隐藏，不应出现"已过期"文字
        assertFalse(html.contains("已过期"), "REVOKED 状态不应显示 [已过期]");

        log.info("✓ Token详情页 REVOKED 状态显示测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testTokenDetailPageExpiredStatus() throws Exception {
        log.info("========== 测试：Token详情页 - EXPIRED 状态显示 ==========");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Detail Expired Client");
        String tokenId = createExpiredToken(clientInfo.getClientId());
        assertNotNull(tokenId, "应该能创建过期 token");

        MvcResult result = mockMvc.perform(get("/admin/token/" + tokenId))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/token-detail"))
                .andExpect(model().attributeExists("token"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("已过期"), "EXPIRED 状态应显示 [已过期]");
        assertFalse(html.contains("有效"), "EXPIRED 状态不应显示 [有效]");
        assertFalse(html.contains("已撤销"), "EXPIRED 状态不应显示 [已撤销]");

        log.info("✓ Token详情页 EXPIRED 状态显示测试通过");
    }

    /**
     * 直接构造一个已过期的 token 记录
     */
    private String createExpiredToken(String clientId) {
        try {
            RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
            if (registeredClient == null) return null;

            Instant issuedAt = Instant.now().minusSeconds(7200);
            Instant expiresAt = Instant.now().minusSeconds(3600);

            OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "expired_token_" + UUID.randomUUID().toString(),
                    issuedAt,
                    expiresAt,
                    new HashSet<>(Arrays.asList("read", "write"))
            );

            OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                    .principalName(clientId)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .token(accessToken)
                    .build();

            authorizationService.save(authorization);
            return authorization.getId();
        } catch (Exception e) {
            log.error("创建过期 token 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testTokenDetailPageExpiredRevokeButtonDisabled() throws Exception {
        log.info("========== 测试：Token详情页 - EXPIRED 状态撤销按钮禁用 ==========");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Expired Revoke Btn Client");
        String tokenId = createExpiredToken(clientInfo.getClientId());
        assertNotNull(tokenId, "应该能创建过期 token");

        MvcResult result = mockMvc.perform(get("/admin/token/" + tokenId))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        // 检查撤销按钮是否包含 disabled 属性（更精确：匹配按钮上的 disabled）
        assertTrue(html.contains("撤销此Token") && html.contains("disabled"), "EXPIRED 状态撤销按钮应被禁用");
        assertTrue(html.contains("已过期的Token无需撤销"), "EXPIRED 状态应有 tooltip 提示");

        log.info("✓ Token详情页 EXPIRED 状态撤销按钮禁用测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testTokenListRevokedFilter() throws Exception {
        log.info("========== 测试：Token列表页 REVOKED 状态过滤 ==========");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Revoked Filter Client");
        String tokenId = fetchTokenId(clientInfo.getClientId(), clientInfo.getClientSecret());
        assertNotNull(tokenId, "应该能换取到 token");

        tokenManagementService.revokeToken(tokenId);

        MvcResult result = mockMvc.perform(get("/admin/token").param("status", "REVOKED"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/token"))
                .andExpect(model().attributeExists("tokens"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains(tokenId), "REVOKED 过滤结果应包含被撤销的 token");

        log.info("✓ Token列表页 REVOKED 过滤测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testClientDetailPageShowsAllFields() throws Exception {
        log.info("========== 测试：Client详情页显示完整字段 ==========");

        ClientInfoResponse clientInfo = clientManagementService.createUserClient(
                "user-001", "testuser", "Test Detail Fields Client");

        MvcResult result = mockMvc.perform(get("/admin/" + clientInfo.getClientId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/detail"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("已启用"), "详情页应显示启用状态");
        assertTrue(html.contains("testuser"), "详情页应显示 ownerUsername");
        assertTrue(html.contains("user-001"), "详情页应显示 ownerUserId");

        log.info("✓ Client详情页完整字段显示测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testClientListEnabledFilter() throws Exception {
        log.info("========== 测试：Client列表页 enabled 过滤 ==========");

        ClientInfoResponse c1 = clientManagementService.createPlatformClient("Enabled Filter Client 1");
        ClientInfoResponse c2 = clientManagementService.createPlatformClient("Enabled Filter Client 2");
        clientManagementService.disableClient(c2.getClientId());

        MvcResult result = mockMvc.perform(get("/admin").param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/index"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains(c2.getClientId()), "已禁用的 client 应出现在结果中");
        assertFalse(html.contains(c1.getClientId()), "已启用的 client 不应出现在已禁用过滤结果中");

        log.info("✓ Client列表页 enabled 过滤测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateClientName() throws Exception {
        log.info("========== 测试：更新 Client 名称 ==========");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Original Name");

        mockMvc.perform(patch("/admin/" + clientInfo.getClientId())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Name\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("名称更新成功"));

        ClientInfoResponse updated = clientManagementService.getClientById(clientInfo.getClientId());
        assertEquals("Updated Name", updated.getClientName(), "名称应已更新");

        log.info("✓ 更新 Client 名称测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteClientRevokesTokensFirst() throws Exception {
        log.info("========== 测试：删除 Client 时先撤销关联 Token ==========");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Delete Revoke Client");
        String tokenId = fetchTokenId(clientInfo.getClientId(), clientInfo.getClientSecret());
        assertNotNull(tokenId, "应该能换取到 token");

        TokenInfoResponse before = tokenManagementService.getTokenById(tokenId);
        assertEquals(TokenInfo.TokenStatus.ACTIVE, before.getStatus(), "删除前 token 应为 ACTIVE");

        mockMvc.perform(delete("/admin/" + clientInfo.getClientId()).with(csrf()))
                .andExpect(status().isOk());

        TokenInfoResponse after = tokenManagementService.getTokenById(tokenId);
        assertNotNull(after, "token 记录应仍存在");
        assertEquals(TokenInfo.TokenStatus.REVOKED, after.getStatus(), "删除 client 后 token 应为 REVOKED");

        log.info("✓ 删除 Client 时先撤销关联 Token 测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testStatisticsCountsMatchActual() throws Exception {
        log.info("========== 测试：getStatistics 统计数量正确 ==========");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Statistics Client");
        String activeTokenId = fetchTokenId(clientInfo.getClientId(), clientInfo.getClientSecret());
        assertNotNull(activeTokenId);

        String revokedTokenId = fetchTokenId(clientInfo.getClientId(), clientInfo.getClientSecret());
        assertNotNull(revokedTokenId);
        tokenManagementService.revokeToken(revokedTokenId);

        String expiredTokenId = createExpiredToken(clientInfo.getClientId());
        assertNotNull(expiredTokenId);

        TokenStatisticsResponse stats = tokenManagementService.getStatistics();
        assertTrue(stats.getActiveCount() >= 1, "至少有 1 个 ACTIVE token");
        assertTrue(stats.getRevokedCount() >= 1, "至少有 1 个 REVOKED token");
        assertTrue(stats.getExpiredCount() >= 1, "至少有 1 个 EXPIRED token");
        // 由于 @AfterEach 已清理 authorization 数据，不应有残留影响
        assertEquals(stats.getTotalCount(), stats.getActiveCount() + stats.getRevokedCount() + stats.getExpiredCount(),
                "总数应等于三种状态之和");

        log.info("✓ getStatistics 统计数量正确测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testTokenDetailPageRevokedRevokeButtonDisabled() throws Exception {
        log.info("========== 测试：Token详情页 - REVOKED 状态撤销按钮禁用 ==========");

        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Test Revoked Revoke Btn Client");
        String tokenId = fetchTokenId(clientInfo.getClientId(), clientInfo.getClientSecret());
        assertNotNull(tokenId, "应该能换取到 token");

        tokenManagementService.revokeToken(tokenId);

        MvcResult result = mockMvc.perform(get("/admin/token/" + tokenId))
                .andExpect(status().isOk())
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("撤销此Token") && html.contains("disabled"), "REVOKED 状态撤销按钮应被禁用");

        log.info("✓ Token详情页 REVOKED 状态撤销按钮禁用测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAdminResetSecretResponseDoesNotExposeSecret() throws Exception {
        ClientInfoResponse clientInfo = clientManagementService.createPlatformClient("Admin Reset Secret Client");
        MvcResult result = mockMvc.perform(put("/admin/" + clientInfo.getClientId() + "/secret")
                        .param("revokeTokens", "true")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String responseBody = result.getResponse().getContentAsString();
        assertTrue(responseBody.contains(clientInfo.getClientId()), "重置响应应保留 Client ID");
        assertFalse(responseBody.contains("clientSecret"), "重置响应不得包含 Secret 字段");
        assertFalse(responseBody.contains("client-secret"), "重置响应不得包含 Secret 字段");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateSuccessPageReadsAndClearsSessionSecret() throws Exception {
        log.info("========== 测试：create-success 页面从会话一次性读取 Secret ==========");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("admin.createSuccess.clientId", "test-client-id-123");
        session.setAttribute("admin.createSuccess.clientSecret", "test-secret-456");
        session.setAttribute("admin.createSuccess.message", "Secret 重置成功！");

        MvcResult result = mockMvc.perform(get("/admin/create-success").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/create-success"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("test-client-id-123"), "页面应显示 clientId");
        assertTrue(html.contains("test-secret-456"), "页面应显示 clientSecret");
        assertTrue(html.contains("Secret 重置成功"), "页面应显示重置成功提示");
        assertNull(session.getAttribute("admin.createSuccess.clientSecret"),
                "页面读取后应清除会话中的一次性 Secret");

        MvcResult secondResult = mockMvc.perform(get("/admin/create-success").session(session))
                .andExpect(status().isOk())
                .andReturn();
        assertFalse(secondResult.getResponse().getContentAsString().contains("test-secret-456"),
                "刷新页面不得再次显示一次性 Secret");
        log.info("✓ create-success 页面一次性会话交付测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateSuccessPageDynamicTitle() throws Exception {
        log.info("========== 测试：create-success 页面动态标题 ==========");

        MockHttpSession resetSession = new MockHttpSession();
        resetSession.setAttribute("admin.createSuccess.clientId", "test-id");
        resetSession.setAttribute("admin.createSuccess.clientSecret", "test-secret");
        resetSession.setAttribute("admin.createSuccess.message", "Secret 重置成功！请妥善保存。");
        MvcResult resetResult = mockMvc.perform(get("/admin/create-success").session(resetSession))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(resetResult.getResponse().getContentAsString().contains("Secret 重置成功"),
                "重置 Secret 时标题应显示 'Secret 重置成功'");

        MockHttpSession createSession = new MockHttpSession();
        createSession.setAttribute("admin.createSuccess.clientId", "test-id");
        createSession.setAttribute("admin.createSuccess.clientSecret", "test-secret");
        createSession.setAttribute("admin.createSuccess.message", "AKSK 创建成功");
        MvcResult createResult = mockMvc.perform(get("/admin/create-success").session(createSession))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(createResult.getResponse().getContentAsString().contains("AKSK创建成功"),
                "创建 AKSK 时标题应显示 'AKSK创建成功'");

        log.info("✓ create-success 页面动态标题测试通过");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testClientListEnabledFilterTrue() throws Exception {
        log.info("========== 测试：Client列表页 enabled=true 过滤 ==========");

        ClientInfoResponse c1 = clientManagementService.createPlatformClient("Enabled Filter True Client 1");
        ClientInfoResponse c2 = clientManagementService.createPlatformClient("Enabled Filter True Client 2");
        clientManagementService.disableClient(c2.getClientId());

        MvcResult result = mockMvc.perform(get("/admin").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/index"))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains(c1.getClientId()), "已启用的 client 应出现在已启用过滤结果中");
        assertFalse(html.contains(c2.getClientId()), "已禁用的 client 不应出现在已启用过滤结果中");

        log.info("✓ Client列表页 enabled=true 过滤测试通过");
    }


    private void assertNavigationOrder(String html) {
        int clientManagement = html.indexOf("Client 管理");
        int applicationAuthorization = html.indexOf("应用授权");
        int tokenManagement = html.indexOf("Token 管理");
        int tokenTest = html.indexOf("Token 测试");
        int securityContext = html.indexOf("安全上下文");
        assertTrue(clientManagement >= 0, "控制台必须提供 Client 管理导航");
        assertTrue(applicationAuthorization > clientManagement, "应用授权必须位于 Client 管理之后");
        assertTrue(tokenManagement > applicationAuthorization, "Token 管理必须位于应用授权之后");
        assertTrue(tokenTest > tokenManagement, "Token 测试必须位于 Token 管理之后");
        assertTrue(securityContext > tokenTest, "安全上下文必须位于 Token 测试之后");
    }

    /**
     * 换取 token 并返回 authorization id
     */
    private String fetchTokenId(String clientId, String clientSecret) throws Exception {
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(applicationAuthorizationRepository, clientId);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/oauth2/token")
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                        .httpBasic(clientId, clientSecret))
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .params(body))
                .andExpect(status().isOk())
                .andReturn();

        TokenQueryRequest queryRequest = new TokenQueryRequest();
        queryRequest.setClientId(clientId);
        queryRequest.setPage(1);
        queryRequest.setSize(10);
        PageResponse<TokenInfoResponse> page = tokenManagementService.queryTokens(queryRequest);

        assertFalse(page.getData().isEmpty(), "换取 Token 后必须存在 Access Token 授权记录");
        return page.getData().get(0).getId();
    }
}
