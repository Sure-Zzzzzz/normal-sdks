package io.github.surezzzzzz.sdk.auth.aksk.server.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.BatchRevokeResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenStatisticsResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2AuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenRevokedEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ClientException;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Token Management Service Implementation
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
public class TokenManagementServiceImpl implements TokenManagementService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String INVALIDATED_KEY = "metadata.token.invalidated";

    private final OAuth2AuthorizationRepository mysqlRepository;
    private final OAuth2AuthorizationEntityRepository authorizationEntityRepository;
    private final OAuth2RegisteredClientEntityRepository clientRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private RedisTokenRepository redisRepository;

    @Autowired(required = false)
    private SmartCacheManager smartCacheManager;

    @Autowired(required = false)
    private RedisKeyHelper redisKeyHelper;

    public TokenManagementServiceImpl(
            OAuth2AuthorizationRepository mysqlRepository,
            OAuth2AuthorizationEntityRepository authorizationEntityRepository,
            OAuth2RegisteredClientEntityRepository clientRepository,
            ApplicationEventPublisher eventPublisher) {
        this.mysqlRepository = mysqlRepository;
        this.authorizationEntityRepository = authorizationEntityRepository;
        this.clientRepository = clientRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PageResponse<TokenInfoResponse> queryTokens(TokenQueryRequest request) {
        // 创建分页参数 (Spring Data JPA的页码从0开始，所以需要-1)
        int currentPage = Math.max(1, request.getPage());
        int pageSize = Math.max(1, request.getSize());
        Sort sort = Sort.by(Sort.Direction.DESC, "accessTokenIssuedAt");
        Pageable pageable = PageRequest.of(currentPage - 1, pageSize, sort);

        // 使用Repository的通用查询方法（纯数据库分页和过滤）
        Page<TokenInfo> tokenPage = mysqlRepository.queryTokensWithFilters(
                request.getClientId(),
                request.getClientType(),
                request.getStatus(),
                request.getSearch(),
                pageable
        );

        // 转换为Response
        List<TokenInfoResponse> responseList = tokenPage.getContent().stream()
                .map(this::toTokenInfoResponse)
                .collect(Collectors.toList());

        return PageResponse.of(responseList, tokenPage.getTotalElements(), currentPage, pageSize);
    }

    @Override
    public PageResponse<TokenInfoResponse> queryRedisTokens(TokenInfo.TokenStatus status, int page, int size) {
        if (redisRepository == null) {
            return PageResponse.of(new ArrayList<>(), 0L, page, size);
        }

        // 从Redis获取所有token
        List<TokenInfo> allRedisTokens = redisRepository.findAllFromRedis();

        // 状态过滤（内存过滤）
        if (status != null) {
            allRedisTokens = allRedisTokens.stream()
                    .filter(token -> token.getStatus() == status)
                    .collect(Collectors.toList());
        }

        // 按签发时间降序排序
        allRedisTokens = allRedisTokens.stream()
                .sorted((a, b) -> b.getIssuedAt().compareTo(a.getIssuedAt()))
                .collect(Collectors.toList());

        // 内存分页
        int currentPage = Math.max(1, page);
        int pageSize = Math.max(1, size);
        long totalElements = allRedisTokens.size();

        // 计算分页范围
        int startIndex = (currentPage - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allRedisTokens.size());

        // 如果startIndex超出范围，返回空页面
        if (startIndex >= allRedisTokens.size()) {
            return PageResponse.of(new ArrayList<>(), totalElements, currentPage, pageSize);
        }

        // 获取当前页的数据
        List<TokenInfoResponse> pageContent = allRedisTokens.subList(startIndex, endIndex)
                .stream()
                .map(this::enrichClientInfo)
                .map(this::toTokenInfoResponse)
                .collect(Collectors.toList());

        return PageResponse.of(pageContent, totalElements, currentPage, pageSize);
    }

    @Override
    public TokenInfoResponse getTokenById(String id) {
        // 先从 MySQL 查询
        TokenInfo tokenInfo = mysqlRepository.findById(id);
        if (tokenInfo != null) {
            return toTokenInfoResponse(tokenInfo);
        }
        // MySQL 中不存在，fallback 到 Redis
        if (redisRepository != null) {
            List<TokenInfo> redisTokens = redisRepository.findAllFromRedis();
            tokenInfo = redisTokens.stream()
                    .filter(t -> id.equals(t.getId()))
                    .findFirst()
                    .orElse(null);
            if (tokenInfo != null) {
                return toTokenInfoResponse(enrichClientInfo(tokenInfo));
            }
        }
        return null;
    }

    @Override
    @Transactional
    public void revokeToken(String id) {
        OAuth2AuthorizationEntity entity = authorizationEntityRepository.findById(id).orElse(null);
        if (entity == null) {
            // MySQL 里没有，但 Redis 里可能有
            log.warn("Token not found in MySQL, checking Redis: {}", id);
            if (redisRepository != null) {
                // 先尝试从 Redis 获取 token 信息，以便发布完整的撤销事件
                List<TokenInfo> allRedisTokens = redisRepository.findAllFromRedis();
                TokenInfo redisToken = allRedisTokens.stream()
                        .filter(token -> id.equals(token.getId()))
                        .findFirst()
                        .orElse(null);

                if (redisToken != null) {
                    log.info("Token found in Redis, revoking: {}", id);
                    // 发布 TokenRevokedEvent 事件
                    try {
                        eventPublisher.publishEvent(new TokenRevokedEvent(
                                this,
                                redisToken.getClientId(),
                                null,
                                redisToken.getOwnerUserId(),
                                redisToken.getOwnerUsername(),
                                redisToken.getTokenValue(),
                                redisToken.getScopes() != null ? new java.util.HashSet<>(redisToken.getScopes()) : null,
                                redisToken.getIssuedAt(),
                                redisToken.getExpiresAt()
                        ));
                        log.debug("Published TokenRevokedEvent for Redis-only token: {}", id);
                    } catch (Exception e) {
                        log.warn("Failed to publish TokenRevokedEvent for Redis-only token: {}", id, e);
                    }
                }

                // 清除 Redis 中的 token
                redisRepository.deleteById(id);
            }
            return;
        }

        // 检查是否已过期：已过期的 token 没必要撤销，等清理掉就行了
        Instant now = Instant.now();
        if (entity.getAccessTokenExpiresAt() != null
                && entity.getAccessTokenExpiresAt().isBefore(now)) {
            log.info("Token already expired, skip revoke: {}", id);
            return;
        }

        // 检查是否已撤销
        if (isAlreadyRevoked(entity.getAccessTokenMetadata())) {
            log.info("Token already revoked: {}", id);
            return;
        }

        // 1. 直接更新数据库 metadata，标记为 invalidated，不走 authorizationService.save()
        byte[] updatedMetadata = markInvalidated(entity.getAccessTokenMetadata());
        if (updatedMetadata == null) {
            log.error("Failed to update token metadata for revocation: {}", id);
            return;
        }
        authorizationEntityRepository.updateAccessTokenMetadata(id, updatedMetadata);
        log.debug("Updated token metadata in database: {}", id);

        // 2. 清 Redis 缓存（id 缓存 + token value 缓存）
        evictTokenCache(id, entity);

        // 3. 发布撤销事件
        publishRevokedEvent(entity);

        log.info("Token revoked: {}", id);
    }

    private boolean isAlreadyRevoked(byte[] metadataBytes) {
        if (metadataBytes == null || metadataBytes.length == 0) return false;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(new String(metadataBytes, java.nio.charset.StandardCharsets.UTF_8));
            JsonNode invalidated = root.get(INVALIDATED_KEY);
            return invalidated != null && invalidated.asBoolean(false);
        } catch (Exception e) {
            log.debug("Failed to parse metadata", e);
            return false;
        }
    }

    private byte[] markInvalidated(byte[] metadataBytes) {
        try {
            ObjectNode root;
            if (metadataBytes != null && metadataBytes.length > 0) {
                root = (ObjectNode) OBJECT_MAPPER.readTree(
                        new String(metadataBytes, java.nio.charset.StandardCharsets.UTF_8));
            } else {
                root = OBJECT_MAPPER.createObjectNode();
            }
            root.put(INVALIDATED_KEY, true);
            return OBJECT_MAPPER.writeValueAsBytes(root);
        } catch (Exception e) {
            log.error("Failed to mark token as invalidated", e);
            return null;
        }
    }

    private void evictTokenCache(String id, OAuth2AuthorizationEntity entity) {
        if (smartCacheManager == null || redisKeyHelper == null) return;
        try {
            // evict SmartCache（按 id 索引）
            smartCacheManager.evict(
                    RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION,
                    redisKeyHelper.buildCacheKeyById(id)
            );
            // evict SmartCache（按 token value 索引）
            if (entity.getAccessTokenValue() != null) {
                String tokenValue = deserializeTokenValue(entity.getAccessTokenValue());
                if (tokenValue != null) {
                    smartCacheManager.evict(
                            RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN,
                            redisKeyHelper.buildCacheKeyByToken(tokenValue, null)
                    );
                    smartCacheManager.evict(
                            RedisKeyHelper.CACHE_OAUTH2_AUTHORIZATION_TOKEN,
                            redisKeyHelper.buildCacheKeyByToken(tokenValue, OAuth2TokenType.ACCESS_TOKEN.getValue())
                    );
                }
            }
            // 同时删掉 RedisTokenRepository 扫描用的完整 key
            if (redisRepository != null) {
                redisRepository.deleteById(id);
            }
            log.debug("Evicted token cache: {}", id);
        } catch (Exception e) {
            log.warn("Failed to evict token cache: {}", id, e);
        }
    }

    private String deserializeTokenValue(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            JsonNode root = OBJECT_MAPPER.readTree(json);
            JsonNode tokenValue = root.get("tokenValue");
            return tokenValue != null ? tokenValue.asText() : null;
        } catch (Exception e) {
            log.debug("Failed to deserialize token value", e);
            return null;
        }
    }

    private void publishRevokedEvent(OAuth2AuthorizationEntity entity) {
        try {
            TokenInfo tokenInfo = mysqlRepository.findById(entity.getId());
            if (tokenInfo == null) return;
            eventPublisher.publishEvent(new TokenRevokedEvent(
                    this,
                    tokenInfo.getClientId(),
                    null,  // clientType 从 JWT claims 里取，这里暂不解析
                    tokenInfo.getOwnerUserId(),
                    tokenInfo.getOwnerUsername(),
                    tokenInfo.getTokenValue(),
                    tokenInfo.getScopes() != null ? new java.util.HashSet<>(tokenInfo.getScopes()) : null,
                    tokenInfo.getIssuedAt(),
                    tokenInfo.getExpiresAt()
            ));
            log.debug("Published TokenRevokedEvent: {}", entity.getId());
        } catch (Exception e) {
            log.warn("Failed to publish TokenRevokedEvent: {}", entity.getId(), e);
        }
    }

    @Override
    public void deleteToken(String id) {
        // 先撤销（这会处理撤销事件和清理Redis）
        try {
            revokeToken(id);
        } catch (Exception e) {
            log.warn("Failed to revoke token before deletion: {}", id, e);
        }

        // 再从 MySQL 删除（先检查存在性）
        try {
            TokenInfo tokenInfo = mysqlRepository.findById(id);
            if (tokenInfo != null) {
                mysqlRepository.deleteById(id);
                log.info("Token deleted from MySQL: {}", id);
            } else {
                log.info("Token not found in MySQL, skipping MySQL deletion: {}", id);
            }
        } catch (Exception e) {
            log.error("Failed to delete token from MySQL: {}", id, e);
        }

        // 确保也从 Redis 删除（revokeToken 里已经删了，这里再确保一下）
        if (redisRepository != null) {
            try {
                redisRepository.deleteById(id);
                log.info("Token deleted from Redis: {}", id);
            } catch (Exception e) {
                log.error("Failed to delete token from Redis: {}", id, e);
            }
        }

        log.info("Token deletion process completed: {}", id);
    }

    @Override
    @Transactional
    public int deleteExpiredTokens() {
        // 删除MySQL中的过期Token
        int mysqlDeleted = mysqlRepository.deleteExpired();

        // Redis中的过期Token会自动过期，但也可以手动清理
        int redisDeleted = 0;
        if (redisRepository != null) {
            List<TokenInfo> redisTokens = redisRepository.findAllFromRedis();
            for (TokenInfo token : redisTokens) {
                if (token.getStatus() == TokenInfo.TokenStatus.EXPIRED) {
                    try {
                        redisRepository.deleteById(token.getId());
                        redisDeleted++;
                    } catch (Exception e) {
                        log.error("Failed to delete expired token from Redis: {}", token.getId(), e);
                    }
                }
            }
        }

        int totalDeleted = mysqlDeleted + redisDeleted;
        log.info("Deleted {} expired tokens (MySQL: {}, Redis: {})", totalDeleted, mysqlDeleted, redisDeleted);
        return totalDeleted;
    }

    @Override
    public TokenStatisticsResponse getStatistics() {
        // 统计MySQL数据（使用count查询，避免加载所有数据）
        long totalCount = mysqlRepository.countAll();

        // 统计各状态的Token数量
        int pageSize = 1000;
        int pageNumber = 0;
        long activeCount = 0;
        long expiredCount = 0;
        long revokedCount = 0;
        Page<TokenInfo> page;

        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            page = mysqlRepository.queryTokensWithFilters(null, null, null, null, pageable);

            for (TokenInfo token : page.getContent()) {
                if (token.getStatus() == TokenInfo.TokenStatus.ACTIVE) {
                    activeCount++;
                } else if (token.getStatus() == TokenInfo.TokenStatus.REVOKED) {
                    revokedCount++;
                } else {
                    expiredCount++;
                }
            }

            pageNumber++;
        } while (page.hasNext());

        TokenStatisticsResponse stats = new TokenStatisticsResponse();
        stats.setTotalCount(totalCount);
        stats.setActiveCount(activeCount);
        stats.setRevokedCount(revokedCount);
        stats.setExpiredCount(expiredCount);

        // MySQL和Redis不再合并统计，只统计MySQL
        stats.setMysqlCount(totalCount);
        stats.setRedisCount(0L);
        stats.setBothCount(0L);

        return stats;
    }

    /**
     * 转换TokenInfo为TokenInfoResponse
     *
     * @param tokenInfo Model对象
     * @return Response对象
     */
    private TokenInfoResponse toTokenInfoResponse(TokenInfo tokenInfo) {
        TokenInfoResponse response = new TokenInfoResponse();
        BeanUtils.copyProperties(tokenInfo, response);
        return response;
    }

    /**
     * 补全Redis token的client信息
     * 使用clientId（AKSK）从MySQL查询client表
     *
     * @param tokenInfo Token信息
     * @return 补全后的Token信息
     */
    private TokenInfo enrichClientInfo(TokenInfo tokenInfo) {
        if (tokenInfo.getClientId() == null || tokenInfo.getClientId().isEmpty()) {
            return tokenInfo;
        }

        try {
            clientRepository.findByClientId(tokenInfo.getClientId()).ifPresent(client -> {
                tokenInfo.setClientName(client.getClientName());
                tokenInfo.setClientType(client.getClientType());
                tokenInfo.setOwnerUserId(client.getOwnerUserId());
                tokenInfo.setOwnerUsername(client.getOwnerUsername());
            });
        } catch (Exception e) {
            log.warn("Failed to enrich client info for token: {}, clientId: {}",
                    tokenInfo.getId(), tokenInfo.getClientId(), e);
        }

        return tokenInfo;
    }

    @Override
    public BatchRevokeResponse revokeAllByClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new ClientException(
                    ErrorCode.TOKEN_CLIENT_ID_REQUIRED,
                    ServerErrorMessage.CLIENT_ID_REQUIRED
            );
        }

        // 通过 clientId（AKSK 字符串）查找 registered client 的 UUID
        OAuth2RegisteredClientEntity clientEntity = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new ClientException(
                        ErrorCode.CLIENT_NOT_FOUND,
                        String.format(ErrorMessage.CLIENT_NOT_FOUND, clientId)
                ));

        int revokedCount = 0;
        int page = 0;
        int batchSize = 200;
        Instant now = Instant.now();
        org.springframework.data.domain.Page<OAuth2AuthorizationEntity> batch;

        do {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, batchSize);
            batch = authorizationEntityRepository
                    .findByRegisteredClientIdOrderByAccessTokenIssuedAtDesc(clientEntity.getId(), pageable);

            for (OAuth2AuthorizationEntity entity : batch.getContent()) {
                // 跳过已过期的 token
                if (entity.getAccessTokenExpiresAt() != null
                        && entity.getAccessTokenExpiresAt().isBefore(now)) {
                    continue;
                }
                // 跳过已撤销的 token
                if (isAlreadyRevoked(entity.getAccessTokenMetadata())) {
                    continue;
                }
                try {
                    revokeToken(entity.getId());
                    revokedCount++;
                } catch (Exception e) {
                    log.warn("Failed to revoke token: {}", entity.getId(), e);
                }
            }
            page++;
        } while (batch.hasNext());

        log.info("Batch revoked {} tokens for client: {}", revokedCount, clientId);
        return new BatchRevokeResponse(revokedCount);
    }
}
