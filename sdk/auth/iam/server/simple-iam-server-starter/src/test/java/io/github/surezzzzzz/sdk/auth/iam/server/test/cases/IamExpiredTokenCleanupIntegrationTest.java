package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.entity.IamRefreshTokenFamilyEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRefreshTokenFamilyRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.IamExpiredTokenCleanupService;
import io.github.surezzzzzz.sdk.auth.iam.server.test.SimpleIamServerTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 过期 Refresh 族分批删除的真实数据库集成测试。
 *
 * <p>{@code deleteExpiredBatch} 是原生 SQL（{@code DELETE ... LIMIT}），无法用 mock
 * 验证，必须连真实 MySQL 执行；清理服务的三路授权行删除同此口径，随
 * {@link IamExpiredTokenCleanupService#cleanupExpired()} 一并真库跑通。</p>
 *
 * <p>batch-size 压到 2 强制触发跨批循环；每个测试开始前先跑生产同款
 * {@code cleanupExpired()} 清空表内既有过期行（本模块其他测试类的残留），
 * 保证精确总数断言不被环境数据干扰。userId 用负数标记本类种子行。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SimpleIamServerTestApplication.class)
@TestPropertySource(properties = {
        "io.github.surezzzzzz.sdk.auth.iam.server.cleanup.batch-size=2"
})
class IamExpiredTokenCleanupIntegrationTest {

    private static final Long TEST_USER_ID = -999L;

    @Autowired
    private IamRefreshTokenFamilyRepository familyRepository;
    @Autowired
    private IamExpiredTokenCleanupService cleanupService;

    @BeforeEach
    void drainAmbientExpiredRows() {
        cleanupService.cleanupExpired();
    }

    @AfterEach
    void tearDown() {
        deleteIfExists("cleanup-family-1");
        deleteIfExists("cleanup-family-2");
        deleteIfExists("cleanup-family-3");
        deleteIfExists("cleanup-family-active");
        for (int i = 0; i < 5; i++) {
            deleteIfExists("cleanup-family-real-" + i);
        }
        deleteIfExists("cleanup-family-real-active");
    }

    private void deleteIfExists(String id) {
        if (familyRepository.existsById(id)) {
            familyRepository.deleteById(id);
        }
    }

    @Test
    void testDeleteExpiredBatchDeletesOnlyExpiredUpToLimit() {
        Instant now = Instant.now();
        seedFamily("cleanup-family-1", now.minus(1, ChronoUnit.DAYS));
        seedFamily("cleanup-family-2", now.minus(1, ChronoUnit.DAYS));
        seedFamily("cleanup-family-3", now.minus(1, ChronoUnit.DAYS));
        seedFamily("cleanup-family-active", now.plus(1, ChronoUnit.DAYS));

        int deletedFirstBatch = familyRepository.deleteExpiredBatch(now, 2);

        assertEquals(2, deletedFirstBatch, "单批删除数受 LIMIT 约束，不应超过 batchSize");
        assertEquals(1, countRemaining("cleanup-family-1", "cleanup-family-2", "cleanup-family-3"),
                "3 条过期族中应恰好剩 1 条未被本批删除");
        assertTrue(familyRepository.findById("cleanup-family-active").isPresent(),
                "未过期族不应被删除");
        log.info("✓ 真实 MySQL：deleteExpiredBatch 原生 DELETE ... LIMIT 只删过期族且受批大小约束");
    }

    @Test
    void testCleanupExpiredAcrossBatchesRemovesAllExpiredFamilies() {
        Instant now = Instant.now();
        String[] expiredIds = new String[5];
        for (int i = 0; i < 5; i++) {
            expiredIds[i] = "cleanup-family-real-" + i;
            seedFamily(expiredIds[i], now.minus(1, ChronoUnit.DAYS));
        }
        seedFamily("cleanup-family-real-active", now.plus(1, ChronoUnit.DAYS));

        int total = cleanupService.cleanupExpired();

        assertEquals(5, total, "drain 已清空环境过期行，本次清理删除数应恰为本类种下的 5 条过期族");
        assertEquals(0, countRemaining(expiredIds), "跨批循环（batch-size=2）应删空本类种下的全部 5 条过期族");
        assertTrue(familyRepository.findById("cleanup-family-real-active").isPresent(),
                "未过期族不应被删除");
        log.info("✓ 真实 MySQL：cleanupExpired 跨批删空过期族，active 族保留");
    }

    private long countRemaining(String... ids) {
        long remaining = 0;
        for (String id : ids) {
            if (familyRepository.findById(id).isPresent()) {
                remaining++;
            }
        }
        return remaining;
    }

    private void seedFamily(String id, Instant expiresAt) {
        Instant issuedAt = expiresAt.minus(1, ChronoUnit.DAYS);
        IamRefreshTokenFamilyEntity family = new IamRefreshTokenFamilyEntity();
        family.setId(id);
        family.setUserId(TEST_USER_ID);
        family.setUsername("cleanup-test");
        family.setSessionId("cleanup-session");
        family.setCurrentTokenHash("hash-" + id);
        family.setIssuedAt(issuedAt);
        family.setExpiresAt(expiresAt);
        familyRepository.save(family);
    }
}
