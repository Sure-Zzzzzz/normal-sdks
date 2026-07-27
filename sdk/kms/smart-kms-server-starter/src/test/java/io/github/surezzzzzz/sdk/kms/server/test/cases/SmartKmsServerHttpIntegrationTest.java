package io.github.surezzzzzz.sdk.kms.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsAlgorithm;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyPurpose;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.model.KmsKey;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsClock;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsDestructionJobRepository;
import io.github.surezzzzzz.sdk.kms.core.service.KeyManagementService;
import io.github.surezzzzzz.sdk.kms.server.service.KmsManagementIdempotencyResult;
import io.github.surezzzzzz.sdk.kms.server.service.KmsManagementIdempotencyService;
import io.github.surezzzzzz.sdk.kms.server.test.SmartKmsServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Smart KMS Server HTTP 与 MySQL 集成测试。
 *
 * @author surezzzzzz
 */
@Slf4j
@AutoConfigureMockMvc
@SpringBootTest(classes = SmartKmsServerTestApplication.class)
class SmartKmsServerHttpIntegrationTest {

    private static final String TENANT_ID = "test-tenant";
    private static final String PRINCIPAL_ID = "test-principal";
    private static final String REQUEST_ID = "test-request-id-000000000001";
    private static final String IDEMPOTENCY_KEY = "test-idempotency-key-000000001";
    private static final String CREATE_KEY_BODY = "{\"keyAlias\":\"test-signing-key\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}";
    private static final String POLICY_IDEMPOTENCY_KEY = "test-idempotency-key-000000002";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private DataSource dataSource;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private KmsClock clock;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private KmsDestructionJobRepository destructionJobRepository;
    @Autowired
    private KmsManagementIdempotencyService idempotencyService;
    @Autowired
    private KeyManagementService keyManagementService;

