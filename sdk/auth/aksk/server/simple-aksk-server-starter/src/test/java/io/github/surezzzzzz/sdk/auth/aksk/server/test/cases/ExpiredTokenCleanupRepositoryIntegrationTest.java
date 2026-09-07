package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2AuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 过期 Token 分批删除的真实数据库集成测试。
 * <p>
 * {@code deleteExpiredBatch} 是原生 SQL（{@code DELETE ... LIMIT}），且显式声明
 * {@code countQuery = "SELECT 1"} 绕过 Spring Data JPA 对 DELETE 语句的 count 派生 NPE；
 * 这两点均无法用 mock 验证，必须连真实 MySQL 执行。
 * <p>
 * 本模块其他集成测试类也会往同一张表写入真实过期记录（各自 {@code @AfterEach} 已清理），
 * 为避免残留或执行顺序导致本类的精确批量断言被 ambient 过期行干扰，每个测试开始前先
 * 用生产同款 {@code deleteExpired()} 清空表中已有的过期行，保证种下的记录是当时表内唯一过期数据；
 * 之后断言可放心使用精确总数，而不仅是按 id 存在性校验。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskServerTestApplication.class)
class ExpiredTokenCleanupRepositoryIntegrationTest {

    @Autowired
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;

    @Autowired
    private OAuth2AuthorizationRepository authorizationRepository;

    @BeforeEach
    void drainAmbientExpiredRows() {
        authorizationRepository.deleteExpired();
    }

    @AfterEach
    void tearDown() {
        deleteIfExists("expired-1");
        deleteIfExists("expired-2");
        deleteIfExists("expired-3");
        deleteIfExists("active-1");
        for (int i = 0; i < 5; i++) {
            deleteIfExists("expired-real-" + i);
        }
        deleteIfExists("active-real");
    }

    private void deleteIfExists(String id) {
        if (authorizationEntityRepository.existsById(id)) {
            authorizationEntityRepository.deleteById(id);
        }
    }

    @Test
    void testDeleteExpiredBatchDeletesOnlyExpiredUpToLimit() {
        Instant now = Instant.now();
        seedAuthorization("expired-1", now.minus(1, ChronoUnit.DAYS));
        seedAuthorization("expired-2", now.minus(1, ChronoUnit.DAYS));
        seedAuthorization("expired-3", now.minus(1, ChronoUnit.DAYS));
        seedAuthorization("active-1", now.plus(1, ChronoUnit.DAYS));

        int deletedFirstBatch = authorizationEntityRepository.deleteExpiredBatch(now, 2);

        assertEquals(2, deletedFirstBatch, "单批删除数受 LIMIT 约束，不应超过 batchSize");
        assertEquals(1, countRemaining("expired-1", "expired-2", "expired-3"),
                "3 条过期记录中应恰好剩 1 条未被本批删除");
        assertTrue(authorizationEntityRepository.findById("active-1").isPresent(),
                "未过期记录不应被删除");
        log.info("✓ 真实 MySQL：deleteExpiredBatch 原生 DELETE ... LIMIT 只删过期记录且受批大小约束");
    }

    @Test
    void testDeleteExpiredAcrossBatchesRemovesAllExpiredViaRealDatabase() {
        Instant now = Instant.now();
        String[] expiredIds = new String[5];
        for (int i = 0; i < 5; i++) {
            expiredIds[i] = "expired-real-" + i;
            seedAuthorization(expiredIds[i], now.minus(1, ChronoUnit.DAYS));
        }
        seedAuthorization("active-real", now.plus(1, ChronoUnit.DAYS));

        int total = authorizationRepository.deleteExpired();

        assertEquals(5, total, "drainAmbientExpiredRows 已清空表内其他过期行，此时过期行只有本类种下的 5 条");
        assertEquals(0, countRemaining(expiredIds), "跨批累计应删除本类种下的全部 5 条过期记录");
        assertTrue(authorizationEntityRepository.findById("active-real").isPresent(),
                "未过期记录不应被删除");
        log.info("✓ 真实 MySQL：deleteExpired 循环分批（batchSize={}）删空过期记录，"
                + "countQuery=\"SELECT 1\" 未触发 count 派生 NPE", 2000);
    }

    private long countRemaining(String... ids) {
        long remaining = 0;
        for (String id : ids) {
            if (authorizationEntityRepository.findById(id).isPresent()) {
                remaining++;
            }
        }
        return remaining;
    }

    private void seedAuthorization(String id, Instant expiresAt) {
        OAuth2AuthorizationEntity authorization = new OAuth2AuthorizationEntity();
        authorization.setId(id);
        authorization.setRegisteredClientId("cleanup-test-client");
        authorization.setPrincipalName("cleanup-test-client");
        authorization.setAuthorizationGrantType("client_credentials");
        authorization.setAccessTokenIssuedAt(expiresAt.minus(1, ChronoUnit.HOURS));
        authorization.setAccessTokenExpiresAt(expiresAt);
        authorizationEntityRepository.save(authorization);
    }
}
