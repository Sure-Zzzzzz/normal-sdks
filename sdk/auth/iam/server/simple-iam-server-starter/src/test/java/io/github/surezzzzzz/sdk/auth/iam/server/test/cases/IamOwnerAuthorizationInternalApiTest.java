package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.service.bootstrap.IamInternalReaderBootstrap;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AKSK owner 授权内部 reader 的认证边界测试。
 *
 * <p>真实走 SAS client_credentials、JWE 解码和独立 SecurityFilterChain，
 * 不用 mock Authentication 绕过 fixed SERVICE claim 校验。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class, properties = {
        "io.github.surezzzzzz.sdk.auth.iam.server.internal-reader.enabled=true",
        "io.github.surezzzzzz.sdk.auth.iam.server.internal-reader.client-secret=Reader-Test-Only@2026"
})
@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
class IamOwnerAuthorizationInternalApiTest {

    private static final String API = "/iam/internal/aksk/owner-authorizations/candidate-applications";
    private static final String RESOLVE_API = "/iam/internal/aksk/owner-authorizations/resolve";
    private static final String CHANGE_PULL_API = "/iam/internal/aksk/owner-authorizations/changes/pull";
    private static final String READER_SECRET = "Reader-Test-Only@2026";
    private static final String ROTATED_READER_SECRET = "Reader-Rotated@2026";

    private final String nonReaderClientId = "internal-reader-negative-"
            + UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IamInternalReaderBootstrap internalReaderBootstrap;

    @Autowired
    private SimpleIamServerProperties properties;

