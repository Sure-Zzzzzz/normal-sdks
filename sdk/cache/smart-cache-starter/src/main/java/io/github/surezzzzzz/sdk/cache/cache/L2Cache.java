package io.github.surezzzzzz.sdk.cache.cache;

import io.github.surezzzzzz.sdk.cache.annotation.SmartCacheComponent;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.constant.SmartCacheConstant;
import io.github.surezzzzzz.sdk.cache.support.KeyHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.RedisSerializer;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * L2 Cache (Redis)
 * <p>
 * 分布式缓存封装
 * </p>
 *
 * @author Sure
 */
@Slf4j
@SmartCacheComponent
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnProperty(prefix = "io.github.surezzzzzz.sdk.cache.l2", name = "enabled", havingValue = "true", matchIfMissing = true)
public class L2Cache {

    private final SmartCacheProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 缓存序列化器实例，避免每次批量操作时都获取
     */
    private RedisSerializer<?> valueSerializer;

    public L2Cache(SmartCacheProperties properties,
                   @Qualifier("smartCacheRedisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 初始化序列化器缓存
     */
    @PostConstruct
    public void init() {
        if (redisTemplate != null) {
            valueSerializer = redisTemplate.getValueSerializer();
        }
    }

    /**
     * 构建 Redis Key（使用配置的 keyFormat）
     */
    private String buildKey(String cacheName, String key) {
        String keyPrefix = properties != null && properties.getKeyPrefix() != null
                ? properties.getKeyPrefix()
                : SmartCacheConstant.REDIS_KEY_PREFIX;
        String me = properties != null && properties.getMe() != null
                ? properties.getMe()
                : SmartCacheConstant.DEFAULT_INSTANCE_ID;
        String keyFormat = properties != null && properties.getL2() != null && properties.getL2().getKeyFormat() != null
                ? properties.getL2().getKeyFormat()
                : "{keyPrefix}:{cacheName}:{me}::{key}";

        return KeyHelper.buildCacheKey(keyFormat, keyPrefix, cacheName, me, key);
    }

    /**
     * 构建 Redis Key 匹配模式（用于 SCAN）
     */
    private String buildKeyPattern(String cacheName) {
        String keyPrefix = properties != null && properties.getKeyPrefix() != null
                ? properties.getKeyPrefix()
                : SmartCacheConstant.REDIS_KEY_PREFIX;
        String me = properties != null && properties.getMe() != null
                ? properties.getMe()
                : SmartCacheConstant.DEFAULT_INSTANCE_ID;
        String keyFormat = properties != null && properties.getL2() != null && properties.getL2().getKeyFormat() != null
                ? properties.getL2().getKeyFormat()
                : "{keyPrefix}:{cacheName}:{me}::{key}";

        return KeyHelper.buildCacheKeyPattern(keyFormat, keyPrefix, cacheName, me);
    }

    /**
     * 计算实际 TTL（带随机偏移，防止缓存雪崩）
     * 使用 ThreadLocalRandom 提升多线程性能
     */
    private long calculateActualTtl(int baseTtl) {
        if (baseTtl <= 0) {
            return baseTtl;
        }
        SmartCacheProperties.L2Config l2Config = properties.getL2();
        double offsetRatio = l2Config.getTtlRandomOffsetRatio();
        int offset = (int) (baseTtl * offsetRatio);
        if (offset <= 0) {
            return baseTtl;
        }
        // 使用 ThreadLocalRandom 避免多线程竞争，确保结果 >= 1
        long result = baseTtl + ThreadLocalRandom.current().nextInt(offset * 2 + 1) - offset;
        return Math.max(1, result);
    }

    /**
     * 获取缓存值
     */
    public <T> T get(String cacheName, String key) {
        try {
            String redisKey = buildKey(cacheName, key);
            Object value = redisTemplate.opsForValue().get(redisKey);
            return value != null ? (T) value : null;
        } catch (Exception e) {
            log.warn("L2 cache get failed, cacheName: {}, key: {}, fallback to L1 only. Error: {}",
                    cacheName, key, e.getMessage());
            return null;
        }
    }

    /**
     * 设置缓存值
     */
    public void put(String cacheName, String key, Object value) {
        if (value == null) {
            return;
        }
        try {
            String redisKey = buildKey(cacheName, key);
            SmartCacheProperties.L2Config l2Config = properties.getL2();
            long actualTtl = calculateActualTtl(l2Config.getExpireSeconds());
            redisTemplate.opsForValue().set(redisKey, value, actualTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("L2 cache put failed, cacheName: {}, key: {}, continue with L1 only. Error: {}",
                    cacheName, key, e.getMessage());
        }
    }

    /**
     * 设置缓存值（指定 TTL，秒）
     */
    public void put(String cacheName, String key, Object value, int ttlSeconds) {
        put(cacheName, key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * 设置缓存值（指定 TTL）
     */
    public void put(String cacheName, String key, Object value, long ttl, TimeUnit timeUnit) {
        if (value == null) {
            return;
        }
        try {
            String redisKey = buildKey(cacheName, key);
            redisTemplate.opsForValue().set(redisKey, value, ttl, timeUnit);
        } catch (Exception e) {
            log.warn("L2 cache put failed, cacheName: {}, key: {}, continue with L1 only. Error: {}",
                    cacheName, key, e.getMessage());
        }
    }

    /**
     * 删除缓存值
     */
    public void evict(String cacheName, String key) {
        try {
            String redisKey = buildKey(cacheName, key);
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("L2 cache evict failed, cacheName: {}, key: {}, continue with L1 only. Error: {}",
                    cacheName, key, e.getMessage());
        }
    }

    /**
     * 清空缓存（删除指定 cacheName 的所有 key）
     * 使用 SCAN + Pipeline 流式删除，避免 OOM
     */
    public void clear(String cacheName) {
        try {
            String pattern = buildKeyPattern(cacheName);
            // 使用流式删除，边扫描边删除
            scanAndDelete(pattern);
        } catch (Exception e) {
            log.error("L2 cache clear error, cacheName: {}", cacheName, e);
        }
    }

    /**
     * 流式扫描并删除匹配的 key
     *
     * @param pattern key 匹配模式
     */
    private void scanAndDelete(String pattern) {
        org.springframework.data.redis.connection.RedisConnection connection = null;
        Cursor<byte[]> cursor = null;
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();

            connection = redisTemplate.getConnectionFactory().getConnection();
            cursor = connection.scan(options);

            List<byte[]> batch = new ArrayList<>(1000);
            while (cursor.hasNext()) {
                batch.add(cursor.next());

                // 每 1000 个 key 批量删除一次
                if (batch.size() >= 1000) {
                    deleteBatch(batch);
                    batch.clear();
                }
            }

            // 删除剩余的 key
            if (!batch.isEmpty()) {
                deleteBatch(batch);
            }
        } catch (Exception e) {
            log.error("Scan and delete keys error, pattern: {}", pattern, e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    log.error("Failed to close cursor, pattern: {}", pattern, e);
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    log.error("Failed to close connection, pattern: {}", pattern, e);
                }
            }
        }
    }

    /**
     * 批量删除 key
     *
     * @param keys key 列表
     */
    private void deleteBatch(List<byte[]> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                for (byte[] key : keys) {
                    connection.del(key);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Batch delete keys error, size: {}", keys.size(), e);
        }
    }

    /**
     * 获取缓存大小
     * 使用 SCAN 命令流式统计，避免 OOM
     */
    public long size(String cacheName) {
        try {
            String pattern = buildKeyPattern(cacheName);
            return countKeys(pattern);
        } catch (Exception e) {
            log.error("L2 cache size error, cacheName: {}", cacheName, e);
            return 0;
        }
    }

    /**
     * 流式统计匹配的 key 数量
     *
     * @param pattern key 匹配模式
     * @return key 数量
     */
    private long countKeys(String pattern) {
        org.springframework.data.redis.connection.RedisConnection connection = null;
        Cursor<byte[]> cursor = null;
        long count = 0;
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();

            connection = redisTemplate.getConnectionFactory().getConnection();
            cursor = connection.scan(options);

            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        } catch (Exception e) {
            log.error("Count keys error, pattern: {}", pattern, e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    log.error("Failed to close cursor, pattern: {}", pattern, e);
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    log.error("Failed to close connection, pattern: {}", pattern, e);
                }
            }
        }
        return count;
    }

    /**
     * 批量获取缓存值
     *
     * @param cacheName 缓存名称
     * @param keys      缓存键列表
     * @return 缓存值 Map
     */
    public <T> Map<String, T> getAll(String cacheName, List<String> keys) {
        Map<String, T> result = new HashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        try {
            List<String> redisKeys = new ArrayList<>();
            for (String key : keys) {
                redisKeys.add(buildKey(cacheName, key));
            }
            List<Object> values = redisTemplate.opsForValue().multiGet(redisKeys);
            if (values != null) {
                for (int i = 0; i < keys.size(); i++) {
                    Object value = values.get(i);
                    if (value != null) {
                        result.put(keys.get(i), (T) value);
                    }
                }
            }
        } catch (Exception e) {
            log.error("L2 cache getAll error, cacheName: {}", cacheName, e);
        }
        return result;
    }

    /**
     * 批量设置缓存值
     *
     * @param cacheName 缓存名称
     * @param entries   缓存键值对
     */
    public void putAll(String cacheName, Map<String, Object> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        try {
            SmartCacheProperties.L2Config l2Config = properties.getL2();
            long actualTtl = calculateActualTtl(l2Config.getExpireSeconds());

            // 使用缓存的序列化器实例
            if (valueSerializer == null) {
                log.error("L2 cache putAll error: value serializer is null, cacheName: {}", cacheName);
                return;
            }

            // 使用 pipeline 批量写入
            @SuppressWarnings("unchecked")
            RedisSerializer<Object> serializer = (RedisSerializer<Object>) valueSerializer;
            redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                entries.forEach((key, value) -> {
                    if (value != null) {
                        try {
                            String redisKey = buildKey(cacheName, key);
                            byte[] keyBytes = redisKey.getBytes();
                            byte[] valueBytes = serializer.serialize(value);
                            if (valueBytes != null) {
                                connection.setEx(keyBytes, actualTtl, valueBytes);
                            }
                        } catch (Exception e) {
                            log.error("L2 cache putAll serialize error, cacheName: {}, key: {}", cacheName, key, e);
                        }
                    }
                });
                return null;
            });
        } catch (Exception e) {
            log.error("L2 cache putAll error, cacheName: {}", cacheName, e);
        }
    }
}
