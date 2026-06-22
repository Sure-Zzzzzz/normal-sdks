package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.SimpleAkskServerException;
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
    void testFindByClientIdCacheMissReturnsFromJpaAndWritesToCache() {
        OAuth2RegisteredClientEntity entity = newEntity();
        when(smartCacheManager.get(eq(CACHE_NAME), eq(CACHE_KEY), any()))
                .thenAnswer(inv -> inv.getArgument(2, java.util.concurrent.Callable.class).call());
        when(delegate.findByClientId(CLIENT_ID)).thenReturn(Optional.of(entity));

        Optional<OAuth2RegisteredClientEntity> result = service.findByClientId(CLIENT_ID);

        assertTrue(result.isPresent());
        assertEquals(CLIENT_ID, result.get().getClientId());
        verify(delegate).findByClientId(CLIENT_ID);
        verify(smartCacheManager).get(eq(CACHE_NAME), eq(CACHE_KEY), any());
        log.info("✓ cache miss 时正确回源 JPA 并写入缓存");
    }

    @Test
    void testFindByClientIdCacheHitReturnsCachedWithoutJpa() {
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
    void testFindByClientIdCacheReturnsNullReturnsEmptyOptional() {
        when(smartCacheManager.get(eq(CACHE_NAME), eq(CACHE_KEY), any()))
                .thenAnswer(inv -> inv.getArgument(2, java.util.concurrent.Callable.class).call());
        when(delegate.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());

        Optional<OAuth2RegisteredClientEntity> result = service.findByClientId(CLIENT_ID);

        assertFalse(result.isPresent());
        verify(delegate).findByClientId(CLIENT_ID);
        log.info("✓ entity 不存在时返回空 Optional");
    }

    @Test
    void testFindByClientIdSmartCacheExceptionThrowsServerException() {
        when(smartCacheManager.get(eq(CACHE_NAME), eq(CACHE_KEY), any()))
                .thenThrow(new RuntimeException("Redis connection failed"));

        log.info("验证 SmartCache 异常时不降级直查 JPA");
        SimpleAkskServerException exception = assertThrows(SimpleAkskServerException.class,
                () -> service.findByClientId(CLIENT_ID));

        assertNotNull(exception.getErrorCode());
        verify(delegate, never()).findByClientId(CLIENT_ID);
        log.info("✓ SmartCache 异常时抛出自定义异常");
    }

    @Test
    void testEvictCallsSmartCacheManagerEvict() {
        service.evict(CLIENT_ID);

        verify(smartCacheManager).evict(CACHE_NAME, CACHE_KEY);
        log.info("✓ evict 调用了 SmartCacheManager.evict");
    }

    @Test
    void testEvictExceptionThrowsServerException() {
        doThrow(new RuntimeException("Redis evict failed"))
                .when(smartCacheManager).evict(CACHE_NAME, CACHE_KEY);

        SimpleAkskServerException exception = assertThrows(SimpleAkskServerException.class,
                () -> service.evict(CLIENT_ID));

        assertNotNull(exception.getErrorCode());
        log.info("✓ evict 异常时抛出自定义异常");
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