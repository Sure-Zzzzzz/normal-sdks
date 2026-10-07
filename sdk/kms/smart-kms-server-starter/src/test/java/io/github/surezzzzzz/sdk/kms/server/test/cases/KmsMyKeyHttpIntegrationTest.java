package io.github.surezzzzzz.sdk.kms.server.test.cases;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.*;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataPermissionFacade;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsAuditOutcome;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsKeyState;
import io.github.surezzzzzz.sdk.kms.core.constant.KmsOperation;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsAuthorizationException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsPersistenceException;
import io.github.surezzzzzz.sdk.kms.core.exception.KmsStateConflictException;
import io.github.surezzzzzz.sdk.kms.core.model.KmsAuditEvent;
import io.github.surezzzzzz.sdk.kms.core.model.KmsPrincipal;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsClock;
import io.github.surezzzzzz.sdk.kms.core.repository.KmsDestructionJobRepository;
import io.github.surezzzzzz.sdk.kms.core.service.DestructionJobService;
import io.github.surezzzzzz.sdk.kms.core.service.KeyManagementService;
import io.github.surezzzzzz.sdk.kms.server.service.*;
import io.github.surezzzzzz.sdk.kms.server.test.SmartKmsServerTestApplication;
import io.github.surezzzzzz.sdk.kms.server.test.support.KmsTestSchemaHelper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本人生命周期的真实 HTTP、事务、锁、审计与 MySQL 验收。
 * 身份夹具只代替外部认证；API、归属、DATA、幂等与领域链使用生产实现。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartKmsServerTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(KmsMyKeyHttpIntegrationTest.HttpBoundaryConfiguration.class)
class KmsMyKeyHttpIntegrationTest {

    private static final String OWNER = "iam:my-key-test-a";
    private static final String OTHER_OWNER = "iam:my-key-test-b";
    private static final String SELF_SCOPES = "kms.me.read,kms.key.read,kms.key.manage,kms.key.destroy,kms.read-public-key";
    private static final String CREATE_BODY = "{\"keyAlias\":\"本人验收密钥\",\"purpose\":\"SIGN\",\"algorithm\":\"ES256\"}";
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate http;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private KmsClock clock;
    @Autowired
    private KeyManagementService management;
    @Autowired
    private DestructionJobService destructionJobs;
    @Autowired
    private KmsDestructionJobRepository jobRepository;
    @Autowired
    private KmsMyPublicKeyService myPublicKeys;
    @Autowired
    private KmsManagementIdempotencyService idempotency;
    @Autowired
    private AuditCapture audit;
    @Autowired
    private AtomicInteger dataEvaluations;

    /**
     * 在独立可销毁数据库重置结构；真实网络请求保留 PATCH 和 DELETE 正文。
     */
    @BeforeEach
    void resetSchema() {
        KmsTestSchemaHelper.reset(dataSource);
        http.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        audit.getEvents().clear();
        dataEvaluations.set(0);
    }

