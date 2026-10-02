package io.github.surezzzzzz.sdk.cache.layer;

import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.exception.CacheRouteException;
import io.github.surezzzzzz.sdk.cache.exception.SmartCacheException;
import io.github.surezzzzzz.sdk.cache.serializer.SmartCacheSerializer;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import io.github.surezzzzzz.sdk.redis.route.exception.SimpleRedisRouteException;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * L2 Redis 缓存
 *
 * @author surezzzzzz
 */
@Slf4j
public class L2Cache {

    private final SmartCacheProperties properties;
    private final RedisRouteTemplate redisRouteTemplate;
    private final SmartCacheSerializer smartCacheSerializer;

    public L2Cache(SmartCacheProperties properties,
                   RedisRouteTemplate redisRouteTemplate,
                   SmartCacheSerializer smartCacheSerializer) {
        this.properties = properties;
        this.redisRouteTemplate = redisRouteTemplate;
        this.smartCacheSerializer = smartCacheSerializer;
    }

    private String buildKey(String cacheName, String key) {
        return KeyHelper.buildCacheKey(getKeyFormat(), getKeyPrefix(), cacheName, getMe(), key);
    }

    private String buildKeyPattern(String cacheName) {
        return KeyHelper.buildCacheKeyPattern(getKeyFormat(), getKeyPrefix(), cacheName, getMe());
    }

    private String buildCacheNamespaceRouteKey(String cacheName) {
        return getKeyPrefix() + SmartCacheConstant.KEY_SEPARATOR + cacheName + SmartCacheConstant.KEY_SEPARATOR + getMe()
                + SmartCacheConstant.KEY_SEPARATOR;
    }

    private String getKeyPrefix() {
        return properties != null && properties.getKeyPrefix() != null
                ? properties.getKeyPrefix()
                : SmartCacheConstant.REDIS_KEY_PREFIX;
    }

    private String getMe() {
        return properties != null && properties.getMe() != null
                ? properties.getMe()
                : SmartCacheConstant.DEFAULT_INSTANCE_ID;
    }

    private String getKeyFormat() {
        return properties != null && properties.getL2() != null && properties.getL2().getKeyFormat() != null
                ? properties.getL2().getKeyFormat()
                : SmartCacheConstant.DEFAULT_L2_KEY_FORMAT;
    }

    private long calculateActualTtl(int baseTtl) {
        if (baseTtl <= 0) {
            return baseTtl;
        }
        if (properties == null || properties.getL2() == null) {
            return baseTtl;
        }
        double offsetRatio = properties.getL2().getTtlRandomOffsetRatio();
        long offset = (long) (baseTtl * offsetRatio);
        if (offset <= 0) {
            return baseTtl;
        }
        long range = offset * 2 + 1;
        long result = baseTtl + ThreadLocalRandom.current().nextLong(range) - offset;
        return Math.max(1, result);
    }

    public <T> T get(String cacheName, String key) {
        return get(cacheName, key, Object.class);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Class<?> expectedType) {
        try {
            String redisKey = buildKey(cacheName, key);
            String payload = redisRouteTemplate.execute(redisKey, template -> template.opsForValue().get(redisKey));
            return (T) smartCacheSerializer.deserialize(payload, expectedType);
        } catch (Exception e) {
            log.warn("L2 缓存读取失败，cacheName：{}，key：{}，降级到 L1。原因：{}", cacheName, key, e.getMessage());
            return null;
        }
    }

    public void put(String cacheName, String key, Object value) {
        SmartCacheProperties.L2Config l2Config = properties.getL2();
        put(cacheName, key, value, l2Config.getExpireSeconds());
    }

    public void put(String cacheName, String key, Object value, int ttlSeconds) {
        if (value == null) {
            return;
        }
        try {
            String redisKey = buildKey(cacheName, key);
            String payload = smartCacheSerializer.serialize(value, value.getClass());
            long actualTtl = calculateActualTtl(ttlSeconds);
            redisRouteTemplate.execute(redisKey, template -> {
                template.opsForValue().set(redisKey, payload, actualTtl, TimeUnit.SECONDS);
                return null;
            });
        } catch (Exception e) {
            log.warn("L2 缓存写入失败，cacheName：{}，key：{}，继续使用 L1。原因：{}", cacheName, key, e.getMessage());
        }
    }