    @BeforeEach
    void resetFixedReaderForTheTest() {
        log.info("重建固定 IAM 内部 reader 测试客户端");
        jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id IN "
                        + "(SELECT id FROM oauth2_registered_client WHERE client_id = ?)",
                IamInternalReaderBootstrap.READER_CLIENT_ID);
        jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE client_id = ?",
                IamInternalReaderBootstrap.READER_CLIENT_ID);
        internalReaderBootstrap.run(new org.springframework.boot.DefaultApplicationArguments());
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM oauth2_authorization WHERE registered_client_id IN "
                + "(SELECT id FROM oauth2_registered_client WHERE client_id = ?)", nonReaderClientId);
        jdbcTemplate.update("DELETE FROM oauth2_registered_client WHERE client_id = ?", nonReaderClientId);
    }

    @Test
    @DisplayName("内部 reader API 无 Bearer 必须返回 401，不允许浏览器会话降级访问")
    void missingBearerMustBeUnauthorized() throws Exception {
        log.info("验证内部 reader API 缺失 Bearer 时拒绝访问");
        mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerSourceId\":\"local-iam\",\"ownerSubjectId\":\"1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("固定 reader SERVICE 的真实短时 token 可读取目录，响应禁止缓存")
    void fixedReaderTokenCanReadCandidateDirectory() throws Exception {
        log.info("验证固定 reader SERVICE 可读取候选应用目录且响应禁止缓存");
        String token = accessToken(IamInternalReaderBootstrap.READER_CLIENT_ID, READER_SECRET);

        mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"ownerSourceId\":\"local-iam\",\"ownerSubjectId\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("reader 部署密钥轮换后重启必须覆盖旧 hash")
    void readerBootstrapMustRefreshDeployedSecret() throws Exception {
        log.info("验证 reader 部署密钥轮换后无需人工删除注册记录");
        properties.getInternalReader().setClientSecret(ROTATED_READER_SECRET);
        try {
            internalReaderBootstrap.run(new org.springframework.boot.DefaultApplicationArguments());

            RegisteredClient persisted = registeredClientRepository.findByClientId(
                    IamInternalReaderBootstrap.READER_CLIENT_ID);
            assertNotNull(persisted, "reader 注册客户端必须存在");
            assertFalse(passwordEncoder.matches(READER_SECRET, persisted.getClientSecret()),
                    "部署密钥轮换后数据库不得继续接受旧密钥");
            assertTrue(passwordEncoder.matches(ROTATED_READER_SECRET, persisted.getClientSecret()),
                    "部署密钥轮换后数据库必须接受新密钥");

            mockMvc.perform(post("/oauth2/token")
                            .with(httpBasic(IamInternalReaderBootstrap.READER_CLIENT_ID, READER_SECRET))
                            .param("grant_type", "client_credentials")
                            .param("scope", IamInternalReaderBootstrap.READER_SCOPE))
                    .andExpect(status().isUnauthorized());
            accessToken(IamInternalReaderBootstrap.READER_CLIENT_ID, ROTATED_READER_SECRET);
        } finally {
            properties.getInternalReader().setClientSecret(READER_SECRET);
            internalReaderBootstrap.run(new org.springframework.boot.DefaultApplicationArguments());
        }
    }

    @Test
    @DisplayName("同 scope 的普通 OAuth client 也必须被固定 reader claim 校验拒绝")
    void ordinaryClientWithSameScopeMustBeUnauthorized() throws Exception {
        log.info("验证普通 OAuth client 即使申请同一 scope 也不能访问内部 reader API");
        registeredClientRepository.save(RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(nonReaderClientId)
                .clientSecret(passwordEncoder.encode("Not-The-Reader@2026"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(IamInternalReaderBootstrap.READER_SCOPE)
                .build());
        String token = accessToken(nonReaderClientId, "Not-The-Reader@2026");

        mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"ownerSourceId\":\"local-iam\",\"ownerSubjectId\":\"1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("stream scope 只能拉取变更日志，read scope 不能越权读取日志")
    void streamScopeMustBeIsolatedFromReadScope() throws Exception {
        log.info("验证 reader 的目录读取 scope 与变更日志 scope 双向隔离");
        String readerToken = accessToken(IamInternalReaderBootstrap.READER_CLIENT_ID,
                READER_SECRET, IamInternalReaderBootstrap.READER_SCOPE);
        String streamToken = accessToken(IamInternalReaderBootstrap.READER_CLIENT_ID,
                READER_SECRET, IamInternalReaderBootstrap.STREAM_SCOPE);

        mockMvc.perform(post(CHANGE_PULL_API).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + readerToken)
                        .content("{\"afterSequence\":0,\"pageSize\":1}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(CHANGE_PULL_API).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + streamToken)
                        .content("{\"afterSequence\":0,\"pageSize\":1}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + streamToken)
                        .content("{\"ownerSourceId\":\"local-iam\",\"ownerSubjectId\":\"1\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("resolve 对有效投影必须返回 active 并携带 ownerUsername 作为 AKU 归属真值")
    void resolveMustCarryOwnerUsernameForActiveProjection() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "owner-reader-" + suffix;
        String applicationCode = "owner-reader-app-" + suffix;
        jdbcTemplate.update("INSERT INTO iam_user (username, password_hash, status, permission_version) "
                + "VALUES (?, ?, 1, 0)", username, "Reader-Test-Only@2026");
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM iam_user WHERE username = ?", Long.class, username);
        jdbcTemplate.update("INSERT INTO iam_trusted_application (application_code, application_name, status) "
                + "VALUES (?, ?, 1)", applicationCode, "owner reader acceptance");
        Long applicationId = jdbcTemplate.queryForObject(
                "SELECT id FROM iam_trusted_application WHERE application_code = ?", Long.class, applicationCode);
        jdbcTemplate.update("INSERT INTO iam_application_authorization_state "
                + "(application_id, authorization_epoch, owner_inherited_access_epoch, owner_inheritance_enabled, "
                + "recompute_barrier) VALUES (?, 1, 1, 1, 0)", applicationId);
        jdbcTemplate.update("INSERT INTO iam_application_authorization "
                        + "(user_id, application_id, admitted, roles_json, page_permissions_json, api_permissions_json, "
                        + "authorization_version, owner_security_epoch, application_authorization_epoch, "
                        + "projection_access_epoch, manifest_version, manifest_digest, status) "
                        + "VALUES (?, ?, 1, '[]', '[]', '[\"order.read\"]', 1, 1, 1, 1, 'reader-v1', 'reader-digest', 1)",
                userId, applicationId);
        try {
            String token = accessToken(IamInternalReaderBootstrap.READER_CLIENT_ID, READER_SECRET);

            MvcResult result = mockMvc.perform(post(RESOLVE_API).contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content("{\"ownerSourceId\":\"local-iam\",\"ownerSubjectId\":\"" + userId
                                    + "\",\"targetApplicationId\":" + applicationId + "}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            log.info("resolve 响应：active={}, ownerUsername={}",
                    body.path("active").asBoolean(), body.path("ownerUsername").asText());
            assertTrue(body.path("active").asBoolean(), "有效投影的 resolve 必须返回 active=true");
            assertEquals(username, body.path("ownerUsername").asText(),
                    "resolve 必须携带 iam_user.username 作为 ownerUsername，AKU 归属展示以此为准");
        } finally {
            jdbcTemplate.update("DELETE FROM iam_application_authorization WHERE user_id = ? AND application_id = ?",
                    userId, applicationId);
            jdbcTemplate.update("DELETE FROM iam_application_authorization_state WHERE application_id = ?",
                    applicationId);
            jdbcTemplate.update("DELETE FROM iam_trusted_application WHERE id = ?", applicationId);
            jdbcTemplate.update("DELETE FROM iam_user WHERE id = ?", userId);
        }
    }

    private String accessToken(String clientId, String clientSecret) throws Exception {
        return accessToken(clientId, clientSecret, IamInternalReaderBootstrap.READER_SCOPE);
    }

    private String accessToken(String clientId, String clientSecret, String scope) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(clientId, clientSecret))
                        .param("grant_type", "client_credentials")
                        .param("scope", scope))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = body.path("access_token").asText();
        assertNotNull(token, "client_credentials 必须签发 access_token");
        return token;
    }
}
