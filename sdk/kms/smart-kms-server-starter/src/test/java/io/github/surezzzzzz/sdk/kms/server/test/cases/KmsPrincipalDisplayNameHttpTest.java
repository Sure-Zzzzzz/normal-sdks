package io.github.surezzzzzz.sdk.kms.server.test.cases;

import io.github.surezzzzzz.sdk.kms.server.service.KmsPrincipalDisplayNameResolver;
import io.github.surezzzzzz.sdk.kms.server.test.SmartKmsServerTestApplication;
import io.github.surezzzzzz.sdk.kms.server.test.support.KmsTestSchemaHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 主体显示名解析端口 HTTP 行为：宿主提供目录实现时列表与详情输出显示名。
 *
 * @author surezzzzzz
 */
@AutoConfigureMockMvc
@SpringBootTest(classes = {SmartKmsServerTestApplication.class,
        KmsPrincipalDisplayNameHttpTest.DisplayNameConfiguration.class})
class KmsPrincipalDisplayNameHttpTest {

    private static final String OWNER_PRINCIPAL_ID = "display-owner";
    private static final String PRINCIPAL_ID = "test-principal";
    private static final String REQUEST_ID = "test-request-id-000000000009";
    private static final String DISPLAY_OWNER_NAME = "目录-展示租户";
    private static final String DISPLAY_SUBJECT_NAME = "目录-被授权方";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetSchema() {
        KmsTestSchemaHelper.reset(dataSource);
    }

    /**
     * 管理列表按归属筛选时输出宿主目录解析出的显示名。
     */
    @Test
    void shouldExposeOwnerDisplayNameOnAdminKeyList() throws Exception {
        mockMvc.perform(post("/api/kms/keys")
                        .header("X-Test-Owner-Principal", OWNER_PRINCIPAL_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", "test-idempotency-key-display-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyAlias\":\"display-signing-key\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/kms/admin/keys").param("page", "1").param("size", "20")
                        .param("ownerPrincipalId", OWNER_PRINCIPAL_ID)
                        .header("X-Test-Owner-Principal", OWNER_PRINCIPAL_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].ownerPrincipalId").value(OWNER_PRINCIPAL_ID))
                .andExpect(jsonPath("$.items[0].ownerDisplayName").value(DISPLAY_OWNER_NAME));
    }

    /**
     * 策略列表输出被授权主体的显示名。
     */
    @Test
    void shouldExposePrincipalDisplayNameOnPolicyList() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/kms/keys")
                        .header("X-Test-Owner-Principal", OWNER_PRINCIPAL_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", "test-idempotency-key-display-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyAlias\":\"display-policy-key\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String keyRef = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("keyRef").textValue();
        mockMvc.perform(post("/api/kms/keys/{keyRef}/policies", keyRef)
                        .header("X-Test-Owner-Principal", OWNER_PRINCIPAL_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", "test-idempotency-key-display-0003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalId\":\"display-subject\",\"operation\":\"SIGN\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/kms/keys/{keyRef}/policies", keyRef)
                        .header("X-Test-Owner-Principal", OWNER_PRINCIPAL_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].principalId").value("display-subject"))
                .andExpect(jsonPath("$.items[0].principalDisplayName").value(DISPLAY_SUBJECT_NAME));
    }

    /**
     * 跨钥策略列表输出归属与被授权主体的显示名。
     */
    @Test
    void shouldExposeDisplayNamesOnAdminPolicyList() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/kms/keys")
                        .header("X-Test-Owner-Principal", OWNER_PRINCIPAL_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", "test-idempotency-key-display-0004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyAlias\":\"display-list-policy-key\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String keyRef = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("keyRef").textValue();
        mockMvc.perform(post("/api/kms/keys/{keyRef}/policies", keyRef)
                        .header("X-Test-Owner-Principal", OWNER_PRINCIPAL_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", "test-idempotency-key-display-0005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalId\":\"display-subject\",\"operation\":\"SIGN\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/kms/admin/policies").param("page", "1").param("size", "20")
                        .header("X-Test-Owner-Principal", OWNER_PRINCIPAL_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].keyAlias").value("display-list-policy-key"))
                .andExpect(jsonPath("$.items[0].ownerDisplayName").value(DISPLAY_OWNER_NAME))
                .andExpect(jsonPath("$.items[0].principalDisplayName").value(DISPLAY_SUBJECT_NAME));
    }

    /**
     * 测试目录桩：仅解析两个已知主体，未命中返回空以保持回退语义。
     */
    @TestConfiguration
    static class DisplayNameConfiguration {

        @Bean
        public KmsPrincipalDisplayNameResolver displayNameResolver() {
            return principalId -> OWNER_PRINCIPAL_ID.equals(principalId) ? DISPLAY_OWNER_NAME
                    : "display-subject".equals(principalId) ? DISPLAY_SUBJECT_NAME : null;
        }
    }
}