    public void evict(String cacheName, String key) {
        try {
            String redisKey = buildKey(cacheName, key);
            redisRouteTemplate.execute(redisKey, template -> {
                template.delete(redisKey);
                return null;
            });
        } catch (Exception e) {
            log.warn("L2 缓存删除失败，cacheName：{}，key：{}，继续使用 L1。原因：{}", cacheName, key, e.getMessage());
        }
    }

    public void clear(String cacheName) {
        if (!isScanEnabled()) {
            log.warn("L2 clear 已跳过，cacheName：{}，如需扫描 Redis 请开启 route.scan-enabled", cacheName);
            return;
        }
        try {
            String pattern = buildKeyPattern(cacheName);
            String routeKey = buildCacheNamespaceRouteKey(cacheName);
            redisRouteTemplate.execute(routeKey, template -> {
                RedisConnection connection = template.getConnectionFactory().getConnection();
                try {
                    scanAndDelete(connection, pattern);
                } finally {
                    connection.close();
                }
                return null;
            });
        } catch (Exception e) {
            throw new CacheRouteException(
                    ErrorCode.SMART_CACHE_ROUTE_MISSING,
                    String.format(ErrorMessage.SMART_CACHE_L2_OPERATION_FAILED, cacheName),
                    e
            );
        }
    }

    public long size(String cacheName) {
        if (!isScanEnabled()) {
            log.debug("L2 size 已跳过，cacheName：{}，如需扫描 Redis 请开启 route.scan-enabled", cacheName);
            return 0;
        }
        try {
            String pattern = buildKeyPattern(cacheName);
            String routeKey = buildCacheNamespaceRouteKey(cacheName);
            return redisRouteTemplate.execute(routeKey, template -> {
                RedisConnection connection = template.getConnectionFactory().getConnection();
                try {
                    return countKeys(connection, pattern);
                } finally {
                    connection.close();
                }
            });
        } catch (Exception e) {
            throw new CacheRouteException(
                    ErrorCode.SMART_CACHE_ROUTE_MISSING,
                    String.format(ErrorMessage.SMART_CACHE_L2_OPERATION_FAILED, cacheName),
                    e
            );
        }
    }

