package io.github.surezzzzzz.sdk.auth.aksk.server.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.core.model.TokenInfo;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.RedisKeyHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
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
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.auth.aksk.server.redis",
        name = "enabled",
        havingValue = "true"
)
public class RedisTokenRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisKeyHelper redisKeyHelper;
    private static final ObjectMapper PLAIN_MAPPER = new ObjectMapper();

    // Spring OAuth2Authorization JSON field names (internal serialization format)
    private static final String JSON_FIELD_ID = "id";
    private static final String JSON_FIELD_REGISTERED_CLIENT_ID = "registeredClientId";
    private static final String JSON_FIELD_PRINCIPAL_NAME = "principalName";
    private static final String JSON_FIELD_ACCESS_TOKEN = "accessToken";
    private static final String JSON_FIELD_TOKEN = "token";
    private static final String JSON_FIELD_TOKEN_VALUE = "tokenValue";
    private static final String JSON_FIELD_ISSUED_AT = "issuedAt";
    private static final String JSON_FIELD_EXPIRES_AT = "expiresAt";
    private static final String JSON_FIELD_METADATA = "metadata";
    private static final String JSON_FIELD_SCOPES = "scopes";
    private static final String JSON_PATH_TOKEN_INVALIDATED = "metadata.token.invalidated";

    public RedisTokenRepository(
            @Qualifier("smartCacheRedisTemplate") RedisTemplate<String, Object> redisTemplate,
            RedisKeyHelper redisKeyHelper) {
        this.redisTemplate = redisTemplate;
        this.redisKeyHelper = redisKeyHelper;
    }

    public List<TokenInfo> findAllFromRedis() {
        String keyPattern = redisKeyHelper.buildAuthorizationScanPattern();
        Set<String> keys = scanKeys(keyPattern);
        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>();
        }
        List<TokenInfo> tokenInfos = new ArrayList<>();
        for (String key : keys) {
            try {
                TokenInfo tokenInfo = readTokenInfoFromRedis(key);
                if (tokenInfo != null) {
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
     * 读取 Redis 中的 token 数据。
     * 兼容两种格式：
     * 1. Java 序列化（历史数据，0xAC 0xED 开头）→ 反序列化为 OAuth2Authorization
     * 2. SmartCache JSON（新数据）→ 直接解析 JsonNode，不反序列化为 OAuth2Authorization
     */
    private TokenInfo readTokenInfoFromRedis(String key) throws Exception {
        byte[] rawBytes = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.get(key.getBytes())
        );
        if (rawBytes == null || rawBytes.length == 0) {
            return null;
        }

        boolean isJavaSerialized = rawBytes.length >= 2
                && (rawBytes[0] & 0xFF) == 0xAC
                && (rawBytes[1] & 0xFF) == 0xED;

        if (isJavaSerialized) {
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(rawBytes))) {
                OAuth2Authorization auth = (OAuth2Authorization) ois.readObject();
                return convertFromOAuth2Authorization(auth);
            }
        } else {
            // SmartCache JSON 格式：DefaultTyping 写入 ["@class", {...}]
            JsonNode root = PLAIN_MAPPER.readTree(rawBytes);
            JsonNode obj = root.isArray() ? root.get(1) : root;
            return convertFromJsonNode(obj);
        }
    }

    private TokenInfo convertFromOAuth2Authorization(OAuth2Authorization auth) {
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setId(auth.getId());
        tokenInfo.setRegisteredClientId(auth.getRegisteredClientId());
        tokenInfo.setClientId(auth.getPrincipalName());
        if (auth.getAccessToken() != null) {
            tokenInfo.setTokenValue(auth.getAccessToken().getToken().getTokenValue());
            tokenInfo.setIssuedAt(auth.getAccessToken().getToken().getIssuedAt());
            tokenInfo.setExpiresAt(auth.getAccessToken().getToken().getExpiresAt());
            tokenInfo.setStatus(computeStatus(
                    auth.getAccessToken().isInvalidated(), tokenInfo.getExpiresAt()));
            if (auth.getAccessToken().getToken().getScopes() != null) {
                tokenInfo.setScopes(new ArrayList<>(auth.getAccessToken().getToken().getScopes()));
            }
        }
        return tokenInfo;
    }

    private TokenInfo convertFromJsonNode(JsonNode obj) {
        TokenInfo tokenInfo = new TokenInfo();
        tokenInfo.setId(text(obj, JSON_FIELD_ID));
        tokenInfo.setRegisteredClientId(text(obj, JSON_FIELD_REGISTERED_CLIENT_ID));
        tokenInfo.setClientId(text(obj, JSON_FIELD_PRINCIPAL_NAME));

        JsonNode accessTokenNode = obj.get(JSON_FIELD_ACCESS_TOKEN);
        // DefaultTyping 数组格式：["@class", {actual}]
        if (accessTokenNode != null && accessTokenNode.isArray() && accessTokenNode.size() == 2) {
            accessTokenNode = accessTokenNode.get(1);
        }
        if (accessTokenNode != null && !accessTokenNode.isNull()) {
            JsonNode tokenNode = accessTokenNode.get(JSON_FIELD_TOKEN);
            if (tokenNode != null && tokenNode.isArray() && tokenNode.size() == 2) {
                tokenNode = tokenNode.get(1);
            }
            if (tokenNode != null) {
                tokenInfo.setTokenValue(text(tokenNode, JSON_FIELD_TOKEN_VALUE));
                tokenInfo.setIssuedAt(parseInstant(tokenNode.get(JSON_FIELD_ISSUED_AT)));
                tokenInfo.setExpiresAt(parseInstant(tokenNode.get(JSON_FIELD_EXPIRES_AT)));
            }
            JsonNode metadata = accessTokenNode.get(JSON_FIELD_METADATA);
            if (metadata != null && metadata.isArray() && metadata.size() == 2) {
                metadata = metadata.get(1);
            }
            boolean invalidated = metadata != null
                    && metadata.path(JSON_PATH_TOKEN_INVALIDATED).asBoolean(false);
            tokenInfo.setStatus(computeStatus(invalidated, tokenInfo.getExpiresAt()));

            JsonNode scopes = tokenNode != null ? tokenNode.get(JSON_FIELD_SCOPES) : null;
            if (scopes != null && scopes.isArray() && scopes.size() == 2) {
                scopes = scopes.get(1);
            }
            if (scopes != null && scopes.isArray()) {
                List<String> scopeList = new ArrayList<>();
                for (JsonNode s : scopes) {
                    scopeList.add(s.asText());
                }
                tokenInfo.setScopes(scopeList);
            }
        }
        return tokenInfo;
    }

    private TokenInfo.TokenStatus computeStatus(boolean invalidated, Instant expiresAt) {
        if (invalidated) return TokenInfo.TokenStatus.REVOKED;
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) return TokenInfo.TokenStatus.EXPIRED;
        return TokenInfo.TokenStatus.ACTIVE;
    }

    private String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null && !n.isNull() ? n.asText() : null;
    }

    private Instant parseInstant(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try {
            return Instant.parse(node.asText());
        } catch (Exception e) {
            return null;
        }
    }

    public void deleteById(String id) {
        // 先尝试标准格式的key
        String key = redisKeyHelper.buildAuthorizationKeyById(id);
        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("Deleted authorization from Redis: {}", id);
        } else {
            // 如果标准格式没找到，尝试用多种模式扫描并删除
            log.warn("Standard key not found, trying scan to find and delete: {}", id);
            String scanPattern = redisKeyHelper.buildAuthorizationScanPattern();
            Set<String> keys = scanKeys(scanPattern);

            if (keys != null && !keys.isEmpty()) {
                for (String k : keys) {
                    if (k.contains(id)) {
                        try {
                            redisTemplate.delete(k);
                            log.info("Scanned and deleted authorization from Redis: {}", k);
                        } catch (Exception e) {
                            log.warn("Failed to delete key via scan: {}", k, e);
                        }
                    }
                }
            } else {
                log.warn("Authorization not found in Redis via scan: {}", id);
            }
        }
    }

    public long countAll() {
        String keyPattern = redisKeyHelper.buildAuthorizationScanPattern();
        Set<String> keys = scanKeys(keyPattern);
        return keys != null ? keys.size() : 0L;
    }

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
}
