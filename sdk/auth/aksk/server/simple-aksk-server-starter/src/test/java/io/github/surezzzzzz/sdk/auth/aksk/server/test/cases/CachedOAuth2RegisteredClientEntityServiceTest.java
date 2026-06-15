package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.CachedOAuth2RegisteredClientEntityService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CachedOAuth2RegisteredClientEntityService 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class CachedOAuth2RegisteredClientEntityServiceTest {

    @Mock
    private OAuth2RegisteredClientEntityRepository delegate;

    @Mock
    private SmartCacheManager smartCacheManager;

    @Mock
    private RedisKeyHelper redisKeyHelper;

    private CachedOAuth2RegisteredClientEntityService service;

    private static final String CLIENT_ID = "AKPtestclient001";
    private static final String CACHE_NAME = "oauth2:client:entity";
    private static final String CACHE_KEY = "{" + CLIENT_ID + "}";

    @BeforeEach
    void setUp() {
        service = new CachedOAuth2RegisteredClientEntityService(delegate, smartCacheManager, redisKeyHelper);
        lenient().when(redisKeyHelper.buildCacheKeyById(CLIENT_ID)).thenReturn(CACHE_KEY);
    }

    @Test
    void findByClientId_cacheMiss_returnsFromJpaAndWritesToCache() {
        OAuth2RegisteredClientEntity entity = newEntity();
        when(smartCacheManager.get(eq(CACHE_NAME), eq(CACHE_KEY), any()))
                .thenAnswer(inv -> inv.getArgument(2, java.util.function.Supplier.class).get());
        when(delegate.findByClientId(CLIENT_ID)).thenReturn(Optional.of(entity));

        Optional<OAuth2RegisteredClientEntity> result = service.findByClientId(CLIENT_ID);

        assertTrue(result.isPresent());
        assertEquals(CLIENT_ID, result.get().getClientId());
        verify(delegate).findByClientId(CLIENT_ID);
        verify(smartCacheManager).get(eq(CACHE_NAME), eq(CACHE_KEY), any());
        log.info("✓ cache miss 时正确回源 JPA 并写入缓存");
    }

    @Test
    void findByClientId_cacheHit_returnsCachedWithoutJpa() {
        OAuth2RegisteredClientEntity cached = newEntity();
        when(smartCacheManager.get(eq(CACHE_NAME), eq(CACHE_KEY), any()))
                .thenReturn(cached);

        Optional<OAuth2RegisteredClientEntity> result = service.findByClientId(CLIENT_ID);

        assertTrue(result.isPresent());
        assertEquals(CLIENT_ID, result.get().getClientId());
        verify(delegate, never()).findByClientId(any());
        log.info("✓ cache hit 时不查 JPA");
    }

    @Test
    void findByClientId_cacheReturnsNull_returnsEmptyOptional() {
        when(smartCacheManager.get(eq(CACHE_NAME), eq(CACHE_KEY), any()))
                .thenAnswer(inv -> inv.getArgument(2, java.util.function.Supplier.class).get());
        when(delegate.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());

        Optional<OAuth2RegisteredClientEntity> result = service.findByClientId(CLIENT_ID);

        assertFalse(result.isPresent());
        verify(delegate).findByClientId(CLIENT_ID);
        log.info("✓ entity 不存在时返回空 Optional");
    }

    @Test
    void findByClientId_smartCacheManagerNull_fallsBackToJpa() {
        service = new CachedOAuth2RegisteredClientEntityService(delegate, null, redisKeyHelper);
        OAuth2RegisteredClientEntity entity = newEntity();
        when(delegate.findByClientId(CLIENT_ID)).thenReturn(Optional.of(entity));

        Optional<OAuth2RegisteredClientEntity> result = service.findByClientId(CLIENT_ID);

        assertTrue(result.isPresent());
        verify(delegate).findByClientId(CLIENT_ID);
        verify(smartCacheManager, never()).get(any(), any(), any());
        log.info("✓ SmartCacheManager 为 null 时降级直查 JPA");
    }

    @Test
    void findByClientId_redisKeyHelperNull_fallsBackToJpa() {
        service = new CachedOAuth2RegisteredClientEntityService(delegate, smartCacheManager, null);
        OAuth2RegisteredClientEntity entity = newEntity();
        when(delegate.findByClientId(CLIENT_ID)).thenReturn(Optional.of(entity));

        Optional<OAuth2RegisteredClientEntity> result = service.findByClientId(CLIENT_ID);

        assertTrue(result.isPresent());
        verify(delegate).findByClientId(CLIENT_ID);
        log.info("✓ RedisKeyHelper 为 null 时降级直查 JPA");
    }

    @Test
    void findByClientId_smartCacheException_fallsBackToJpa() {
        OAuth2RegisteredClientEntity entity = newEntity();
        when(smartCacheManager.get(eq(CACHE_NAME), eq(CACHE_KEY), any()))
                .thenThrow(new RuntimeException("Redis connection failed"));
        when(delegate.findByClientId(CLIENT_ID)).thenReturn(Optional.of(entity));

        log.info("验证 SmartCache 异常时降级直查 JPA（loader 抛异常后 catch 内重试一次）");
        Optional<OAuth2RegisteredClientEntity> result = service.findByClientId(CLIENT_ID);

        assertTrue(result.isPresent());
        assertEquals(CLIENT_ID, result.get().getClientId());
        // loader 内部调用 1 次 + catch 后 delegate 重试 1 次
        verify(delegate, atLeastOnce()).findByClientId(CLIENT_ID);
        log.info("✓ SmartCache 异常时降级直查 JPA（异常透传）");
    }

    @Test
    void evict_callsSmartCacheManagerEvict() {
        service.evict(CLIENT_ID);

        verify(smartCacheManager).evict(CACHE_NAME, CACHE_KEY);
        log.info("✓ evict 调用了 SmartCacheManager.evict");
    }

    @Test
    void evict_smartCacheManagerNull_noOp() {
        service = new CachedOAuth2RegisteredClientEntityService(delegate, null, redisKeyHelper);

        service.evict(CLIENT_ID);

        verify(smartCacheManager, never()).evict(any(), any());
        log.info("✓ SmartCacheManager 为 null 时 evict 无操作");
    }

    @Test
    void evict_redisKeyHelperNull_noOp() {
        service = new CachedOAuth2RegisteredClientEntityService(delegate, smartCacheManager, null);

        service.evict(CLIENT_ID);

        verify(smartCacheManager, never()).evict(any(), any());
        log.info("✓ RedisKeyHelper 为 null 时 evict 无操作");
    }

    @Test
    void evict_exception_swallowed() {
        doThrow(new RuntimeException("Redis evict failed"))
                .when(smartCacheManager).evict(CACHE_NAME, CACHE_KEY);

        assertDoesNotThrow(() -> service.evict(CLIENT_ID));
        log.info("✓ evict 异常不外抛，仅 log 记录");
    }

    private OAuth2RegisteredClientEntity newEntity() {
        OAuth2RegisteredClientEntity entity = new OAuth2RegisteredClientEntity();
        entity.setId("test-id-001");
        entity.setClientId(CLIENT_ID);
        entity.setClientName("Test Client");
        entity.setClientType(1);
        entity.setEnabled(true);
        return entity;
    }
}