    public <T> Map<String, T> getAll(String cacheName, List<String> keys) {
        return getAll(cacheName, keys, Object.class);
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getAll(String cacheName, List<String> keys, Class<?> expectedType) {
        Map<String, T> result = new HashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        try {
            List<String> redisKeys = buildRedisKeys(cacheName, keys);
            List<String> values = redisRouteTemplate.execute(redisKeys, template -> template.opsForValue().multiGet(redisKeys));
            if (values != null) {
                for (int i = 0; i < keys.size(); i++) {
                    String payload = values.get(i);
                    Object value = smartCacheSerializer.deserialize(payload, expectedType);
                    if (value != null) {
                        result.put(keys.get(i), (T) value);
                    }
                }
            }
        } catch (SimpleRedisRouteException e) {
            if (io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode.REDIS_ROUTE_009.equals(e.getErrorCode())) {
                throw new CacheRouteException(
                        ErrorCode.SMART_CACHE_ROUTE_CROSS_DATASOURCE,
                        String.format(ErrorMessage.SMART_CACHE_ROUTE_CROSS_DATASOURCE, cacheName),
                        e
                );
            }
            throw new SmartCacheException(
                    ErrorCode.SMART_CACHE_L2_OPERATION_FAILED,
                    String.format(ErrorMessage.SMART_CACHE_L2_OPERATION_FAILED, cacheName),
                    e
            );
        } catch (SmartCacheException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartCacheException(
                    ErrorCode.SMART_CACHE_L2_OPERATION_FAILED,
                    String.format(ErrorMessage.SMART_CACHE_L2_OPERATION_FAILED, cacheName),
                    e
            );
        }
        return result;
    }

    public long getTtl(String cacheName, String key) {
        try {
            String redisKey = buildKey(cacheName, key);
            Long ttl = redisRouteTemplate.execute(redisKey, template -> template.getExpire(redisKey, TimeUnit.SECONDS));
            return ttl != null ? ttl : -1;
        } catch (Exception e) {
            log.warn("L2 TTL 读取失败，cacheName：{}，key：{}，原因：{}", cacheName, key, e.getMessage());
            return -1;
        }
    }

    public void putAll(String cacheName, Map<String, Object> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Map<String, String> payloads = new HashMap<>();
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            if (entry.getValue() != null) {
                String redisKey = buildKey(cacheName, entry.getKey());
                payloads.put(redisKey, smartCacheSerializer.serialize(entry.getValue(), entry.getValue().getClass()));
            }
        }
        if (payloads.isEmpty()) {
            return;
        }
        SmartCacheProperties.L2Config l2Config = properties.getL2();
        long actualTtl = calculateActualTtl(l2Config.getExpireSeconds());
        List<String> redisKeys = new ArrayList<>(payloads.keySet());
        try {
            redisRouteTemplate.execute(redisKeys, template -> {
                template.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                        payloads.forEach((redisKey, payload) -> operations.opsForValue().set(
                                redisKey, payload, actualTtl, TimeUnit.SECONDS));
                        return null;
                    }
                });
                return null;
            });
        } catch (SimpleRedisRouteException e) {
            if (io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode.REDIS_ROUTE_009.equals(e.getErrorCode())) {
                throw new CacheRouteException(
                        ErrorCode.SMART_CACHE_ROUTE_CROSS_DATASOURCE,
                        String.format(ErrorMessage.SMART_CACHE_ROUTE_CROSS_DATASOURCE, cacheName),
                        e
                );
            }
            throw new SmartCacheException(
                    ErrorCode.SMART_CACHE_L2_OPERATION_FAILED,
                    String.format(ErrorMessage.SMART_CACHE_L2_OPERATION_FAILED, cacheName),
                    e
            );
        } catch (SmartCacheException e) {
            throw e;
        } catch (Exception e) {
            throw new SmartCacheException(
                    ErrorCode.SMART_CACHE_L2_OPERATION_FAILED,
                    String.format(ErrorMessage.SMART_CACHE_L2_OPERATION_FAILED, cacheName),
                    e
            );
        }
    }

    private List<String> buildRedisKeys(String cacheName, List<String> keys) {
        List<String> redisKeys = new ArrayList<>(keys.size());
        for (String key : keys) {
            redisKeys.add(buildKey(cacheName, key));
        }
        return redisKeys;
    }

    private boolean isScanEnabled() {
        return properties != null && properties.getRoute() != null
                && Boolean.TRUE.equals(properties.getRoute().getScanEnabled());
    }

    private int getScanCount() {
        if (properties == null || properties.getRoute() == null || properties.getRoute().getScanCount() == null) {
            return SmartCacheConstant.DEFAULT_SCAN_COUNT;
        }
        return properties.getRoute().getScanCount();
    }

    private void scanAndDelete(RedisConnection connection, String pattern) {
        Cursor<byte[]> cursor = null;
        try {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(getScanCount()).build();
            cursor = connection.scan(options);
            List<byte[]> batch = new ArrayList<>(getScanCount());
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= getScanCount()) {
                    deleteBatch(connection, batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                deleteBatch(connection, batch);
            }
        } finally {
            closeCursor(cursor);
        }
    }

    private long countKeys(RedisConnection connection, String pattern) {
        Cursor<byte[]> cursor = null;
        long count = 0;
        try {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(getScanCount()).build();
            cursor = connection.scan(options);
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
            return count;
        } finally {
            closeCursor(cursor);
        }
    }

    private void deleteBatch(RedisConnection connection, List<byte[]> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (byte[] key : keys) {
            connection.del(key);
        }
    }

    private void closeCursor(Cursor<byte[]> cursor) {
        if (cursor == null) {
            return;
        }
        try {
            cursor.close();
        } catch (Exception e) {
            log.debug("关闭 Redis scan cursor 失败：{}", e.getMessage());
        }
    }
}
