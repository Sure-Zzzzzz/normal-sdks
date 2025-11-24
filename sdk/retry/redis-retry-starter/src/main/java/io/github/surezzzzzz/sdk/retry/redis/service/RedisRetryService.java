package io.github.surezzzzzz.sdk.retry.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.retry.redis.configuration.RedisRetryComponent;
import io.github.surezzzzzz.sdk.retry.redis.configuration.RedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.exception.RedisRetryException;
import io.github.surezzzzzz.sdk.retry.redis.model.RetryInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 重试服务核心实现
 *
 * @author: Sure.
 * @Date: 2025/3/11
 */
@RedisRetryComponent
@Slf4j
public class RedisRetryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisRetryProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisRetryService(@Qualifier("redisRetryTemplate") RedisTemplate<String, String> redisTemplate,
                             RedisRetryProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;

        log.info("初始化 Redis 重试服务，配置参数: {}", properties.toString());

        // 启动时验证配置
        properties.validate();
        log.debug("配置验证通过");
    }

    // ==================== ✅ 新增：类似 TaskRetryExecutor 的完整参数方法 ====================

    /**
     * 记录重试（完整参数，类似 TaskRetryExecutor）
     *
     * @param retryType     重试类型（相当于 prefix）
     * @param retryKey      重试唯一标识（相当于 identifier）
     * @param maxRetryTimes 最大重试次数
     * @param retryInterval 重试间隔
     * @param timeUnit      时间单位
     */
    public void recordRetry(String retryType,
                            String retryKey,
                            Integer maxRetryTimes,
                            Integer retryInterval,
                            TimeUnit timeUnit) {
        log.debug("记录重试: retryType={}, retryKey={}, maxRetryTimes={}, retryInterval={}, timeUnit={}",
                retryType, retryKey, maxRetryTimes, retryInterval, timeUnit);

        try {
            String fullKey = buildRetryKey(retryType, retryKey);
            long currentTime = System.currentTimeMillis();
            long retryIntervalMs = timeUnit.toMillis(retryInterval);

            String existingJson = redisTemplate.opsForValue().get(fullKey);
            RetryInfo retryInfo;

            if (existingJson == null) {
                // ✅ 第一次失败
                retryInfo = new RetryInfo();
                retryInfo.setCount(1);
                retryInfo.setMaxRetryTimes(maxRetryTimes);
                retryInfo.setRetryIntervalMs(retryIntervalMs);
                retryInfo.setFirstFailTime(currentTime);
                retryInfo.setLastFailTime(currentTime);
                retryInfo.setNextRetryTime(currentTime + retryIntervalMs);

                log.debug("记录首次失败: fullKey={}, maxRetryTimes={}, retryIntervalMs={}ms",
                        fullKey, maxRetryTimes, retryIntervalMs);
            } else {
                // ✅ 之前已经失败过
                retryInfo = objectMapper.readValue(existingJson, RetryInfo.class);
                retryInfo.setCount(retryInfo.getCount() + 1);
                retryInfo.setLastFailTime(currentTime);
                retryInfo.setNextRetryTime(currentTime + retryIntervalMs);

                log.debug("更新失败记录: fullKey={}, count={}, nextRetryTime={}",
                        fullKey, retryInfo.getCount(), new Date(retryInfo.getNextRetryTime()));
            }

            // ✅ 保存重试信息，设置TTL
            String updatedJson = objectMapper.writeValueAsString(retryInfo);
            long ttlSeconds = calculateTtl(maxRetryTimes, retryIntervalMs);

            redisTemplate.opsForValue().set(
                    fullKey,
                    updatedJson,
                    ttlSeconds,
                    TimeUnit.SECONDS
            );

            log.info("记录重试信息成功: fullKey={}, count={}, maxRetryTimes={}, ttl={}s",
                    fullKey, retryInfo.getCount(), maxRetryTimes, ttlSeconds);

        } catch (Exception e) {
            log.error("记录重试信息失败: retryType={}, retryKey={}", retryType, retryKey, e);
            throw new RedisRetryException("记录重试信息失败", e);
        }
    }

    /**
     * ✅ 记录重试（带上下文信息）
     */
    public void recordRetry(String retryType,
                            String retryKey,
                            Integer maxRetryTimes,
                            Integer retryInterval,
                            TimeUnit timeUnit,
                            Map<String, Object> context) {
        log.debug("记录重试（带上下文）: retryType={}, retryKey={}, context={}",
                retryType, retryKey, context);

        try {
            String fullKey = buildRetryKey(retryType, retryKey);
            long currentTime = System.currentTimeMillis();
            long retryIntervalMs = timeUnit.toMillis(retryInterval);

            String existingJson = redisTemplate.opsForValue().get(fullKey);
            RetryInfo retryInfo;

            if (existingJson == null) {
                retryInfo = new RetryInfo();
                retryInfo.setCount(1);
                retryInfo.setMaxRetryTimes(maxRetryTimes);
                retryInfo.setRetryIntervalMs(retryIntervalMs);
                retryInfo.setFirstFailTime(currentTime);
                retryInfo.setLastFailTime(currentTime);
                retryInfo.setNextRetryTime(currentTime + retryIntervalMs);
                retryInfo.setContext(context);  // ✅ 设置上下文
            } else {
                retryInfo = objectMapper.readValue(existingJson, RetryInfo.class);
                retryInfo.setCount(retryInfo.getCount() + 1);
                retryInfo.setLastFailTime(currentTime);
                retryInfo.setNextRetryTime(currentTime + retryIntervalMs);

                // ✅ 更新上下文（合并）
                if (context != null) {
                    if (retryInfo.getContext() == null) {
                        retryInfo.setContext(new HashMap<>());
                    }
                    retryInfo.getContext().putAll(context);
                }
            }

            String updatedJson = objectMapper.writeValueAsString(retryInfo);
            long ttlSeconds = calculateTtl(maxRetryTimes, retryIntervalMs);

            redisTemplate.opsForValue().set(
                    fullKey,
                    updatedJson,
                    ttlSeconds,
                    TimeUnit.SECONDS
            );

            log.info("记录重试信息成功（带上下文）: fullKey={}, count={}, contextKeys={}",
                    fullKey, retryInfo.getCount(), context != null ? context.keySet() : "null");

        } catch (Exception e) {
            log.error("记录重试信息失败: retryType={}, retryKey={}", retryType, retryKey, e);
            throw new RedisRetryException("记录重试信息失败", e);
        }
    }

    /**
     * ✅ 检查是否可以重试（考虑时间间隔）
     */
    public boolean canRetry(String retryType, String retryKey) {
        log.debug("检查重试状态: retryType={}, retryKey={}", retryType, retryKey);

        try {
            String fullKey = buildRetryKey(retryType, retryKey);
            String retryInfoJson = redisTemplate.opsForValue().get(fullKey);

            if (retryInfoJson == null) {
                log.debug("未找到重试记录，可以重试: {}", fullKey);
                return true; // 第一次处理
            }

            RetryInfo retryInfo = objectMapper.readValue(retryInfoJson, RetryInfo.class);
            long currentTime = System.currentTimeMillis();

            // ✅ 检查重试次数
            if (retryInfo.getCount() >= retryInfo.getMaxRetryTimes()) {
                log.warn("已达到最大重试次数: fullKey={}, count={}, maxRetryTimes={}",
                        fullKey, retryInfo.getCount(), retryInfo.getMaxRetryTimes());
                return false;
            }

            // ✅ 检查重试间隔
            if (currentTime < retryInfo.getNextRetryTime()) {
                long remainingMs = retryInfo.getNextRetryTime() - currentTime;
                log.debug("重试间隔未到: fullKey={}, 剩余时间={}ms", fullKey, remainingMs);
                return false;
            }

            log.debug("可以重试: fullKey={}, count={}, maxRetryTimes={}",
                    fullKey, retryInfo.getCount(), retryInfo.getMaxRetryTimes());
            return true;

        } catch (Exception e) {
            log.error("检查重试状态失败: retryType={}, retryKey={}", retryType, retryKey, e);
            throw new RedisRetryException("检查重试状态失败", e);
        }
    }

    /**
     * ✅ 清除重试记录（成功后调用）
     */
    public void clearRetry(String retryType, String retryKey) {
        log.debug("清除重试记录: retryType={}, retryKey={}", retryType, retryKey);

        try {
            String fullKey = buildRetryKey(retryType, retryKey);
            Boolean deleted = redisTemplate.delete(fullKey);
            log.info("清除重试记录: fullKey={}, 删除结果={}", fullKey, deleted);
        } catch (Exception e) {
            log.error("清除重试记录失败: retryType={}, retryKey={}", retryType, retryKey, e);
            throw new RedisRetryException("清除重试记录失败", e);
        }
    }

    /**
     * ✅ 获取所有重试记录的 key（用于定时任务扫描）
     */
    public List<String> getRetryKeys(String retryType) {
        log.debug("获取所有重试记录: retryType={}", retryType);

        try {
            String pattern;
            boolean shouldUseHashTag = shouldUseHashTag();

            if (shouldUseHashTag) {
                pattern = "{" + retryType + "}:retry:*";
            } else {
                pattern = retryType + ":retry:*";
            }

            Set<String> keys = redisTemplate.keys(pattern);
            List<String> result = keys != null ? new ArrayList<>(keys) : new ArrayList<>();

            log.debug("找到重试记录: retryType={}, count={}", retryType, result.size());
            return result;

        } catch (Exception e) {
            log.error("获取重试记录失败: retryType={}", retryType, e);
            return new ArrayList<>();
        }
    }

    /**
     * ✅ 获取当前重试信息
     */
    public RetryInfo getCurrentRetryInfo(String retryType, String retryKey) {
        log.debug("获取重试信息: retryType={}, retryKey={}", retryType, retryKey);

        try {
            String fullKey = buildRetryKey(retryType, retryKey);
            String retryInfoJson = redisTemplate.opsForValue().get(fullKey);

            if (retryInfoJson == null) {
                log.debug("未找到重试记录: {}", fullKey);
                return null;
            }

            RetryInfo retryInfo = objectMapper.readValue(retryInfoJson, RetryInfo.class);
            log.debug("获取重试信息成功: fullKey={}, count={}, maxRetryTimes={}",
                    fullKey, retryInfo.getCount(), retryInfo.getMaxRetryTimes());

            return retryInfo;
        } catch (Exception e) {
            log.error("获取重试信息失败: retryType={}, retryKey={}", retryType, retryKey, e);
            throw new RedisRetryException("获取重试信息失败", e);
        }
    }

    // ==================== 原有方法（向后兼容）====================

    /**
     * 检查是否可以重试（完整key）
     */
    public boolean canRetry(String fullKey) {
        log.debug("检查重试状态: {}", fullKey);

        try {
            String retryInfoJson = redisTemplate.opsForValue().get(fullKey);
            if (retryInfoJson == null) {
                log.debug("未找到重试记录，可以重试: {}", fullKey);
                return true;
            }

            RetryInfo retryInfo = objectMapper.readValue(retryInfoJson, RetryInfo.class);

            // ✅ 兼容旧版本（没有 maxRetryTimes 字段）
            int maxRetryTimes = retryInfo.getMaxRetryTimes() != null
                    ? retryInfo.getMaxRetryTimes()
                    : properties.getMaxRetryCount();

            boolean canRetry = retryInfo.getCount() < maxRetryTimes;

            log.debug("重试记录检查完成: key={}, 当前次数={}, 最大次数={}, 可重试={}",
                    fullKey, retryInfo.getCount(), maxRetryTimes, canRetry);

            return canRetry;

        } catch (Exception e) {
            log.error("检查重试状态失败: {}", fullKey, e);
            throw new RedisRetryException("检查重试状态失败: " + fullKey, e);
        }
    }

    /**
     * 记录失败并增加重试次数（完整key）
     */
    public void recordFailure(String fullKey, Exception error) {
        log.debug("记录失败信息: key={}, error={}", fullKey, error.getMessage());

        try {
            long currentTime = System.currentTimeMillis();
            String existingJson = redisTemplate.opsForValue().get(fullKey);
            RetryInfo retryInfo;

            if (existingJson == null) {
                retryInfo = new RetryInfo();
                retryInfo.setCount(1);
                retryInfo.setFirstFailTime(currentTime);
                retryInfo.setLastFailTime(currentTime);
                retryInfo.setLastError(error.getMessage());
                log.debug("记录首次失败: key={}, 错误信息={}", fullKey, error.getMessage());
            } else {
                retryInfo = objectMapper.readValue(existingJson, RetryInfo.class);
                retryInfo.setCount(retryInfo.getCount() + 1);
                retryInfo.setLastFailTime(currentTime);
                retryInfo.setLastError(error.getMessage());
                log.debug("更新失败记录: key={}, 重试次数={}, 错误信息={}",
                        fullKey, retryInfo.getCount(), error.getMessage());
            }

            String updatedJson = objectMapper.writeValueAsString(retryInfo);
            redisTemplate.opsForValue().set(
                    fullKey,
                    updatedJson,
                    properties.getRetryRecordTtlSeconds(),
                    TimeUnit.SECONDS
            );

            log.info("记录重试信息成功: key={}, count={}, ttl={}s",
                    fullKey, retryInfo.getCount(), properties.getRetryRecordTtlSeconds());

        } catch (Exception e) {
            log.error("记录重试信息失败: {}", fullKey, e);
            throw new RedisRetryException("记录重试信息失败: " + fullKey, e);
        }
    }

    /**
     * 获取当前重试信息（完整key）
     */
    public RetryInfo getCurrentRetryInfo(String fullKey) {
        log.debug("获取重试信息: {}", fullKey);

        try {
            String retryInfoJson = redisTemplate.opsForValue().get(fullKey);
            if (retryInfoJson == null) {
                log.debug("未找到重试记录，返回默认信息: {}", fullKey);
                RetryInfo defaultInfo = new RetryInfo();
                defaultInfo.setCount(0);
                defaultInfo.setFirstFailTime(0L);
                defaultInfo.setLastFailTime(0L);
                return defaultInfo;
            }

            RetryInfo retryInfo = objectMapper.readValue(retryInfoJson, RetryInfo.class);
            log.debug("获取重试信息成功: key={}, count={}, firstFailTime={}, lastFailTime={}",
                    fullKey, retryInfo.getCount(), retryInfo.getFirstFailTime(), retryInfo.getLastFailTime());

            return retryInfo;
        } catch (Exception e) {
            log.error("获取重试信息失败: {}", fullKey, e);
            throw new RedisRetryException("获取重试信息失败: " + fullKey, e);
        }
    }

    /**
     * 清除重试记录（完整key）
     */
    public void clearRetryRecord(String fullKey) {
        log.debug("清除重试记录: {}", fullKey);

        try {
            Boolean deleted = redisTemplate.delete(fullKey);
            log.info("清除重试记录: key={}, 删除结果={}", fullKey, deleted);
        } catch (Exception e) {
            log.error("清除重试记录失败: {}", fullKey, e);
            throw new RedisRetryException("清除重试记录失败: " + fullKey, e);
        }
    }

    // ==================== 便捷方法（向后兼容）====================

    public void recordFailure(String prefix, String identifier, Exception error) {
        String fullKey = buildRetryKey(prefix, identifier);
        log.debug("便捷方法记录失败: prefix={}, identifier={}, fullKey={}", prefix, identifier, fullKey);
        recordFailure(fullKey, error);
    }

    public void clearRetryRecord(String prefix, String identifier) {
        String fullKey = buildRetryKey(prefix, identifier);
        log.debug("便捷方法清除记录: prefix={}, identifier={}, fullKey={}", prefix, identifier, fullKey);
        clearRetryRecord(fullKey);
    }

    // ==================== 工具方法 ====================

    /**
     * ✅ 计算 TTL（基于最大重试次数和重试间隔）
     */
    private long calculateTtl(int maxRetryTimes, long retryIntervalMs) {
        // TTL = 最大重试次数 * 重试间隔 + 额外缓冲时间（1小时）
        long ttlMs = maxRetryTimes * retryIntervalMs + TimeUnit.HOURS.toMillis(1);
        return TimeUnit.MILLISECONDS.toSeconds(ttlMs);
    }

    /**
     * 计算重试延迟时间（指数退避）
     */
    public long calculateRetryDelay(int retryCount) {
        if (retryCount <= 0) {
            return 0;
        }
        long delay = properties.getBaseDelayMs() * (1L << (retryCount - 1));
        long finalDelay = Math.min(delay, properties.getMaxDelayMs());

        log.debug("计算重试延迟: retryCount={}, baseDelay={}ms, 计算延迟={}ms, 最终延迟={}ms",
                retryCount, properties.getBaseDelayMs(), delay, finalDelay);

        return finalDelay;
    }

    /**
     * 构建重试key
     */
    public String buildRetryKey(String prefix, String identifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(identifier.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String identifierHash = sb.toString().toUpperCase();

            boolean shouldUseHashTag = shouldUseHashTag();
            String key;

            if (shouldUseHashTag) {
                key = String.format("{%s}:retry:%s", prefix, identifierHash);
                log.debug("构建Cluster重试key: prefix={}, identifier={}, hash={}, key={}",
                        prefix, identifier, identifierHash.substring(0, 8) + "...", key);
            } else {
                key = String.format("%s:retry:%s", prefix, identifierHash);
                log.debug("构建单机重试key: prefix={}, identifier={}, hash={}, key={}",
                        prefix, identifier, identifierHash.substring(0, 8) + "...", key);
            }

            return key;
        } catch (Exception e) {
            log.warn("生成标识符摘要失败，使用hashCode降级: prefix={}, identifier={}, error={}",
                    prefix, identifier, e.getMessage());
            int identifierHash = Math.abs(identifier.hashCode());

            boolean shouldUseHashTag = shouldUseHashTag();
            String key;

            if (shouldUseHashTag) {
                key = String.format("{%s}:retry:%d", prefix, identifierHash);
            } else {
                key = String.format("%s:retry:%d", prefix, identifierHash);
            }

            log.debug("降级构建重试key: prefix={}, identifier={}, hashCode={}, key={}, cluster={}",
                    prefix, identifier, identifierHash, key, shouldUseHashTag);

            return key;
        }
    }

    private boolean shouldUseHashTag() {
        try {
            if (properties.getForceHashTag() != null) {
                log.debug("使用手动配置的hash tag策略: {}", properties.getForceHashTag());
                return properties.getForceHashTag();
            }

            boolean isCluster = detectRedisCluster();
            log.debug("自动检测Redis环境: cluster={}", isCluster);
            return isCluster;

        } catch (Exception e) {
            log.debug("检测Redis环境失败，默认不使用hash tag: {}", e.getMessage());
            return false;
        }
    }

    private boolean detectRedisCluster() {
        try {
            String connectionFactoryClass = redisTemplate.getConnectionFactory().getClass().getName();
            log.debug("Redis连接工厂类型: {}", connectionFactoryClass);

            if (connectionFactoryClass.contains("Cluster") ||
                    connectionFactoryClass.contains("cluster")) {
                log.debug("通过连接工厂检测到Cluster环境");
                return true;
            }

            try {
                Object nativeConnection = redisTemplate.getConnectionFactory()
                        .getConnection()
                        .getNativeConnection();

                if (nativeConnection != null) {
                    String connectionType = nativeConnection.getClass().getName();
                    log.debug("原生连接类型: {}", connectionType);

                    if (connectionType.contains("Cluster") ||
                            connectionType.contains("cluster")) {
                        log.debug("通过原生连接检测到Cluster环境");
                        return true;
                    }
                }
            } catch (Exception e) {
                log.debug("获取原生连接失败: {}", e.getMessage());
            }

            log.debug("未检测到Cluster特征，判定为单机环境");
            return false;

        } catch (Exception e) {
            log.warn("检测Redis环境时发生异常，默认为单机环境: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 构建器模式（向后兼容）====================

    public RetryContext withPrefix(String prefix) {
        log.debug("创建重试上下文: prefix={}", prefix);
        return new RetryContextImpl(prefix);
    }

    private class RetryContextImpl implements RetryContext {
        private final String prefix;

        public RetryContextImpl(String prefix) {
            this.prefix = prefix;
            log.debug("创建重试上下文实例: prefix={}", prefix);
        }

        @Override
        public boolean canRetry(String identifier) {
            log.debug("上下文检查重试: prefix={}, identifier={}", prefix, identifier);
            return RedisRetryService.this.canRetry(prefix, identifier);
        }

        @Override
        public void recordFailure(String identifier, Exception error) {
            log.debug("上下文记录失败: prefix={}, identifier={}, error={}", prefix, identifier, error.getMessage());
            RedisRetryService.this.recordFailure(prefix, identifier, error);
        }

        @Override
        public RetryInfo getCurrentRetryInfo(String identifier) {
            log.debug("上下文获取信息: prefix={}, identifier={}", prefix, identifier);
            return RedisRetryService.this.getCurrentRetryInfo(prefix, identifier);
        }

        @Override
        public long calculateRetryDelay(String identifier) {
            log.debug("上下文计算延迟: prefix={}, identifier={}", prefix, identifier);
            RetryInfo retryInfo = getCurrentRetryInfo(identifier);
            return RedisRetryService.this.calculateRetryDelay(retryInfo.getCount());
        }

        @Override
        public void clearRetryRecord(String identifier) {
            log.debug("上下文清除记录: prefix={}, identifier={}", prefix, identifier);
            RedisRetryService.this.clearRetryRecord(prefix, identifier);
        }

        @Override
        public String buildRetryKey(String identifier) {
            return RedisRetryService.this.buildRetryKey(prefix, identifier);
        }
    }


    /**
     * 判断是否为重试处理
     *
     * @param retryInfo 重试信息
     * @return true=重试处理，false=首次处理
     */
    public boolean isRetryAttempt(RetryInfo retryInfo) {
        return retryInfo != null && retryInfo.getCount() > 0;
    }

    /**
     * 获取重试次数（安全方法）
     *
     * @param retryInfo 重试信息
     * @return 重试次数，首次处理返回0
     */
    public int getRetryCount(RetryInfo retryInfo) {
        return retryInfo != null ? retryInfo.getCount() : 0;
    }
}
