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
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenEventCause;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenRevokedEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ClientException;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.SimpleAkskServerException;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.ManagementDataAccessPlanHelper;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Token 管理服务实现。
 *
 * <p>撤销操作同时维护授权状态、缓存与生命周期事件；事件构造或发布失败时必须上抛，
 * 由外层事务避免提交不完整的状态变更。
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
public class TokenManagementServiceImpl implements TokenManagementService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PROP_ACCESS_TOKEN_ISSUED_AT = "accessTokenIssuedAt";
    private static final String INVALIDATED_KEY = "metadata.token.invalidated";
    private static final String JSON_FIELD_TOKEN_VALUE = "tokenValue";
    private static final java.nio.charset.Charset UTF_8 = java.nio.charset.StandardCharsets.UTF_8;

    private final OAuth2AuthorizationRepository mysqlRepository;
    private final OAuth2AuthorizationEntityRepository authorizationEntityRepository;
    private final OAuth2RegisteredClientEntityRepository clientRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTokenRepository redisRepository;
    private final SmartCacheManager smartCacheManager;
    private final RedisKeyHelper redisKeyHelper;

    public TokenManagementServiceImpl(
            OAuth2AuthorizationRepository mysqlRepository,
            OAuth2AuthorizationEntityRepository authorizationEntityRepository,
            OAuth2RegisteredClientEntityRepository clientRepository,
            ApplicationEventPublisher eventPublisher,
            RedisTokenRepository redisRepository,
            SmartCacheManager smartCacheManager,
            RedisKeyHelper redisKeyHelper) {
        this.mysqlRepository = mysqlRepository;
        this.authorizationEntityRepository = authorizationEntityRepository;
        this.clientRepository = clientRepository;
        this.eventPublisher = eventPublisher;
        this.redisRepository = redisRepository;
        this.smartCacheManager = smartCacheManager;
        this.redisKeyHelper = redisKeyHelper;
    }

    @Override
    public PageResponse<TokenInfoResponse> queryTokens(TokenQueryRequest request) {
        // 创建分页参数 (Spring Data JPA的页码从0开始，所以需要-1)
        int currentPage = Math.max(1, request.getPage());
        int pageSize = Math.max(1, request.getSize());
        Sort sort = Sort.by(Sort.Direction.DESC, PROP_ACCESS_TOKEN_ISSUED_AT);
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
        List<TokenInfo> redisTokens = redisRepository.findAllFromRedis();
        tokenInfo = redisTokens.stream()
                .filter(t -> id.equals(t.getId()))
                .findFirst()
                .orElse(null);
        if (tokenInfo != null) {
            return toTokenInfoResponse(enrichClientInfo(tokenInfo));
        }
        return null;
    }

    @Override
    @Transactional
    public void revokeToken(String id) {
        revokeToken(id, TokenEventCause.TOKEN_MANAGEMENT);
    }

    private void revokeToken(String id, TokenEventCause cause) {
        OAuth2AuthorizationEntity entity = authorizationEntityRepository.findById(id).orElse(null);
        if (entity == null) {
            // MySQL 里没有，但 Redis 里可能有
            log.warn("Token not found in MySQL, checking Redis: {}", id);
            // 先尝试从 Redis 获取 token 信息，以便发布完整的撤销事件
            List<TokenInfo> allRedisTokens = redisRepository.findAllFromRedis();
            TokenInfo redisToken = allRedisTokens.stream()
                    .filter(token -> id.equals(token.getId()))
                    .findFirst()
                    .orElse(null);

            if (redisToken != null) {
                log.info("Token found in Redis, revoking: {}", id);
                try {
                    // 事件构造或发布失败时不能继续删除 Redis 数据，否则会形成状态已变更但生命周期事件缺失的伪成功。
                    eventPublisher.publishEvent(new TokenRevokedEvent(
                            this, cause,
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
                    throw tokenOperationFailed(id, e);
                }
            }

            // 清除 Redis 中的 token
            redisRepository.deleteById(id);
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
            throw new SimpleAkskServerException(ErrorCode.TOKEN_OPERATION_FAILED,
                    String.format(ServerErrorMessage.TOKEN_OPERATION_FAILED, id));
        }
        authorizationEntityRepository.updateAccessTokenMetadata(id, updatedMetadata);
        log.debug("Updated token metadata in database: {}", id);

        // 2. 清 Redis 缓存（id 缓存 + token value 缓存）
        evictTokenCache(id, entity);

        // 3. 发布撤销事件
        publishRevokedEvent(entity, cause);

        log.info("Token revoked: {}", id);
    }

    private boolean isAlreadyRevoked(byte[] metadataBytes) {
        if (metadataBytes == null || metadataBytes.length == 0) return false;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(new String(metadataBytes, UTF_8));
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
                        new String(metadataBytes, UTF_8));
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
            redisRepository.deleteById(id);
            log.debug("Evicted token cache: {}", id);
        } catch (Exception e) {
            log.warn("Failed to evict token cache: {}", id, e);
            throw new SimpleAkskServerException(ErrorCode.CACHE_OPERATION_FAILED,
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, id), e);
        }
    }

    private String deserializeTokenValue(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        String tokenValue = new String(bytes, UTF_8);
        try {
            JsonNode root = OBJECT_MAPPER.readTree(tokenValue);
            JsonNode embeddedToken = root == null ? null : root.get(JSON_FIELD_TOKEN_VALUE);
            return embeddedToken == null ? tokenValue : embeddedToken.asText();
        } catch (Exception e) {
            return tokenValue;
        }
    }

    private void publishRevokedEvent(OAuth2AuthorizationEntity entity, TokenEventCause cause) {
        try {
            // 撤销事件必须具有最小上下文；缺失时让外层事务回滚，不能以不完整事件掩盖真实变更。
            TokenInfo tokenInfo = mysqlRepository.findById(entity.getId());
            if (tokenInfo == null) {
                throw new SimpleAkskServerException(ErrorCode.TOKEN_OPERATION_FAILED, "Token audit data not found");
            }
            eventPublisher.publishEvent(new TokenRevokedEvent(
                    this, cause,
                    tokenInfo.getClientId(),
                    null,  // clientType 由 JWT claims 提供；当前审计数据源不保存该字段，因此不伪造值
                    tokenInfo.getOwnerUserId(),
                    tokenInfo.getOwnerUsername(),
                    tokenInfo.getTokenValue(),
                    tokenInfo.getScopes() != null ? new java.util.HashSet<>(tokenInfo.getScopes()) : null,
                    tokenInfo.getIssuedAt(),
                    tokenInfo.getExpiresAt()
            ));
            log.debug("Published TokenRevokedEvent: {}", entity.getId());
        } catch (Exception e) {
            throw tokenOperationFailed(entity.getId(), e);
        }
    }

    /**
     * 将生命周期事件链路失败提升为业务失败，使外层事务不会提交缺少对应事件的 Token 状态。
     */
    private SimpleAkskServerException tokenOperationFailed(String tokenId, Exception cause) {
        return new SimpleAkskServerException(ErrorCode.TOKEN_OPERATION_FAILED,
                String.format(ServerErrorMessage.TOKEN_OPERATION_FAILED, tokenId), cause);
    }

    @Override
    @Transactional
    public void deleteToken(String id) {
        // 先撤销（这会处理撤销事件和清理Redis）
        try {
            revokeToken(id);
        } catch (Exception e) {
            log.warn("Failed to revoke token before deletion: {}", id, e);
            throw new SimpleAkskServerException(ErrorCode.TOKEN_OPERATION_FAILED,
                    String.format(ServerErrorMessage.TOKEN_OPERATION_FAILED, id), e);
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
            throw new SimpleAkskServerException(ErrorCode.TOKEN_OPERATION_FAILED,
                    String.format(ServerErrorMessage.TOKEN_OPERATION_FAILED, id), e);
        }

        // 确保也从 Redis 删除（revokeToken 里已经删了，这里再确保一下）
        try {
            redisRepository.deleteById(id);
            log.info("Token deleted from Redis: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete token from Redis: {}", id, e);
            throw new SimpleAkskServerException(ErrorCode.CACHE_OPERATION_FAILED,
                    String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, id), e);
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
        List<TokenInfo> redisTokens = redisRepository.findAllFromRedis();
        for (TokenInfo token : redisTokens) {
            if (token.getStatus() == TokenInfo.TokenStatus.EXPIRED) {
                try {
                    redisRepository.deleteById(token.getId());
                    redisDeleted++;
                } catch (Exception e) {
                    log.error("Failed to delete expired token from Redis: {}", token.getId(), e);
                    throw new SimpleAkskServerException(ErrorCode.CACHE_OPERATION_FAILED,
                            String.format(ServerErrorMessage.CACHE_OPERATION_FAILED, token.getId()), e);
                }
            }
        }

        int totalDeleted = mysqlDeleted + redisDeleted;
        log.info("Deleted {} expired tokens (MySQL: {}, Redis: {})", totalDeleted, mysqlDeleted, redisDeleted);
        return totalDeleted;
    }

    @Override
    public TokenStatisticsResponse getStatistics() {
        Instant now = Instant.now();

        // EXPIRED：纯 SQL COUNT，O(1)
        long expiredCount = authorizationEntityRepository.countExpired(now);
        long notExpiredCount = authorizationEntityRepository.countNotExpired(now);
        long totalCount = expiredCount + notExpiredCount;

        // ACTIVE / REVOKED：只扫未过期的 token，数量通常远小于总数
        long revokedCount = 0;
        int pageNumber = 0;
        int pageSize = 1000;
        org.springframework.data.domain.Page<OAuth2AuthorizationEntity> batch;

        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            batch = authorizationEntityRepository.findActiveTokens(now, pageable);
            for (OAuth2AuthorizationEntity entity : batch.getContent()) {
                if (isAlreadyRevoked(entity.getAccessTokenMetadata())) {
                    revokedCount++;
                }
            }
            pageNumber++;
        } while (batch.hasNext());

        long activeCount = notExpiredCount - revokedCount;

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
    @Transactional
    public BatchRevokeResponse revokeAllByClientId(String clientId) {
        return revokeAllByClientId(clientId, TokenEventCause.TOKEN_MANAGEMENT);
    }

    @Override
    @Transactional
    public BatchRevokeResponse revokeAllByClientId(String clientId, TokenEventCause cause) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new ClientException(
                    ErrorCode.TOKEN_CLIENT_ID_REQUIRED,
                    ServerErrorMessage.CLIENT_ID_REQUIRED
            );
        }

        OAuth2RegisteredClientEntity clientEntity = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new ClientException(
                        ErrorCode.CLIENT_NOT_FOUND,
                        String.format(ErrorMessage.CLIENT_NOT_FOUND, clientId)
                ));

        int revokedCount = 0;
        int page = 0;
        int batchSize = 200;
        Instant now = Instant.now();
        Set<String> processedTokenIds = new HashSet<>();
        org.springframework.data.domain.Page<OAuth2AuthorizationEntity> batch;

        do {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, batchSize);
            batch = authorizationEntityRepository
                    .findByRegisteredClientIdOrderByAccessTokenIssuedAtDesc(clientEntity.getId(), pageable);

            for (OAuth2AuthorizationEntity entity : batch.getContent()) {
                processedTokenIds.add(entity.getId());
                if (entity.getAccessTokenExpiresAt() != null
                        && entity.getAccessTokenExpiresAt().isBefore(now)) {
                    continue;
                }
                if (isAlreadyRevoked(entity.getAccessTokenMetadata())) {
                    continue;
                }
                try {
                    revokeToken(entity.getId(), cause);
                    revokedCount++;
                } catch (Exception e) {
                    log.warn("Failed to revoke token: {}", entity.getId(), e);
                    throw new SimpleAkskServerException(ErrorCode.TOKEN_OPERATION_FAILED,
                            String.format(ServerErrorMessage.TOKEN_OPERATION_FAILED, entity.getId()), e);
                }
            }
            page++;
        } while (batch.hasNext());

        for (TokenInfo token : redisRepository.findAllFromRedis()) {
            if (!clientId.equals(token.getClientId())
                    || processedTokenIds.contains(token.getId())
                    || token.getStatus() != TokenInfo.TokenStatus.ACTIVE) {
                continue;
            }
            try {
                revokeToken(token.getId(), cause);
                revokedCount++;
            } catch (Exception e) {
                log.warn("Failed to revoke Redis token: {}", token.getId(), e);
                throw new SimpleAkskServerException(ErrorCode.TOKEN_OPERATION_FAILED,
                        String.format(ServerErrorMessage.TOKEN_OPERATION_FAILED, token.getId()), e);
            }
        }

        log.info("Batch revoked {} tokens for client: {}", revokedCount, clientId);
        return new BatchRevokeResponse(revokedCount);
    }

    @Override
    public PageResponse<TokenInfoResponse> queryTokens(TokenQueryRequest request, DataAccessPlan plan) {
        TokenQueryRequest query = new TokenQueryRequest();
        query.setClientId(request.getClientId());
        query.setClientType(request.getClientType());
        query.setSearch(request.getSearch());
        List<TokenInfo> tokens = queryAllMysqlTokens(query).stream()
                .filter(token -> request.getStatus() == null || token.getStatus() == request.getStatus())
                .filter(token -> ManagementDataAccessPlanHelper.isTokenAllowed(plan, token))
                .collect(Collectors.toList());
        return pageTokens(tokens, request.getPage(), request.getSize());
    }

    @Override
    public PageResponse<TokenInfoResponse> queryRedisTokens(TokenInfo.TokenStatus status, int page, int size,
                                                            DataAccessPlan plan) {
        List<TokenInfo> tokens = redisRepository.findAllFromRedis().stream()
                .map(this::enrichClientInfo)
                .filter(token -> status == null || token.getStatus() == status)
                .filter(token -> ManagementDataAccessPlanHelper.isTokenAllowed(plan, token))
                .collect(Collectors.toList());
        return pageTokens(tokens, page, size);
    }

    @Override
    public TokenInfoResponse getTokenById(String id, DataAccessPlan plan) {
        TokenInfo token = findToken(id);
        if (token == null) {
            return null;
        }
        requireTokenAllowed(plan, token);
        return toTokenInfoResponse(token);
    }

    @Override
    @Transactional
    public void revokeToken(String id, DataAccessPlan plan) {
        requireTokenAllowed(plan, requireToken(id));
        revokeToken(id, TokenEventCause.TOKEN_MANAGEMENT);
    }

    @Override
    public void deleteToken(String id, DataAccessPlan plan) {
        requireTokenAllowed(plan, requireToken(id));
        deleteToken(id);
    }

    @Override
    public int deleteExpiredTokens(DataAccessPlan plan) {
        List<TokenInfo> tokens = queryAllMysqlTokens(expiredRequest()).stream()
                .filter(token -> ManagementDataAccessPlanHelper.isTokenAllowed(plan, token))
                .collect(Collectors.toList());
        List<TokenInfo> redisTokens = redisRepository.findAllFromRedis().stream()
                .map(this::enrichClientInfo)
                .filter(token -> token.getStatus() == TokenInfo.TokenStatus.EXPIRED)
                .filter(token -> ManagementDataAccessPlanHelper.isTokenAllowed(plan, token))
                .collect(Collectors.toList());
        for (TokenInfo token : tokens) {
            mysqlRepository.deleteById(token.getId());
        }
        for (TokenInfo token : redisTokens) {
            redisRepository.deleteById(token.getId());
        }
        return tokens.size() + redisTokens.size();
    }

    @Override
    public TokenStatisticsResponse getStatistics(DataAccessPlan plan) {
        List<TokenInfo> tokens = queryAllMysqlTokens(new TokenQueryRequest()).stream()
                .filter(token -> ManagementDataAccessPlanHelper.isTokenAllowed(plan, token))
                .collect(Collectors.toList());
        TokenStatisticsResponse statistics = new TokenStatisticsResponse();
        long active = tokens.stream().filter(token -> token.getStatus() == TokenInfo.TokenStatus.ACTIVE).count();
        long revoked = tokens.stream().filter(token -> token.getStatus() == TokenInfo.TokenStatus.REVOKED).count();
        long expired = tokens.stream().filter(token -> token.getStatus() == TokenInfo.TokenStatus.EXPIRED).count();
        statistics.setTotalCount((long) tokens.size());
        statistics.setActiveCount(active);
        statistics.setRevokedCount(revoked);
        statistics.setExpiredCount(expired);
        statistics.setMysqlCount((long) tokens.size());
        statistics.setRedisCount(0L);
        statistics.setBothCount(0L);
        return statistics;
    }

    @Override
    public BatchRevokeResponse revokeAllByClientId(String clientId, DataAccessPlan plan) {
        return revokeAllByClientId(clientId, plan, TokenEventCause.TOKEN_MANAGEMENT);
    }

    @Override
    @Transactional
    public BatchRevokeResponse revokeAllByClientId(String clientId, DataAccessPlan plan, TokenEventCause cause) {
        requireAllByClientIdAllowed(clientId, plan);
        return revokeAllByClientId(clientId, cause);
    }

    @Override
    public void requireAllByClientIdAllowed(String clientId, DataAccessPlan plan) {
        OAuth2RegisteredClientEntity client = clientRepository.findByClientId(clientId)
                .orElseThrow(() -> new ClientException(ErrorCode.CLIENT_NOT_FOUND,
                        String.format(ErrorMessage.CLIENT_NOT_FOUND, clientId)));
        List<TokenInfo> mysqlTokens = queryAllMysqlTokens(clientRequest(client.getClientId()));
        for (TokenInfo token : mysqlTokens) {
            if (token.getStatus() == TokenInfo.TokenStatus.ACTIVE) {
                requireTokenAllowed(plan, token);
            }
        }
        for (TokenInfo token : redisRepository.findAllFromRedis().stream()
                .map(this::enrichClientInfo)
                .filter(token -> clientId.equals(token.getClientId()))
                .filter(token -> token.getStatus() == TokenInfo.TokenStatus.ACTIVE)
                .collect(Collectors.toList())) {
            requireTokenAllowed(plan, token);
        }
    }

    private List<TokenInfo> queryAllMysqlTokens(TokenQueryRequest request) {
        TokenQueryRequest query = request == null ? new TokenQueryRequest() : request;
        List<TokenInfo> tokens = new ArrayList<>();
        int pageNumber = 0;
        int batchSize = 200;
        Page<TokenInfo> page;
        do {
            page = mysqlRepository.queryTokensWithFilters(query.getClientId(), query.getClientType(),
                    query.getStatus(), query.getSearch(), PageRequest.of(pageNumber, batchSize,
                            Sort.by(Sort.Direction.DESC, PROP_ACCESS_TOKEN_ISSUED_AT)));
            tokens.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return tokens;
    }

    private PageResponse<TokenInfoResponse> pageTokens(List<TokenInfo> tokens, int page, int size) {
        List<TokenInfo> sorted = tokens.stream()
                .sorted((left, right) -> right.getIssuedAt().compareTo(left.getIssuedAt()))
                .collect(Collectors.toList());
        int currentPage = Math.max(1, page);
        int pageSize = Math.max(1, size);
        int startIndex = Math.min((currentPage - 1) * pageSize, sorted.size());
        int endIndex = Math.min(startIndex + pageSize, sorted.size());
        List<TokenInfoResponse> response = sorted.subList(startIndex, endIndex).stream()
                .map(this::toTokenInfoResponse)
                .collect(Collectors.toList());
        return PageResponse.of(response, (long) sorted.size(), currentPage, pageSize);
    }

    private TokenInfo requireToken(String id) {
        TokenInfo token = findToken(id);
        if (token == null) {
            throw new ManagementAccessDeniedException();
        }
        return token;
    }

    private TokenInfo findToken(String id) {
        TokenInfo token = mysqlRepository.findById(id);
        if (token != null) {
            return token;
        }
        return redisRepository.findAllFromRedis().stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .map(this::enrichClientInfo)
                .orElse(null);
    }

    private TokenQueryRequest expiredRequest() {
        TokenQueryRequest request = new TokenQueryRequest();
        request.setStatus(TokenInfo.TokenStatus.EXPIRED);
        return request;
    }

    private TokenQueryRequest clientRequest(String clientId) {
        TokenQueryRequest request = new TokenQueryRequest();
        request.setClientId(clientId);
        return request;
    }

    private void requireTokenAllowed(DataAccessPlan plan, TokenInfo token) {
        if (!ManagementDataAccessPlanHelper.isTokenAllowed(plan, token)) {
            throw new ManagementAccessDeniedException();
        }
    }
}
