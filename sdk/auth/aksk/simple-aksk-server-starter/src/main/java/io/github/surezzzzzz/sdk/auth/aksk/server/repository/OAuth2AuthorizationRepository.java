package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2AuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.OAuth2RegisteredClientEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OAuth2 Authorization Repository
 * 使用JPA Repository访问oauth2_authorization表
 * 负责Entity到DTO的转换和JOIN逻辑
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class OAuth2AuthorizationRepository {

    private final OAuth2AuthorizationEntityRepository authorizationEntityRepository;
    private final OAuth2RegisteredClientEntityRepository clientEntityRepository;

    /**
     * 通用的Token查询方法（支持所有过滤条件，纯数据库分页）
     *
     * @param clientId   客户端ID（可选）
     * @param clientType 客户端类型（可选）
     * @param status     Token状态（可选）
     * @param search     搜索关键字（可选）
     * @param pageable   分页参数
     * @return Token信息分页结果
     */
    public Page<TokenInfo> queryTokensWithFilters(
            String clientId,
            Integer clientType,
            TokenInfo.TokenStatus status,
            String search,
            Pageable pageable) {

        // 构建需要查询的registeredClientId列表
        List<String> registeredClientIds = null;

        // 如果指定了clientId，直接查询对应的registeredClientId
        if (clientId != null && !clientId.trim().isEmpty()) {
            OAuth2RegisteredClientEntity client = clientEntityRepository.findByClientId(clientId).orElse(null);
            if (client == null) {
                return Page.empty(pageable);
            }
            registeredClientIds = new ArrayList<>();
            registeredClientIds.add(client.getId());
        }
        // 如果指定了clientType或search，需要通过client表查询
        else if (clientType != null || (search != null && !search.trim().isEmpty())) {
            List<OAuth2RegisteredClientEntity> clients;

            if (clientType != null && search != null && !search.trim().isEmpty()) {
                // 同时有clientType和search条件
                clients = clientEntityRepository.findByClientTypeAndSearchKeyword(clientType, search.trim());
            } else if (clientType != null) {
                // 只有clientType条件
                clients = clientEntityRepository.findByClientType(clientType);
            } else {
                // 只有search条件
                clients = clientEntityRepository.findBySearchKeyword(search.trim());
            }

            if (clients.isEmpty()) {
                return Page.empty(pageable);
            }

            registeredClientIds = clients.stream()
                    .map(OAuth2RegisteredClientEntity::getId)
                    .collect(Collectors.toList());
        }

        // 根据status和registeredClientIds选择查询方法
        Page<OAuth2AuthorizationEntity> authorizationPage;
        Instant now = Instant.now();

        if (status == TokenInfo.TokenStatus.EXPIRED) {
            // 查询过期Token
            if (registeredClientIds != null) {
                authorizationPage = authorizationEntityRepository.findExpiredTokensByClientIds(registeredClientIds, now, pageable);
            } else {
                authorizationPage = authorizationEntityRepository.findExpiredTokens(now, pageable);
            }
        } else if (status == TokenInfo.TokenStatus.ACTIVE) {
            // 查询未过期Token
            if (registeredClientIds != null) {
                authorizationPage = authorizationEntityRepository.findActiveTokensByClientIds(registeredClientIds, now, pageable);
            } else {
                authorizationPage = authorizationEntityRepository.findActiveTokens(now, pageable);
            }
        } else {
            // 查询所有Token
            if (registeredClientIds != null) {
                authorizationPage = authorizationEntityRepository.findByRegisteredClientIdInOrderByAccessTokenIssuedAtDesc(registeredClientIds, pageable);
            } else {
                authorizationPage = authorizationEntityRepository.findAllOrderByIssuedAtDesc(pageable);
            }
        }

        return authorizationPage.map(this::convertToTokenInfo);
    }

    /**
     * 根据ID查询Token
     *
     * @param id 授权ID
     * @return Token信息，如果不存在返回null
     */
    public TokenInfo findById(String id) {
        return authorizationEntityRepository.findById(id)
                .map(this::convertToTokenInfo)
                .orElse(null);
    }

    /**
     * 删除授权记录
     *
     * @param id 授权ID
     */
    public void deleteById(String id) {
        authorizationEntityRepository.deleteById(id);
        log.info("Deleted authorization from MySQL: {}", id);
    }

    /**
     * 删除过期的授权记录
     *
     * @return 删除的记录数
     */
    public int deleteExpired() {
        int count = authorizationEntityRepository.deleteByAccessTokenExpiresAtBefore(Instant.now());
        log.info("Deleted {} expired authorizations from MySQL", count);
        return count;
    }

    /**
     * 统计总数
     *
     * @return 授权记录总数
     */
    public long countAll() {
        return authorizationEntityRepository.countAllAuthorizations();
    }

    /**
     * 删除所有授权记录
     */
    public void deleteAll() {
        authorizationEntityRepository.deleteAll();
        log.info("Deleted all authorizations from MySQL");
    }

    /**
     * 将OAuth2AuthorizationEntity转换为TokenInfo（包含Client信息）
     *
     * @param authorization 授权实体
     * @return Token信息
     */
    private TokenInfo convertToTokenInfo(OAuth2AuthorizationEntity authorization) {
        TokenInfo tokenInfo = new TokenInfo();

        // 基本信息
        tokenInfo.setId(authorization.getId());
        tokenInfo.setRegisteredClientId(authorization.getRegisteredClientId());

        // 查询Client信息（LEFT JOIN逻辑）
        OAuth2RegisteredClientEntity client = clientEntityRepository.findById(authorization.getRegisteredClientId())
                .orElse(null);

        if (client != null) {
            tokenInfo.setClientId(client.getClientId());
            tokenInfo.setClientName(client.getClientName());
            tokenInfo.setClientType(client.getClientType());
            tokenInfo.setOwnerUserId(client.getOwnerUserId());
            tokenInfo.setOwnerUsername(client.getOwnerUsername());
        }

        // 反序列化BLOB获取Token值
        tokenInfo.setTokenValue(deserializeToken(authorization.getAccessTokenValue()));

        // 时间信息
        tokenInfo.setIssuedAt(authorization.getAccessTokenIssuedAt());
        tokenInfo.setExpiresAt(authorization.getAccessTokenExpiresAt());

        // 计算状态
        if (tokenInfo.getExpiresAt() != null) {
            boolean isExpired = Instant.now().isAfter(tokenInfo.getExpiresAt());
            tokenInfo.setStatus(isExpired ? TokenInfo.TokenStatus.EXPIRED : TokenInfo.TokenStatus.ACTIVE);
        } else {
            tokenInfo.setStatus(TokenInfo.TokenStatus.ACTIVE);
        }

        // Scopes
        String scopesStr = authorization.getAccessTokenScopes();
        if (scopesStr != null && !scopesStr.isEmpty()) {
            tokenInfo.setScopes(Arrays.asList(scopesStr.split(",")));
        }

        // 数据源
        tokenInfo.setDataSource(TokenInfo.DataSource.MYSQL);

        return tokenInfo;
    }

    /**
     * 反序列化Token BLOB
     *
     * @param bytes BLOB字节数组
     * @return Token值
     */
    private String deserializeToken(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "[Empty Token]";
        }

        // 先尝试作为Java序列化对象
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            OAuth2AccessToken token = (OAuth2AccessToken) ois.readObject();
            return token.getTokenValue();
        } catch (Exception e) {
            // 如果反序列化失败,尝试直接作为UTF-8字符串读取
            try {
                String tokenStr = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                // 简单验证是否看起来像token字符串(不包含太多不可打印字符)
                if (tokenStr.length() > 0 && tokenStr.length() < 10000) {
                    return tokenStr;
                }
            } catch (Exception ex) {
                log.debug("Also failed to read as UTF-8 string", ex);
            }

            log.warn("Failed to deserialize token: {}", e.getMessage());
            return "[Unable to deserialize]";
        }
    }
}
