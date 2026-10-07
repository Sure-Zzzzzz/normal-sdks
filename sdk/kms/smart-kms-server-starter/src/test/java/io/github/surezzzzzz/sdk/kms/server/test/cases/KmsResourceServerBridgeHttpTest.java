package io.github.surezzzzzz.sdk.kms.server.test.cases;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.auth.resource.server.support.VerifiedResourceAuthentication;
import io.github.surezzzzzz.sdk.kms.server.test.support.KmsTestSchemaHelper;
import io.github.surezzzzzz.sdk.kms.server.testapp.bridge.KmsResourceServerBridgeTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 组合式 Resource Server 认证桥 HTTP 集成测试：桥读取公共层认证态并贯通 KMS 领域链。
 *
 * @author surezzzzzz
 */
@Slf4j
@AutoConfigureMockMvc
@SpringBootTest(classes = KmsResourceServerBridgeTestApplication.class)
class KmsResourceServerBridgeHttpTest {

    private static final String SOURCE_ID = "aksk";
    private static final String SUBJECT_ID = "svc-bridge-test";
    private static final String COMPOSED_PRINCIPAL_ID = "aksk:svc-bridge-test";
    private static final String OWNER_PRINCIPAL_ID = COMPOSED_PRINCIPAL_ID;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DataSource dataSource;

    /**
     * 每个用例均从可销毁的 MySQL 结构开始。
     */
    @BeforeEach
    void resetSchema() {
        SecurityContextHolder.clearContext();
        KmsTestSchemaHelper.reset(dataSource);
    }

    /**
     * 每个用例结束清理认证态，避免泄漏到其他测试类。
     */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 直接向 {@code SecurityContextHolder} 注入公共层认证态：桥的真实读取入口就是
     * {@code SecurityContextHolder}（MockMvc 未挂公共层 FilterChain，spring-security-test
     * 的 {@code securityContext()} post processor 不会填充它）。
     */
    private void bridgeIdentity() {
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.SERVICE, SUBJECT_ID, "kms-test-app", true,
                Collections.<String>emptyList(), Collections.singletonList("kms.page.keys"),
                Arrays.asList("kms.me.read", "kms.key.read", "kms.key.manage", "kms.key.policy", "kms.key.destroy",
                        "kms.sign", "kms.verify", "kms.encrypt", "kms.decrypt", "kms.read-public-key"),
                new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                        Collections.singletonList(new DataGrant("kms-key", Arrays.asList("read", "manage", "policy", "destroy"), false,
                                Collections.singletonList(new DataConstraint("ownerPrincipalId", DataConstraintOperator.IN,
                                        Collections.singletonList(OWNER_PRINCIPAL_ID)))))),
                1L, "manifest-v1", "manifest-digest-0123456789abcdef",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(600));
        VerifiedResourceContext context = new VerifiedResourceContext(
                new VerifiedResourcePrincipal(new ResourceAuthenticationSourceId(SOURCE_ID),
                        ResourceSubjectType.SERVICE, SUBJECT_ID),
                authorization, "req-bridge-000000000001");
        SecurityContextHolder.getContext().setAuthentication(new VerifiedResourceAuthentication(context));
    }

    /**
     * 验证无公共层认证态时创建端点 fail-closed。
     *
     * <p>创建豁免 DataPlan（归属服务端固定为本人），无认证直接落在控制器主体校验，
     * MockMvc 与真实部署（Resource Filter Bearer 质询）一致返回 401。</p>
     */
    @Test
    void shouldRejectUnauthenticatedRequestWithBearerChallenge() throws Exception {
        mockMvc.perform(post("/api/kms/keys")
                        .header("Idempotency-Key", "bridge-idempotency-key-000000000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyAlias\":\"bridge-key\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 验证门户自省复用认证桥的主体翻译结果，并只透传页面权限投影。
     */
    @Test
    void shouldExposeBridgeIdentityAndPagePermissionsThroughMe() throws Exception {
        bridgeIdentity();

        mockMvc.perform(get("/api/kms/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalId").value(COMPOSED_PRINCIPAL_ID))
                .andExpect(jsonPath("$.subjectType").value("SERVICE"))
                .andExpect(jsonPath("$.scopes").value(org.hamcrest.Matchers.hasItem("kms.me.read")))
                .andExpect(jsonPath("$.pagePermissions[0]").value("kms.page.keys"))
                .andExpect(jsonPath("$.subjectId").doesNotExist())
                .andExpect(jsonPath("$.dataGrantDocument").doesNotExist());
    }

    /**
     * 验证门户自省在不存在公共层认证态时沿用 KMS 的统一未认证响应。
     */
    @Test
    void shouldRejectUnauthenticatedMeRequestWithBearerChallenge() throws Exception {
        mockMvc.perform(get("/api/kms/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    /**
     * 验证公共层认证态经桥贯通 KMS 领域链：建钥、合成主体 policy、签名、验签。
     */
    @Test
    void shouldCreateKeyAndSignVerifyThroughBridge() throws Exception {
        bridgeIdentity();
        MvcResult created = mockMvc.perform(post("/api/kms/keys")
                        .header("Idempotency-Key", "bridge-idempotency-key-000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyAlias\":\"bridge-key\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String keyRef = OBJECT_MAPPER.readTree(created.getResponse().getContentAsString())
                .get("keyRef").textValue();
        log.info("桥链路建钥完成: {}", keyRef);

        createExactPolicy(keyRef, "SIGN", "bridge-idempotency-key-000000002");
        createExactPolicy(keyRef, "VERIFY", "bridge-idempotency-key-000000003");

        MvcResult signed = mockMvc.perform(post("/api/kms/crypto/signatures")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyRef\":\"" + keyRef + "\",\"input\":\"aGVsbG8\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyRef").value(keyRef))
                .andReturn();
        String signature = OBJECT_MAPPER.readTree(signed.getResponse().getContentAsString())
                .get("signature").textValue();

        mockMvc.perform(post("/api/kms/crypto/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyRef\":\"" + keyRef + "\",\"version\":1,\"input\":\"aGVsbG8\","
                                + "\"signature\":\"" + signature + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        log.info("桥链路签名验签闭环完成，合成主体: {}", COMPOSED_PRINCIPAL_ID);
    }

    private void createExactPolicy(String keyRef, String operation, String idempotencyKey) throws Exception {
        mockMvc.perform(post("/api/kms/keys/{keyRef}/policies", keyRef)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalId\":\"" + COMPOSED_PRINCIPAL_ID
                                + "\",\"keyVersion\":1,\"operation\":\"" + operation + "\"}"))
                .andExpect(status().isCreated());
    }
}
