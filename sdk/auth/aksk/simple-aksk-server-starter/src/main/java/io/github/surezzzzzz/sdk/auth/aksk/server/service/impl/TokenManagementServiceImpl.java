package io.github.surezzzzzz.sdk.auth.aksk.server.service.impl;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.request.TokenQueryRequest;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.PageResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenInfoResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.controller.response.TokenStatisticsResponse;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2AuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.OAuth2RegisteredClientEntityRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.RedisTokenRepository;
import io.github.surezzzzzz.sdk.auth.aksk.server.service.TokenManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

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

    private final OAuth2AuthorizationRepository mysqlRepository;
    private final RedisTokenRepository redisRepository;  // 可能为null（Redis未启用时）
    private final OAuth2RegisteredClientEntityRepository clientRepository;

    public TokenManagementServiceImpl(
            OAuth2AuthorizationRepository mysqlRepository,
            @Autowired(required = false) RedisTokenRepository redisRepository,
            OAuth2RegisteredClientEntityRepository clientRepository) {
        this.mysqlRepository = mysqlRepository;
        this.redisRepository = redisRepository;
        this.clientRepository = clientRepository;
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
        // 只从MySQL查询
        TokenInfo tokenInfo = mysqlRepository.findById(id);
        return tokenInfo != null ? toTokenInfoResponse(tokenInfo) : null;
    }

    @Override
    public void deleteToken(String id) {
        // 同时删除MySQL和Redis中的数据
        try {
            mysqlRepository.deleteById(id);
        } catch (Exception e) {
            log.error("Failed to delete token from MySQL: {}", id, e);
        }

        if (redisRepository != null) {
            try {
                redisRepository.deleteById(id);
            } catch (Exception e) {
                log.error("Failed to delete token from Redis: {}", id, e);
            }
        }

        log.info("Token deleted: {}", id);
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
        Page<TokenInfo> page;

        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            page = mysqlRepository.queryTokensWithFilters(null, null, null, null, pageable);

            for (TokenInfo token : page.getContent()) {
                if (token.getStatus() == TokenInfo.TokenStatus.ACTIVE) {
                    activeCount++;
                } else {
                    expiredCount++;
                }
            }

            pageNumber++;
        } while (page.hasNext());

        TokenStatisticsResponse stats = new TokenStatisticsResponse();
        stats.setTotalCount(totalCount);
        stats.setActiveCount(activeCount);
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
}
