package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.executor.TokenRefreshExecutor;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.configuration.SimpleAkskRedisTokenManagerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.constant.SimpleAkskRedisTokenManagerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.manager.RedisTokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.model.TokenWithExpiry;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RedisTokenManager 并发单元测试。
 *
 * <p>不依赖 Token 端点或 Redis 进程，专门验证同一 JVM 内的刷新收敛契约。
 *
 * @author surezzzzzz
 */
@Slf4j
class RedisTokenManagerConcurrencyUnitTest {

    private static final String CACHE_NAME = "unit-token-cache";
    private static final String SECURITY_CONTEXT = "{\"subjectId\":\"unit-subject\"}";
    private static final int THREAD_COUNT = 50;

    @Test
    @DisplayName("同一安全上下文的 50 个并发请求只刷新一次 Token")
    void shouldRefreshTokenOnceForConcurrentRequests() throws Exception {
        Map<String, TokenWithExpiry> cachedTokens = new ConcurrentHashMap<>();
        SmartCacheManager cacheManager = mock(SmartCacheManager.class);
        when(cacheManager.get(anyString(), anyString(), eq(TokenWithExpiry.class)))
                .thenAnswer(invocation -> cachedTokens.get(invocation.getArgument(1)));
        doAnswer(invocation -> {
            cachedTokens.put(invocation.getArgument(1), invocation.getArgument(2));
            return null;
        }).when(cacheManager).put(anyString(), anyString(), any(TokenWithExpiry.class), anyInt());

        SecurityContextProvider securityContextProvider = mock(SecurityContextProvider.class);
        when(securityContextProvider.getSecurityContext()).thenReturn(SECURITY_CONTEXT);

        TokenRefreshExecutor tokenRefreshExecutor = mock(TokenRefreshExecutor.class);
        AtomicInteger refreshCount = new AtomicInteger();
        doAnswer(invocation -> {
            refreshCount.incrementAndGet();
            Thread.sleep(100);
            invocation.<java.util.function.BiConsumer<String, Long>>getArgument(1)
                    .accept("unit-access-token", 3600L);
            return "unit-access-token";
        }).when(tokenRefreshExecutor).fetchTokenFromServer(anyString(), any());

        SimpleRedisLock redisLock = mock(SimpleRedisLock.class);
        when(redisLock.tryLock(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);

        RedisTokenManager tokenManager = new RedisTokenManager(
                securityContextProvider,
                cacheManager,
                createTokenManagerProperties(),
                createSmartCacheProperties(),
                tokenRefreshExecutor,
                redisLock
        );

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        List<String> tokens = Collections.synchronizedList(new ArrayList<String>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<Throwable>());
        for (int i = 0; i < THREAD_COUNT; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    tokens.add(tokenManager.getToken());
                } catch (Throwable throwable) {
                    failures.add(throwable);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(10, TimeUnit.SECONDS), "并发请求必须在限定时间内结束");
        executorService.shutdownNow();

        assertTrue(failures.isEmpty(), "并发请求不应失败: " + failures);
        assertEquals(THREAD_COUNT, tokens.size(), "全部请求都应返回 Token");
        assertTrue(tokens.stream().allMatch("unit-access-token"::equals), "全部请求应返回同一 Token");
        assertEquals(1, refreshCount.get(), "同一 JVM 的并发缓存未命中只能刷新一次");
        verify(redisLock, times(1)).tryLock(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        assertEquals(SimpleAkskRedisTokenManagerConstant.LOCAL_LOCK_STRIPE_COUNT, getLocalLockCount(),
                "本地锁数量必须固定，不能随安全上下文增长");
        log.info("并发刷新收敛验证通过，请求数: {}, 刷新次数: {}", THREAD_COUNT, refreshCount.get());
    }

    private SimpleAkskRedisTokenManagerProperties createTokenManagerProperties() {
        SimpleAkskRedisTokenManagerProperties properties = new SimpleAkskRedisTokenManagerProperties();
        properties.getRedis().getToken().setCacheName(CACHE_NAME);
        return properties;
    }

    private SmartCacheProperties createSmartCacheProperties() {
        SmartCacheProperties properties = new SmartCacheProperties();
        properties.setKeyPrefix("unit");
        properties.setMe("unit-instance");
        properties.getLock().setTimeoutSeconds(1);
        return properties;
    }

    private int getLocalLockCount() throws Exception {
        Field field = RedisTokenManager.class.getDeclaredField("LOCAL_LOCKS");
        field.setAccessible(true);
        return ((Object[]) field.get(null)).length;
    }
}
