package io.github.surezzzzzz.sdk.retry.redis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.retry.redis.annotation.RedisRetryComponent;
import io.github.surezzzzzz.sdk.retry.redis.configuration.RedisRetryProperties;
import io.github.surezzzzzz.sdk.retry.redis.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.retry.redis.constant.RedisRetryConstant;
import io.github.surezzzzzz.sdk.retry.redis.exception.RedisRetryException;
import io.github.surezzzzzz.sdk.retry.redis.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.support.RetryKeyHelper;
import io.github.surezzzzzz.sdk.retry.redis.support.RetryValidationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 重试服务
 *
 * @author surezzzzzz
 */
@Slf4j
@RedisRetryComponent
public class RedisRetryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisRetryProperties properties;
    private final RetryKeyHelper retryKeyHelper;
    private final ObjectMapper objectMapper;

    public RedisRetryService(@Qualifier("redisRetryTemplate") RedisTemplate<String, String> redisTemplate,
                             RedisRetryProperties properties,
                             RetryKeyHelper retryKeyHelper) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.retryKeyHelper = retryKeyHelper;
        this.objectMapper = new ObjectMapper();
        RetryValidationHelper.validateProperties(properties);
        log.info("初始化 Redis 重试服务，keyPrefix={}, me={}", properties.getKeyPrefix(), properties.getMe());
    }

    /**
     * 记录重试
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @param maxRetryTimes 最大重试次数
     * @param retryInterval 重试间隔
     * @param timeUnit 时间单位
     */
    public void recordRetry(String retryType,
                            String retryKey,
                            Integer maxRetryTimes,
                            Integer retryInterval,
                            TimeUnit timeUnit) {
        recordRetry(retryType, retryKey, maxRetryTimes, retryInterval, timeUnit, null);
    }

    /**
     * 记录重试
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @param maxRetryTimes 最大重试次数
     * @param retryInterval 重试间隔
     * @param timeUnit 时间单位
     * @param context 上下文信息
     */
    public void recordRetry(String retryType,
                            String retryKey,
                            Integer maxRetryTimes,
                            Integer retryInterval,
                            TimeUnit timeUnit,
                            Map<String, Object> context) {
        try {
            String standardKey = buildRetryKey(retryType, retryKey);
            String legacyKey = buildLegacyRetryKey(retryType, retryKey);
            String existingKey = resolveExistingKey(standardKey, legacyKey);
            String existingJson = redisTemplate.opsForValue().get(existingKey);
            long currentTime = System.currentTimeMillis();
            long retryIntervalMs = timeUnit.toMillis(retryInterval);
            RetryInfo retryInfo = buildRetryInfo(existingJson, maxRetryTimes, retryIntervalMs, currentTime, context);
            saveRetryInfo(standardKey, retryInfo, maxRetryTimes, retryIntervalMs);
            deleteLegacyIfNecessary(standardKey, existingKey);
            log.info("记录重试信息成功: key={}, count={}", standardKey, retryInfo.getCount());
        } catch (Exception e) {
            log.error("记录重试信息失败: retryType={}, retryKey={}", retryType, retryKey, e);
            throw new RedisRetryException(ErrorMessage.RECORD_RETRY_FAILED, e);
        }
    }

    /**
     * 检查是否可以重试
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @return true 可以重试，false 不可重试
     */
    public boolean canRetry(String retryType, String retryKey) {
        try {
            RetryInfo retryInfo = getCurrentRetryInfo(retryType, retryKey);
            if (retryInfo == null) {
                return true;
            }
            if (retryInfo.getCount() >= retryInfo.getMaxRetryTimes()) {
                return false;
            }
            return System.currentTimeMillis() >= retryInfo.getNextRetryTime();
        } catch (Exception e) {
            log.error("检查重试状态失败: retryType={}, retryKey={}", retryType, retryKey, e);
            throw new RedisRetryException(ErrorMessage.CHECK_RETRY_FAILED, e);
        }
    }

    /**
     * 清除重试记录
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     */
    public void clearRetry(String retryType, String retryKey) {
        try {
            String standardKey = buildRetryKey(retryType, retryKey);
            String legacyKey = buildLegacyRetryKey(retryType, retryKey);
            redisTemplate.delete(standardKey);
            redisTemplate.delete(legacyKey);
            log.info("清除重试记录: key={}, legacyKey={}", standardKey, legacyKey);
        } catch (Exception e) {
            log.error("清除重试记录失败: retryType={}, retryKey={}", retryType, retryKey, e);
            throw new RedisRetryException(ErrorMessage.CLEAR_RETRY_FAILED, e);
        }
    }

    /**
     * 获取重试记录 Key 列表
     *
     * @param retryType 重试类型
     * @return 重试记录 Key 列表
     */
    public List<String> getRetryKeys(String retryType) {
        try {
            Set<String> keys = new HashSet<>();
            Set<String> standardKeys = redisTemplate.keys(retryKeyHelper.buildStandardKeysPattern(retryType));
            Set<String> legacyKeys = redisTemplate.keys(retryKeyHelper.buildLegacyKeysPattern(retryType, redisTemplate));
            if (standardKeys != null) {
                keys.addAll(standardKeys);
            }
            if (legacyKeys != null) {
                keys.addAll(legacyKeys);
            }
            return new ArrayList<>(keys);
        } catch (Exception e) {
            log.error("获取重试记录失败: retryType={}", retryType, e);
            throw new RedisRetryException(ErrorMessage.QUERY_RETRY_KEYS_FAILED, e);
        }
    }

    /**
     * 获取当前重试信息
     *
     * @param retryType 重试类型
     * @param retryKey 重试标识
     * @return 当前重试信息
     */
    public RetryInfo getCurrentRetryInfo(String retryType, String retryKey) {
        try {
            String standardKey = buildRetryKey(retryType, retryKey);
            String legacyKey = buildLegacyRetryKey(retryType, retryKey);
            String retryInfoJson = redisTemplate.opsForValue().get(standardKey);
            if (retryInfoJson == null) {
                retryInfoJson = redisTemplate.opsForValue().get(legacyKey);
            }
            if (retryInfoJson == null) {
                return null;
            }
            return objectMapper.readValue(retryInfoJson, RetryInfo.class);
        } catch (Exception e) {
            log.error("获取重试信息失败: retryType={}, retryKey={}", retryType, retryKey, e);
            throw new RedisRetryException(ErrorMessage.QUERY_RETRY_FAILED, e);
        }
    }

    /**
     * 检查完整 Key 是否可以重试
     *
     * @param fullKey 完整 Key
     * @return true 可以重试，false 不可重试
     */
    public boolean canRetry(String fullKey) {
        try {
            RetryInfo retryInfo = readRetryInfo(fullKey);
            if (retryInfo == null) {
                return true;
            }
            int maxRetryTimes = retryInfo.getMaxRetryTimes() != null ? retryInfo.getMaxRetryTimes() : properties.getMaxRetryCount();
            return retryInfo.getCount() < maxRetryTimes;
        } catch (Exception e) {
            log.error("检查重试状态失败: key={}", fullKey, e);
            throw new RedisRetryException(ErrorMessage.CHECK_RETRY_FAILED, e);
        }
    }

    /**
     * 记录完整 Key 失败
     *
     * @param fullKey 完整 Key
     * @param error 异常
     */
    public void recordFailure(String fullKey, Exception error) {
        try {
            long currentTime = System.currentTimeMillis();
            RetryInfo retryInfo = readRetryInfo(fullKey);
            if (retryInfo == null) {
                retryInfo = new RetryInfo();
                retryInfo.setCount(1);
                retryInfo.setFirstFailTime(currentTime);
            } else {
                retryInfo.setCount(retryInfo.getCount() + 1);
            }
            retryInfo.setLastFailTime(currentTime);
            retryInfo.setLastError(error.getMessage());
            String updatedJson = objectMapper.writeValueAsString(retryInfo);
            redisTemplate.opsForValue().set(fullKey, updatedJson, properties.getRetryRecordTtlSeconds(), TimeUnit.SECONDS);
            log.info("记录重试信息成功: key={}, count={}", fullKey, retryInfo.getCount());
        } catch (Exception e) {
            log.error("记录重试信息失败: key={}", fullKey, e);
            throw new RedisRetryException(ErrorMessage.RECORD_RETRY_FAILED, e);
        }
    }

    /**
     * 获取完整 Key 当前重试信息
     *
     * @param fullKey 完整 Key
     * @return 当前重试信息
     */
    public RetryInfo getCurrentRetryInfo(String fullKey) {
        try {
            RetryInfo retryInfo = readRetryInfo(fullKey);
            if (retryInfo != null) {
                return retryInfo;
            }
            RetryInfo defaultInfo = new RetryInfo();
            defaultInfo.setCount(0);
            defaultInfo.setFirstFailTime(0L);
            defaultInfo.setLastFailTime(0L);
            return defaultInfo;
        } catch (Exception e) {
            log.error("获取重试信息失败: key={}", fullKey, e);
            throw new RedisRetryException(ErrorMessage.QUERY_RETRY_FAILED, e);
        }
    }

    /**
     * 清除完整 Key 重试记录
     *
     * @param fullKey 完整 Key
     */
    public void clearRetryRecord(String fullKey) {
        try {
            redisTemplate.delete(fullKey);
            log.info("清除重试记录: key={}", fullKey);
        } catch (Exception e) {
            log.error("清除重试记录失败: key={}", fullKey, e);
            throw new RedisRetryException(ErrorMessage.CLEAR_RETRY_FAILED, e);
        }
    }

    /**
     * 记录失败
     *
     * @param prefix 前缀
     * @param identifier 标识
     * @param error 异常
     */
    public void recordFailure(String prefix, String identifier, Exception error) {
        recordFailure(buildRetryKey(prefix, identifier), error);
    }

    /**
     * 清除重试记录
     *
     * @param prefix 前缀
     * @param identifier 标识
     */
    public void clearRetryRecord(String prefix, String identifier) {
        clearRetry(prefix, identifier);
    }

    /**
     * 计算重试延迟时间
     *
     * @param retryCount 重试次数
     * @return 延迟时间，单位毫秒
     */
    public long calculateRetryDelay(int retryCount) {
        if (retryCount <= 0) {
            return 0;
        }
        long delay = properties.getBaseDelayMs() * (1L << (retryCount - 1));
        return Math.min(delay, properties.getMaxDelayMs());
    }

    /**
     * 构建重试 Key
     *
     * @param prefix 前缀
     * @param identifier 标识
     * @return 标准重试 Key
     */
    public String buildRetryKey(String prefix, String identifier) {
        return retryKeyHelper.buildStandardKey(prefix, identifier, redisTemplate);
    }

    /**
     * 创建带前缀的重试上下文
     *
     * @param prefix 前缀
     * @return 重试上下文
     */
    public RetryContext withPrefix(String prefix) {
        return new RetryContextImpl(prefix);
    }

    /**
     * 判断是否为重试处理
     *
     * @param retryInfo 重试信息
     * @return true 是重试处理，false 首次处理
     */
    public boolean isRetryAttempt(RetryInfo retryInfo) {
        return retryInfo != null && retryInfo.getCount() > 0;
    }

    /**
     * 获取重试次数
     *
     * @param retryInfo 重试信息
     * @return 重试次数
     */
    public int getRetryCount(RetryInfo retryInfo) {
        return retryInfo != null && retryInfo.getCount() != null ? retryInfo.getCount() : 0;
    }

    private RetryInfo buildRetryInfo(String existingJson,
                                     Integer maxRetryTimes,
                                     long retryIntervalMs,
                                     long currentTime,
                                     Map<String, Object> context) throws Exception {
        RetryInfo retryInfo;
        if (existingJson == null) {
            retryInfo = new RetryInfo();
            retryInfo.setCount(1);
            retryInfo.setMaxRetryTimes(maxRetryTimes);
            retryInfo.setRetryIntervalMs(retryIntervalMs);
            retryInfo.setFirstFailTime(currentTime);
        } else {
            retryInfo = objectMapper.readValue(existingJson, RetryInfo.class);
            retryInfo.setCount(retryInfo.getCount() + 1);
        }
        retryInfo.setLastFailTime(currentTime);
        retryInfo.setNextRetryTime(currentTime + retryIntervalMs);
        mergeContext(retryInfo, context);
        return retryInfo;
    }

    private void saveRetryInfo(String fullKey, RetryInfo retryInfo, int maxRetryTimes, long retryIntervalMs) throws Exception {
        String updatedJson = objectMapper.writeValueAsString(retryInfo);
        redisTemplate.opsForValue().set(fullKey, updatedJson, calculateTtl(maxRetryTimes, retryIntervalMs), TimeUnit.SECONDS);
    }

    private RetryInfo readRetryInfo(String fullKey) throws Exception {
        String retryInfoJson = redisTemplate.opsForValue().get(fullKey);
        if (retryInfoJson == null) {
            return null;
        }
        return objectMapper.readValue(retryInfoJson, RetryInfo.class);
    }

    private void mergeContext(RetryInfo retryInfo, Map<String, Object> context) {
        if (context == null) {
            return;
        }
        if (retryInfo.getContext() == null) {
            retryInfo.setContext(new HashMap<String, Object>());
        }
        retryInfo.getContext().putAll(context);
    }

    private String buildLegacyRetryKey(String retryType, String retryKey) {
        return retryKeyHelper.buildLegacyKey(retryType, retryKey, redisTemplate);
    }

    private String resolveExistingKey(String standardKey, String legacyKey) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(standardKey))) {
            return standardKey;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(legacyKey))) {
            return legacyKey;
        }
        return standardKey;
    }

    private void deleteLegacyIfNecessary(String standardKey, String existingKey) {
        if (!standardKey.equals(existingKey)) {
            redisTemplate.delete(existingKey);
        }
    }

    private long calculateTtl(int maxRetryTimes, long retryIntervalMs) {
        long ttlMs = maxRetryTimes * retryIntervalMs + TimeUnit.HOURS.toMillis(RedisRetryConstant.TTL_BUFFER_HOURS);
        return TimeUnit.MILLISECONDS.toSeconds(ttlMs);
    }

    @RequiredArgsConstructor
    private class RetryContextImpl implements RetryContext {
        private final String prefix;

        @Override
        public boolean canRetry(String identifier) {
            return RedisRetryService.this.canRetry(prefix, identifier);
        }

        @Override
        public void recordFailure(String identifier, Exception error) {
            RedisRetryService.this.recordFailure(prefix, identifier, error);
        }

        @Override
        public RetryInfo getCurrentRetryInfo(String identifier) {
            return RedisRetryService.this.getCurrentRetryInfo(prefix, identifier);
        }

        @Override
        public long calculateRetryDelay(String identifier) {
            RetryInfo retryInfo = getCurrentRetryInfo(identifier);
            return RedisRetryService.this.calculateRetryDelay(getRetryCount(retryInfo));
        }

        @Override
        public void clearRetryRecord(String identifier) {
            RedisRetryService.this.clearRetry(prefix, identifier);
        }

        @Override
        public String buildRetryKey(String identifier) {
            return RedisRetryService.this.buildRetryKey(prefix, identifier);
        }
    }
}
