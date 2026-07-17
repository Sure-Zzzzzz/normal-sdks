package io.github.surezzzzzz.sdk.retry.redis.smart.script;

import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryFailure;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryInfo;
import io.github.surezzzzzz.sdk.retry.redis.smart.model.RetryPolicy;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 重试脚本执行器
 *
 * @author surezzzzzz
 */
public interface RedisRetryScriptExecutor {

    /**
     * 原子记录失败信息
     *
     * @param template Redis 操作模板
     * @param redisKey Redis 记录 Key
     * @param failure 失败信息
     * @param policy 重试策略
     * @param nowMillis 当前时间毫秒值
     * @param ttlMillis 记录存活时间毫秒值
     * @return 最新重试状态
     */
    RetryInfo recordFailure(StringRedisTemplate template,
                            String redisKey,
                            RetryFailure failure,
                            RetryPolicy policy,
                            long nowMillis,
                            long ttlMillis);

    /**
     * 原子读取并删除重试状态。
     *
     * @param template Redis 操作模板
     * @param redisKey Redis 记录 Key
     * @param expectedCount 期望的失败次数，空值表示不校验
     * @return 被删除的重试状态；记录不存在或失败次数已变化时返回 null
     */
    RetryInfo clear(StringRedisTemplate template, String redisKey, Integer expectedCount);
}