    private HttpHeaders headers(String owner, String scopes, String key, boolean allData) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Test-Owner-Principal", owner);
        headers.set("X-Test-Principal", owner);
        headers.set("X-Test-Request-Id", "my-key-request-000000001");
        headers.set("X-Test-Scopes", scopes);
        headers.set("X-Test-Subject-Type", "HUMAN");
        if (key != null) {
            headers.set("Idempotency-Key", key);
        }
        if (allData) {
            headers.set("X-Test-All-Data", "true");
        }
        return headers;
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String body, HttpHeaders headers) {
        return http.exchange("http://localhost:" + port + "/api/kms" + path, method,
                new HttpEntity<String>(body, headers), String.class);
    }

    private ResponseEntity<String> self(HttpMethod method, String path, String body, String key) {
        return call(method, path, body, headers(OWNER, SELF_SCOPES, key, false));
    }

    private JsonNode json(ResponseEntity<String> response, int expectedStatus) throws Exception {
        assertEquals(expectedStatus, response.getStatusCodeValue(), "HTTP 状态必须符合契约: " + response.getBody());
        return JSON.readTree(response.getBody());
    }

    private String create(String owner) throws Exception {
        return json(call(HttpMethod.POST, "/keys", CREATE_BODY,
                headers(owner, SELF_SCOPES, "my-key-create-000000001", false)), 201).get("keyRef").asText();
    }

    private String path(String keyRef, String suffix) {
        return "/me/keys/" + keyRef + suffix;
    }

    private long version(String keyRef) throws Exception {
        return json(self(HttpMethod.GET, path(keyRef, ""), null, null), 200).get("rowVersion").asLong();
    }

    private String versionBody(long version) {
        return "{\"expectedRowVersion\":" + version + "}";
    }

    private String stateBody(String state, long version) {
        return "{\"state\":\"" + state + "\",\"expectedRowVersion\":" + version + "}";
    }

    private String scheduleBody(Instant dueAt, long version) {
        return "{\"dueAt\":\"" + dueAt + "\",\"expectedRowVersion\":" + version + "}";
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class).intValue();
    }

    /**
     * 自助公钥只依赖人员证明和对应 API；旧公钥、签名仍要求使用策略。
     */
    @Test
    void shouldReadOwnPublicKeysWithoutPolicyAndKeepLegacyAuthorization() throws Exception {
        String ref = create(OWNER);
        assertEquals(0, count("smart_kms_key_policy"));
        ResponseEntity<String> response = call(HttpMethod.GET, path(ref, "/public-keys"), null,
                headers(OWNER, "kms.read-public-key", null, false));
        JsonNode keys = json(response, 200);
        assertTrue(keys.isArray());
        assertEquals(1, keys.size());
        assertEquals(5, keys.get(0).size(), "宿主宽松 Mapper 不得扩大公钥字段");
        assertEquals(ref, keys.get(0).get("keyRef").asText());
        assertEquals(1, keys.get(0).get("version").asInt());
        assertEquals("ACTIVE", keys.get(0).get("state").asText());
        assertFalse(keys.get(0).get("publicKey").asText().contains("="));
        assertTrue(Base64.getUrlDecoder().decode(keys.get(0).get("publicKey").asText()).length > 0);
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        json(self(HttpMethod.GET, "/keys/" + ref + "/public-keys", null, null), 403);
        json(call(HttpMethod.POST, "/crypto/signatures", "{\"keyRef\":\"" + ref + "\",\"input\":\"YQ\"}",
                headers(OWNER, "kms.sign", null, false)), 403);
        json(call(HttpMethod.GET, path(ref, "/public-keys"), null,
                headers(OWNER, "kms.key.read", null, false)), 403);
        assertEquals(0, dataEvaluations.get(), "本人公钥不能消费治理 DATA");
        assertTrue(audit.getEvents().stream().anyMatch(event -> event.getOperation() == KmsOperation.READ_PUBLIC_KEY
                && event.getOutcome() == KmsAuditOutcome.ALLOWED && ref.equals(event.getKeyRef())));
    }

    /**
     * 不根据人员前缀推断 HUMAN，治理 DATA 也不能扩大本人公钥归属。
     */
    @Test
    void shouldRejectServiceAndLegacyResolverAndHideForeignPublicKeys() throws Exception {
        String ownRef = create(OWNER);
        String foreignRef = create(OTHER_OWNER);
        HttpHeaders service = headers(OWNER, SELF_SCOPES, null, true);
        service.set("X-Test-Subject-Type", "SERVICE");
        json(call(HttpMethod.GET, path(ownRef, "/public-keys"), null, service), 403);
        service.remove("X-Test-Subject-Type");
        json(call(HttpMethod.GET, path(ownRef, "/public-keys"), null, service), 403);
        json(call(HttpMethod.GET, path(foreignRef, "/public-keys"), null,
                headers(OWNER, SELF_SCOPES, null, true)), 404);
        json(self(HttpMethod.GET, path("missing-key", "/public-keys"), null, null), 404);
        json(call(HttpMethod.GET, path(ownRef, "/public-keys"), null, new HttpHeaders()), 401);
        assertEquals(0, dataEvaluations.get());
    }

    /**
     * 直接使用可替换服务也必须提供人员证明和对应 API，不能绕过控制器边界。
     */
    @Test
    void shouldRecheckHumanAndApiPermissionInsidePublicKeyService() throws Exception {
        String ref = create(OWNER);
        KmsPrincipal principal = new KmsPrincipal(OWNER, OWNER, Collections.singleton("kms.read-public-key"));
        assertThrows(KmsAuthorizationException.class, () -> myPublicKeys.list(
                new KmsRequestContext(principal, "direct-public-request-000000001"), ref));
        KmsPrincipal noRead = new KmsPrincipal(OWNER, OWNER, Collections.singleton("kms.key.read"));
        assertThrows(KmsAuthorizationException.class, () -> myPublicKeys.list(
                KmsRequestContext.forVerifiedHuman(noRead, "direct-public-request-000000001"), ref));
        assertEquals(1, myPublicKeys.list(KmsRequestContext.forVerifiedHuman(principal,
                "direct-public-request-000000001"), ref).size());
    }

    /**
     * 停用与历史版本仍可分发；缺失材料和不可能状态必须失败，不能返回部分结果。
     */
    @Test
    void shouldReadRetiredAndDisabledPublicKeysAndFailClosedOnCorruption() throws Exception {
        String ref = create(OWNER);
        json(self(HttpMethod.POST, path(ref, "/versions"), versionBody(0), "public-rotate-000000001"), 200);
        json(self(HttpMethod.PATCH, path(ref, "/state"), stateBody("DISABLED", 1), "public-disable-000000001"), 200);
        JsonNode keys = json(self(HttpMethod.GET, path(ref, "/public-keys"), null, null), 200);
        assertEquals(2, keys.size());
        assertEquals(1, keys.get(0).get("version").asInt());
        assertEquals("RETIRED", keys.get(0).get("state").asText());
        assertEquals(2, keys.get(1).get("version").asInt());
        assertEquals("ACTIVE", keys.get(1).get("state").asText());
        audit.getEvents().clear();
        jdbc.update("UPDATE smart_kms_key_version SET public_material=NULL WHERE version=1");
        json(self(HttpMethod.GET, path(ref, "/public-keys"), null, null), 503);
        assertFalse(audit.getEvents().stream().anyMatch(event -> event.getOutcome() == KmsAuditOutcome.ALLOWED),
                "整批校验失败不能先发布部分成功审计");
        jdbc.update("UPDATE smart_kms_key_version SET state='PENDING_DESTRUCTION' WHERE version=1");
        json(self(HttpMethod.GET, path(ref, "/public-keys"), null, null), 503);
        byte[] activePublic = jdbc.queryForObject("SELECT public_material FROM smart_kms_key_version WHERE version=2", byte[].class);
        jdbc.update("UPDATE smart_kms_key_version SET state='ACTIVE', public_material=? WHERE version=1", activePublic);
        json(self(HttpMethod.GET, path(ref, "/public-keys"), null, null), 503);
        jdbc.update("UPDATE smart_kms_key SET active_version=99");
        json(self(HttpMethod.GET, path(ref, "/public-keys"), null, null), 503);
    }

    /**
     * 销毁明细属于读取权限；治理入口独立消费完整读取 DATA，固定本人路径不消费 DATA。
     */
    @Test
    void shouldReadDestructionWithReadPermissionAndEnforceAdminData() throws Exception {
        String ref = create(OWNER);
        String foreignRef = create(OTHER_OWNER);
        ResponseEntity<String> response = call(HttpMethod.GET, path(ref, "/destruction"), null,
                headers(OWNER, "kms.key.read", null, false));
        JsonNode empty = json(response, 200);
        assertEquals(5, empty.size());
        assertEquals(ref, empty.get("keyRef").asText());
        assertEquals("ACTIVE", empty.get("keyState").asText());
        assertEquals(0, empty.get("rowVersion").asLong());
        assertFalse(empty.get("cancelEligible").asBoolean());
        assertEquals(0, empty.get("items").size());
        assertEquals("no-store", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        json(call(HttpMethod.GET, path(ref, "/destruction"), null,
                headers(OWNER, "kms.key.destroy", null, false)), 403);
        json(call(HttpMethod.GET, path(foreignRef, "/destruction"), null,
                headers(OWNER, "kms.key.read", null, true)), 404);
        assertEquals(0, dataEvaluations.get());
        json(call(HttpMethod.GET, "/admin/keys/" + foreignRef + "/destruction", null,
                headers(OWNER, "kms.key.read", null, false)), 403);
        HttpHeaders restricted = headers(OWNER, "kms.key.read", null, false);
        restricted.set("X-Test-Data-Owner", OTHER_OWNER);
        assertEquals(foreignRef, json(call(HttpMethod.GET, "/admin/keys/" + foreignRef + "/destruction", null,
                restricted), 200).get("keyRef").asText());
        json(call(HttpMethod.GET, "/admin/keys/" + ref + "/destruction", null, restricted), 404);
        json(call(HttpMethod.GET, "/admin/keys/" + foreignRef + "/destruction", null,
                headers(OWNER, "kms.key.destroy", null, true)), 403);
        json(call(HttpMethod.GET, path(ref, "/destruction"), null, new HttpHeaders()), 401);
    }

    /**
     * 排程、取消、真正领取与释放、后台完成均从数据库回读真实任务及历史资格。
     */
    @Test
    void shouldExposeDestructionProgressAndNeverRestoreEligibilityAfterClaim() throws Exception {
        String ref = create(OWNER);
        json(self(HttpMethod.POST, path(ref, "/versions"), versionBody(0), "detail-rotate-000000001"), 200);
        Instant dueAt = clock.now().plusSeconds(86400);
        json(self(HttpMethod.PUT, path(ref, "/destruction"), scheduleBody(dueAt, 1), "detail-schedule-000000001"), 200);
        JsonNode pending = json(self(HttpMethod.GET, path(ref, "/destruction"), null, null), 200);
        assertEquals("PENDING_DESTRUCTION", pending.get("keyState").asText());
        assertEquals(2, pending.get("rowVersion").asLong());
        assertTrue(pending.get("cancelEligible").asBoolean());
        assertEquals(2, pending.get("items").size());
        for (int index = 0; index < 2; index++) {
            JsonNode job = pending.get("items").get(index);
            assertEquals(4, job.size(), "任务仅暴露安全字段");
            assertEquals(index + 1, job.get("keyVersion").asInt());
            assertEquals("PENDING", job.get("state").asText());
            assertEquals(dueAt.toEpochMilli(), Instant.parse(job.get("dueAt").asText()).toEpochMilli());
            assertTrue(job.get("completedAt").isNull());
        }
        json(self(HttpMethod.GET, path(ref, "/public-keys"), null, null), 409);
        assertEquals(204, self(HttpMethod.DELETE, path(ref, "/destruction"), versionBody(2), "detail-cancel-000000001").getStatusCodeValue());
        JsonNode canceled = json(self(HttpMethod.GET, path(ref, "/destruction"), null, null), 200);
        assertEquals(0, canceled.get("items").size());
        assertFalse(canceled.get("cancelEligible").asBoolean());
        json(self(HttpMethod.PUT, path(ref, "/destruction"), scheduleBody(clock.now().plusSeconds(86400), 3),
                "detail-reschedule-000000001"), 200);
        jdbc.update("UPDATE smart_kms_destruction_job SET due_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 SECOND)");
        Instant now = clock.now();
        assertTrue(jobRepository.claim(OWNER, ref, 1, "detail-claim-000000001", now.plusSeconds(60), now));
        JsonNode claimed = json(self(HttpMethod.GET, path(ref, "/destruction"), null, null), 200);
        assertEquals("CLAIMED", claimed.get("items").get(0).get("state").asText());
        assertFalse(claimed.get("cancelEligible").asBoolean());
        assertTrue(jobRepository.release(OWNER, ref, 1, "detail-claim-000000001"));
        JsonNode released = json(self(HttpMethod.GET, path(ref, "/destruction"), null, null), 200);
        assertEquals("PENDING", released.get("items").get(0).get("state").asText());
        assertFalse(released.get("cancelEligible").asBoolean(), "历史领取后释放不能恢复取消资格");
        json(self(HttpMethod.DELETE, path(ref, "/destruction"), versionBody(4), "detail-claimed-cancel-000000001"), 409);
        jdbc.update("UPDATE smart_kms_destruction_job SET due_at=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY) WHERE key_version=2");
        destructionJobs.processDueJobs("destruction-detail-http-worker");
        JsonNode partial = json(self(HttpMethod.GET, path(ref, "/destruction"), null, null), 200);
        assertEquals("PENDING_DESTRUCTION", partial.get("keyState").asText());
        assertFalse(partial.get("cancelEligible").asBoolean());
        assertEquals("COMPLETED", partial.get("items").get(0).get("state").asText());
        assertFalse(partial.get("items").get(0).get("completedAt").isNull());
        assertEquals("PENDING", partial.get("items").get(1).get("state").asText());
        assertTrue(partial.get("items").get(1).get("completedAt").isNull());
        jdbc.update("UPDATE smart_kms_destruction_job SET due_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 SECOND) WHERE key_version=2");
        destructionJobs.processDueJobs("destruction-detail-http-worker");
        JsonNode completed = json(self(HttpMethod.GET, path(ref, "/destruction"), null, null), 200);
        assertEquals("DESTROYED", completed.get("keyState").asText());
        assertFalse(completed.get("cancelEligible").asBoolean());
        for (JsonNode job : completed.get("items")) {
            assertEquals("COMPLETED", job.get("state").asText());
            assertFalse(job.get("completedAt").isNull());
            Instant.parse(job.get("completedAt").asText());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM smart_kms_key_version WHERE private_material IS NOT NULL OR public_material IS NOT NULL", Integer.class));
        json(self(HttpMethod.GET, path(ref, "/public-keys"), null, null), 409);
    }

    /**
     * 对称密钥明确拒绝公钥；缺失任务、版本或损坏时间不伪装成未排程。
     */
    @Test
    void shouldRejectUnsupportedPublicKeyAndCorruptDestructionSnapshots() throws Exception {
        String aes = json(call(HttpMethod.POST, "/keys", "{\"keyAlias\":\"对称验收密钥\",\"purpose\":\"ENCRYPT\",\"algorithm\":\"AES_256_GCM\"}",
                headers(OWNER, SELF_SCOPES, "aes-create-000000001", false)), 201).get("keyRef").asText();
        json(self(HttpMethod.GET, path(aes, "/public-keys"), null, null), 409);
        json(self(HttpMethod.GET, path(aes, "/destruction"), null, null), 200);
        json(self(HttpMethod.PUT, path(aes, "/destruction"), scheduleBody(clock.now().plusSeconds(86400), 0),
                "corrupt-schedule-000000001"), 200);
        jdbc.update("UPDATE smart_kms_destruction_job SET state='COMPLETED', completed_at=NULL");
        json(self(HttpMethod.GET, path(aes, "/destruction"), null, null), 503);
        jdbc.update("DELETE FROM smart_kms_destruction_job");
        json(self(HttpMethod.GET, path(aes, "/destruction"), null, null), 503);
        jdbc.update("DELETE FROM smart_kms_key_version");
        json(self(HttpMethod.GET, path(aes, "/destruction"), null, null), 503);
    }

    /**
     * 真正的持久化读取失败按 503 返回，不能伪装成空任务。
     */
    @Test
    void shouldReportUnavailableWhenDestructionStorageCannotBeRead() throws Exception {
        String ref = create(OWNER);
        jdbc.execute("RENAME TABLE smart_kms_destruction_job TO smart_kms_destruction_job_unavailable");
        try {
            json(self(HttpMethod.GET, path(ref, "/destruction"), null, null), 503);
        } finally {
            jdbc.execute("RENAME TABLE smart_kms_destruction_job_unavailable TO smart_kms_destruction_job");
        }
    }

    /**
     * 真正领取与取消同时竞争时只有一种结局，读快照不能组合已领取与可取消。
     */
    @Test
    void shouldKeepDestructionSnapshotConsistentDuringClaimCancelRace() throws Exception {
        String ref = create(OWNER);
        json(self(HttpMethod.PUT, path(ref, "/destruction"), scheduleBody(clock.now().plusSeconds(86400), 0),
                "race-schedule-000000001"), 200);
        jdbc.update("UPDATE smart_kms_destruction_job SET due_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 SECOND)");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> claim = executor.submit(() -> {
                start.await();
                Instant now = clock.now();
                return jobRepository.claim(OWNER, ref, 1, "race-claim-000000001", now.plusSeconds(60), now);
            });
            Future<ResponseEntity<String>> cancel = executor.submit(() -> {
                start.await();
                return self(HttpMethod.DELETE, path(ref, "/destruction"), versionBody(1), "race-cancel-000000001");
            });
            start.countDown();
            for (int index = 0; index < 10; index++) {
                JsonNode snapshot = json(self(HttpMethod.GET, path(ref, "/destruction"), null, null), 200);
                if (snapshot.get("cancelEligible").asBoolean()) {
                    assertEquals("PENDING_DESTRUCTION", snapshot.get("keyState").asText());
                    assertEquals(1, snapshot.get("items").size());
                    assertEquals("PENDING", snapshot.get("items").get(0).get("state").asText());
                }
            }
            boolean claimed = claim.get(20, TimeUnit.SECONDS);
            int canceled = cancel.get(20, TimeUnit.SECONDS).getStatusCodeValue();
            assertEquals(claimed ? 409 : 204, canceled, "领取成功则取消冲突，取消成功则领取失败");
            JsonNode finalSnapshot = json(self(HttpMethod.GET, path(ref, "/destruction"), null, null), 200);
            assertFalse(finalSnapshot.get("cancelEligible").asBoolean());
            assertEquals(claimed ? "PENDING_DESTRUCTION" : "ACTIVE", finalSnapshot.get("keyState").asText());
            assertEquals(claimed ? 1 : 0, finalSnapshot.get("items").size());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * DATA 为空的五项 API 自助主体完成完整生命周期，四种写入及取消都可重放。
     */
    @Test
    void shouldCompleteSelfLifecycleWithoutDataAndReplayAllCommands() throws Exception {
        String ref = create(OWNER);
        String statePath = path(ref, "/state");
        String disabledBody = stateBody("DISABLED", 0);
        ResponseEntity<String> disabled = self(HttpMethod.PATCH, statePath, disabledBody, "my-key-disable-000000001");
        assertEquals("DISABLED", json(disabled, 200).get("state").asText());
        assertEquals(disabled.getBody(), self(HttpMethod.PATCH, statePath, disabledBody, "my-key-disable-000000001").getBody());
        json(self(HttpMethod.PATCH, statePath, stateBody("ACTIVE", 1), "my-key-enable-000000001"), 200);
        ResponseEntity<String> rotated = self(HttpMethod.POST, path(ref, "/versions"), versionBody(2), "my-key-rotate-000000001");
        assertEquals(2, json(rotated, 200).get("activeVersion").asInt());
        assertEquals(rotated.getBody(), self(HttpMethod.POST, path(ref, "/versions"), versionBody(2), "my-key-rotate-000000001").getBody());
        String body = scheduleBody(clock.now().plusSeconds(86400), 3);
        ResponseEntity<String> scheduled = self(HttpMethod.PUT, path(ref, "/destruction"), body, "my-key-schedule-000000001");
        assertEquals("PENDING_DESTRUCTION", json(scheduled, 200).get("state").asText());
        assertEquals(scheduled.getBody(), self(HttpMethod.PUT, path(ref, "/destruction"), body, "my-key-schedule-000000001").getBody());
        assertEquals(2, count("smart_kms_destruction_job"));
        assertEquals(204, self(HttpMethod.DELETE, path(ref, "/destruction"), versionBody(4), "my-key-cancel-000000001").getStatusCodeValue());
        ResponseEntity<String> canceled = self(HttpMethod.DELETE, path(ref, "/destruction"), versionBody(4), "my-key-cancel-000000001");
        assertEquals(204, canceled.getStatusCodeValue());
        assertNull(canceled.getBody());
        assertEquals(0, count("smart_kms_destruction_job"));
        JsonNode current = json(self(HttpMethod.GET, path(ref, ""), null, null), 200);
        assertEquals("ACTIVE", current.get("state").asText());
        assertEquals(5, current.get("rowVersion").asLong());
        assertEquals(2, count("smart_kms_key_version"));
        assertEquals(0, dataEvaluations.get(), "本人入口不得请求 DATA 计划");
        JsonNode response = JSON.readTree(rotated.getBody());
        assertEquals(9, response.size(), "响应必须限定为九项元数据");
        assertFalse(response.has("responseSnapshot"));
        assertFalse(response.has("ownerPrincipalId"));
        assertFalse(response.has("privateMaterial"));
        long replays = audit.getEvents().stream().filter(event -> "true".equals(event.getMetadata().get("idempotencyReplay"))).count();
        assertEquals(4, replays, "四个路径的重放应准确分类和关联 keyRef");
        assertTrue(audit.getEvents().stream().filter(event -> event.getOperation() != KmsOperation.CREATE_KEY)
                .allMatch(event -> ref.equals(event.getKeyRef())));
        log.info("本人真实 HTTP 生命周期与四路重放通过，keyRef={}", ref);
    }

    /**
     * 四种本人写入都拒绝已知他人标识，ALLOW_ALL 也不能扩大本人归属。
     */
    @Test
    void shouldHideOtherOwnerFromEverySelfCommandEvenWithAllData() throws Exception {
        String ref = create(OTHER_OWNER);
        HttpMethod[] methods = {HttpMethod.PATCH, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE};
        String[] suffixes = {"/state", "/versions", "/destruction", "/destruction"};
        String[] bodies = {stateBody("DISABLED", 0), versionBody(0), scheduleBody(clock.now().plusSeconds(86400), 0), versionBody(0)};
        for (int index = 0; index < methods.length; index++) {
            assertEquals(404, call(methods[index], path(ref, suffixes[index]), bodies[index],
                    headers(OWNER, SELF_SCOPES, "other-owner-reject-00000000" + index, true)).getStatusCodeValue());
        }
        assertEquals(0L, jdbc.queryForObject("SELECT row_version FROM smart_kms_key WHERE key_ref=?", Long.class, ref));
        assertEquals(1, count("smart_kms_key_version"));
        assertEquals(0, count("smart_kms_destruction_job"));
        assertEquals(1, count("smart_kms_idempotency_record"), "拒绝不得生成成功快照");
        assertEquals(4, audit.getEvents().stream().filter(event -> event.getOutcome() == KmsAuditOutcome.REJECTED).count());
    }

    /**
     * API 精确校验先于重放，权限撤销后不能重放旧成功结果。
     */
    @Test
    void shouldRejectMissingExactApiAndRevokedReplay() throws Exception {
        String ref = create(OWNER);
        String body = stateBody("DISABLED", 0);
        json(self(HttpMethod.PATCH, path(ref, "/state"), body, "permission-replay-000000001"), 200);
        HttpMethod[] methods = {HttpMethod.PATCH, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE};
        String[] suffixes = {"/state", "/versions", "/destruction", "/destruction"};
        String[] bodies = {body, versionBody(1), scheduleBody(clock.now().plusSeconds(86400), 1), versionBody(1)};
        for (int index = 0; index < methods.length; index++) {
            assertEquals(403, call(methods[index], path(ref, suffixes[index]), bodies[index],
                    headers(OWNER, "kms.me.read,kms.key.read,kms.read-public-key", "permission-replay-000000001", false)).getStatusCodeValue());
        }
        HttpHeaders unauthenticated = new HttpHeaders();
        unauthenticated.setContentType(MediaType.APPLICATION_JSON);
        unauthenticated.set("Idempotency-Key", "unauthenticated-000000001");
        assertEquals(401, call(HttpMethod.POST, path(ref, "/versions"), versionBody(1), unauthenticated).getStatusCodeValue());
        assertEquals(1, version(ref));
    }

    /**
     * 自助入口不消费 DATA，但同主体调用旧管理写仍必须被 DATA 拦截。
     */
    @Test
    void shouldKeepLegacyDataGateAndSeparateIdempotencyScopes() throws Exception {
        String ref = create(OWNER);
        String legacy = "/keys/" + ref;
        assertEquals(403, self(HttpMethod.PATCH, legacy + "/state", stateBody("DISABLED", 0), "legacy-deny-000000001").getStatusCodeValue());
        assertEquals(403, self(HttpMethod.POST, legacy + "/versions", versionBody(0), "legacy-deny-000000002").getStatusCodeValue());
        assertEquals(403, self(HttpMethod.PUT, legacy + "/destruction", scheduleBody(clock.now().plusSeconds(86400), 0), "legacy-deny-000000003").getStatusCodeValue());
        assertEquals(403, self(HttpMethod.DELETE, legacy + "/destruction", versionBody(0), "legacy-deny-000000004").getStatusCodeValue());
        String key = "separate-path-scope-000000001";
        HttpHeaders all = headers(OWNER, SELF_SCOPES, key, true);
        ResponseEntity<String> old = call(HttpMethod.PATCH, legacy + "/state", stateBody("DISABLED", 0), all);
        json(old, 200);
        ResponseEntity<String> current = self(HttpMethod.PATCH, path(ref, "/state"), stateBody("ACTIVE", 1), key);
        json(current, 200);
        assertEquals(old.getBody(), call(HttpMethod.PATCH, legacy + "/state", stateBody("DISABLED", 0), all).getBody());
        assertEquals(current.getBody(), self(HttpMethod.PATCH, path(ref, "/state"), stateBody("ACTIVE", 1), key).getBody());
        assertEquals(2, version(ref));
        assertEquals(3, count("smart_kms_idempotency_record"));
        assertEquals(3, audit.getEvents().stream().filter(event -> event.getOutcome() == KmsAuditOutcome.ALLOWED
                && !"true".equals(event.getMetadata().get("idempotencyReplay"))).count(), "重放不能重复首次成功事件");
    }

    /**
     * 宿主宽松 Mapper 不能放宽未知字段、数字类型、重复字段或尾随 JSON。
     */
    @Test
    void shouldRejectMalformedTypedRequestsAndProtocolErrors() throws Exception {
        String ref = create(OWNER);
        String[] bodies = {"{}", "null", "[]", "{", "{\"expectedRowVersion\":null}",
                "{\"expectedRowVersion\":\"0\"}", "{\"expectedRowVersion\":0.5}", "{\"expectedRowVersion\":true}",
                "{\"expectedRowVersion\":-1}", "{\"expectedRowVersion\":9223372036854775808}",
                "{\"expectedRowVersion\":0,\"ownerPrincipalId\":\"" + OTHER_OWNER + "\"}",
                "{\"expectedRowVersion\":0,\"expectedRowVersion\":1}", "{\"expectedRowVersion\":0} {}"};
        for (String body : bodies) {
            JsonNode error = json(self(HttpMethod.POST, path(ref, "/versions"), body, "malformed-version-000000001"), 400);
            assertEquals(3, error.size(), "错误体只能含安全消息、时间与请求标识");
        }
        for (String state : Arrays.asList("PENDING_DESTRUCTION", "DESTROYED", "active", "unknown")) {
            json(self(HttpMethod.PATCH, path(ref, "/state"), stateBody(state, 0), "malformed-state-000000001"), 400);
        }
        json(self(HttpMethod.PUT, path(ref, "/destruction"), "{\"dueAt\":\"2030-01-02T10:00:00\",\"expectedRowVersion\":0}", "malformed-time-000000001"), 400);
        for (String dueAt : Arrays.asList("+10000-01-01T00:00:00Z", "9999-12-31T23:30:00-01:00",
                "0000-01-01T00:00:00Z")) {
            json(self(HttpMethod.PUT, path(ref, "/destruction"),
                    "{\"dueAt\":\"" + dueAt + "\",\"expectedRowVersion\":0}", "out-of-range-time-000000001"), 400);
        }
        json(self(HttpMethod.POST, path(ref, "/versions"), versionBody(0), null), 400);
        HttpHeaders media = headers(OWNER, SELF_SCOPES, "bad-media-000000001", false);
        media.setContentType(MediaType.TEXT_PLAIN);
        json(call(HttpMethod.POST, path(ref, "/versions"), versionBody(0), media), 415);
        json(self(HttpMethod.GET, path(ref, "/state"), null, null), 405);
        assertEquals(0, version(ref));
        assertEquals(1, count("smart_kms_idempotency_record"));
    }

    /**
     * 幂等冲突与旧资源版本不写入；相同时间的时区表示规范化后重放。
     */
    @Test
    void shouldCanonicalizeTimeAndRejectConflictingRetries() throws Exception {
        String ref = create(OWNER);
        Instant dueAt = Instant.ofEpochMilli(clock.now().plusSeconds(86400).toEpochMilli());
        ResponseEntity<String> first = self(HttpMethod.PUT, path(ref, "/destruction"), scheduleBody(dueAt, 0), "time-normalization-000000001");
        json(first, 200);
        String equivalent = "{\"expectedRowVersion\":0,\"dueAt\":\"" + dueAt.atOffset(ZoneOffset.ofHours(8)) + "\"}";
        assertEquals(first.getBody(), self(HttpMethod.PUT, path(ref, "/destruction"), equivalent, "time-normalization-000000001").getBody());
        json(self(HttpMethod.PUT, path(ref, "/destruction"), scheduleBody(dueAt.plusSeconds(1), 0), "time-normalization-000000001"), 409);
        json(self(HttpMethod.DELETE, path(ref, "/destruction"), versionBody(0), "stale-cancel-000000001"), 409);
        assertEquals(1, count("smart_kms_destruction_job"));
        assertEquals(1, version(ref));
    }

    /**
     * 受限治理只允许指定归属，保留真实操作者；本人入口不继承治理范围。
     */
    @Test
    void shouldPreserveRestrictedGovernanceAndSelfOwnership() throws Exception {
        String ownRef = create(OWNER);
        String otherRef = create(OTHER_OWNER);
        HttpHeaders restricted = headers(OWNER, SELF_SCOPES, "restricted-data-000000001", false);
        restricted.set("X-Test-Data-Owner", OTHER_OWNER);
        json(call(HttpMethod.PATCH, "/keys/" + otherRef + "/state", stateBody("DISABLED", 0), restricted), 200);
        restricted.set("Idempotency-Key", "restricted-enable-000000001");
        json(call(HttpMethod.PATCH, "/keys/" + otherRef + "/state", stateBody("ACTIVE", 1), restricted), 200);
        json(call(HttpMethod.POST, "/keys/" + otherRef + "/versions", versionBody(2), restricted), 200);
        json(call(HttpMethod.PUT, "/keys/" + otherRef + "/destruction",
                scheduleBody(clock.now().plusSeconds(86400), 3), restricted), 200);
        assertEquals(204, call(HttpMethod.DELETE, "/keys/" + otherRef + "/destruction", versionBody(4), restricted).getStatusCodeValue());
        for (HttpMethod method : Arrays.asList(HttpMethod.PATCH, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE)) {
            String suffix = method == HttpMethod.PATCH ? "/state" : method == HttpMethod.POST ? "/versions" : "/destruction";
            String body = method == HttpMethod.PATCH ? stateBody("DISABLED", 0) : method == HttpMethod.PUT
                    ? scheduleBody(clock.now().plusSeconds(86400), 0) : versionBody(0);
            json(call(method, "/keys/" + ownRef + suffix, body, restricted), 404);
        }
        int evaluations = dataEvaluations.get();
        json(call(HttpMethod.PATCH, path(otherRef, "/state"), stateBody("DISABLED", 5), restricted), 404);
        json(call(HttpMethod.PATCH, path(ownRef, "/state"), stateBody("DISABLED", 0), restricted), 200);
        assertEquals(evaluations, dataEvaluations.get(), "本人归属不得消费受限治理范围");
        assertEquals(1, version(ownRef));
        assertEquals(0, count("smart_kms_destruction_job"));
        assertTrue(audit.getEvents().stream().anyMatch(event -> OWNER.equals(event.getPrincipalId())
                && OTHER_OWNER.equals(event.getOwnerPrincipalId()) && otherRef.equals(event.getKeyRef())
                && event.getOutcome() == KmsAuditOutcome.ALLOWED));
    }

    /**
     * 新旧 HTTP 和直接领域调用都不能通过启停绕过待销毁任务。
     */
    @Test
    void shouldProtectPendingAndDestroyedStateWithoutChangingTasksOrMaterial() throws Exception {
        String ref = create(OWNER);
        json(self(HttpMethod.PUT, path(ref, "/destruction"), scheduleBody(clock.now().plusSeconds(86400), 0), "protect-schedule-000000001"), 200);
        for (String state : Arrays.asList("ACTIVE", "DISABLED")) {
            json(self(HttpMethod.PATCH, path(ref, "/state"), stateBody(state, 1), "pending-self-00000000" + state), 409);
            json(call(HttpMethod.PATCH, "/keys/" + ref + "/state", stateBody(state, 1),
                    headers(OWNER, SELF_SCOPES, "pending-legacy-00000000" + state, true)), 409);
        }
        KmsPrincipal principal = new KmsPrincipal(OWNER, OWNER, new HashSet<String>(Arrays.asList(SELF_SCOPES.split(","))));
        for (KmsKeyState target : KmsKeyState.values()) {
            assertThrows(KmsStateConflictException.class, () -> management.changeState(principal, ref, target, 1,
                    "pending-direct-000000001", "direct-request-000000001"));
        }
        assertEquals(1, count("smart_kms_destruction_job"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM smart_kms_key_version WHERE private_material IS NOT NULL", Integer.class));
        assertEquals(1, version(ref));
        assertEquals(204, self(HttpMethod.DELETE, path(ref, "/destruction"), versionBody(1), "protect-cancel-000000001").getStatusCodeValue());
        json(self(HttpMethod.PUT, path(ref, "/destruction"), scheduleBody(clock.now().plusSeconds(86400), 2),
                "worker-schedule-000000001"), 200);
        jdbc.update("UPDATE smart_kms_destruction_job SET due_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 SECOND)");
        destructionJobs.processDueJobs("my-key-http-test-worker");
        JsonNode destroyed = json(self(HttpMethod.GET, path(ref, ""), null, null), 200);
        assertEquals("DESTROYED", destroyed.get("state").asText());
        assertTrue(destroyed.get("activeVersion").isNull());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM smart_kms_key_version WHERE private_material IS NOT NULL", Integer.class));
        long destroyedVersion = destroyed.get("rowVersion").asLong();
        json(self(HttpMethod.PATCH, path(ref, "/state"), stateBody("ACTIVE", destroyedVersion), "destroyed-self-000000001"), 409);
        json(call(HttpMethod.PATCH, "/keys/" + ref + "/state", stateBody("DISABLED", destroyedVersion),
                headers(OWNER, SELF_SCOPES, "destroyed-legacy-000000001", true)), 409);
        assertThrows(KmsStateConflictException.class, () -> management.changeState(principal, ref, KmsKeyState.ACTIVE,
                destroyedVersion, "destroyed-direct-000000001", "direct-request-000000001"));
    }

    /**
     * 领取历史即使租约已过期仍禁止取消；政策只约束未来排程。
     */
    @Test
    void shouldApplyOwnerWindowAndRejectCancellationAfterClaimHistory() throws Exception {
        String ref = create(OWNER);
        String policy = "{\"minScheduleAheadSeconds\":3600,\"maxScheduleAheadSeconds\":7200}";
        json(self(HttpMethod.PUT, "/me/destruction-policy", policy, "policy-window-000000001"), 200);
        json(self(HttpMethod.PUT, path(ref, "/destruction"), scheduleBody(clock.now().plusSeconds(100), 0), "policy-outside-000000001"), 400);
        Instant dueAt = clock.now().plusSeconds(5400);
        json(self(HttpMethod.PUT, path(ref, "/destruction"), scheduleBody(dueAt, 0), "policy-inside-000000001"), 200);
        jdbc.update("UPDATE smart_kms_destruction_job SET first_claimed_at=UTC_TIMESTAMP(3), claim_until=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 1 SECOND)");
        json(self(HttpMethod.DELETE, path(ref, "/destruction"), versionBody(1), "claimed-cancel-000000001"), 409);
        assertEquals(1, count("smart_kms_destruction_job"));
        assertEquals("PENDING_DESTRUCTION", json(self(HttpMethod.GET, path(ref, ""), null, null), 200).get("state").asText());
    }

    /**
     * 不同入口同版本并发只有一方成功；相同本人幂等键并发只创建一个新版本。
     */
    @Test
    void shouldSerializeLegacyAndSelfWritesAndConcurrentReplays() throws Exception {
        String ref = create(OWNER);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<ResponseEntity<String>> own = executor.submit(() -> {
                start.await();
                return self(HttpMethod.POST,
                        path(ref, "/versions"), versionBody(0), "concurrent-path-000000001");
            });
            Future<ResponseEntity<String>> legacy = executor.submit(() -> {
                start.await();
                return call(HttpMethod.POST,
                        "/keys/" + ref + "/versions", versionBody(0), headers(OWNER, SELF_SCOPES, "concurrent-path-000000001", true));
            });
            start.countDown();
            List<Integer> statuses = Arrays.asList(own.get(20, TimeUnit.SECONDS).getStatusCodeValue(), legacy.get(20, TimeUnit.SECONDS).getStatusCodeValue());
            assertTrue(statuses.contains(200) && statuses.contains(409), "同版本竞争应成功一次并返回一次冲突: " + statuses);
            assertEquals(1, version(ref));
            CountDownLatch retryStart = new CountDownLatch(1);
            Callable<ResponseEntity<String>> rotate = () -> {
                retryStart.await();
                return self(HttpMethod.POST,
                        path(ref, "/versions"), versionBody(1), "concurrent-replay-000000001");
            };
            Future<ResponseEntity<String>> first = executor.submit(rotate);
            Future<ResponseEntity<String>> second = executor.submit(rotate);
            retryStart.countDown();
            ResponseEntity<String> response = first.get(20, TimeUnit.SECONDS);
            ResponseEntity<String> replay = second.get(20, TimeUnit.SECONDS);
            json(response, 200);
            json(replay, 200);
            assertEquals(response.getBody(), replay.getBody());
            assertEquals(2, version(ref));
            assertEquals(3, count("smart_kms_key_version"));
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 快照存储前失败必须回滚领域写入，只发布失败事件；未声明路径不得误分类。
     */
    @Test
    void shouldRollBackFailedSnapshotAndClassifyOnlyDeclaredSelfEndpoints() throws Exception {
        String ref = create(OWNER);
        audit.getEvents().clear();
        KmsPrincipal principal = new KmsPrincipal(OWNER, OWNER, Collections.singleton("kms.key.manage"));
        String endpoint = "POST:/api/kms/me/keys/" + ref + "/versions";
        assertThrows(KmsPersistenceException.class, () -> idempotency.execute(principal, endpoint,
                "rollback-snapshot-000000001", "rollback-request-000000001", endpoint + "\n{}", () -> {
                    management.rotate(principal, ref, 0, "rollback-snapshot-000000001", "rollback-request-000000001");
                    return new KmsManagementIdempotencyResult(500, "{}", ref, null, false);
                }));
        assertEquals(0, version(ref));
        assertEquals(1, count("smart_kms_key_version"));
        assertFalse(audit.getEvents().stream().anyMatch(event -> event.getOutcome() == KmsAuditOutcome.ALLOWED));
        assertTrue(audit.getEvents().stream().anyMatch(event -> event.getOutcome() == KmsAuditOutcome.FAILED
                && event.getOperation() == KmsOperation.ROTATE_KEY && ref.equals(event.getKeyRef())));
        for (String unknown : Arrays.asList("GET:/api/kms/me/keys/" + ref + "/versions",
                "POST:/api/kms/me/keys/" + ref + "/policies", "POST:/api/kms/me/keys/" + ref + "/extra/versions")) {
            assertThrows(KmsPersistenceException.class, () -> idempotency.execute(principal, unknown,
                    "unknown-endpoint-000000001", "unknown-request-000000001", "{}", () -> {
                        fail("未知路径不能执行业务回调");
                        return null;
                    }));
        }
    }

    /**
     * 收集跨 HTTP 线程的生产审计事件，不替换事件发布链。
     */
    @Getter
    static class AuditCapture {
        private final Queue<KmsAuditEvent> events = new ConcurrentLinkedQueue<KmsAuditEvent>();

        /**
         * 接收生产链发布的安全事件。
         */
        @EventListener
        public void capture(KmsAuditEvent event) {
            events.add(event);
        }
    }

    /**
     * 外部身份与 DATA 快照夹具，默认只有自助五码和空 DATA。
     */
    @TestConfiguration
    static class HttpBoundaryConfiguration {

        /**
         * 注册固定测试身份解析器，保留生产控制器和领域 API 校验。
         */
        @Bean
        @Primary
        public KmsPrincipalResolver selfPrincipalResolver() {
            return request -> {
                String owner = request.getHeader("X-Test-Owner-Principal");
                String actor = request.getHeader("X-Test-Principal");
                String requestId = request.getHeader("X-Test-Request-Id");
                String scopes = request.getHeader("X-Test-Scopes");
                if (owner == null || actor == null || requestId == null || scopes == null) {
                    return null;
                }
                KmsPrincipal principal = new KmsPrincipal(actor, owner,
                        new HashSet<String>(Arrays.asList(scopes.split(","))));
                return "HUMAN".equals(request.getHeader("X-Test-Subject-Type")) && actor.equals(owner)
                        ? KmsRequestContext.forVerifiedHuman(principal, requestId) : new KmsRequestContext(principal, requestId);
            };
        }

        /**
         * 记录 DATA 消费次数，验证本人写路径不会调用管理授权。
         */
        @Bean
        public AtomicInteger dataEvaluations() {
            return new AtomicInteger();
        }

        /**
         * 模拟 IAM 的空或全量 DATA 文档，生产计划评估和 SQL 仍保留。
         */
        @Bean
        @Primary
        public DataPermissionFacade selfDataPermissionFacade(AtomicInteger dataEvaluations) {
            return (resource, action) -> {
                dataEvaluations.incrementAndGet();
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
                boolean all = "true".equals(attributes.getRequest().getHeader("X-Test-All-Data"));
                String restrictedOwner = attributes.getRequest().getHeader("X-Test-Data-Owner");
                DataGrantDocument grants = all ? new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                        SimpleDataPermissionConstant.VERSION, Collections.singletonList(new DataGrant(resource,
                        Collections.singletonList(action), true, Collections.emptyList()))) : null;
                if (!all && restrictedOwner != null) {
                    grants = new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                            SimpleDataPermissionConstant.VERSION, Collections.singletonList(new DataGrant(resource,
                            Collections.singletonList(action), false, Collections.singletonList(new DataConstraint(
                            "ownerPrincipalId", DataConstraintOperator.IN, Collections.singletonList(restrictedOwner))))));
                }
                return DataAccessPlan.evaluate(grants, new DataPermissionRequest(resource, action));
            };
        }

        /**
         * 故意提供宽松宿主 Mapper，验证新请求不受其影响。
         */
        @Bean
        @Primary
        public ObjectMapper permissiveHostMapper() {
            return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }

        /**
         * 注册生产事件监听器。
         */
        @Bean
        public AuditCapture auditCapture() {
            return new AuditCapture();
        }
    }
}