    /**
     * 取得并发 HTTP 调用结果。
     */
    private static MvcResult result(Future<MvcResult> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException exception) {
            throw new AssertionError("并发 HTTP 调用失败", exception.getCause());
        }
    }

    /**
     * 构造指定长度的无填充 Base64url 字符串。
     *
     * @param length 字符串长度
     * @return 固定 Base64url 字符串
     */
    private static String repeatedBase64urlCharacter(int length) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            value.append('A');
        }
        return value.toString();
    }

    /**
     * 每个用例均从可销毁的 MySQL 结构开始。
     */
    @BeforeEach
    void resetSchema() {
        new ResourceDatabasePopulator(
                new FileSystemResource("docs/01_schema.sql")).execute(dataSource);
    }

    /**
     * 验证密钥首次创建可在真实 MySQL 上完成材料持久化。
     */
    @Test
    void shouldCreateKeyWithDatabasePersistence() {
        KmsPrincipal principal = new KmsPrincipal(PRINCIPAL_ID, TENANT_ID, java.util.Collections.singleton("kms.manage"));
        KmsKey key = KmsKey.builder().tenantId(TENANT_ID).keyRef("pending").keyAlias("direct-create-key")
                .purpose(KmsKeyPurpose.SIGN).algorithm(KmsAlgorithm.ES256).state(KmsKeyState.ACTIVE)
                .activeVersion(1).rowVersion(0L).build();

        KmsKey created = keyManagementService.create(principal, key, IDEMPOTENCY_KEY, REQUEST_ID);

        assertNotNull(created.getKeyRef(), "首次创建必须生成逻辑密钥标识");
    }

    /**
     * 验证幂等会话锁和安全响应快照可在真实 MySQL 上完成首次写入。
     */
    @Test
    void shouldPersistIdempotencySnapshotWithDatabaseScopeLock() {
        KmsPrincipal principal = new KmsPrincipal(PRINCIPAL_ID, TENANT_ID, java.util.Collections.singleton("kms.manage"));
        KmsManagementIdempotencyResult result = idempotencyService.execute(principal, "POST:/api/v1/kms/keys",
                IDEMPOTENCY_KEY, REQUEST_ID, "POST:/api/v1/kms/keys\n{}", () ->
                        new KmsManagementIdempotencyResult(201, "{}", "test-key-ref", "/api/v1/kms/keys/test-key-ref", false));

        assertEquals(201, result.getStatus(), "首次幂等写入必须成功返回业务结果");
    }

    /**
     * 验证数据库时钟、创建和同摘要幂等重放。
     *
     * @throws Exception HTTP 调用失败
     */
    @Test
    void shouldCreateAndReplayKeyWithDatabaseClock() throws Exception {
        Instant databaseNow = clock.now();
        log.info("数据库权威时间已取得: {}", databaseNow);
        assertNotNull(databaseNow, "数据库权威时间不能为空");

        MvcResult created = mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KEY_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/kms/keys/")))
                .andExpect(jsonPath("$.keyRef").isString())
                .andExpect(jsonPath("$.keyAlias").value("test-signing-key"))
                .andExpect(jsonPath("$.activeVersion").value(1))
                .andReturn();
        String createdBody = created.getResponse().getContentAsString();
        String createdLocation = created.getResponse().getHeader("Location");
        log.info("创建逻辑密钥状态: {}, 资源路径: {}", created.getResponse().getStatus(), createdLocation);
        assertNotNull(createdLocation, "创建响应必须包含资源路径");

        MvcResult replayed = mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KEY_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", createdLocation))
                .andReturn();
        String replayedBody = replayed.getResponse().getContentAsString();
        log.info("幂等重放状态: {}, 资源路径: {}", replayed.getResponse().getStatus(), createdLocation);
        assertTrue(createdBody.equals(replayedBody), "相同摘要必须返回首次安全响应快照");
    }

    /**
     * 验证同一幂等键的不同请求摘要被拒绝。
     *
     * @throws Exception HTTP 调用失败
     */
    @Test
    void shouldRejectDifferentRequestForSameIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KEY_BODY))
                .andExpect(status().isCreated());

        String differentBody = "{\"keyAlias\":\"test-signing-key-next\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}";
        log.info("使用同一幂等键提交不同请求摘要");
        mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));
    }

    /**
     * 验证公钥集合使用直接数组响应并禁止缓存。
     *
     * @throws Exception HTTP 调用失败
     */
    @Test
    void shouldReturnPublicKeyArrayWithNoStore() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KEY_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode key = OBJECT_MAPPER.readTree(created.getResponse().getContentAsString());
        String keyRef = key.get("keyRef").textValue();
        String policyBody = "{\"principalId\":\"" + PRINCIPAL_ID
                + "\",\"keyVersion\":1,\"operation\":\"READ_PUBLIC_KEY\"}";
        mockMvc.perform(post("/api/v1/kms/keys/{keyRef}/policies", keyRef)
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", POLICY_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyBody))
                .andExpect(status().isCreated());

        log.info("查询公钥集合资源: {}", keyRef);
        mockMvc.perform(get("/api/v1/kms/keys/{keyRef}/public-keys", keyRef)
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].keyRef").value(keyRef))
                .andExpect(jsonPath("$[0].version").value(1))
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    /**
     * 验证 ES256 签名与验签在精确策略授权下形成闭环。
     *
     * @throws Exception HTTP 调用失败
     */
    @Test
    void shouldSignAndVerifyWithExactPolicies() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KEY_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String keyRef = OBJECT_MAPPER.readTree(created.getResponse().getContentAsString()).get("keyRef").textValue();
        createExactPolicy(keyRef, "SIGN", "test-idempotency-key-000000006");
        createExactPolicy(keyRef, "VERIFY", "test-idempotency-key-000000007");
        String signBody = "{\"keyRef\":\"" + keyRef + "\",\"input\":\"aGVsbG8\"}";
        MvcResult signed = mockMvc.perform(post("/api/v1/kms/crypto/signatures")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyRef").value(keyRef))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.signature").isString())
                .andReturn();
        String signature = OBJECT_MAPPER.readTree(signed.getResponse().getContentAsString()).get("signature").textValue();
        String verifyBody = "{\"keyRef\":\"" + keyRef + "\",\"version\":1,\"input\":\"aGVsbG8\",\"signature\":\""
                + signature + "\"}";
        mockMvc.perform(post("/api/v1/kms/crypto/verifications")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        String invalidVerifyBody = "{\"keyRef\":\"" + keyRef + "\",\"version\":1,\"input\":\"d29ybGQ\",\"signature\":\""
                + signature + "\"}";
        log.info("ES256 签名与验签闭环已生成，密钥资源: {}", keyRef);
        mockMvc.perform(post("/api/v1/kms/crypto/verifications")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidVerifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    /**
     * 验证 AES-GCM 封装与解封在精确策略授权下形成闭环。
     *
     * @throws Exception HTTP 调用失败
     */
    @Test
    void shouldEncryptAndDecryptWithExactPolicies() throws Exception {
        String keyBody = "{\"keyAlias\":\"test-encryption-key\",\"purpose\":\"ENCRYPT\",\"algorithm\":\"AES_256_GCM\"}";
        MvcResult created = mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", "test-idempotency-key-000000008")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(keyBody))
                .andExpect(status().isCreated())
                .andReturn();
        String keyRef = OBJECT_MAPPER.readTree(created.getResponse().getContentAsString()).get("keyRef").textValue();
        createExactPolicy(keyRef, "ENCRYPT", "test-idempotency-key-000000009");
        createExactPolicy(keyRef, "DECRYPT", "test-idempotency-key-000000010");
        MvcResult encrypted = mockMvc.perform(post("/api/v1/kms/crypto/envelopes")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyRef\":\"" + keyRef + "\",\"plaintext\":\"aGVsbG8\",\"aad\":\"dGVzdA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envelope").isString())
                .andReturn();
        String envelope = OBJECT_MAPPER.readTree(encrypted.getResponse().getContentAsString()).get("envelope").textValue();
        mockMvc.perform(post("/api/v1/kms/crypto/decryptions")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"envelope\":\"" + envelope + "\",\"aad\":\"dGVzdA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plaintext").value("aGVsbG8"));
    }

    /**
     * 验证跨 tenant 不得通过已知 keyRef 执行密码学操作。
     *
     * @throws Exception HTTP 调用失败
     */
    @Test
    void shouldRejectCrossTenantCryptoAccess() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KEY_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String keyRef = OBJECT_MAPPER.readTree(created.getResponse().getContentAsString()).get("keyRef").textValue();
        mockMvc.perform(post("/api/v1/kms/crypto/signatures")
                        .header("X-Test-Tenant", "other-tenant")
                        .header("X-Test-Principal", "other-principal")
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyRef\":\"" + keyRef + "\",\"input\":\"aGVsbG8\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));
    }

    /**
     * 验证别名筛选将 LIKE 保留字符按字面量处理。
     *
     * @throws Exception HTTP 调用失败
     */
    @Test
    void shouldEscapeLikeCharactersInKeyAliasFilter() throws Exception {
        String wildcardAlias = "test%_\\-key";
        String wildcardBody = "{\"keyAlias\":\"test%_\\\\-key\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}";
        String ordinaryBody = "{\"keyAlias\":\"test-ordinary-key\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}";
        mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wildcardBody))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", "test-idempotency-key-000000005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ordinaryBody))
                .andExpect(status().isCreated());

        log.info("以含 LIKE 保留字符的别名筛选当前 tenant 密钥: {}", wildcardAlias);
        mockMvc.perform(get("/api/v1/kms/keys").param("alias", wildcardAlias)
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].keyAlias").value(wildcardAlias));
    }

    /**
     * 验证已领取任务即使被释放也禁止取消销毁。
     *
     * @throws Exception HTTP 调用失败
     */
    @Test
    void shouldRejectCancellationAfterHistoricalWorkerClaim() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KEY_BODY))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode key = OBJECT_MAPPER.readTree(created.getResponse().getContentAsString());
        String keyRef = key.get("keyRef").textValue();
        long rowVersion = key.get("rowVersion").longValue();
        String scheduleBody = "{\"destroyAfter\":\"2099-01-01T00:00:00.000Z\",\"expectedRowVersion\":"
                + rowVersion + "}";
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/api/v1/kms/keys/{keyRef}/destruction", keyRef)
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", "test-idempotency-key-000000003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleBody))
                .andExpect(status().isOk());
        Instant now = clock.now();
        jdbcTemplate.update("UPDATE smart_kms_destruction_job SET due_at = ?, updated_at = ?",
                Timestamp.from(now), Timestamp.from(now));
        boolean claimedByWorker = destructionJobRepository.claim(TENANT_ID, keyRef, 1, "test-claim-token",
                now.plusSeconds(60L), now);
        Integer claimed = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM smart_kms_destruction_job "
                + "WHERE first_claimed_at IS NOT NULL AND state = 'CLAIMED'", Integer.class);
        assertTrue(claimedByWorker, "到期任务必须能由真实 CAS 领取");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                                "/api/v1/kms/keys/{keyRef}/destruction", keyRef)
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", "test-idempotency-key-000000004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRowVersion\":" + (rowVersion + 1L) + "}"))
                .andExpect(status().isConflict());
        log.info("历史领取后取消销毁被拒绝，已写入首次领取事实数量: {}", claimed);
        assertEquals(Integer.valueOf(1), claimed, "首次领取事实必须持久化且不可绕过");
    }

    /**
     * 为测试主体创建单版本精确策略。
     *
     * @param keyRef         逻辑密钥标识
     * @param operation      策略操作
     * @param idempotencyKey 幂等键
     * @throws Exception HTTP 调用失败
     */
    private void createExactPolicy(String keyRef, String operation, String idempotencyKey) throws Exception {
        String policyBody = "{\"principalId\":\"" + PRINCIPAL_ID + "\",\"keyVersion\":1,\"operation\":\""
                + operation + "\"}";
        mockMvc.perform(post("/api/v1/kms/keys/{keyRef}/policies", keyRef)
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyBody))
                .andExpect(status().isCreated());
    }

    /**
     * 验证同一幂等作用域的并发首写只创建一个逻辑密钥。
     *
     * @throws Exception 并发 HTTP 调用失败
     */
    @Test
    void shouldSerializeConcurrentFirstWrite() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> action = new Callable<MvcResult>() {
                @Override
                public MvcResult call() throws Exception {
                    return mockMvc.perform(post("/api/v1/kms/keys")
                                    .header("X-Test-Tenant", TENANT_ID)
                                    .header("X-Test-Principal", PRINCIPAL_ID)
                                    .header("X-Test-Request-Id", REQUEST_ID)
                                    .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(CREATE_KEY_BODY))
                            .andExpect(status().isCreated())
                            .andReturn();
                }
            };
            Future<MvcResult> first = executor.submit(action);
            Future<MvcResult> second = executor.submit(action);
            MvcResult firstResult = result(first);
            MvcResult secondResult = result(second);
            String firstBody = firstResult.getResponse().getContentAsString();
            String secondBody = secondResult.getResponse().getContentAsString();
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM smart_kms_key", Integer.class);
            log.info("并发首写状态: {}, {}; 逻辑密钥数量: {}", firstResult.getResponse().getStatus(),
                    secondResult.getResponse().getStatus(), count);
            assertEquals(Integer.valueOf(1), count, "同一幂等作用域只能创建一个逻辑密钥");
            assertTrue(firstBody.equals(secondBody), "并发相同请求必须重放同一安全响应快照");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证未认证、媒体类型错误和错误挑战响应。
     *
     * @throws Exception HTTP 调用失败
     */
    @Test
    void shouldReturnSafeHttpErrors() throws Exception {
        log.info("验证未认证请求的安全错误响应");
        mockMvc.perform(get("/api/v1/kms/keys"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString());

        log.info("验证不支持媒体类型的安全错误响应");
        mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(CREATE_KEY_BODY))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").isString());

        mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_KEY_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")));
    }

    /**
     * 验证参数化 JSON 和超出编码上限的二进制字段边界。
     *
     * @throws Exception HTTP 调用失败
     */
    @Test
    void shouldAcceptParameterizedJsonAndRejectOversizedBinaryField() throws Exception {
        mockMvc.perform(post("/api/v1/kms/keys")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                        .content(CREATE_KEY_BODY))
                .andExpect(status().isCreated());

        String oversizedInput = repeatedBase64urlCharacter(87385);
        mockMvc.perform(post("/api/v1/kms/crypto/signatures")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyRef\":\"test-key-ref\",\"input\":\"" + oversizedInput + "\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));
    }

    /**
     * 验证当前 tenant 不存在的管理资源与损坏封装使用确定状态响应。
     *
     * @throws Exception HTTP 调用失败
     */
    @Test
    void shouldReturnNotFoundForMissingManagedKeyAndUnprocessableForMalformedEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/kms/keys/{keyRef}", "missing-key-ref")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));

        mockMvc.perform(post("/api/v1/kms/crypto/decryptions")
                        .header("X-Test-Tenant", TENANT_ID)
                        .header("X-Test-Principal", PRINCIPAL_ID)
                        .header("X-Test-Request-Id", REQUEST_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"envelope\":\"AA\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));
    }
}
