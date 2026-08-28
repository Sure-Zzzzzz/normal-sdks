package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.BatchRevokeResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2AuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.SimpleAkskServerException;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.impl.TokenManagementServiceImpl;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Token 管理失败语义单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class TokenManagementFailureSemanticsTest {

    @Mock
    private OAuth2AuthorizationRepository mysqlRepository;

    @Mock
    private OAuth2AuthorizationEntityRepository authorizationEntityRepository;

    @Mock
    private OAuth2RegisteredClientEntityRepository clientRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RedisTokenRepository redisRepository;

    @Mock
    private SmartCacheManager smartCacheManager;

    @Mock
    private RedisKeyHelper redisKeyHelper;

    private TokenManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TokenManagementServiceImpl(
                mysqlRepository,
                authorizationEntityRepository,
                clientRepository,
                eventPublisher,
                redisRepository,
                smartCacheManager,
                redisKeyHelper
        );
    }

    @Test
    void testQueryRedisTokensKeepsRedisDataSource() {
        TokenInfo redisToken = new TokenInfo();
        redisToken.setId("redis-token-001");
        redisToken.setClientId("AKPtestclient001");
        redisToken.setIssuedAt(Instant.now());
        redisToken.setExpiresAt(Instant.now().plusSeconds(60));
        redisToken.setStatus(TokenInfo.TokenStatus.ACTIVE);
        redisToken.setDataSource(TokenInfo.DataSource.REDIS);
        when(redisRepository.findAllFromRedis()).thenReturn(Collections.singletonList(redisToken));

        PageResponse<TokenInfoResponse> response = service.queryRedisTokens(
                TokenInfo.TokenStatus.ACTIVE, 1, 10);

        assertEquals(1, response.getData().size());
        assertEquals(TokenInfo.DataSource.REDIS, response.getData().get(0).getDataSource());
        verify(redisRepository).findAllFromRedis();
    }

    @Test
    void testDeleteTokenRedisDeleteFailureThrowsCacheException() {
        String tokenId = "token-001";
        when(authorizationEntityRepository.findById(tokenId)).thenReturn(Optional.empty());
        when(redisRepository.findAllFromRedis()).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("Redis delete failed")).when(redisRepository).deleteById(tokenId);

        log.info("验证 deleteToken 遇到 Redis 删除失败时不吞异常");
        SimpleAkskServerException exception = assertThrows(SimpleAkskServerException.class,
                () -> service.deleteToken(tokenId));

        assertEquals(ErrorCode.TOKEN_OPERATION_FAILED, exception.getErrorCode());
        verify(redisRepository).deleteById(tokenId);
        log.info("✓ deleteToken Redis 删除失败时抛出自定义异常");
    }

    @Test
    void testDeleteExpiredTokensRedisDeleteFailureThrowsCacheException() {
        String tokenId = "expired-token-001";
        TokenInfo expiredToken = new TokenInfo();
        expiredToken.setId(tokenId);
        expiredToken.setStatus(TokenInfo.TokenStatus.EXPIRED);
        when(mysqlRepository.deleteExpired()).thenReturn(0);
        when(redisRepository.findAllFromRedis()).thenReturn(Collections.singletonList(expiredToken));
        doThrow(new RuntimeException("Redis delete failed")).when(redisRepository).deleteById(tokenId);

        log.info("验证 deleteExpiredTokens 遇到 Redis 删除失败时不吞异常");
        SimpleAkskServerException exception = assertThrows(SimpleAkskServerException.class,
                () -> service.deleteExpiredTokens());

        assertEquals(ErrorCode.CACHE_OPERATION_FAILED, exception.getErrorCode());
        verify(redisRepository).deleteById(tokenId);
        log.info("✓ deleteExpiredTokens Redis 删除失败时抛出自定义异常");
    }

    @Test
    void testRevokeTokenEventPublishFailureThrowsTokenException() {
        String tokenId = "token-event-failure-001";
        OAuth2AuthorizationEntity token = activeToken(tokenId);
        TokenInfo tokenInfo = tokenInfo(tokenId);
        when(authorizationEntityRepository.findById(tokenId)).thenReturn(Optional.of(token));
        when(mysqlRepository.findById(tokenId)).thenReturn(tokenInfo);
        doThrow(new RuntimeException("event publish failed")).when(eventPublisher).publishEvent(any());

        SimpleAkskServerException exception = assertThrows(SimpleAkskServerException.class,
                () -> service.revokeToken(tokenId));

        assertEquals(ErrorCode.TOKEN_OPERATION_FAILED, exception.getErrorCode());
        verify(authorizationEntityRepository).updateAccessTokenMetadata(eq(tokenId), any());
        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void testRevokeTokenAuditDataMissingThrowsTokenException() {
        String tokenId = "token-audit-data-missing-001";
        OAuth2AuthorizationEntity token = activeToken(tokenId);
        when(authorizationEntityRepository.findById(tokenId)).thenReturn(Optional.of(token));
        when(mysqlRepository.findById(tokenId)).thenReturn(null);

        SimpleAkskServerException exception = assertThrows(SimpleAkskServerException.class,
                () -> service.revokeToken(tokenId));

        assertEquals(ErrorCode.TOKEN_OPERATION_FAILED, exception.getErrorCode());
        verify(authorizationEntityRepository).updateAccessTokenMetadata(eq(tokenId), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void testRevokeRedisOnlyTokenEventPublishFailureThrowsTokenException() {
        String tokenId = "redis-event-failure-001";
        TokenInfo redisToken = tokenInfo(tokenId);
        when(authorizationEntityRepository.findById(tokenId)).thenReturn(Optional.empty());
        when(redisRepository.findAllFromRedis()).thenReturn(Collections.singletonList(redisToken));
        doThrow(new RuntimeException("event publish failed")).when(eventPublisher).publishEvent(any());

        SimpleAkskServerException exception = assertThrows(SimpleAkskServerException.class,
                () -> service.revokeToken(tokenId));

        assertEquals(ErrorCode.TOKEN_OPERATION_FAILED, exception.getErrorCode());
        verify(eventPublisher).publishEvent(any());
        verify(redisRepository, never()).deleteById(tokenId);
    }

    @Test
    void testRevokeAllByClientIdRevokesRedisOnlyToken() {
        String clientId = "AKPtestclient001";
        String tokenId = "redis-batch-token-001";
        OAuth2RegisteredClientEntity client = new OAuth2RegisteredClientEntity();
        client.setId("registered-client-001");
        client.setClientId(clientId);
        TokenInfo redisToken = tokenInfo(tokenId);
        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(client));
        when(authorizationEntityRepository.findByRegisteredClientIdOrderByAccessTokenIssuedAtDesc(
                eq(client.getId()), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 200), 0));
        when(redisRepository.findAllFromRedis()).thenReturn(Collections.singletonList(redisToken));

        BatchRevokeResponse response = service.revokeAllByClientId(clientId);

        assertEquals(1, response.getRevokedCount());
        verify(eventPublisher).publishEvent(any());
        verify(redisRepository).deleteById(tokenId);
    }

    @Test
    void testRevokeAllByClientIdTokenFailureThrowsTokenException() {
        String clientId = "AKPtestclient001";
        String registeredClientId = "registered-client-001";
        String tokenId = "token-002";
        OAuth2RegisteredClientEntity client = new OAuth2RegisteredClientEntity();
        client.setId(registeredClientId);
        client.setClientId(clientId);
        OAuth2AuthorizationEntity token = new OAuth2AuthorizationEntity();
        token.setId(tokenId);
        token.setAccessTokenExpiresAt(Instant.now().plusSeconds(60));
        token.setAccessTokenMetadata("{invalid-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(clientRepository.findByClientId(clientId)).thenReturn(Optional.of(client));
        when(authorizationEntityRepository.findByRegisteredClientIdOrderByAccessTokenIssuedAtDesc(eq(registeredClientId), any()))
                .thenReturn(new PageImpl<>(Collections.singletonList(token), PageRequest.of(0, 200), 1));
        doThrow(new RuntimeException("Redis delete failed")).when(redisRepository).deleteById(tokenId);

        log.info("验证 revokeAllByClientId 遇到单个 Token 撤销失败时不继续吞异常");
        SimpleAkskServerException exception = assertThrows(SimpleAkskServerException.class,
                () -> service.revokeAllByClientId(clientId));

        assertEquals(ErrorCode.TOKEN_OPERATION_FAILED, exception.getErrorCode());
        verify(redisRepository).deleteById(tokenId);
        log.info("✓ revokeAllByClientId 单 Token 撤销失败时抛出自定义异常");
    }

    private OAuth2AuthorizationEntity activeToken(String tokenId) {
        OAuth2AuthorizationEntity token = new OAuth2AuthorizationEntity();
        token.setId(tokenId);
        token.setAccessTokenExpiresAt(Instant.now().plusSeconds(60));
        token.setAccessTokenMetadata("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return token;
    }

    private TokenInfo tokenInfo(String tokenId) {
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setId(tokenId);
        tokenInfo.setClientId("AKPtestclient001");
        tokenInfo.setIssuedAt(Instant.now());
        tokenInfo.setExpiresAt(Instant.now().plusSeconds(60));
        tokenInfo.setStatus(TokenInfo.TokenStatus.ACTIVE);
        return tokenInfo;
    }
}
