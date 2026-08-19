package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.ApplicationAuthorizationRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.ClientInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.AkskApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.AkskApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ApplicationAuthorizationManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.ClientManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.SimpleAkskServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.server.test.helper.ApplicationAuthorizationTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 应用授权投影乐观锁并发集成测试。
 *
 * @author surezzzzzz
 */
@SpringBootTest(classes = SimpleAkskServerTestApplication.class)
class ApplicationAuthorizationOptimisticLockIntegrationTest {

    private static final String APPLICATION_CODE = "aksk-server";
    private static final String MANIFEST_VERSION = "optimistic-lock-manifest";
    private static final String MANIFEST_DIGEST = "optimistic-lock-manifest-digest";

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private ApplicationAuthorizationManagementService applicationAuthorizationManagementService;

    @Autowired
    private AkskApplicationAuthorizationRepository applicationAuthorizationRepository;

    @Autowired
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;

    @Autowired
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private String clientId;

    @BeforeEach
    void setUp() {
        ClientInfoResponse client = clientManagementService.createPlatformClient("Authorization Optimistic Lock Client");
        clientId = client.getClientId();
        ApplicationAuthorizationTestHelper.grantManagementAuthorization(applicationAuthorizationRepository, client);
    }

    @AfterEach
    void tearDown() {
        authorizationEntityRepository.deleteAll();
        applicationAuthorizationRepository.deleteAll();
        clientRepository.deleteAll();
        Set<String> keys = redisTemplate.keys("sure-auth-aksk:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void shouldRejectOneOfTwoConcurrentAuthorizationReplacements() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(replaceAfterLoading("api.concurrent.first", barrier));
            Future<Boolean> second = executor.submit(replaceAfterLoading("api.concurrent.second", barrier));

            int successes = (first.get().booleanValue() ? 1 : 0) + (second.get().booleanValue() ? 1 : 0);
            assertEquals(1, successes, "并发完整替换必须恰有一个成功");

            AkskApplicationAuthorizationEntity persisted = requireAuthorization();
            assertEquals(2L, persisted.getAuthorizationVersion().longValue(), "仅成功替换可以递增授权版本");
            assertTrue("[\"api.concurrent.first\"]".equals(persisted.getApiPermissionsJson())
                            || "[\"api.concurrent.second\"]".equals(persisted.getApiPermissionsJson()),
                    "最终授权内容必须完整来自唯一成功的替换请求");
            assertEquals(1L, persisted.getLockVersion().longValue(), "JPA 乐观锁版本必须仅递增一次");
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Callable<Boolean> replaceAfterLoading(String apiPermission, CyclicBarrier barrier) {
        return () -> {
            try {
                return transactionTemplate.execute(status -> {
                    AkskApplicationAuthorizationEntity authorization = requireAuthorization();
                    await(barrier);
                    authorization.setApiPermissionsJson("[\"" + apiPermission + "\"]");
                    authorization.setAuthorizationVersion(authorization.getAuthorizationVersion() + 1L);
                    authorization.setUpdatedAt(Instant.now());
                    applicationAuthorizationRepository.saveAndFlush(authorization);
                    return Boolean.TRUE;
                }).booleanValue();
            } catch (RuntimeException exception) {
                return Boolean.FALSE;
            }
        };
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("并发测试同步失败", exception);
        }
    }

    private AkskApplicationAuthorizationEntity requireAuthorization() {
        return applicationAuthorizationRepository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalStateException("测试授权投影不存在"));
    }
}
