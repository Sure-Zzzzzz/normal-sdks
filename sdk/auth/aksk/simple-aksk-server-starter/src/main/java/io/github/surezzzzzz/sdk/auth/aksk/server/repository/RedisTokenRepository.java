package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Redis Token Repository
 * 使用RedisTemplate查询Redis中的Token数据
 * 仅在Redis启用时加载
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.auth.aksk.server.redis",
        name = "enabled",
        havingValue = "true"
)
public class RedisTokenRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisKeyHelper redisKeyHelper;

    /**
     * 查询所有Redis中的Token
     * 注意：此方法需要配合CachedOAuth2AuthorizationService使用
     * Spring Cache会自动管理key前缀：sure-auth-aksk:{me}:oauth2:authorization::
     *
     * @return Token信息列表
     */
    public List<TokenInfo> findAllFromRedis() {
        // 构建完整的key pattern用于scan
        // 实际key格式：sure-auth-aksk:{me}:oauth2:authorization::{uuid}
        String keyPattern = redisKeyHelper.buildAuthorizationScanPattern();

        // 使用SCAN命令替代KEYS，避免阻塞Redis
        Set<String> keys = scanKeys(keyPattern);

        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>();
        }

        List<TokenInfo> tokenInfos = new ArrayList<>();
        for (String key : keys) {
            try {
                OAuth2Authorization authorization = getAuthorizationFromRedis(key);
                if (authorization != null) {
                    TokenInfo tokenInfo = convertToTokenInfo(authorization);
                    tokenInfo.setDataSource(TokenInfo.DataSource.REDIS);
                    tokenInfos.add(tokenInfo);
                }
            } catch (Exception e) {
                log.error("Failed to deserialize authorization from Redis: {}", key, e);
            }
        }

        return tokenInfos;
    }

    /**
     * 从Redis获取Authorization对象
     * 兼容两种序列化格式:
     * 1. Java序列化格式(历史数据,以0xAC 0xED开头)
     * 2. JSON格式(新数据)
     *
     * @param key Redis key
     * @return OAuth2Authorization对象,如果不存在或反序列化失败则返回null
     */
    private OAuth2Authorization getAuthorizationFromRedis(String key) {
        try {
            // 获取原始字节数据
            byte[] rawBytes = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                    connection.get(key.getBytes())
            );

            if (rawBytes == null || rawBytes.length == 0) {
                return null;
            }

            // 检测序列化格式
            // Java序列化格式魔术字节: 0xAC 0xED (JDK序列化流的标识)
            boolean isJavaSerialized = rawBytes.length >= 2 &&
                    (rawBytes[0] & 0xFF) == 0xAC &&
                    (rawBytes[1] & 0xFF) == 0xED;

            if (isJavaSerialized) {
                // 使用Java反序列化
                log.debug("Deserializing Java serialized data from key: {}", key);
                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(rawBytes))) {
                    return (OAuth2Authorization) ois.readObject();
                }
            } else {
                // 使用JSON反序列化
                log.debug("Deserializing JSON data from key: {}", key);
                RedisSerializer<?> valueSerializer = redisTemplate.getValueSerializer();
                return (OAuth2Authorization) valueSerializer.deserialize(rawBytes);
            }
        } catch (Exception e) {
            log.error("Failed to deserialize authorization from Redis: {}", key, e);
            return null;
        }
    }

    /**
     * 删除Redis中的Token
     * 注意：key格式需要与CachedOAuth2AuthorizationService保持一致
     *
     * @param id 授权ID
     */
    public void deleteById(String id) {
        // 构建完整的key：sure-auth-aksk:{me}:oauth2:authorization::{id}
        String key = redisKeyHelper.buildAuthorizationKeyById(id);
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Deleted authorization from Redis: {}", id);
        } else {
            log.warn("Authorization not found in Redis: {}", id);
        }
    }

    /**
     * 统计Redis中的Token数量
     * 使用SCAN命令避免阻塞Redis
     *
     * @return Token数量
     */
    public long countAll() {
        String keyPattern = redisKeyHelper.buildAuthorizationScanPattern();
        Set<String> keys = scanKeys(keyPattern);
        return keys != null ? keys.size() : 0L;
    }

    /**
     * 使用SCAN命令扫描匹配的keys
     * 避免使用KEYS命令阻塞Redis
     *
     * @param pattern key模式
     * @return 匹配的key集合
     */
    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        try {
            redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(pattern)
                        .count(SimpleAkskServerConstant.REDIS_SCAN_COUNT)
                        .build();

                Cursor<byte[]> cursor = connection.scan(options);
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
                cursor.close();
                return keys;
            });
        } catch (Exception e) {
            log.error("Failed to scan keys with pattern: {}", pattern, e);
        }
        return keys;
    }

    /**
     * 转换OAuth2Authorization为TokenInfo
     *
     * @param authorization OAuth2授权对象
     * @return TokenInfo
     */
    private TokenInfo convertToTokenInfo(OAuth2Authorization authorization) {
        TokenInfo tokenInfo = new TokenInfo();

        // 基本信息
        tokenInfo.setId(authorization.getId());
        tokenInfo.setRegisteredClientId(authorization.getRegisteredClientId());

        // Token信息
        if (authorization.getAccessToken() != null) {
            tokenInfo.setTokenValue(authorization.getAccessToken().getToken().getTokenValue());
            tokenInfo.setIssuedAt(authorization.getAccessToken().getToken().getIssuedAt());
            tokenInfo.setExpiresAt(authorization.getAccessToken().getToken().getExpiresAt());

            // 计算状态
            if (tokenInfo.getExpiresAt() != null) {
                boolean isExpired = Instant.now().isAfter(tokenInfo.getExpiresAt());
                tokenInfo.setStatus(isExpired ? TokenInfo.TokenStatus.EXPIRED : TokenInfo.TokenStatus.ACTIVE);
            } else {
                tokenInfo.setStatus(TokenInfo.TokenStatus.ACTIVE);
            }

            // Scopes
            if (authorization.getAccessToken().getToken().getScopes() != null) {
                tokenInfo.setScopes(new ArrayList<>(authorization.getAccessToken().getToken().getScopes()));
            }
        }

        // Client信息：优先从principalName获取（AKSK的client ID）
        // 如果principalName为空，则尝试从attributes中获取
        if (authorization.getPrincipalName() != null) {
            tokenInfo.setClientId(authorization.getPrincipalName());
        } else if (authorization.getAttributes().containsKey(SimpleAkskServerConstant.JWT_CLAIM_CLIENT_ID)) {
            tokenInfo.setClientId((String) authorization.getAttributes().get(SimpleAkskServerConstant.JWT_CLAIM_CLIENT_ID));
        }

        return tokenInfo;
    }
}
